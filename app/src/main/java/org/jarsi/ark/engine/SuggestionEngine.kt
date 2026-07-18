package org.jarsi.ark.engine

import java.util.Locale

/** Kokoaa ehdotusrivin: omat sanat kärkeen, yleissanaston täydennykset perään. */
class SuggestionEngine(
    private val dictionary: DictionaryEngine,
    private val learning: LearningEngine,
) {

    private val fiLocale = Locale.forLanguageTag("fi")

    fun suggest(prefix: String, max: Int = 8): List<String> {
        if (prefix.isEmpty() || max <= 0) return emptyList()
        val result = ArrayList<String>(max)
        val seen = HashSet<String>()
        for (word in learning.suggest(prefix, max = OWN_WORDS_FIRST)) {
            if (result.size >= max) break
            if (seen.add(word.lowercase(fiLocale))) result += word
        }
        for (word in dictionary.suggest(prefix, max)) {
            if (result.size >= max) break
            if (learning.isBlocked(word)) continue
            if (seen.add(word.lowercase(fiLocale))) result += word
        }
        return result
    }

    /** Yleisimmät sanat tyhjälle syötteelle, estetyt pois suodatettuina. */
    fun topWords(): List<String> = dictionary.topWords().filterNot { learning.isBlocked(it) }

    private companion object {
        const val OWN_WORDS_FIRST = 3
    }
}
