package org.jarsi.ark.engine

/** Opittu sana: [word] on ensimmäinen kirjoitusasu. */
data class LearnedWord(
    val word: String,
    val count: Int,
    val lastUsed: Long,
    val blocked: Boolean,
    val created: Long,
    val acceptedCount: Int = 0,
    val ignoredCount: Int = 0,
    val pinned: Boolean = false,
)

/** Sanan pisteytyssignaalit; [usage] on käyttömäärä × tuoreuskerroin. */
data class WordSignals(
    val usage: Float,
    val manuallyTyped: Boolean,
    val acceptedCount: Int,
    val ignoredCount: Int,
    val pinned: Boolean,
    val blocked: Boolean,
)

/** Kontekstiosuman raakapisteet (määrä × tuoreuskerroin). */
data class NextMatch(
    val bigram: Float,
    val trigram: Float,
)

/** Peräkkäisten sanojen pari pienennetyssä muodossa. */
data class LearnedBigram(
    val previous: String,
    val next: String,
    val count: Int,
    val lastUsed: Long,
)

/** Kolmen peräkkäisen sanan ketju pienennetyssä muodossa. */
data class LearnedTrigram(
    val first: String,
    val second: String,
    val next: String,
    val count: Int,
    val lastUsed: Long,
)

/** Tietokantaan kirjoitettavat kertyneet muutokset. */
data class DirtyLearned(
    val words: List<LearnedWord>,
    val bigrams: List<LearnedBigram>,
    val trigrams: List<LearnedTrigram>,
    val removedWords: List<String>,
)
