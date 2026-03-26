---
phase: 03-auth-and-youtube-integration
plan: "03"
subsystem: youtube-data-layer
tags: [youtube, retrofit, serialization, repository, hilt, tdd]
dependency_graph:
  requires: [03-01, 03-02]
  provides: [YouTubeRepository, YouTubeApiService, YouTubeRepositoryImpl, FakeYouTubeRepository]
  affects: [di/YouTubeModule, data/remote/youtube]
tech_stack:
  added: [Retrofit 3.0.0, converter-kotlinx-serialization, YouTubeModule]
  patterns: [single-call search strategy, delete+recreate playlist, Hilt abstract module with companion object providers]
key_files:
  created:
    - app/src/main/java/com/musicali/app/data/remote/youtube/model/SearchResponse.kt
    - app/src/main/java/com/musicali/app/data/remote/youtube/model/PlaylistModels.kt
    - app/src/main/java/com/musicali/app/data/remote/youtube/YouTubeApiService.kt
    - app/src/main/java/com/musicali/app/data/remote/youtube/YouTubeRepository.kt
    - app/src/main/java/com/musicali/app/data/remote/youtube/YouTubeRepositoryImpl.kt
    - app/src/main/java/com/musicali/app/di/YouTubeModule.kt
    - app/src/test/java/com/musicali/app/data/remote/youtube/FakeYouTubeRepository.kt
    - app/src/test/java/com/musicali/app/data/remote/youtube/YouTubeRepositoryTest.kt
  modified: []
decisions:
  - "Single-call search strategy (type=video + videoCategoryId=10 = 100 units/artist) — two-call Topic channel strategy was revised because 200 units x 65 = 13,000 exceeds 10,000 free daily quota"
  - "deletePlaylist() returns Unit in Retrofit interface — required for 204 No Content response (Pitfall 5)"
  - "YouTubeModule uses abstract class + companion object pattern to combine @Binds and @Provides in one Hilt module"
  - "Json configured with ignoreUnknownKeys=true and coerceInputValues=true for YouTube API forward-compatibility"
  - "tokenStore.savePlaylistId() called immediately after createPlaylist() response — persists ID for delete+recreate in next run (D-10)"
metrics:
  duration_minutes: 15
  completed_date: "2026-03-26"
  tasks_completed: 3
  files_created: 8
  files_modified: 0
---

# Phase 03 Plan 03: YouTube Data Layer Summary

**One-liner:** Retrofit 3.0.0 YouTube Data API v3 client with @Serializable models, YouTubeRepository interface/impl, Hilt module sharing OkHttpClient with AuthInterceptor, and 8 offline TDD tests.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | YouTube API models + Retrofit interface + YouTubeRepository | 3b26b24 | SearchResponse.kt, PlaylistModels.kt, YouTubeApiService.kt, YouTubeRepository.kt |
| 2 | YouTubeRepositoryImpl + YouTubeModule + Retrofit wiring | b512646 | YouTubeRepositoryImpl.kt, YouTubeModule.kt |
| 3 | FakeYouTubeRepository + YouTubeRepositoryTest (TDD) | 4ca7536 | FakeYouTubeRepository.kt, YouTubeRepositoryTest.kt |

## What Was Built

### YouTube API Models (@Serializable)

`SearchResponse.kt` — models for `search.list` API response: `SearchResponse`, `SearchItem`, `SearchItemId`, `SearchSnippet`. All fields with safe defaults for optional YouTube API fields.

`PlaylistModels.kt` — request and response models for playlist management: `CreatePlaylistRequest`, `PlaylistSnippet`, `PlaylistStatus`, `PlaylistResponse`, `AddPlaylistItemRequest`, `PlaylistItemSnippet`, `ResourceId`, `PlaylistItemResponse`.

### YouTubeApiService (Retrofit Interface)

