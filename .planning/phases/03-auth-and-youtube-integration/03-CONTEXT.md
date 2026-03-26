# Phase 3: Auth and YouTube Integration - Context

**Gathered:** 2026-03-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 3 delivers two independently-verified external integrations: (1) Google OAuth PKCE sign-in with token persistence and proactive refresh, and (2) YouTube Data API v3 search and playlist management. The user can sign in once and the app can search for 65 artists' top songs and create/replace the AliMusings playlist. Phase 4 assembles the full pipeline — this phase verifies each integration in isolation.

</domain>

<decisions>
## Implementation Decisions

### Token Storage (AUTH-02)

- **D-01:** OAuth access token and refresh token are stored in **EncryptedSharedPreferences** (backed by Android Keystore). DataStore Preferences is already in the build for the run counter but is NOT suitable for secrets without Tink encryption — using ESP keeps tokens encrypted at rest with no added dependencies.
- **D-02:** AUTH-02 requirement says "DataStore" — this is a documentation artifact. EncryptedSharedPreferences is the canonical decision. Planner should reconcile REQUIREMENTS.md wording but implement with ESP.

### Auth Flow (AUTH-01, AUTH-02, AUTH-03)

- **D-03:** Auth pattern (from STATE.md — locked): Credential Manager for Google account picker → AppAuth PKCE flow targeting `https://www.googleapis.com/auth/youtube` scope → access_token + refresh_token stored in EncryptedSharedPreferences.
- **D-04:** Sign-in trigger: **Eager — on first launch**. The app shows a sign-in screen before reaching the main Generate UI. Once signed in, the app always assumes an authenticated state. Phase 4 does not need to handle a "not signed in" state during generation.
- **D-05:** Proactive token refresh: OkHttp interceptor checks token expiry before each outbound YouTube API request. If within a grace window (e.g., 60s before expiry), the interceptor refreshes the access token using the stored refresh token and retries the original request transparently. The interceptor is added to the existing `OkHttpClient` in `NetworkModule`.

### YouTube Search Strategy (YT-01)

- **D-06:** Search approach: **Topic channel filter**. YouTube auto-generates "Topic" channels (e.g., "Bonobo - Topic") for most artists. Primary strategy: search for `"{artistName} Topic"` to find the Topic channel, then retrieve its most-viewed video. Fallback: plain `search.list` with `q="{artistName}"`, `type=video`, `videoCategoryId=10` if no Topic channel result is found.
- **D-07:** The researcher must spike the exact API call sequence for Topic channel lookup — `search.list` does not have a first-class `channelType=topic` filter, so the approach may combine channel search + video search. Researcher locks the exact parameters before planning.
- **D-08:** Each artist search costs 100 quota units. 65 searches = 6,500 units. Within the 10,000 unit free daily quota (confirmed in Phase 1 D-02).

### Playlist Management (YT-04, YT-05)

- **D-09:** Replace strategy: **delete + recreate** (never item-level deletes). On each run: call `playlists.delete` on the existing AliMusings playlist ID (if stored), then call `playlists.insert` to create a fresh playlist, then batch-insert all tracks via `playlistItems.insert`. This costs ~100 quota units total for create/delete vs 7,500 for item-level deletes.
- **D-10:** AliMusings playlist ID is persisted in EncryptedSharedPreferences alongside the OAuth tokens. If no stored ID, skip the delete step and go straight to insert.

### API Key Storage

- **D-11:** YouTube Data API key is stored in `local.properties` (gitignored) as `YOUTUBE_API_KEY=...`. `build.gradle.kts` reads it and exposes via `BuildConfig.YOUTUBE_API_KEY`. CI injects the key via environment variable. The key is never committed to source control.

### Architecture

- **D-12:** Two new repository interfaces: `AuthRepository` (sign-in, token get/refresh) and `YouTubeRepository` (search, playlist CRUD). Both injected via Hilt into a new `YouTubeModule` (or extend `NetworkModule`).
- **D-13:** Retrofit is added for YouTube API calls — consistent with STATE.md architecture decision. The existing `OkHttpClient` singleton from `NetworkModule` is the shared base; Retrofit wraps it.
- **D-14:** Phase 3 does NOT wire `ArtistSelectionUseCase` into the pipeline yet — that is Phase 4. Phase 3 verifies YouTube integration with hardcoded test artist names in a test or a dedicated integration test.

### Testing Strategy

- **D-15:** YouTube layer tests use **hand-written fakes** — `FakeYouTubeRepository` implementing the interface. Consistent with `FakeArtistDao` from Phase 1 and the selection use case fakes from Phase 2. Tests are offline, fast, no additional test dependencies.
- **D-16:** Auth flow cannot be unit-tested (requires real Google accounts and OS-level Credential Manager). Auth is tested manually / integration only. Document the manual test steps in a TESTING.md or test task comment.

