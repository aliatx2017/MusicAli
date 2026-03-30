---
phase: "05"
plan: "01"
subsystem: data-layer
tags: [room, cache, quota, youtube, resilience]
dependency_graph:
  requires: [04-02-PLAN.md, YouTubeRepository, AppDatabase v2]
  provides: [VideoIdCacheEntity, VideoIdCacheDao, AppDatabase v3, cache-first searchTopSong]
  affects: [YouTubeRepositoryImpl, DatabaseModule, GeneratePlaylistUseCase (indirectly)]
tech_stack:
  added: [VideoIdCacheEntity (Room entity), VideoIdCacheDao (Room DAO)]
  patterns: [cache-first with null sentinel, Room schema migration, in-memory DAO testing with Robolectric]
key_files:
  created:
    - app/src/main/java/com/musicali/app/data/local/VideoIdCacheEntity.kt
    - app/src/main/java/com/musicali/app/data/local/VideoIdCacheDao.kt
    - app/src/test/java/com/musicali/app/data/local/VideoIdCacheDaoTest.kt
    - app/schemas/com.musicali.app.data.local.AppDatabase/3.json
  modified:
    - app/src/main/java/com/musicali/app/data/local/AppDatabase.kt
    - app/src/main/java/com/musicali/app/di/DatabaseModule.kt
    - app/src/main/java/com/musicali/app/data/remote/youtube/YouTubeRepositoryImpl.kt
    - app/src/test/java/com/musicali/app/data/remote/youtube/FakeYouTubeRepository.kt
decisions:
  - "Cache null videoId as sentinel: a null entry in video_id_cache means known no-result — avoids burning quota for artists that definitely have no YouTube match"
  - "Cache-first in YouTubeRepositoryImpl.searchTopSong(): check Room before calling the API; write all results (including null) to cache after API call"
  - "YT-03 skip behavior was already implemented in GeneratePlaylistUseCase; no changes to use case layer required"
metrics:
  duration_seconds: 204
  completed_date: "2026-03-30"
  tasks_completed: 4
  files_created_or_modified: 8
---

# Phase 05 Plan 01: Resilience and Quota Management Summary

**One-liner:** Room video_id_cache table (schema v3) with cache-first searchTopSong() eliminates repeat YouTube API quota spend on warm runs; null sentinel caches known no-result artists.

## What Was Built

Phase 5 adds video ID caching (YT-02) so that artists resolved in a previous playlist generation run bypass the YouTube `search.list` API entirely on subsequent runs. This reduces per-run quota cost from 6,500 units toward zero for stable artist pools.

### YT-02: Video ID Cache

A new Room entity `VideoIdCacheEntity` (table `video_id_cache`) stores:
- `normalizedArtistName` (primary key, `name.trim().lowercase()`)
- `videoId` (nullable — `null` = known no-result for this artist)
- `cachedAt` (epoch millis)

The `VideoIdCacheDao` provides `getByArtistName()`, `upsert()`, and `deleteAll()`.

`AppDatabase` was bumped from version 2 to version 3 with `MIGRATION_2_3` creating the new table. The migration is registered in `DatabaseModule`.

`YouTubeRepositoryImpl.searchTopSong()` now uses a cache-first strategy:
1. Normalize artist name
2. Check `VideoIdCacheDao` — if hit, return `cached.videoId` immediately (may be null)
3. If miss, call `YouTubeApiService.searchVideos()`
4. Write result (including null) to cache via `upsert()`
5. Return result

### YT-03: Skip Artists with No Result

This was already implemented in `GeneratePlaylistUseCase` (Phase 4). `searchTopSong()` returns null, `GeneratePlaylistUseCase` filters nulls into the skip count, and generation continues. Verified by existing tests (`execute_success_countsMatchVideoIds` — 3 found, 2 skipped).

## Tests

- `VideoIdCacheDaoTest`: 5 Robolectric in-memory Room tests covering upsert, null sentinel, cache miss, replace-on-same-key, deleteAll
- `FakeYouTubeRepository`: Added `searchCallCounts` map for future test verification of cache bypass
- Full test suite: **71 tests, all passing**

## Deviations from Plan

### YT-03 Already Implemented

**Found during:** Task 3 research
**Issue:** Plan Task 4 mentioned adding tests to verify skip behavior; the behavior itself was already in `GeneratePlaylistUseCase` from Phase 4.
**Fix:** No code change needed. Existing `GeneratePlaylistUseCaseTest.execute_success_countsMatchVideoIds` already covers YT-03. `FakeYouTubeRepository.searchCallCounts` was added as planned for future cache verification use.
**Classification:** Rule 3 (no blocking issue — plan description was accurate about the cache task; skip behavior was a bonus).

## Known Stubs

None. All features are fully wired:
- `VideoIdCacheDao` is injected into `YouTubeRepositoryImpl` via Hilt
- `VideoIdCacheDao` is provided by `DatabaseModule`
- Cache is read and written in `searchTopSong()`

## Self-Check: PASSED

Files verified to exist:
- `app/src/main/java/com/musicali/app/data/local/VideoIdCacheEntity.kt` — FOUND
- `app/src/main/java/com/musicali/app/data/local/VideoIdCacheDao.kt` — FOUND
- `app/src/test/java/com/musicali/app/data/local/VideoIdCacheDaoTest.kt` — FOUND
- `app/schemas/com.musicali.app.data.local.AppDatabase/3.json` — FOUND

Commit `06f4c6a` verified: feat(05-01): add video ID cache (Room v3) for quota-free warm runs