Four typed endpoint methods:
- `searchVideos()` — `@GET("youtube/v3/search")` with single-call strategy parameters (type=video, videoCategoryId=10)
- `createPlaylist()` — `@POST("youtube/v3/playlists")` returns `PlaylistResponse`
- `deletePlaylist()` — `@DELETE("youtube/v3/playlists")` returns `Unit` (204 No Content, Pitfall 5)
- `addPlaylistItem()` — `@POST("youtube/v3/playlistItems")` returns `PlaylistItemResponse`

### YouTubeRepository Interface

Clean interface with four methods: `searchTopSong()`, `createPlaylist()`, `deletePlaylist()`, `addTrack()`. Quota costs documented in KDoc comments.

### YouTubeRepositoryImpl

- `searchTopSong()` uses `runCatching` — returns null on any network failure rather than propagating exceptions
- `createPlaylist()` calls `tokenStore.savePlaylistId()` immediately after API response (D-10)
- `deletePlaylist()` uses `runCatching` — silently ignores errors (first run has no existing playlist)
- `addTrack()` delegates directly to `apiService.addPlaylistItem()`

### YouTubeModule (Hilt)

Abstract class with companion object pattern — enables both `@Binds` (interface binding) and `@Provides` (Retrofit construction) in a single module. Retrofit is built with:
- `baseUrl("https://www.googleapis.com/")`
- shared `OkHttpClient` singleton from `NetworkModule` (carries `AuthInterceptor`)
- `Json { ignoreUnknownKeys = true; coerceInputValues = true }` converter factory

### FakeYouTubeRepository + YouTubeRepositoryTest

8 offline unit tests — no API calls, no network, all pass in < 1 second:
- `searchTopSong returns videoId for known artist` (YT-01)
- `searchTopSong returns null for artist not in results` (YT-01)
- `searchTopSong returns null when artist mapped to null` (YT-01)
- `deletePlaylist records the playlist ID` (YT-04)
- `createPlaylist returns the preconfigured ID` (YT-04)
- `addTrack appends videoId to addedVideoIds` (YT-05)
- `all videoIds from a batch run are in addedVideoIds` (YT-05)
- `delete then create then insert models the replace flow` (D-09)

## Quota Budget Confirmed

| Operation | Count | Cost Each | Total |
|-----------|-------|-----------|-------|
| search.list (single-call) | 65 | 100 units | 6,500 |
| playlistItems.insert | 65 | 50 units | 3,250 |
| playlists.delete | 1 | 50 units | 50 |
| playlists.insert | 1 | 50 units | 50 |
| **TOTAL** | | | **9,850 units** |

Free daily quota: 10,000 units. Headroom: 150 units (1.5%).

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. All four repository methods are fully implemented. The implementation will require a real YouTube API key in `local.properties` (BuildConfig.YOUTUBE_API_KEY) and a valid OAuth access token from the auth layer (03-02) to make live API calls — but these are infrastructure prerequisites, not code stubs.

## Self-Check: PASSED

Files verified:
- app/src/main/java/com/musicali/app/data/remote/youtube/model/SearchResponse.kt — EXISTS
- app/src/main/java/com/musicali/app/data/remote/youtube/model/PlaylistModels.kt — EXISTS
- app/src/main/java/com/musicali/app/data/remote/youtube/YouTubeApiService.kt — EXISTS
- app/src/main/java/com/musicali/app/data/remote/youtube/YouTubeRepository.kt — EXISTS
- app/src/main/java/com/musicali/app/data/remote/youtube/YouTubeRepositoryImpl.kt — EXISTS
- app/src/main/java/com/musicali/app/di/YouTubeModule.kt — EXISTS
- app/src/test/java/com/musicali/app/data/remote/youtube/FakeYouTubeRepository.kt — EXISTS
- app/src/test/java/com/musicali/app/data/remote/youtube/YouTubeRepositoryTest.kt — EXISTS

Commits verified: 3b26b24, b512646, 4ca7536 — all present in git log.

Build: `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
Tests: `./gradlew :app:testDebugUnitTest --tests "com.musicali.app.data.remote.youtube.*"` — BUILD SUCCESSFUL (8 tests)
