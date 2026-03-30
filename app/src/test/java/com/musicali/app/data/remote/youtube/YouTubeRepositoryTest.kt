package com.musicali.app.data.remote.youtube

import com.musicali.app.auth.TokenStore
import com.musicali.app.data.local.VideoIdCacheEntity
import com.musicali.app.data.remote.youtube.model.AddPlaylistItemRequest
import com.musicali.app.data.remote.youtube.model.CreatePlaylistRequest
import com.musicali.app.data.remote.youtube.model.PlaylistItemResponse
import com.musicali.app.data.remote.youtube.model.PlaylistResponse
import com.musicali.app.data.remote.youtube.model.PlaylistSnippet
import com.musicali.app.data.remote.youtube.model.SearchItem
import com.musicali.app.data.remote.youtube.model.SearchItemId
import com.musicali.app.data.remote.youtube.model.SearchResponse
import com.musicali.app.data.remote.youtube.model.SearchSnippet
import com.musicali.app.fake.FakeVideoIdCacheDao
import com.musicali.app.fake.InMemorySharedPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Tests for YouTubeRepository contract using FakeYouTubeRepository.
 * Covers YT-01 (search), YT-04 (playlist delete+recreate), YT-05 (track insertion).
 * Per D-15: all offline, no API calls.
 */
class YouTubeRepositoryTest {

    private lateinit var repo: FakeYouTubeRepository

    @Before
    fun setup() {
        repo = FakeYouTubeRepository()
    }

    // --- YT-01 ---

    @Test
    fun `searchTopSong returns videoId for known artist`() = runTest {
        repo.searchResults = mapOf("Bonobo" to "dQw4w9WgXcQ")
        val result = repo.searchTopSong("Bonobo")
        assertEquals("dQw4w9WgXcQ", result)
    }

    @Test
    fun `searchTopSong returns null for artist not in results`() = runTest {
        repo.searchResults = emptyMap()
        val result = repo.searchTopSong("UnknownArtist")
        assertNull(result)
    }

    @Test
    fun `searchTopSong returns null when artist mapped to null`() = runTest {
        repo.searchResults = mapOf("Obscure Artist" to null)
        val result = repo.searchTopSong("Obscure Artist")
        assertNull(result)
    }

    // --- YT-04 ---

    @Test
    fun `deletePlaylist records the playlist ID`() = runTest {
        repo.deletePlaylist("existing-playlist-id")
        assertTrue(repo.deletedPlaylistIds.contains("existing-playlist-id"))
    }

    @Test
    fun `createPlaylist returns the preconfigured ID`() = runTest {
        repo.createdPlaylistId = "new-playlist-123"
        val result = repo.createPlaylist("AliMusings")
        assertEquals("new-playlist-123", result)
    }

    // --- YT-05 ---

    @Test
    fun `addTrack appends videoId to addedVideoIds`() = runTest {
        repo.addTrack("playlist-id", "video-1")
        repo.addTrack("playlist-id", "video-2")
        assertEquals(listOf("video-1", "video-2"), repo.addedVideoIds)
    }

    @Test
    fun `all videoIds from a batch run are in addedVideoIds`() = runTest {
        val videoIds = (1..65).map { "video-$it" }
        videoIds.forEach { repo.addTrack("playlist-id", it) }
        assertEquals(65, repo.addedVideoIds.size)
        assertEquals(videoIds, repo.addedVideoIds)
    }

    @Test
    fun `delete then create then insert models the replace flow`() = runTest {
        // Simulate delete + recreate strategy (D-09)
        repo.deletePlaylist("old-playlist-id")
        val newId = repo.createPlaylist("AliMusings")
        repo.addTrack(newId, "video-a")
        repo.addTrack(newId, "video-b")

        assertTrue(repo.deletedPlaylistIds.contains("old-playlist-id"))
        assertEquals("fake-playlist-id", newId)
        assertEquals(listOf("video-a", "video-b"), repo.addedVideoIds)
    }
}

// ---------------------------------------------------------------------------
// Cache-behavior tests for YouTubeRepositoryImpl (YT-02)
// Uses FakeVideoIdCacheDao + FakeYouTubeApiService to test the real impl.
// ---------------------------------------------------------------------------

/**
 * Hand-written fake for YouTubeApiService.
 * Allows controlling search results and throwing errors for test scenarios.
 */
