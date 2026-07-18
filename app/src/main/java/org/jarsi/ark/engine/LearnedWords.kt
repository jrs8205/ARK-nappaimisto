package org.jarsi.ark.engine

/** Opittu sana: [word] on ensimmäinen kirjoitusasu. */
data class LearnedWord(
    val word: String,
    val count: Int,
    val lastUsed: Long,
    val blocked: Boolean,
    val created: Long,
)

/** Peräkkäisten sanojen pari pienennetyssä muodossa. */
data class LearnedBigram(
    val previous: String,
    val next: String,
    val count: Int,
    val lastUsed: Long,
)

/** Tietokantaan kirjoitettavat kertyneet muutokset. */
data class DirtyLearned(
    val words: List<LearnedWord>,
    val bigrams: List<LearnedBigram>,
    val removedWords: List<String>,
)
