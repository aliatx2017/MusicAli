---
phase: 02-scraping-and-selection
plan: 01
subsystem: data-layer
tags: [room, migration, okhttp, jsoup, genre-cache, scraping-interface]
dependency_graph:
  requires: [01-02 (AppDatabase v1, ArtistEntity, Hilt DI)]
  provides: [GenreCacheEntity, GenreCacheDao, AppDatabase-v2, ScrapingRepository interface, Genre enum, NetworkModule, OkHttpClient singleton]
  affects: [02-02 (implements ScrapingRepository), 02-03 (depends on ScrapingRepository interface)]
tech_stack:
  added: [OkHttp 4.12.0, Jsoup 1.21.1]
  patterns: [Room composite PK entity, Room Migration v1->v2, Hilt singleton OkHttpClient, interface-first contract definition]
key_files:
  created:
    - app/src/main/java/com/musicali/app/data/local/GenreCacheEntity.kt
    - app/src/main/java/com/musicali/app/data/local/GenreCacheDao.kt
    - app/src/main/java/com/musicali/app/data/remote/Genre.kt
    - app/src/main/java/com/musicali/app/data/remote/ScrapingRepository.kt
    - app/src/main/java/com/musicali/app/di/NetworkModule.kt
    - app/src/test/java/com/musicali/app/data/local/GenreCacheDaoTest.kt
    - app/schemas/com.musicali.app.data.local.AppDatabase/2.json
  modified:
    - app/build.gradle.kts
    - app/src/main/java/com/musicali/app/data/local/AppDatabase.kt
    - app/src/main/java/com/musicali/app/di/DatabaseModule.kt
decisions:
  - "No @Transaction on GenreCacheDao suspend functions — transaction wrapping happens at repository level via db.withTransaction {}"
  - "Genre enum holds EveryNoise URLs directly — no runtime config needed for 3 hardcoded pages"
  - "ScrapingRepository interface defined here (Plan 01) so Plans 02 and 03 can execute independently"
metrics:
  duration_minutes: 18
  completed_date: "2026-03-26"
  tasks_completed: 2
  files_created: 7
  files_modified: 3
---

# Phase 02 Plan 01: Genre Cache Schema, ScrapingRepository Interface, and OkHttp/Jsoup Setup Summary

**One-liner:** Room genre_cache table with composite PK (genre+artistName) migrated from v1, ScrapingRepository interface and Genre enum define the scraping contract, OkHttp 4.12.0 and Jsoup 1.21.1 added to build.

## What Was Built

Two tasks completed:

**Task 1 — Infrastructure and contracts:**
- Added OkHttp 4.12.0 and Jsoup 1.21.1 to `app/build.gradle.kts`
- Created `GenreCacheEntity` with composite primary key `(genre, artistName)` and `cachedAt` epoch millis column
- Created `GenreCacheDao` with `getArtistsByGenre`, `insertAll`, and `deleteByGenre` — no `@Transaction` on DAO (per research pitfall, transaction wrapping happens at repository level)
- Created `Genre` enum with `INDIETRONICA`, `NU_DISCO`, `INDIE_SOUL` and their EveryNoise URLs
- Created `ScrapingRepository` interface defining `fetchArtists`, `getCachedArtists`, `cacheArtists` — the contract Plan 02 will implement
- Migrated `AppDatabase` from v1 to v2: added `GenreCacheEntity::class` to `entities`, added `genreCacheDao()`, added `MIGRATION_1_2` companion object executing the `CREATE TABLE genre_cache` DDL
- Updated `DatabaseModule` to register `MIGRATION_1_2` and expose `GenreCacheDao` via `provideGenreCacheDao`
- Created `NetworkModule` providing a singleton `OkHttpClient` with 15s connect / 30s read timeouts

**Task 2 — TDD: GenreCacheDaoTest (4 tests, all green):**
- `insertAll_and_getArtistsByGenre`: inserts 3+2 rows across two genres, queries each genre returns correct count
- `deleteByGenre_removesOnlyTargetGenre`: deleting one genre leaves the other intact
- `insertAll_compositeKeyPrevents_duplicates`: duplicate composite PK throws `SQLiteConstraintException`
- `getArtistsByGenre_returnsEmptyForUnknownGenre`: unknown genre returns empty list

## Verification

- `./gradlew :app:assembleDebug -x lint` — BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest -x lint` — BUILD SUCCESSFUL (all Phase 1 + Phase 2 tests pass)
- Room schema v2 JSON exported at `app/schemas/com.musicali.app.data.local.AppDatabase/2.json`

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 | d84ee53 | feat(02-01): add OkHttp/Jsoup deps, GenreCache schema, ScrapingRepository interface |
| Task 2 | 7ed43ee | test(02-01): GenreCacheDaoTest - 4 tests proving DAO correctness |

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. This plan creates data contracts and infrastructure only. No UI rendering, no data flows to stubs.

## Self-Check: PASSED

- `/Users/alex.maksimchuk/claude/projects/MusicAli/app/src/main/java/com/musicali/app/data/local/GenreCacheEntity.kt` — FOUND
- `/Users/alex.maksimchuk/claude/projects/MusicAli/app/src/main/java/com/musicali/app/data/local/GenreCacheDao.kt` — FOUND
- `/Users/alex.maksimchuk/claude/projects/MusicAli/app/src/main/java/com/musicali/app/data/remote/Genre.kt` — FOUND
- `/Users/alex.maksimchuk/claude/projects/MusicAli/app/src/main/java/com/musicali/app/data/remote/ScrapingRepository.kt` — FOUND
- `/Users/alex.maksimchuk/claude/projects/MusicAli/app/src/main/java/com/musicali/app/di/NetworkModule.kt` — FOUND
- `/Users/alex.maksimchuk/claude/projects/MusicAli/app/src/test/java/com/musicali/app/data/local/GenreCacheDaoTest.kt` — FOUND
- `/Users/alex.maksimchuk/claude/projects/MusicAli/app/schemas/com.musicali.app.data.local.AppDatabase/2.json` — FOUND
- Commit d84ee53 — FOUND
- Commit 7ed43ee — FOUND
