package com.musicali.app.data.remote.youtube

import com.musicali.app.BuildConfig
import com.musicali.app.data.remote.youtube.model.AddPlaylistItemRequest
import com.musicali.app.data.remote.youtube.model.CreatePlaylistRequest
import com.musicali.app.data.remote.youtube.model.PlaylistItemResponse
import com.musicali.app.data.remote.youtube.model.PlaylistResponse
import com.musicali.app.data.remote.youtube.model.SearchResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface YouTubeApiService {

    /**
     * Search for a video by artist name.
     * Single-call strategy: type=video + videoCategoryId=10 (Music) = 100 quota units.
     * Per D-08 and RESEARCH.md §Pitfall 1: two-call Topic channel strategy would cost
     * 200 units/artist × 65 = 13,000 units, exceeding the 10,000 daily free quota.
     * Authorization header added by AuthInterceptor — do NOT pass here.
     */
    @GET("youtube/v3/search")
    suspend fun searchVideos(
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("videoCategoryId") videoCategoryId: String = "10",
        @Query("order") order: String = "relevance",
        @Query("maxResults") maxResults: Int = 1,
        @Query("part") part: String = "snippet",
        @Query("key") apiKey: String = BuildConfig.YOUTUBE_API_KEY
    ): SearchResponse

    /**
     * Create a new YouTube playlist. Costs 50 quota units.
     * Returns PlaylistResponse with the new playlist's id.
     */
    @POST("youtube/v3/playlists")
    suspend fun createPlaylist(
        @Query("part") part: String = "snippet,status",
        @Query("key") apiKey: String = BuildConfig.YOUTUBE_API_KEY,
        @Body body: CreatePlaylistRequest
    ): PlaylistResponse

    /**
     * Delete an existing playlist. Costs 50 quota units.
     * Returns Unit — YouTube returns 204 No Content (no body).
     * Per Pitfall 5: must be declared as Unit to avoid EOFException from Retrofit.
     */
    @DELETE("youtube/v3/playlists")
    suspend fun deletePlaylist(
        @Query("id") id: String,
        @Query("key") apiKey: String = BuildConfig.YOUTUBE_API_KEY
    ): Unit

    /**
     * Add a video to a playlist. Costs 50 quota units.
     * 65 tracks × 50 = 3,250 units within the quota budget.
     */
    @POST("youtube/v3/playlistItems")
    suspend fun addPlaylistItem(
        @Query("part") part: String = "snippet",
        @Query("key") apiKey: String = BuildConfig.YOUTUBE_API_KEY,
        @Body body: AddPlaylistItemRequest
    ): PlaylistItemResponse
}