### Claude's Discretion

- Exact Hilt module structure (new `YouTubeModule` vs extending `NetworkModule`)
- Retrofit converter (Moshi vs kotlinx.serialization — either works; kotlinx.serialization is already in the project if used for other JSON)
- EncryptedSharedPreferences file name and key constants
- AppAuth library version selection (researcher verifies latest stable)
- Grace window duration for proactive token refresh (60s recommended)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` §AUTH-01, AUTH-02, AUTH-03 — auth requirements (note: AUTH-02 says DataStore, canonical decision is EncryptedSharedPreferences per D-01)
- `.planning/REQUIREMENTS.md` §YT-01, YT-04, YT-05 — YouTube search and playlist requirements

### Architecture Decisions
- `.planning/STATE.md` §Architecture Decisions — auth pattern (Credential Manager → AppAuth PKCE), playlist replace strategy (delete+recreate = 100 units), quota arithmetic confirming 65 artists fits free tier
- `.planning/STATE.md` §Critical Constraints — three Phase 3 research spikes noted: AppAuth + Credential Manager integration boundary, YouTube search quality for indie artists, playlistItems.insert quota cost confirmation

### Phase Context
- `.planning/ROADMAP.md` Phase 3 section — success criteria (5 items), UI hint: yes
- `.planning/phases/01-foundation/01-CONTEXT.md` — D-04 (minSdk=28 enables Credential Manager 1.5.0), D-05 (single :app module)

### Existing Code Integration Points
- `app/src/main/java/com/musicali/app/di/NetworkModule.kt` — OkHttpClient singleton lives here; auth interceptor is added here
- `app/src/main/java/com/musicali/app/di/DataStoreModule.kt` — DataStore pattern to reference for ESP module structure
- `app/src/main/java/com/musicali/app/feature/playlist/PlaylistViewModel.kt` — currently empty; Phase 3 repositories will eventually be injected here (Phase 4 wires the pipeline)

### Technology References (from CLAUDE.md)
- `CLAUDE.md` §Auth & API Layer — Credential Manager 1.5.0 + googleid 1.0.0 + credentials-play-services-auth 1.5.0
- `CLAUDE.md` §HTTP & Scraping Layer — OkHttp 4.12.0 (already in build); Retrofit 2.11.x (needs adding)
- `CLAUDE.md` §What NOT to Use — legacy Google Sign-In SDK and Google API Java Client Library both explicitly forbidden

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `NetworkModule.provideOkHttpClient()` — existing singleton OkHttpClient; auth interceptor added here (not a new client)
- `DataStoreModule` — existing Hilt module pattern; new ESP module follows same `@InstallIn(SingletonComponent::class)` structure
- `ScrapingRepositoryImpl` — OkHttp usage pattern to replicate for YouTube API calls (before Retrofit is added)
- `ArtistDaoTest` / `FakeArtistDao` — test pattern Phase 3 fakes should mirror

### Established Patterns
- Flat interface + Impl split (e.g., `ScrapingRepository` / `ScrapingRepositoryImpl`) — `AuthRepository` / `YouTubeRepository` follow this
- Hand-written interface fakes for testing — `FakeYouTubeRepository` implementing `YouTubeRepository`
- KSP for annotation processing — no KAPT
- `@Config(sdk = [35])` + `@RunWith(RobolectricTestRunner::class)` for any test touching Android APIs

### Integration Points
- Phase 4 will call `YouTubeRepository.search(artistName)` and `YouTubeRepository.createPlaylist()` / `replacePlaylist()` — these are the contracts Phase 3 must define and implement
- Phase 4 will call `AuthRepository.getValidToken()` (or similar) — Phase 3 must expose a clean token-retrieval interface, not raw EncryptedSharedPreferences access
- `PlaylistViewModel` is the injection point where Phase 3's use cases / repositories enter the UI layer (wired in Phase 4)

</code_context>

<specifics>
## Specific Ideas

- Topic channel search as primary strategy: search `"{artistName} Topic"` to find auto-generated YouTube channels, then get their most popular video. Researcher must confirm exact API call sequence since `channelType=topic` is not a first-class filter.
- EncryptedSharedPreferences holds: `access_token`, `refresh_token`, `token_expiry_ms`, `playlist_id` (AliMusings ID from previous run)
- Eager sign-in: `MainActivity` checks for stored token on `onCreate`. If missing, shows sign-in screen. If present, proceeds directly to Generate screen.
- `BuildConfig.YOUTUBE_API_KEY` pattern: planner must include a `local.properties` setup task with clear instructions for the developer.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 03-auth-and-youtube-integration*
*Context gathered: 2026-03-26*
