---
phase: 02-scraping-and-selection
plan: 03
subsystem: domain-layer
tags: [use-case, weighted-sampling, dedup, hilt-di, tdd, artist-selection]
dependency_graph:
  requires: [02-01 (ScrapingRepository interface, Genre enum, OkHttpClient), 01-02 (ArtistHistoryRepository, ArtistDao, Room)]
  provides: [ArtistSelectionUseCase, ScrapingRepositoryImpl, ScrapingRepository Hilt binding]
  affects: [03-xx (GeneratePlaylistUseCase calls selectArtists())]
tech_stack:
  added: []
  patterns: [coroutineScope/async parallel fetches, weighted proportional sampling with deficit redistribution, TDD with hand-written fakes, @Inject constructor use-case, @Provides interface binding]
key_files:
  created:
    - app/src/main/java/com/musicali/app/domain/usecase/ArtistSelectionUseCase.kt
    - app/src/main/java/com/musicali/app/data/remote/ScrapingRepositoryImpl.kt
    - app/src/test/java/com/musicali/app/domain/usecase/ArtistSelectionUseCaseTest.kt
  modified:
    - app/src/main/java/com/musicali/app/di/NetworkModule.kt
decisions:
  - "TARGET_ARTIST_COUNT = 65 (D-07): fits within 10,000 unit YouTube free tier per D-01/D-02"
  - "Parallel genre fetches via coroutineScope { async {} } per D-09"
  - "Seen artists excluded before quota computation per D-04; quotas based on eligible pool size"
  - "Deficit redistributed proportionally to non-shortfall genres per D-05"
  - "Total < target returns all available, total = 0 returns empty list per D-06 (never hard-error)"
  - "ScrapingRepositoryImpl included in this plan to enable Hilt binding in NetworkModule (parallel worktree context)"
metrics:
  duration_minutes: 3
  completed_date: "2026-03-26"
  tasks_completed: 2
  files_created: 3
  files_modified: 1
---

# Phase 02 Plan 03: ArtistSelectionUseCase with Weighted Proportional Sampling Summary

**One-liner:** ArtistSelectionUseCase fetches 3 genres in parallel, excludes seen artists, computes proportional quotas targeting 65 artists with deficit redistribution, and falls back to cache on IOException.

## What Was Built

Two tasks completed:

**Task 1 — TDD: ArtistSelectionUseCase (7 tests, all green):**

- Created `ArtistSelectionUseCase` with `@Inject constructor` taking `ScrapingRepository` and `ArtistHistoryRepository`
- `selectArtists()` is the single public entry point:
  1. Fetches all 3 genres in parallel via `coroutineScope { async {} }` (D-09)
  2. Gets seen artist names from `ArtistHistoryRepository.getSeenArtistNames()` (D-04)
  3. Filters each genre's artists, excluding seen names via `trim().lowercase()` normalization
  4. Calls `selectWeighted()` with eligible artists targeting 65 (D-07)
- `selectWeighted()` algorithm:
  - Total = 0 → return empty list (D-06)
  - Total <= target → return all available shuffled (D-06)
  - First pass: proportional quotas via `(size/total * target).roundToInt()` with rounding correction
  - Second pass: shortfall genres contribute all available; deficit redistributed proportionally to other genres (D-05)
  - Result deduped, shuffled, capped at target
- Cache fallback: `IOException` on `fetchArtists()` → `getCachedArtists()` (SCRP-04); `IllegalStateException` (min-count fail) also falls back
- Test fakes: `FakeScrapingRepository` (configurable artists/failures/cache), `FakeArtistDao` (configurable seenNames)
- Real `ArtistHistoryRepository` constructed with `FakeArtistDao` and `PreferenceDataStoreFactory.create(tempFile)` — no Robolectric needed
- All 7 tests call `selectArtists()` and pass (0 failures, 0 errors)

**Task 2 — Hilt DI binding:**

- Created `ScrapingRepositoryImpl` with `@Inject constructor(OkHttpClient, AppDatabase)` implementing full scraping + cache logic
- Added `provideScrapingRepository` to `NetworkModule` binding `ScrapingRepositoryImpl` to `ScrapingRepository` interface with `@Singleton`
- `assembleDebug` passes: full Hilt DI graph compiles, `ArtistSelectionUseCase` is injectable via constructor injection

## Verification

- `./gradlew :app:testDebugUnitTest --tests "*.ArtistSelectionUseCaseTest"` — 7 tests, 0 failures, BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest -x lint` — full suite (all Phase 1 + Phase 2 tests), BUILD SUCCESSFUL
- `./gradlew :app:assembleDebug -x lint` — BUILD SUCCESSFUL

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 RED | a192097 | test(02-03): add failing tests for ArtistSelectionUseCase |
| Task 1 GREEN | 242c4c1 | feat(02-03): implement ArtistSelectionUseCase with weighted proportional sampling |
| Task 2 | 4bc995b | feat(02-03): wire ScrapingRepository binding in NetworkModule for Hilt DI |

## Deviations from Plan

**1. [Rule 2 - Missing Critical Artifact] ScrapingRepositoryImpl created in this plan**

- **Found during:** Task 2 — NetworkModule binding requires `ScrapingRepositoryImpl` to compile
- **Issue:** `ScrapingRepositoryImpl` is the responsibility of Plan 02-02 (running in parallel), but it didn't exist in this worktree. The `provideScrapingRepository` binding in `NetworkModule` cannot compile without the class.
- **Fix:** Created `ScrapingRepositoryImpl.kt` in this worktree matching the implementation from the parallel agent's worktree (agent-adff3633). This enables the Hilt binding to compile and `assembleDebug` to pass. The orchestrator merge will resolve any minor differences.
- **Files modified:** `app/src/main/java/com/musicali/app/data/remote/ScrapingRepositoryImpl.kt` (created)
- **Commit:** 4bc995b

## Known Stubs

None. `ArtistSelectionUseCase.selectArtists()` is fully wired and returns real data. No placeholder values or hardcoded empty collections flow to the result.

## Self-Check: PASSED

- `/Users/alex.maksimchuk/claude/projects/MusicAli/app/src/main/java/com/musicali/app/domain/usecase/ArtistSelectionUseCase.kt` — FOUND
- `/Users/alex.maksimchuk/claude/projects/MusicAli/app/src/main/java/com/musicali/app/data/remote/ScrapingRepositoryImpl.kt` — FOUND
- `/Users/alex.maksimchuk/claude/projects/MusicAli/app/src/test/java/com/musicali/app/domain/usecase/ArtistSelectionUseCaseTest.kt` — FOUND
- `/Users/alex.maksimchuk/claude/projects/MusicAli/app/src/main/java/com/musicali/app/di/NetworkModule.kt` — FOUND (modified)
- Commit a192097 — FOUND
- Commit 242c4c1 — FOUND
- Commit 4bc995b — FOUND
