package com.musicali.app.data.remote.youtube

interface YouTubeRepository {
    /**
     * Search YouTube Data API v3 for an artist's top song.
     * Returns the videoId string, or null if no result is found.
     * Uses single-call search.list: type=video, videoCategoryId=10, order=relevance.
     * Cost: 100 quota units per call.
     */
    suspend fun searchTopSong(artistName: String): String?

    /**
     * Create a new playlist with the given title in the authenticated user's account.
     * Returns the new playlist ID. Does not delete any existing playlist.
     * Cost: 50 quota units.
     */
    suspend fun createPlaylist(title: String): String

    /**
     * Delete the playlist with the given ID.
     * Per D-09: delete + recreate strategy — this is called once per run on the old playlist.
     * Cost: 50 quota units.
     */
    suspend fun deletePlaylist(playlistId: String)

    /**
     * Add a single video to the playlist.
     * Callers must call this once per videoId — bulk insert is not supported by the API.
     * Cost: 50 quota units per call.
     */
    suspend fun addTrack(playlistId: String, videoId: String)
}
