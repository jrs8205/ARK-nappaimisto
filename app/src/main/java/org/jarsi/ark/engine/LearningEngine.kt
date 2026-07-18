package org.jarsi.ark.engine

import java.util.Locale

/**
 * Henkilökohtaisten sanojen oppiminen: sanat ja sanaketjut pidetään muistissa
 * ja muutokset kerätään eriin, jotka kutsuja kirjoittaa tietokantaan
 * [drainDirty]-kutsulla. Kutsut tehdään päälangalta; ei säieturvallinen.
 */
class LearningEngine(private val clock: () -> Long = System::currentTimeMillis) {

    private class WordState(
        val word: String,
        var count: Int,
        var lastUsed: Long,
        var blocked: Boolean,
        val created: Long,
    )

    private val fiLocale = Locale.forLanguageTag("fi")
    private val words = HashMap<String, WordState>()
    private val bigrams = HashMap<Pair<String, String>, LearnedBigram>()
    private val dirtyWords = HashSet<String>()
    private val dirtyBigrams = HashSet<Pair<String, String>>()
    private val removedWords = HashSet<String>()
    private var previousToken: String? = null
    private var loaded = false

    val isLoaded: Boolean get() = loaded
    val dirtyCount: Int get() = dirtyWords.size + dirtyBigrams.size + removedWords.size

    private fun keyOf(word: String) = word.lowercase(fiLocale)

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '-' || c == '.'

    /** Sanastoon kelpaa 2–32 merkin sana, jossa on ainakin yksi kirjain. */
    private fun isEligibleWord(word: String) =
        word.length in 2..32 && word.any { it.isLetter() } && word.all { isWordChar(it) }

    /** Ketjuun kelpaa myös pelkkä numero (esim. "20"). */
    private fun isChainToken(word: String) =
        word.length in 1..32 && word.all { isWordChar(it) }

    fun load(loadedWords: List<LearnedWord>, loadedBigrams: List<LearnedBigram>) {
        words.clear()
        bigrams.clear()
        for (w in loadedWords) {
            words[keyOf(w.word)] = WordState(w.word, w.count, w.lastUsed, w.blocked, w.created)
        }
        for (b in loadedBigrams) {
            bigrams[b.previous to b.next] = b
        }
        loaded = true
    }

    /** Käsin kirjoitettu sana päättyi: opi sana ja kirjaa ketju. */
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
            return
        }
        val key = keyOf(word)
        previousToken?.let { prev ->
            val pair = prev to key
            val old = bigrams[pair]
            bigrams[pair] = LearnedBigram(prev, key, (old?.count ?: 0) + 1, clock())
            dirtyBigrams += pair
        }
        previousToken = key
    }

    /** Katkaisee sanaketjun (kenttä vaihtui tai kursori siirtyi muualle). */
    fun resetContext() {
        previousToken = null
    }

    /** Tuoreuskerroin: alle 7 pv ×1,0, alle 30 pv ×0,7, muuten ×0,4. */
    private fun score(state: WordState): Float {
        val age = clock() - state.lastUsed
        val factor = when {
            age < 7L * 24 * 60 * 60 * 1000 -> 1.0f
            age < 30L * 24 * 60 * 60 * 1000 -> 0.7f
            else -> 0.4f
        }
        return state.count * factor
    }

    fun suggest(prefix: String, max: Int = 3): List<String> {
        if (prefix.isEmpty() || max <= 0) return emptyList()
        val key = keyOf(prefix)
        return words.entries
            .filter { !it.value.blocked && it.value.count > 0 && it.key.startsWith(key) }
            .sortedByDescending { score(it.value) }
            .take(max)
            .map { it.value.word }
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
        val outBigrams = dirtyBigrams.mapNotNull { bigrams[it] }
        val outRemoved = removedWords.toList()
        dirtyWords.clear()
        dirtyBigrams.clear()
        removedWords.clear()
        return DirtyLearned(outWords, outBigrams, outRemoved)
    }
}
