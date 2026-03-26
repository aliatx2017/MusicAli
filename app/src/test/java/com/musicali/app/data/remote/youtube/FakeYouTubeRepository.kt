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

    override suspend fun searchTopSong(artistName: String): String? =
        searchResults[artistName]

    override suspend fun createPlaylist(title: String): String = createdPlaylistId

    override suspend fun deletePlaylist(playlistId: String) {
        deletedPlaylistIds.add(playlistId)
    }

    override suspend fun addTrack(playlistId: String, videoId: String) {
        addedVideoIds.add(videoId)
    }
}
