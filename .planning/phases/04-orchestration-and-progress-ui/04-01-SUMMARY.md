---
phase: 04-orchestration-and-progress-ui
plan: 01
subsystem: domain-orchestration
tags: [domain, usecase, flow, channelflow, tdd, contracts]
dependency_graph:
  requires: [03-auth-and-youtube-integration]
  provides: [GenerationProgress, PlaylistUiState, GeneratePlaylistUseCase]
  affects: [04-02-playlist-viewmodel-and-ui]
tech_stack:
  added: []
  patterns: [channelFlow, Semaphore, AtomicInteger, TDD-red-green]
key_files:
  created:
    - app/src/main/java/com/musicali/app/feature/playlist/GenerationProgress.kt
    - app/src/main/java/com/musicali/app/feature/playlist/PlaylistUiState.kt
    - app/src/main/java/com/musicali/app/domain/usecase/GeneratePlaylistUseCase.kt
    - app/src/test/java/com/musicali/app/fake/FakeArtistDao.kt
    - app/src/test/java/com/musicali/app/fake/FakeScrapingRepository.kt
    - app/src/test/java/com/musicali/app/fake/InMemorySharedPreferences.kt
    - app/src/test/java/com/musicali/app/domain/usecase/GeneratePlaylistUseCaseTest.kt
  modified:
    - app/src/test/java/com/musicali/app/data/remote/youtube/FakeYouTubeRepository.kt
decisions:
  - "channelFlow used (not flow) to allow send() calls from async coroutines — Pitfall 1 from RESEARCH.md"
  - "AtomicInteger for SearchProgress counter — prevents race conditions in concurrent async blocks (Pitfall 2)"
  - "ArtistSelectionUseCase receives empty artist list and returns ScrapeFailed — no throwing from the use case itself when all genres fall back to empty cache"
  - "D-11 enforced: markArtistsSeen() placed after all addTrack() calls in the building stage"
  - "FakeYouTubeRepository enhanced with configurable exception fields (backward-compatible) to enable error injection in tests"
  - "Shared fake package created (fake/) to avoid duplication of FakeArtistDao, FakeScrapingRepository, InMemorySharedPreferences across test classes"
metrics:
  duration_seconds: 297
  completed_date: "2026-03-26"
  tasks_completed: 2
  files_created: 7
  files_modified: 1
---

# Phase 04 Plan 01: Domain Orchestration Contracts and Pipeline Summary

**One-liner:** channelFlow pipeline orchestrator emitting typed GenerationProgress events across SCRAPING/SELECTING/SEARCHING/BUILDING stages with D-11 atomic history write enforcement.

## What Was Built

### Task 1: GenerationProgress and PlaylistUiState sealed class hierarchy

Two contract files consumed by the upcoming ViewModel (Plan 02):

**GenerationProgress.kt** — per-event sealed class for pipeline progress:
- `StageChanged(stage: Stage)` — emitted when pipeline transitions between stages
- `SearchProgress(completed: Int, total: Int)` — emitted once per artist during YouTube search
- `Success(songsAdded: Int, artistsSkipped: Int)` — terminal success event
- `Failed(error: GenerationError)` — terminal failure event

**PlaylistUiState.kt** — ViewModel-managed UI state:
- `Idle` — initial state, button enabled
- `Generating(stage, searchedCount, totalArtists, progressFraction)` — active generation
- `Success(songsAdded, artistsSkipped)` — completion display
- `Error(message, action)` — with `ErrorAction` (Retry / SignIn / DismissOnly)

### Task 2: GeneratePlaylistUseCase with channelFlow pipeline (TDD)

Full TDD cycle (RED → GREEN):

**Pipeline flow:**
1. Emit `StageChanged(SCRAPING)` → emit `StageChanged(SELECTING)` → call `ArtistSelectionUseCase.selectArtists()`
2. Emit `StageChanged(SEARCHING)` → launch 65 async coroutines with `Semaphore(10)` → emit `SearchProgress(n, total)` per artist
3. Emit `StageChanged(BUILDING)` → delete old playlist (if exists) → create new → add tracks sequentially → `markArtistsSeen()` (D-11)
4. Emit `Success(songsAdded, artistsSkipped)`

**Error handling:**
- `IOException` during `selectArtists()` → `Failed(ScrapeFailed)`
- Empty artist list (all genres empty after cache fallback) → `Failed(ScrapeFailed)`
- `HttpException(401)` → `Failed(AuthExpired)`
- `HttpException(403)` → `Failed(QuotaExceeded)`
- Any other `IOException` from YouTube API → `Failed(NetworkError)`

