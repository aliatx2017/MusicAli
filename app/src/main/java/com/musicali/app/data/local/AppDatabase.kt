package com.musicali.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ArtistEntity::class, GenreCacheEntity::class, VideoIdCacheEntity::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun artistDao(): ArtistDao
    abstract fun genreCacheDao(): GenreCacheDao
    abstract fun videoIdCacheDao(): VideoIdCacheDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `video_id_cache`
                    (`normalizedArtistName` TEXT NOT NULL,
                     `videoId` TEXT,
                     `cachedAt` INTEGER NOT NULL,
                     PRIMARY KEY(`normalizedArtistName`))
                """.trimIndent())
            }
        }
    }
}
