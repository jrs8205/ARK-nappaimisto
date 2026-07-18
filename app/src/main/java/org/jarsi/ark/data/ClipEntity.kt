package org.jarsi.ark.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Tallennettu leike: teksti tai kuvatiedoston polku. */
@Entity(tableName = "clips")
data class ClipEntity(
    @PrimaryKey val id: Long,
    val text: String?,
    val imagePath: String?,
    val created: Long,
    val pinned: Boolean,
)
