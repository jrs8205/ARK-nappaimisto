package org.jarsi.ark.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Oppimisdatan paikallinen tietokanta. */
@Database(entities = [WordEntity::class, BigramEntity::class], version = 1, exportSchema = false)
abstract class LearnedDatabase : RoomDatabase() {
    abstract fun dao(): LearnedDao

    companion object {
        fun create(context: Context): LearnedDatabase =
            Room.databaseBuilder(context, LearnedDatabase::class.java, "oppiminen.db").build()
    }
}
