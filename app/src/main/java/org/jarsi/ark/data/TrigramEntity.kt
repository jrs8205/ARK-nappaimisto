package org.jarsi.ark.data

import androidx.room.Entity

/** Kolmen peräkkäisen sanan ketju seuraavan sanan ennustusta varten. */
@Entity(tableName = "trigrams", primaryKeys = ["first", "second", "next"])
data class TrigramEntity(
    val first: String,
    val second: String,
    val next: String,
    val count: Int,
    val lastUsed: Long,
)