private class FakeYouTubeApiService(
    private val searchResult: SearchResponse = SearchResponse(emptyList()),
    private val searchException: Exception? = null
) : YouTubeApiService {

    var searchCallCount = 0

    override suspend fun searchVideos(
        query: String,
        type: String,
        videoCategoryId: String,
        order: String,
        maxResults: Int,
        part: String,
        apiKey: String
    ): SearchResponse {
        searchCallCount++
        searchException?.let { throw it }
        return searchResult
    }

    override suspend fun createPlaylist(
        part: String,
        apiKey: String,
        body: CreatePlaylistRequest
    ): PlaylistResponse = PlaylistResponse(id = "test-playlist-id", snippet = PlaylistSnippet(title = "AliMusings"))

    override suspend fun deletePlaylist(id: String, apiKey: String): Unit = Unit

    override suspend fun addPlaylistItem(
        part: String,
        apiKey: String,
        body: AddPlaylistItemRequest
    ): PlaylistItemResponse = PlaylistItemResponse(id = "test-item-id")
}

/**
 * Tests proving YouTubeRepositoryImpl cache-hit/miss/null/error behavior.
 * YT-02: warm runs must cost zero search quota units.
 */
class YouTubeRepositoryImplCacheTest {

    private lateinit var cacheDao: FakeVideoIdCacheDao
    private lateinit var tokenStore: TokenStore

    @Before
    fun setUp() {
        cacheDao = FakeVideoIdCacheDao()
        tokenStore = TokenStore(prefs = InMemorySharedPreferences())
    }

    @Test
    fun `searchTopSong returns cached videoId without calling API`() = runTest {
        // Pre-populate cache with a known result
        cacheDao.upsert(
            VideoIdCacheEntity(
                normalizedArtistName = "radiohead",
                videoId = "cachedVid",
                cachedAt = 1000L
            )
        )
        val fakeApiService = FakeYouTubeApiService(
            searchResult = SearchResponse(
                listOf(SearchItem(id = SearchItemId("video", "apiVid"), snippet = SearchSnippet("title")))
            )
        )
        val impl = YouTubeRepositoryImpl(fakeApiService, tokenStore, cacheDao)

        val result = impl.searchTopSong("Radiohead")

        assertEquals("cachedVid", result)
        assertEquals("API should NOT be called on cache hit", 0, fakeApiService.searchCallCount)
    }

    @Test
    fun `searchTopSong calls API on cache miss and stores result`() = runTest {
        val fakeApiService = FakeYouTubeApiService(
            searchResult = SearchResponse(
                listOf(SearchItem(id = SearchItemId("video", "apiVid"), snippet = SearchSnippet("title")))
            )
        )
        val impl = YouTubeRepositoryImpl(fakeApiService, tokenStore, cacheDao)

        val result = impl.searchTopSong("Radiohead")

        assertEquals("apiVid", result)
        assertEquals("API should be called on cache miss", 1, fakeApiService.searchCallCount)
        val cached = cacheDao.getEntry("radiohead")
        assertEquals("Result should be stored in cache", "apiVid", cached?.videoId)
    }

    @Test
    fun `searchTopSong returns null on cache miss and API no result`() = runTest {
        val fakeApiService = FakeYouTubeApiService(searchResult = SearchResponse(emptyList()))
        val impl = YouTubeRepositoryImpl(fakeApiService, tokenStore, cacheDao)

        val result = impl.searchTopSong("Unknown Artist")

        assertNull(result)
        // The null result (known no-result) should be stored as a null sentinel in the cache
        assertTrue("Cache should have an entry for the artist", cacheDao.containsKey("unknown artist"))
        assertNull("Cached videoId should be null (no-result sentinel)", cacheDao.getEntry("unknown artist")?.videoId)
    }

    @Test
    fun `searchTopSong returns null on cache miss and API error`() = runTest {
        val fakeApiService = FakeYouTubeApiService(searchException = IOException("network error"))
        val impl = YouTubeRepositoryImpl(fakeApiService, tokenStore, cacheDao)

        val result = impl.searchTopSong("Artist")

        assertNull("runCatching swallows IOException and returns null", result)
        assertEquals("Nothing should be stored in cache on API error", 0, cacheDao.size())
    }
}
