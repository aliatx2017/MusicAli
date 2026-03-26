package com.musicali.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ArtistEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun artistDao(): ArtistDao
}
