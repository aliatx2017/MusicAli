package com.musicali.app.fake

import com.musicali.app.data.local.VideoIdCacheDao
import com.musicali.app.data.local.VideoIdCacheEntity

/**
 * In-memory fake for VideoIdCacheDao.
 * Follows the same fake patterns as FakeArtistDao in the project.
 * Used by YouTubeRepositoryImpl cache-behavior tests.
 */
class FakeVideoIdCacheDao : VideoIdCacheDao {

    private val cache = mutableMapOf<String, VideoIdCacheEntity>()

    override suspend fun getByArtistName(normalizedArtistName: String): VideoIdCacheEntity? =
        cache[normalizedArtistName]

    override suspend fun upsert(entity: VideoIdCacheEntity) {
        cache[entity.normalizedArtistName] = entity
    }

    override suspend fun deleteAll() {
        cache.clear()
    }

    /** Returns current size of the in-memory cache. */
    fun size(): Int = cache.size

    /** Returns true if the cache contains an entry for the given normalized name. */
    fun containsKey(normalizedName: String): Boolean = cache.containsKey(normalizedName)

    /** Returns the cached entry for test assertions. */
    fun getEntry(normalizedName: String): VideoIdCacheEntity? = cache[normalizedName]
}
