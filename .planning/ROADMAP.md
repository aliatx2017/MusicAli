# Roadmap: MusicAli

**Created:** 2026-03-25
**Milestone:** v1
**Granularity:** Standard (5 phases)
**Coverage:** 19/19 v1 requirements mapped

---

## Phases

- [x] **Phase 1: Foundation** - Build tooling, Room schema with artist history TTL, Hilt DI wiring, and confirmed YouTube quota strategy (completed 2026-03-26)
- [ ] **Phase 2: Scraping and Selection** - EveryNoise scraping for 3 genres, artist cache fallback, weighted proportional selection, and cross-run deduplication
- [x] **Phase 3: Auth and YouTube Integration** - Google OAuth PKCE flow, token persistence and proactive refresh, YouTube search and playlist create/replace (completed 2026-03-26)
- [ ] **Phase 4: Orchestration and Progress UI** - Full pipeline wired end-to-end via GeneratePlaylistUseCase, single-screen Compose UI with real-time progress
- [ ] **Phase 5: Resilience and Quota Management** - Video ID caching to eliminate repeat quota spend, graceful skip for artists with no search result

---

## Phase Details

### Phase 1: Foundation
**Goal**: The project builds cleanly and persists artist data correctly — all downstream phases have a reliable base to build on
**Depends on**: Nothing (first phase)
**Requirements**: SEL-03, SEL-04
**Success Criteria** (what must be TRUE):
  1. Project compiles with Kotlin 2.3.20 + KSP (not KAPT), AGP 9.1.0, and Jetpack Compose BOM — zero KAPT annotations in the build
  2. Room database exists with an ArtistEntity table that includes a `seenAt` timestamp column — the schema supports TTL-based history expiry from day one
  3. Artist history records expire and re-enter the eligible pool after a configurable TTL (default: 10 runs) without any schema migration required
  4. Hilt dependency graph wires successfully — all repositories and the use case can be injected at compile time
  5. YouTube quota strategy is confirmed before any search code is written — either a quota increase request is submitted or the Room-based video ID cache is locked as the primary mitigation
**Plans:** 3/3 plans complete
Plans:
- [x] 01-01-PLAN.md — Android project scaffolding with Gradle, KSP, Compose BOM, and all Phase 1 dependencies
- [x] 01-02-PLAN.md — Room schema with hybrid TTL, Hilt DI modules, ArtistHistoryRepository, and DAO tests

### Phase 2: Scraping and Selection
**Goal**: The app reliably produces a list of 65 unique, eligible artists from EveryNoise genre pages — the core data input for every playlist generation
**Depends on**: Phase 1
**Requirements**: SCRP-01, SCRP-02, SCRP-03, SCRP-04, SEL-01, SEL-02
**Success Criteria** (what must be TRUE):
  1. App fetches and parses artist names from each of the three EveryNoise pages (Indietronica, Nu Disco, Indie Soul) on demand — parsing validates a minimum artist count and raises a hard error if the page returns fewer than 30 artists
  2. When an EveryNoise page is unreachable, the app falls back to the last successfully cached scrape and continues without crashing
  3. App selects exactly 65 unique artists weighted proportionally by genre list size — larger genre lists contribute more artists, not an equal three-way split
  4. Artists already in the history database are excluded from selection before the final 65 are drawn
**Plans:** 3/3 plans executed
Plans:
- [x] 02-01-PLAN.md — Room schema migration v1->v2, GenreCacheEntity/Dao, OkHttp/Jsoup deps, ScrapingRepository interface, Genre enum, NetworkModule
- [x] 02-02-PLAN.md — ScrapingRepositoryImpl with OkHttp+Jsoup parsing, HTML fixture tests
- [x] 02-03-PLAN.md — ArtistSelectionUseCase with weighted proportional sampling, dedup, and Hilt DI wiring
**UI hint**: no

### Phase 3: Auth and YouTube Integration
**Goal**: The user can sign in once and the app can search YouTube and write playlists to their account — the two external integrations are verified independently before the pipeline is assembled
**Depends on**: Phase 1
**Requirements**: AUTH-01, AUTH-02, AUTH-03, YT-01, YT-04, YT-05
**Success Criteria** (what must be TRUE):
  1. User can sign in with their Google account using AppAuth PKCE + Credential Manager — no WebView; Custom Tabs are used for the consent screen
  2. App persists OAuth tokens in EncryptedSharedPreferences and silently refreshes them on the next launch without prompting the user to sign in again
  3. If the access token expires mid-generation, a proactive OkHttp interceptor refreshes it transparently and the in-flight request ret, — generation continues without user intervention
  4. App can search the YouTube Data API v3 for a top song given an artist name and return a valid video ID
  5. App can delete the existing AliMusings playlist (if it exists) and recreate it using delete + recreate semantics — never item-level deletes — and insert all tracks in a single session
