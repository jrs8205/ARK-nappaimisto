package org.jarsi.ark.engine

import java.util.Locale

/**
 * Henkilökohtaisten sanojen oppiminen: sanat ja sanaketjut (parit ja kolmikot)
 * pidetään muistissa ja muutokset kerätään eriin, jotka kutsuja kirjoittaa
 * tietokantaan [drainDirty]-kutsulla. Julkiset metodit on synkronoitu, koska
 * ehdotushaku lukee rakenteita taustasäikeessä samalla kun päälanka oppii.
 */
class LearningEngine(private val clock: () -> Long = System::currentTimeMillis) {

    private class WordState(
        val word: String,
        var count: Int,
        var lastUsed: Long,
        var blocked: Boolean,
        val created: Long,
        var acceptedCount: Int = 0,
        var ignoredCount: Int = 0,
        var pinned: Boolean = false,
    )

    private class CountState(var count: Int, var lastUsed: Long)

    private val fiLocale = Locale.forLanguageTag("fi")
    private val words = HashMap<String, WordState>()

    // Ketjut järjestettynä ennustushakua varten: konteksti → jatkot.
    private val bigrams = HashMap<String, HashMap<String, CountState>>()
    private val trigrams = HashMap<Pair<String, String>, HashMap<String, CountState>>()

    private val dirtyWords = HashSet<String>()
    private val dirtyBigrams = HashSet<Pair<String, String>>()
    private val dirtyTrigrams = HashSet<Triple<String, String, String>>()
    private val removedWords = HashSet<String>()
    private val removedChainWords = HashSet<String>()

    // Kylmäkäynnistyksessä kirjoitetut sanat odottavat tietokannan latausta.
    private val pendingCommits = ArrayList<String>()

    private var previousToken: String? = null
    private var beforePreviousToken: String? = null
    private var loaded = false

    val isLoaded: Boolean get() = loaded
    val dirtyCount: Int
        get() = dirtyWords.size + dirtyBigrams.size + dirtyTrigrams.size + removedWords.size

    private fun keyOf(word: String) = word.lowercase(fiLocale)

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '-' || c == '.'

    /** Sanastoon kelpaa 2–32 merkin sana, jossa on ainakin yksi kirjain. */
    private fun isEligibleWord(word: String) =
        word.length in 2..32 && word.any { it.isLetter() } && word.all { isWordChar(it) }

    /** Ketjuun kelpaa myös pelkkä numero (esim. "20"). */
    private fun isChainToken(word: String) =
        word.length in 1..32 && word.all { isWordChar(it) }

    @Synchronized
    fun load(
        loadedWords: List<LearnedWord>,
        loadedBigrams: List<LearnedBigram>,
        loadedTrigrams: List<LearnedTrigram>,
    ) {
        words.clear()
        bigrams.clear()
        trigrams.clear()
        for (w in loadedWords) {
            words[keyOf(w.word)] = WordState(
                w.word, w.count, w.lastUsed, w.blocked, w.created,
                w.acceptedCount, w.ignoredCount, w.pinned,
            )
        }
        for (b in loadedBigrams) {
            bigrams.getOrPut(b.previous) { HashMap() }[b.next] = CountState(b.count, b.lastUsed)
        }
        for (t in loadedTrigrams) {
            trigrams.getOrPut(t.first to t.second) { HashMap() }[t.next] =
                CountState(t.count, t.lastUsed)
        }
        loaded = true
        // Latauksen aikana kirjoitetut sanat opitaan jälkikäteen järjestyksessä.
        val pending = pendingCommits.toList()
        pendingCommits.clear()
        pending.forEach { onWordCommitted(it) }
    }

    /** Käsin kirjoitettu sana päättyi: opi sana ja kirjaa ketjut. */
    @Synchronized
    fun onWordCommitted(word: String) {
        if (!loaded) {
            if (pendingCommits.size < PENDING_LIMIT) pendingCommits += word
            return
        }
        if (isEligibleWord(word)) {
            val key = keyOf(word)
            val state = words.getOrPut(key) { WordState(word, 0, clock(), false, clock()) }
            state.count++
            state.lastUsed = clock()
            dirtyWords += key
        }
        chain(word)
    }

