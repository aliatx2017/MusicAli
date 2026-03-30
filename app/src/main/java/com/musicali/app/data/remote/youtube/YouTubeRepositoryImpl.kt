package com.musicali.app.data.remote.youtube

import com.musicali.app.auth.TokenStore
import com.musicali.app.data.local.VideoIdCacheDao
import com.musicali.app.data.local.VideoIdCacheEntity
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
    private val tokenStore: TokenStore,
    private val videoIdCacheDao: VideoIdCacheDao
) : YouTubeRepository {

    /**
     * Single-call search strategy per RESEARCH.md §Pitfall 1 and D-08.
     * type=video + videoCategoryId=10 (Music) = 100 quota units per call.
     * Two-call Topic channel strategy (D-06) was revised: costs 200 units/artist × 65 = 13,000
     * which exceeds the 10,000 daily free quota. Single-call = 6,500 units, within budget.
     *
     * YT-02: Cache-first strategy. On cache hit (including null = known no-result), returns
     * immediately without calling the YouTube API. On cache miss, calls the API and writes
     * the result (including null) to the cache for future runs.
     */
    override suspend fun searchTopSong(artistName: String): String? {
        val normalizedName = artistName.trim().lowercase()

        // Cache hit: return immediately without calling the API
        val cached = videoIdCacheDao.getByArtistName(normalizedName)
        if (cached != null) {
            return cached.videoId  // may be null (cached no-result)
        }

        // Cache miss: call the YouTube API
        val response = runCatching {
            apiService.searchVideos(query = artistName)
        }.getOrNull() ?: return null

        val videoId = response.items.firstOrNull()?.id?.videoId?.takeIf { it.isNotBlank() }

        // Write result to cache (including null = no result found for this artist)
        videoIdCacheDao.upsert(
            VideoIdCacheEntity(
                normalizedArtistName = normalizedName,
                videoId = videoId,
                cachedAt = System.currentTimeMillis()
            )
        )

        return videoId
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
     * Deletes the playlist. Ignores 404 (already deleted) but lets other errors propagate.
     * Per D-09: delete + recreate strategy — item-level deletes forbidden (7,500 units).
     *
     * Only 404 is swallowed — quota (403) and auth (401) errors are re-thrown so the caller
     * (GeneratePlaylistUseCase) can surface the correct error message instead of proceeding
     * with playlist creation that will also fail.
     */
    override suspend fun deletePlaylist(playlistId: String) {
        try {
            apiService.deletePlaylist(id = playlistId)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 404) return  // Playlist already gone — safe to ignore
            throw e  // 401, 403, 5xx etc. must propagate to the use case
        }
        // Non-HTTP exceptions (IOException, etc.) propagate naturally
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
