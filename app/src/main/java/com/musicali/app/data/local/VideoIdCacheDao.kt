package com.musicali.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for the video_id_cache table.
 * Used by YouTubeRepositoryImpl to avoid repeat YouTube API calls for the same artist.
 */
@Dao
interface VideoIdCacheDao {

    /**
     * Returns the cached entry for the given normalized artist name, or null if not cached.
     * Callers must normalize the name (trim().lowercase()) before querying.
     */
    @Query("SELECT * FROM video_id_cache WHERE normalizedArtistName = :normalizedArtistName LIMIT 1")
    suspend fun getByArtistName(normalizedArtistName: String): VideoIdCacheEntity?

    /**
     * Inserts or replaces the cache entry for an artist.
     * Pass videoId = null to cache a "no result" outcome.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: VideoIdCacheEntity)

    /**
     * Clears all cached video IDs. Useful for testing and cache invalidation.
     */
    @Query("DELETE FROM video_id_cache")
    suspend fun deleteAll()
}
