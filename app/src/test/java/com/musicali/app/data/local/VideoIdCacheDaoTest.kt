package com.musicali.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric 4.14.1 max supported SDK is 35; targetSdk=36 requires explicit config override
@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class VideoIdCacheDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: VideoIdCacheDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.videoIdCacheDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsert_andGetByArtistName_returnsCorrectVideoId() = runTest {
        val entity = VideoIdCacheEntity(
            normalizedArtistName = "radiohead",
            videoId = "abc123",
            cachedAt = 1000L
        )
        dao.upsert(entity)

        val result = dao.getByArtistName("radiohead")
        assertNotNull(result)
        assertEquals("abc123", result!!.videoId)
        assertEquals(1000L, result.cachedAt)
    }

    @Test
    fun upsert_nullVideoId_cachesNoResultOutcome() = runTest {
        val entity = VideoIdCacheEntity(
            normalizedArtistName = "unknown artist",
            videoId = null,
            cachedAt = 2000L
        )
        dao.upsert(entity)

        val result = dao.getByArtistName("unknown artist")
        assertNotNull("Entry should exist in cache", result)
        assertNull("videoId should be null (known no-result)", result!!.videoId)
    }

    @Test
    fun getByArtistName_returnsNull_whenNotCached() = runTest {
        val result = dao.getByArtistName("not cached artist")
        assertNull(result)
    }

    @Test
    fun upsert_replacesExistingEntry_onSameNormalizedName() = runTest {
        val original = VideoIdCacheEntity(
            normalizedArtistName = "radiohead",
            videoId = "old-video-id",
            cachedAt = 1000L
        )
        dao.upsert(original)

        val updated = VideoIdCacheEntity(
            normalizedArtistName = "radiohead",
            videoId = "new-video-id",
            cachedAt = 9999L
        )
        dao.upsert(updated)

        val result = dao.getByArtistName("radiohead")
        assertNotNull(result)
        assertEquals("new-video-id", result!!.videoId)
        assertEquals(9999L, result.cachedAt)
    }

    @Test
    fun deleteAll_removesAllEntries() = runTest {
        dao.upsert(VideoIdCacheEntity("artist a", "vid1", 1000L))
        dao.upsert(VideoIdCacheEntity("artist b", "vid2", 2000L))
        dao.upsert(VideoIdCacheEntity("artist c", null, 3000L))

        dao.deleteAll()

        assertNull(dao.getByArtistName("artist a"))
        assertNull(dao.getByArtistName("artist b"))
        assertNull(dao.getByArtistName("artist c"))
    }
}
