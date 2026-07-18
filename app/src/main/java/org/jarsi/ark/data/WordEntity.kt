package org.jarsi.ark.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Opittu sana. [key] on pienennetty muoto, [word] ensimmäinen kirjoitusasu. */
@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey val key: String,
    val word: String,
    val count: Int,
    val lastUsed: Long,
    val blocked: Boolean,
    val created: Long,
    @ColumnInfo(defaultValue = "0") val acceptedCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val ignoredCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
)
