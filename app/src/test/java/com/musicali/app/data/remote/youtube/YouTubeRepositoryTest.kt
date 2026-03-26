package com.musicali.app.data.remote.youtube

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
