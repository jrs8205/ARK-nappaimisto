package org.jarsi.ark.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Oppimisdatan paikallinen tietokanta. */
@Database(
    entities = [WordEntity::class, BigramEntity::class, TrigramEntity::class, ClipEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class LearnedDatabase : RoomDatabase() {
    abstract fun dao(): LearnedDao

    companion object {
        // Versio 2 lisäsi trigramitaulun; vanha data säilyy koskemattomana.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trigrams` (" +
                        "`first` TEXT NOT NULL, `second` TEXT NOT NULL, `next` TEXT NOT NULL, " +
                        "`count` INTEGER NOT NULL, `lastUsed` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`first`, `second`, `next`))"
                )
            }
        }

        // Versio 3 lisäsi palautesarakkeet; vanha data säilyy.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `words` ADD COLUMN `acceptedCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `words` ADD COLUMN `ignoredCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `words` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Versio 4 lisäsi leiketaulun; vanha data säilyy.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `clips` (" +
                        "`id` INTEGER NOT NULL, `text` TEXT, `imagePath` TEXT, " +
                        "`created` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        fun create(context: Context): LearnedDatabase =
            Room.databaseBuilder(context, LearnedDatabase::class.java, "oppiminen.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
