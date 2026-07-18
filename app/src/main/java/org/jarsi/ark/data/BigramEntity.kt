package org.jarsi.ark.data

import androidx.room.Entity

/** Peräkkäisten sanojen pari seuraavan sanan ennustusta varten (vaihe 4). */
@Entity(tableName = "bigrams", primaryKeys = ["previous", "next"])
data class BigramEntity(
    val previous: String,
    val next: String,
    val count: Int,
    val lastUsed: Long,
)