**Plans:** 4/4 plans complete
Plans:
- [x] 03-01-PLAN.md — GCP developer setup (checkpoint) + build.gradle.kts Phase 3 dependencies + BuildConfig fields
- [x] 03-02-PLAN.md — TokenStore + AuthRepository interface/impl + AuthInterceptor + AuthModule + SignInScreen + MainActivity eager gate
- [x] 03-03-PLAN.md — YouTube API @Serializable models + Retrofit interface + YouTubeRepository interface/impl + YouTubeModule + FakeYouTubeRepository + unit tests
- [x] 03-04-PLAN.md — FakeAuthRepository + AuthInterceptorTest (MockWebServer) + TokenStoreTest (injectable clock)
**UI hint**: yes

### Phase 4: Orchestration and Progress UI
**Goal**: The user can tap one button and watch the full scrape-select-search-build pipeline execute with real-time progress — the app is end-to-end functional
**Depends on**: Phase 2, Phase 3
**Requirements**: UX-01, UX-02, UX-03
**Success Criteria** (what must be TRUE):
  1. User sees a single Generate button on the home screen and tapping it starts playlist generation — no other navigation or setup required
  2. User sees a real-time progress indicator that advances through labeled stages (Scraping genres, Selecting artists, Searching YouTube, Building playlist) — the screen never appears frozen during the 30-90 second operation
  3. When generation completes, user sees a summary showing how many artists were found, how many songs were added, and how many artists were skipped due to no search result
  4. Each failure mode (scrape failure, auth expiry, quota exceeded, no results) displays a specific error message with a recovery action — not a generic crash or silent hang
**Plans:** 1/2 plans executed
Plans:
- [x] 04-01-PLAN.md — GenerationProgress/PlaylistUiState contracts + GeneratePlaylistUseCase orchestrator with channelFlow + unit tests
- [ ] 04-02-PLAN.md — PlaylistViewModel state reduction + PlaylistScreen composable + MainActivity wiring + human verification
**UI hint**: yes

### Phase 5: Resilience and Quota Management
**Goal**: The app handles transient YouTube API failures gracefully and avoids burning quota on artists it has already resolved
**Depends on**: Phase 4
**Requirements**: YT-02, YT-03
**Success Criteria** (what must be TRUE):
  1. When an artist's video ID was resolved in a previous run, the app uses the cached Room result instead of calling the YouTube API — warm runs cost near-zero search quota
  2. When a YouTube search returns no result for an artist, the app skips that artist, logs it as skipped, and continues generating the playlist — one missing artist does not abort the entire run
**Plans**: TBD
**UI hint**: no

---

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation | 3/3 | Complete   | 2026-03-26 |
| 2. Scraping and Selection | 3/3 | Complete   | 2026-03-26 |
| 3. Auth and YouTube Integration | 4/4 | Complete   | 2026-03-26 |
| 4. Orchestration and Progress UI | 1/2 | In Progress|  |
| 5. Resilience and Quota Management | 0/? | Not started | - |

---

## Coverage Map

| Requirement | Phase | Notes |
|-------------|-------|-------|
| SEL-03 | Phase 1 | Room schema with seenAt timestamp — must exist before any other phase |
| SEL-04 | Phase 1 | TTL/rolling window built into schema from day one |
| SCRP-01 | Phase 2 | Indietronica scraping |
| SCRP-02 | Phase 2 | Nu Disco scraping |
| SCRP-03 | Phase 2 | Indie Soul scraping |
| SCRP-04 | Phase 2 | Fallback to cached scrape |
| SEL-01 | Phase 2 | Weighted proportional selection of 65 artists |
| SEL-02 | Phase 2 | Exclude history-seen artists from selection |
| AUTH-01 | Phase 3 | AppAuth PKCE + Credential Manager sign-in |
| AUTH-02 | Phase 3 | Token persistence + silent refresh |
| AUTH-03 | Phase 3 | Mid-generation token refresh via interceptor |
| YT-01 | Phase 3 | YouTube Data API v3 search.list |
| YT-04 | Phase 3 | Playlist delete + recreate (not item-level) |
| YT-05 | Phase 3 | Insert all tracks in a single session |
| UX-01 | Phase 4 | Real-time step-level progress indicator |
| UX-02 | Phase 4 | Single-tap generation trigger |
| UX-03 | Phase 4 | Post-generation summary (found / added / skipped) |
| YT-02 | Phase 5 | Video ID cache in Room to avoid repeat quota spend |
| YT-03 | Phase 5 | Skip artists with no YouTube result, continue run |

**Total mapped: 19/19**

---

*Created: 2026-03-25*
*Last updated: 2026-03-26 after Phase 4 planning*