    /**
     * Ehdotus valittiin: hyväksyntä kirjautuu (myös yleissanalle) ja nollaa
     * ohitukset; käyttömäärä kasvaa vain omilla sanoilla. Ketju jatkuu aina.
     */
    @Synchronized
    fun onSuggestionAccepted(word: String) {
        if (!loaded) return
        val key = keyOf(word)
        val state = words.getOrPut(key) { WordState(word, 0, clock(), false, clock()) }
        if (state.count > 0) state.count++
        state.acceptedCount++
        state.ignoredCount = 0
        state.lastUsed = clock()
        dirtyWords += key
        chain(word)
    }

    /**
     * Käyttäjä napautti rivillä näkyvää omaa kirjoitettua sanaansa: sana
     * opitaan kuin kirjoitettuna ja hyväksyntä kirjataan samalla kertaa.
     * Ketju jatkuu vain kerran, ettei synny sana→sana-tuplabigramia.
     */
    @Synchronized
    fun onTypedWordAccepted(word: String) {
        if (!loaded) return
        if (isEligibleWord(word)) {
            val key = keyOf(word)
            val state = words.getOrPut(key) { WordState(word, 0, clock(), false, clock()) }
            state.count++
            state.acceptedCount++
            state.ignoredCount = 0
            state.lastUsed = clock()
            dirtyWords += key
        }
        chain(word)
    }

    /**
     * Sana korvattiin vaihtoehtolistasta jälkikäteen: hyväksyntä kirjautuu
     * kuten ehdotuksen valinnassa, mutta ketjua ei jatketa, koska kursori
     * on hypännyt keskelle vanhaa tekstiä.
     */
    @Synchronized
    fun onCorrectionAccepted(word: String) {
        if (!loaded) return
        val key = keyOf(word)
        val state = words.getOrPut(key) { WordState(word, 0, clock(), false, clock()) }
        if (state.count > 0) state.count++
        state.acceptedCount++
        state.ignoredCount = 0
        state.lastUsed = clock()
        dirtyWords += key
    }

    /** Käytetyimmät omat sanat esim. puheentunnistuksen sanastovihjeiksi. */
    @Synchronized
    fun biasWords(max: Int): List<String> {
        if (!loaded || max <= 0) return emptyList()
        return words.values
            .filter { !it.blocked && it.count > 0 }
            .sortedByDescending { it.count * recency(it.lastUsed) }
            .take(max)
            .map { it.word }
    }

    /**
     * Sanat, jotka ovat aiemmin edeltäneet [next]-sanaa, bigramivahvuuksineen.
     * Käytetään paikkaan sopivien vaihtoehtojen hakuun: ehdokas kelpaa, jos
     * se on joskus esiintynyt juuri ennen seuraavaa sanaa.
     */
    @Synchronized
    fun previousMatches(next: String): Map<String, Float> {
        if (!loaded) return emptyMap()
        val nextKey = keyOf(next)
        val result = HashMap<String, Float>()
        for ((previous, followers) in bigrams) {
            val state = followers[nextKey] ?: continue
            result[previous] = state.count * recency(state.lastUsed)
        }
        return result
    }

    /** Omat sanat enintään [maxDistance] muokkauksen päässä, käytetyin ensin. */
    @Synchronized
    fun near(word: String, maxDistance: Int, max: Int = 5): List<String> {
        if (word.isEmpty() || max <= 0) return emptyList()
        val key = keyOf(word)
        return words.entries
            .filter { (k, state) ->
                !state.blocked && (state.count > 0 || state.pinned) && k != key &&
                    WordTools.editDistanceAtMost(k, key, maxDistance) <= maxDistance
            }
            .sortedByDescending { it.value.count * recency(it.value.lastUsed) }
            .take(max)
            .map { it.value.word }
    }

