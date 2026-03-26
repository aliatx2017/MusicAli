package com.musicali.app.data.local

import androidx.room.Entity

@Entity(
    tableName = "genre_cache",
    primaryKeys = ["genre", "artistName"]
)
data class GenreCacheEntity(
    val genre: String,        // Genre.name e.g. "INDIETRONICA"
    val artistName: String,   // original capitalization from EveryNoise
    val cachedAt: Long        // epoch millis of the successful scrape
)
