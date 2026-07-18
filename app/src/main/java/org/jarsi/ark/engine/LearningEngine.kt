package org.jarsi.ark.engine

import java.util.Locale

/**
 * Henkilökohtaisten sanojen oppiminen: sanat ja sanaketjut (parit ja kolmikot)
 * pidetään muistissa ja muutokset kerätään eriin, jotka kutsuja kirjoittaa
 * tietokantaan [drainDirty]-kutsulla. Kutsut tehdään päälangalta; ei
 * säieturvallinen.
 */
class LearningEngine(private val clock: () -> Long = System::currentTimeMillis) {

    private class WordState(
        val word: String,
        var count: Int,
        var lastUsed: Long,
        var blocked: Boolean,
        val created: Long,
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

    fun load(
        loadedWords: List<LearnedWord>,
        loadedBigrams: List<LearnedBigram>,
        loadedTrigrams: List<LearnedTrigram>,
    ) {
        words.clear()
        bigrams.clear()
        trigrams.clear()
        for (w in loadedWords) {
            words[keyOf(w.word)] = WordState(w.word, w.count, w.lastUsed, w.blocked, w.created)
        }
        for (b in loadedBigrams) {
            bigrams.getOrPut(b.previous) { HashMap() }[b.next] = CountState(b.count, b.lastUsed)
        }
        for (t in loadedTrigrams) {
            trigrams.getOrPut(t.first to t.second) { HashMap() }[t.next] =
                CountState(t.count, t.lastUsed)
        }
        loaded = true
    }

    /** Käsin kirjoitettu sana päättyi: opi sana ja kirjaa ketjut. */
    fun onWordCommitted(word: String) {
        if (!loaded) return
        if (isEligibleWord(word)) {
            val key = keyOf(word)
            val state = words.getOrPut(key) { WordState(word, 0, clock(), false, clock()) }
            state.count++
            state.lastUsed = clock()
            dirtyWords += key
        }
        chain(word)
    }

    /** Ehdotus valittiin: ketju jatkuu aina, määrä kasvaa vain omilla sanoilla. */
    fun onSuggestionAccepted(word: String) {
        if (!loaded) return
        val state = words[keyOf(word)]
        if (state != null && state.count > 0) {
            state.count++
            state.lastUsed = clock()
            dirtyWords += keyOf(word)
        }
        chain(word)
    }

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

    fun suggest(prefix: String, max: Int = 3): List<String> {
        if (prefix.isEmpty() || max <= 0) return emptyList()
        val key = keyOf(prefix)
        return words.entries
            .filter { !it.value.blocked && it.value.count > 0 && it.key.startsWith(key) }
            .sortedByDescending { it.value.count * recency(it.value.lastUsed) }
            .take(max)
            .map { it.value.word }
    }

    /**
     * Todennäköisimmät seuraavat sanat. Trigramiosumat (kaksi edeltävää sanaa
     * täsmäävät) painottuvat kertoimella ×3, bigramit täydentävät.
     */
    fun predictNext(context: List<String>, max: Int = 3): List<String> {
        if (context.isEmpty() || max <= 0) return emptyList()
        val scores = HashMap<String, Float>()
        val last = keyOf(context.last())
        if (context.size >= 2) {
            trigrams[keyOf(context[context.size - 2]) to last]?.forEach { (next, state) ->
                scores[next] = (scores[next] ?: 0f) + state.count * recency(state.lastUsed) * 3f
            }
        }
        bigrams[last]?.forEach { (next, state) ->
            scores[next] = (scores[next] ?: 0f) + state.count * recency(state.lastUsed)
        }
        return scores.entries
            .filter { words[it.key]?.blocked != true }
            .sortedByDescending { it.value }
            .take(max)
            .map { words[it.key]?.word ?: it.key }
    }

    fun isOwnWord(word: String): Boolean =
        words[keyOf(word)]?.let { it.count > 0 && !it.blocked } == true

    fun isBlocked(word: String): Boolean = words[keyOf(word)]?.blocked == true

    fun removeWord(word: String) {
        val key = keyOf(word)
        if (words.remove(key) != null) {
            dirtyWords -= key
            removedWords += key
        }
    }

    /** Estää sanan pysyvästi; toimii myös yleissanaston sanoille. */
    fun blockWord(word: String) {
        if (!loaded) return
        val key = keyOf(word)
        val state = words.getOrPut(key) { WordState(word, 0, clock(), false, clock()) }
        state.blocked = true
        dirtyWords += key
    }

    fun drainDirty(): DirtyLearned {
        val outWords = dirtyWords.mapNotNull { key ->
            words[key]?.let { LearnedWord(it.word, it.count, it.lastUsed, it.blocked, it.created) }
        }
        val outBigrams = dirtyBigrams.mapNotNull { (prev, next) ->
            bigrams[prev]?.get(next)?.let { LearnedBigram(prev, next, it.count, it.lastUsed) }
        }
        val outTrigrams = dirtyTrigrams.mapNotNull { (first, second, next) ->
            trigrams[first to second]?.get(next)
                ?.let { LearnedTrigram(first, second, next, it.count, it.lastUsed) }
        }
        val outRemoved = removedWords.toList()
        dirtyWords.clear()
        dirtyBigrams.clear()
        dirtyTrigrams.clear()
        removedWords.clear()
        return DirtyLearned(outWords, outBigrams, outTrigrams, outRemoved)
    }
}