    /**
     * Rivillä näkyneet täydennykset ohitettiin: kasvata ohituslaskuria
     * kaikilta paitsi lopulliselta sanalta. Vaikutus pisteisiin on kevyt ja
     * alkaa vasta toistuvista ohituksista.
     */
    @Synchronized
    fun onSuggestionsIgnored(shown: List<String>, finalWord: String) {
        if (!loaded) return
        val finalKey = keyOf(finalWord)
        for (word in shown) {
            val key = keyOf(word)
            if (key == finalKey) continue
            val state = words.getOrPut(key) { WordState(word, 0, clock(), false, clock()) }
            state.ignoredCount++
            dirtyWords += key
        }
    }

    @Synchronized
    fun setPinned(word: String, pinned: Boolean) {
        if (!loaded) return
        val key = keyOf(word)
        val state = words.getOrPut(key) { WordState(word, 0, clock(), false, clock()) }
        state.pinned = pinned
        dirtyWords += key
    }

    @Synchronized
    fun isPinned(word: String): Boolean = words[keyOf(word)]?.pinned == true

    /** Sanan pisteytyssignaalit tai null, jos sanasta ei ole mitään tietoa. */
    @Synchronized
    fun signals(word: String): WordSignals? = words[keyOf(word)]?.let {
        WordSignals(
            usage = it.count * recency(it.lastUsed),
            manuallyTyped = it.count > 0,
            acceptedCount = it.acceptedCount,
            ignoredCount = it.ignoredCount,
            pinned = it.pinned,
            blocked = it.blocked,
        )
    }

    /** Tallennettu kirjoitusasu tai avain sellaisenaan. */
    @Synchronized
    fun displayForm(key: String): String = words[keyOf(key)]?.word ?: key

    private fun chain(word: String) {
        if (!isChainToken(word)) {
            previousToken = null
            beforePreviousToken = null
            return
        }
        val key = keyOf(word)
        previousToken?.let { prev ->
            val nexts = bigrams.getOrPut(prev) { HashMap() }
            val state = nexts.getOrPut(key) { CountState(0, clock()) }
            state.count++
            state.lastUsed = clock()
            dirtyBigrams += prev to key
            beforePreviousToken?.let { first ->
                val triNexts = trigrams.getOrPut(first to prev) { HashMap() }
                val triState = triNexts.getOrPut(key) { CountState(0, clock()) }
                triState.count++
                triState.lastUsed = clock()
                dirtyTrigrams += Triple(first, prev, key)
            }
        }
        beforePreviousToken = previousToken
        previousToken = key
    }

    /** Katkaisee sanaketjun (kenttä vaihtui tai kursori siirtyi muualle). */
    @Synchronized
    fun resetContext() {
        previousToken = null
        beforePreviousToken = null
    }

    /** Tuoreuskerroin: alle 7 pv ×1,0, alle 30 pv ×0,7, muuten ×0,4. */
    private fun recency(lastUsed: Long): Float {
        val age = clock() - lastUsed
        return when {
            age < 7L * 24 * 60 * 60 * 1000 -> 1.0f
            age < 30L * 24 * 60 * 60 * 1000 -> 0.7f
            else -> 0.4f
        }
    }

    @Synchronized
    fun suggest(prefix: String, max: Int = 3): List<String> {
        if (prefix.isEmpty() || max <= 0) return emptyList()
        val key = keyOf(prefix)
        return words.entries
            .filter {
                !it.value.blocked && it.key.startsWith(key) &&
                    // Kiinnitetty pääsee ehdolle, vaikkei sitä olisi kirjoitettu.
                    (it.value.count > 0 || it.value.pinned)
            }
            .sortedByDescending { it.value.count * recency(it.value.lastUsed) }
            .take(max)
            .map { it.value.word }
    }

    /**
     * Kontekstiin täsmäävät jatkot raakapistein: bigram- ja trigramosumat
     * eriteltyinä (määrä × tuoreuskerroin). Avaimet pienennettyinä.
     */
    @Synchronized
    fun contextMatches(context: List<String>): Map<String, NextMatch> {
        if (context.isEmpty()) return emptyMap()
        val result = HashMap<String, NextMatch>()
        val last = keyOf(context.last())
        bigrams[last]?.forEach { (next, state) ->
            result[next] = NextMatch(state.count * recency(state.lastUsed), 0f)
        }
        if (context.size >= 2) {
            trigrams[keyOf(context[context.size - 2]) to last]?.forEach { (next, state) ->
                val old = result[next] ?: NextMatch(0f, 0f)
                result[next] = old.copy(trigram = state.count * recency(state.lastUsed))
            }
        }
        return result
    }

