package com.musicali.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ArtistEntity::class, GenreCacheEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun artistDao(): ArtistDao
    abstract fun genreCacheDao(): GenreCacheDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `genre_cache`
                    (`genre` TEXT NOT NULL,
                     `artistName` TEXT NOT NULL,
                     `cachedAt` INTEGER NOT NULL,
                     PRIMARY KEY(`genre`, `artistName`))
                """.trimIndent())
            }
        }
    }
}
