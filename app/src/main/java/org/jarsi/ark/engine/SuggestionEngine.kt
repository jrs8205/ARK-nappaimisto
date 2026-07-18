package org.jarsi.ark.engine

import java.util.Locale
import kotlin.math.ln

/**
 * Kokoaa ehdotusrivin yhtenäisellä pisteytysmallilla: yleisyys, oma käyttö,
 * kontekstiosumat (bigram/trigram), hyväksynnät, kiinnitys ja ohitukset
 * summautuvat yhdeksi pistemääräksi (kokonaissuunnitelman kohta 22).
 */
class SuggestionEngine(
    private val dictionary: DictionaryEngine,
    private val learning: LearningEngine,
) {

    private val fiLocale = Locale.forLanguageTag("fi")

    /** Ehdotukset keskeneräiselle sanalle; [context] on edeltävät 1–2 sanaa. */
    fun suggest(prefix: String, context: List<String> = emptyList(), max: Int = 8): List<String> {
        if (prefix.isEmpty() || max <= 0) return emptyList()
        val prefixKey = prefix.lowercase(fiLocale)
        val contextMatches = learning.contextMatches(context)
        val candidates = LinkedHashSet<String>()
        for (word in dictionary.suggest(prefix, DICTIONARY_CANDIDATES)) {
            candidates += word.lowercase(fiLocale)
        }
        for (word in learning.suggest(prefix, OWN_CANDIDATES)) {
            candidates += word.lowercase(fiLocale)
        }
        for (key in contextMatches.keys) {
            if (key.startsWith(prefixKey)) candidates += key
        }
        return rank(candidates, contextMatches, max)
    }

    /** Rivi tyhjälle syötteelle: ennustukset ja halutessa yleisimmät täytteeksi. */
    fun emptyInput(context: List<String>, includeCommon: Boolean, max: Int = 8): List<String> {
        val contextMatches = learning.contextMatches(context)
        val candidates = LinkedHashSet<String>()
        candidates += contextMatches.keys
        if (includeCommon) {
            for (word in dictionary.topWords()) {
                candidates += word.lowercase(fiLocale)
            }
        }
        return rank(candidates, contextMatches, max)
    }

    private fun rank(
        candidateKeys: Collection<String>,
        contextMatches: Map<String, NextMatch>,
        max: Int,
    ): List<String> {
        val maxFreq = dictionary.maxFrequency()
        val candidates = candidateKeys.mapNotNull { key ->
            val signals = learning.signals(key)
            if (signals?.blocked == true) return@mapNotNull null
            Candidate(key, baseScore(key, signals, contextMatches[key], maxFreq), signals?.pinned == true)
        }
        // Kiinnitysbonus vain kahdelle parhaalle, etteivät kiinnitetyt sanat
        // täytä koko riviä, jos samalla alulla on monta kiinnitystä.
        val bonusKeys = candidates
            .filter { it.pinned }
            .sortedByDescending { it.base }
            .take(PINNED_TOP)
            .mapTo(HashSet()) { it.key }
        return candidates
            .sortedByDescending { it.base + if (it.key in bonusKeys) PINNED_WEIGHT else 0f }
            .take(max)
            .map { learning.displayForm(it.key) }
    }

    private class Candidate(val key: String, val base: Float, val pinned: Boolean)

    private fun baseScore(
        key: String,
        signals: WordSignals?,
        match: NextMatch?,
        maxFreq: Long,
    ): Float {
        var score = 0f
        if (maxFreq > 0) {
            val freq = dictionary.frequencyOf(key)
            score += (ln(1.0 + freq) / ln(1.0 + maxFreq)).toFloat() * FREQUENCY_WEIGHT
        }
        if (match != null) {
            score += match.bigram / (match.bigram + 3f) * BIGRAM_WEIGHT
            score += match.trigram / (match.trigram + 3f) * TRIGRAM_WEIGHT
        }
        if (signals != null) {
            score += signals.usage / (signals.usage + 5f) * USAGE_WEIGHT
            score += signals.acceptedCount / (signals.acceptedCount + 3f) * ACCEPTED_WEIGHT
            if (signals.manuallyTyped) score += MANUAL_WEIGHT
            if (signals.ignoredCount >= IGNORED_THRESHOLD) {
                score -= signals.ignoredCount / (signals.ignoredCount + 3f) * IGNORED_WEIGHT
            }
        }
        return score
    }

    private companion object {
        const val DICTIONARY_CANDIDATES = 12
        const val OWN_CANDIDATES = 10
        const val IGNORED_THRESHOLD = 2
        const val PINNED_TOP = 2

        // Kokonaissuunnitelman kohdan 22 painokertoimet.
        const val FREQUENCY_WEIGHT = 1.0f
        const val USAGE_WEIGHT = 2.0f
        const val BIGRAM_WEIGHT = 4.0f
        const val TRIGRAM_WEIGHT = 7.0f
        const val ACCEPTED_WEIGHT = 3.0f
        const val MANUAL_WEIGHT = 2.5f
        const val PINNED_WEIGHT = 20.0f
        const val IGNORED_WEIGHT = 5.0f
    }
}
