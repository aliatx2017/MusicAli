package com.musicali.app.data.remote.youtube

import com.musicali.app.auth.TokenStore
import com.musicali.app.data.remote.youtube.model.AddPlaylistItemRequest
import com.musicali.app.data.remote.youtube.model.CreatePlaylistRequest
import com.musicali.app.data.remote.youtube.model.PlaylistItemSnippet
import com.musicali.app.data.remote.youtube.model.PlaylistSnippet
import com.musicali.app.data.remote.youtube.model.PlaylistStatus
import com.musicali.app.data.remote.youtube.model.ResourceId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRepositoryImpl @Inject constructor(
    private val apiService: YouTubeApiService,
    private val tokenStore: TokenStore
) : YouTubeRepository {

    /**
     * Single-call search strategy per RESEARCH.md §Pitfall 1 and D-08.
     * type=video + videoCategoryId=10 (Music) = 100 quota units per call.
     * Two-call Topic channel strategy (D-06) was revised: costs 200 units/artist × 65 = 13,000
     * which exceeds the 10,000 daily free quota. Single-call = 6,500 units, within budget.
     */
    override suspend fun searchTopSong(artistName: String): String? {
        val response = runCatching {
            apiService.searchVideos(query = artistName)
        }.getOrNull() ?: return null
        return response.items.firstOrNull()?.id?.videoId?.takeIf { it.isNotBlank() }
    }

    /**
     * Creates the AliMusings playlist (private) and saves its ID to TokenStore.
     * Per D-10: playlist ID is persisted for delete+recreate in the next run.
     */
    override suspend fun createPlaylist(title: String): String {
        val response = apiService.createPlaylist(
            body = CreatePlaylistRequest(
                snippet = PlaylistSnippet(title = title),
                status = PlaylistStatus(privacyStatus = "private")
            )
        )
        tokenStore.savePlaylistId(response.id)
        return response.id
    }

    /**
     * Deletes the playlist. Ignores 404 (already deleted).
     * Per D-09: delete + recreate strategy — item-level deletes forbidden (7,500 units).
     */
    override suspend fun deletePlaylist(playlistId: String) {
        runCatching {
            apiService.deletePlaylist(id = playlistId)
        }
        // Silently ignore errors (playlist may not exist on first run)
    }

    /**
     * Adds a single video to the playlist.
     * Called once per videoId in the caller — no bulk API exists.
     * 65 calls × 50 units = 3,250 units within quota.
     */
    override suspend fun addTrack(playlistId: String, videoId: String) {
        apiService.addPlaylistItem(
            body = AddPlaylistItemRequest(
                snippet = PlaylistItemSnippet(
                    playlistId = playlistId,
                    resourceId = ResourceId(kind = "youtube#video", videoId = videoId)
                )
            )
        )
    }
}
