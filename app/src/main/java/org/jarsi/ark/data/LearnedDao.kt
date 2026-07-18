package org.jarsi.ark.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface LearnedDao {
    @Query("SELECT * FROM words") fun allWords(): List<WordEntity>
    @Query("SELECT * FROM bigrams") fun allBigrams(): List<BigramEntity>
    @Query("SELECT * FROM trigrams") fun allTrigrams(): List<TrigramEntity>
    @Upsert fun upsertWords(words: List<WordEntity>)
    @Upsert fun upsertBigrams(bigrams: List<BigramEntity>)
    @Upsert fun upsertTrigrams(trigrams: List<TrigramEntity>)
    @Query("DELETE FROM words WHERE `key` = :key") fun deleteWord(key: String)
}