**9 unit tests verified:**
1. `execute_emitsStagesInOrder` — SCRAPING < SELECTING < SEARCHING < BUILDING
2. `execute_emitsSearchProgressPerArtist` — completed values 1..N with correct total
3. `execute_success_countsMatchVideoIds` — 3 found + 2 null = songsAdded=3, artistsSkipped=2
4. `execute_scrapeFailed_emitsError` — all fetch failures + no cache → ScrapeFailed
5. `execute_quotaExceeded_emitsError` — addTrack throws HttpException(403) → QuotaExceeded
6. `execute_authExpired_emitsError` — addTrack throws HttpException(401) → AuthExpired
7. `execute_failure_noHistoryWritten` — failure → FakeArtistDao has 0 upserted entities (D-11)
8. `execute_deletesExistingPlaylist` — tokenStore has "old-id" → deletePlaylist("old-id") called
9. `execute_noExistingPlaylist_skipsDelete` — no playlistId → deletedPlaylistIds empty
10. `execute_networkError_emitsNetworkError` — createPlaylist throws IOException → NetworkError

### Shared Test Infrastructure Created

- `fake/FakeArtistDao.kt` — shared fake with `getUpsertedArtists()` for D-11 verification
- `fake/FakeScrapingRepository.kt` — shared fake (was private in ArtistSelectionUseCaseTest)
- `fake/InMemorySharedPreferences.kt` — shared fake for TokenStore (was private in TokenStoreTest)
- `FakeYouTubeRepository.kt` — enhanced with `searchException`, `createPlaylistException`, `addTrackException` fields (backward-compatible; existing tests unaffected)

## Commits

| Hash | Message |
|------|---------|
| `690b6e4` | feat(04-01): add GenerationProgress and PlaylistUiState sealed classes |
| `bc0c3f4` | test(04-01): add failing tests for GeneratePlaylistUseCase (RED) |
| `6a533d2` | feat(04-01): implement GeneratePlaylistUseCase pipeline orchestrator (GREEN) |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed OkHttp MediaType deprecation in test helper**
- **Found during:** Task 2 GREEN phase
- **Issue:** `okhttp3.ResponseBody.create(null, "error")` and `MediaType.parse()` are deprecated in OkHttp 4.x
- **Fix:** Used `"text/plain".toMediaTypeOrNull()` extension function with `ResponseBody.create()`
- **Files modified:** `GeneratePlaylistUseCaseTest.kt`
- **Commit:** `6a533d2`

**2. [Rule 2 - Missing] Shared fake package created**
- **Found during:** Task 2 setup
- **Issue:** `FakeScrapingRepository` and `FakeArtistDao` were private to `ArtistSelectionUseCaseTest`, making them unavailable for `GeneratePlaylistUseCaseTest`
- **Fix:** Created `fake/` package with shared versions of both fakes plus `InMemorySharedPreferences`
- **Files created:** `fake/FakeArtistDao.kt`, `fake/FakeScrapingRepository.kt`, `fake/InMemorySharedPreferences.kt`
- **Commit:** `bc0c3f4`

## Known Stubs

None — all data flows are wired. `GeneratePlaylistUseCase.execute()` calls real dependencies (ArtistSelectionUseCase, YouTubeRepository, ArtistHistoryRepository, TokenStore) with no placeholder data.

## Self-Check: PASSED

Files verified:
- `app/src/main/java/com/musicali/app/feature/playlist/GenerationProgress.kt` — FOUND
- `app/src/main/java/com/musicali/app/feature/playlist/PlaylistUiState.kt` — FOUND
- `app/src/main/java/com/musicali/app/domain/usecase/GeneratePlaylistUseCase.kt` — FOUND
- `app/src/test/java/com/musicali/app/domain/usecase/GeneratePlaylistUseCaseTest.kt` — FOUND
- `app/src/test/java/com/musicali/app/fake/FakeArtistDao.kt` — FOUND
- `app/src/test/java/com/musicali/app/fake/FakeScrapingRepository.kt` — FOUND
- `app/src/test/java/com/musicali/app/fake/InMemorySharedPreferences.kt` — FOUND

Commits verified:
- `690b6e4` — feat(04-01): add GenerationProgress and PlaylistUiState sealed classes — FOUND
- `bc0c3f4` — test(04-01): add failing tests for GeneratePlaylistUseCase (RED) — FOUND
- `6a533d2` — feat(04-01): implement GeneratePlaylistUseCase pipeline orchestrator (GREEN) — FOUND

Tests: `./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL (all suites green, no regressions)
