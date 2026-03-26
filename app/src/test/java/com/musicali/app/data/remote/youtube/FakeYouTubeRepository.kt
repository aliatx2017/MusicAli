package com.musicali.app.data.remote.youtube

/**
 * Hand-written fake implementing YouTubeRepository.
 * Mirrors FakeArtistDao and FakeScrapingRepository patterns from the project.
 * Per D-15: offline, fast, no additional test dependencies.
 */
class FakeYouTubeRepository : YouTubeRepository {
    /** Map of artistName -> videoId (or null if no result). Set this in test setup. */
    var searchResults: Map<String, String?> = emptyMap()

    /** Returned by createPlaylist() — override in tests to a known value. */
    var createdPlaylistId: String = "fake-playlist-id"

    /** Accumulates all videoIds added via addTrack(). Assert on this in tests. */
    val addedVideoIds = mutableListOf<String>()

    /** Accumulates all playlistIds passed to deletePlaylist(). Assert on this in tests. */
    val deletedPlaylistIds = mutableListOf<String>()

    /** If set, searchTopSong() throws this exception instead of returning from searchResults. */
    var searchException: Exception? = null

    /** If set, createPlaylist() throws this exception. */
    var createPlaylistException: Exception? = null

    /** If set, addTrack() throws this exception. */
    var addTrackException: Exception? = null

    override suspend fun searchTopSong(artistName: String): String? {
        searchException?.let { throw it }
        return searchResults[artistName]
    }

    override suspend fun createPlaylist(title: String): String {
        createPlaylistException?.let { throw it }
        return createdPlaylistId
    }

    override suspend fun deletePlaylist(playlistId: String) {
        deletedPlaylistIds.add(playlistId)
    }

    override suspend fun addTrack(playlistId: String, videoId: String) {
        addTrackException?.let { throw it }
        addedVideoIds.add(videoId)
    }
}
