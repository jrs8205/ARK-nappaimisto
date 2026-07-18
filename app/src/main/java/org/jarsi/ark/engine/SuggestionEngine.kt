package org.jarsi.ark.engine

import java.util.Locale

/**
 * Kokoaa ehdotusrivin: seuraavan sanan ennustukset kärkeen, sitten omat
 * sanat ja lopuksi yleissanaston täydennykset.
 */
class SuggestionEngine(
    private val dictionary: DictionaryEngine,
    private val learning: LearningEngine,
) {

    private val fiLocale = Locale.forLanguageTag("fi")

    /** Ehdotukset keskeneräiselle sanalle; [context] on edeltävät 1–2 sanaa. */
    fun suggest(prefix: String, context: List<String> = emptyList(), max: Int = 8): List<String> {
        if (prefix.isEmpty() || max <= 0) return emptyList()
        val result = ArrayList<String>(max)
        val seen = HashSet<String>()
        val prefixKey = prefix.lowercase(fiLocale)
        for (word in learning.predictNext(context, max = PREDICTIONS_FIRST)) {
            if (result.size >= max) break
            val key = word.lowercase(fiLocale)
            if (!key.startsWith(prefixKey)) continue
            if (seen.add(key)) result += word
        }
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

    /** Rivi tyhjälle syötteelle: ennustukset ja halutessa yleisimmät täytteeksi. */
    fun emptyInput(context: List<String>, includeCommon: Boolean, max: Int = 8): List<String> {
        val result = ArrayList<String>(max)
        val seen = HashSet<String>()
        for (word in learning.predictNext(context, max = PREDICTIONS_FIRST)) {
            if (seen.add(word.lowercase(fiLocale))) result += word
        }
        if (includeCommon) {
            for (word in dictionary.topWords()) {
                if (result.size >= max) break
                if (learning.isBlocked(word)) continue
                if (seen.add(word.lowercase(fiLocale))) result += word
            }
        }
        return result
    }

    private companion object {
        const val PREDICTIONS_FIRST = 3
        const val OWN_WORDS_FIRST = 3
    }
}