    /**
     * Todennäköisimmät seuraavat sanat. Trigramiosumat (kaksi edeltävää sanaa
     * täsmäävät) painottuvat kertoimella ×3, bigramit täydentävät.
     */
    @Synchronized
    fun predictNext(context: List<String>, max: Int = 3): List<String> {
        if (max <= 0) return emptyList()
        return contextMatches(context).entries
            .filter { words[it.key]?.blocked != true }
            .sortedByDescending { it.value.bigram + it.value.trigram * 3f }
            .take(max)
            .map { displayForm(it.key) }
    }

    @Synchronized
    fun isOwnWord(word: String): Boolean =
        words[keyOf(word)]?.let { it.count > 0 && !it.blocked } == true

    @Synchronized
    fun isBlocked(word: String): Boolean = words[keyOf(word)]?.blocked == true

    @Synchronized
    fun removeWord(word: String) {
        val key = keyOf(word)
        if (words.remove(key) != null) {
            dirtyWords -= key
            removedWords += key
        }
        // Myös sanaketjut siivotaan, ettei poistettu sana palaa ennustuksista.
        bigrams.remove(key)
        bigrams.values.forEach { it.remove(key) }
        trigrams.keys.removeAll { it.first == key || it.second == key }
        trigrams.values.forEach { it.remove(key) }
        dirtyBigrams.removeAll { it.first == key || it.second == key }
        dirtyTrigrams.removeAll { it.first == key || it.second == key || it.third == key }
        removedChainWords += key
        if (previousToken == key || beforePreviousToken == key) resetContext()
    }

    /** Estää sanan pysyvästi; toimii myös yleissanaston sanoille. */
    @Synchronized
    fun blockWord(word: String) {
        if (!loaded) return
        val key = keyOf(word)
        val state = words.getOrPut(key) { WordState(word, 0, clock(), false, clock()) }
        state.blocked = true
        dirtyWords += key
    }

    /** Palauttaa epäonnistuneen tallennuserän takaisin jonoon uutta yritystä varten. */
    @Synchronized
    fun requeueDirty(batch: DirtyLearned) {
        for (word in batch.words) dirtyWords += keyOf(word.word)
        for (bigram in batch.bigrams) dirtyBigrams += bigram.previous to bigram.next
        for (trigram in batch.trigrams) {
            dirtyTrigrams += Triple(trigram.first, trigram.second, trigram.next)
        }
        removedWords += batch.removedWords
        removedChainWords += batch.removedChainWords
    }

    @Synchronized
    fun drainDirty(): DirtyLearned {
        val outWords = dirtyWords.mapNotNull { key ->
            words[key]?.let {
                LearnedWord(
                    it.word, it.count, it.lastUsed, it.blocked, it.created,
                    it.acceptedCount, it.ignoredCount, it.pinned,
                )
            }
        }
        val outBigrams = dirtyBigrams.mapNotNull { (prev, next) ->
            bigrams[prev]?.get(next)?.let { LearnedBigram(prev, next, it.count, it.lastUsed) }
        }
        val outTrigrams = dirtyTrigrams.mapNotNull { (first, second, next) ->
            trigrams[first to second]?.get(next)
                ?.let { LearnedTrigram(first, second, next, it.count, it.lastUsed) }
        }
        val outRemoved = removedWords.toList()
        val outRemovedChains = removedChainWords.toList()
        dirtyWords.clear()
        dirtyBigrams.clear()
        dirtyTrigrams.clear()
        removedWords.clear()
        removedChainWords.clear()
        return DirtyLearned(outWords, outBigrams, outTrigrams, outRemoved, outRemovedChains)
    }

    private companion object {
        const val PENDING_LIMIT = 200
    }
}
