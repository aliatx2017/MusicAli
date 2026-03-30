package com.musicali.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Caches the YouTube video ID resolved for a given artist name.
 * A null videoId means the YouTube search returned no result for this artist —
 * this negative result is cached to avoid burning quota on repeat lookups.
 *
 * YT-02: Warm runs use this cache instead of calling search.list.
 */
@Entity(tableName = "video_id_cache")
data class VideoIdCacheEntity(
    @PrimaryKey val normalizedArtistName: String,  // name.trim().lowercase() — dedup key
    val videoId: String?,                           // null = known no-result for this artist
    val cachedAt: Long                              // epoch millis when cached
)
