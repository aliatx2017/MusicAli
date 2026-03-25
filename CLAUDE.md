<!-- GSD:project-start source:PROJECT.md -->
## Project

**MusicAli**

An Android music discovery app that scrapes three EveryNoise genre pages (Indietronica, Nu Disco, Indie Soul), randomly selects 150 unique artists weighted by list size, finds each artist's top song on YouTube Music, and builds or refreshes a single YouTube Music playlist called "AliMusings" in the user's account.

**Core Value:** One tap generates a fresh 150-song discovery playlist seeded from curated genre lists — no manual curation required.

### Constraints

- **Platform**: Android (Kotlin/Jetpack Compose) — no cross-platform requirement
- **Music backend**: YouTube Music + YouTube Data API v3 — no Spotify
- **Auth**: Google OAuth (user account) — playlist created in user's own YT Music library
- **Data source**: EveryNoise HTML scraping — no paid data feeds
- **Scale**: 150 artists, 1 song each → 150-track playlist per run
<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->
## Technology Stack

## Recommended Stack
### Core Technologies
| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Kotlin | 2.3.20 | Primary language | Mandated by PROJECT.md; Kotlin 2.x brings K2 compiler (faster builds, better type inference); 2.3.20 is the current stable tooling release as of March 2026 |
| Android Gradle Plugin | 9.1.0 | Build system | Current stable (March 2026); requires Gradle 9.3.1 and JDK 17; sets compileSdk 36 |
| Jetpack Compose BOM | 2026.03.00 | Declarative UI | Maps compose-ui 1.10.5 + material3 1.4.0; BOM approach prevents version conflicts; Compose is the standard UI toolkit for new Android apps |
| Kotlin Coroutines | 1.8.x | Async/concurrency | All 150 network calls (scraping + 150 YouTube searches + playlist ops) must run concurrently; coroutines with structured concurrency via `async`/`await` give fine-grained control over the work; coroutines are the standard async primitive in Kotlin Android |
| Room | 2.8.4 | Local artist history database | Artist history must survive app restarts (PROJECT.md requirement); Room gives type-safe SQL with suspend function support, coroutine flows, and KSP-based code generation — no raw SQLite boilerplate |
### Auth & API Layer
| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| androidx.credentials | 1.5.0 (stable) / 1.6.0-rc02 | Google Sign-In flow | Google deprecated the legacy Google Sign-In SDK in 2023; Credential Manager is the official replacement — it handles the OS-level account picker and returns the ID token needed to bootstrap OAuth |
| com.google.android.libraries.identity.googleid | 1.0.0 | Google ID token request builder | Companion to Credential Manager; provides `GetGoogleIdOption` and `GoogleIdTokenCredential` types needed to construct the sign-in request and parse the result |
| credentials-play-services-auth | 1.5.0 / 1.6.0-rc02 | Play Services fallback for <API 34 | Required for Android 13 and below; delegates credential ops to Google Play Services when the OS-native pathway is unavailable |
### HTTP & Scraping Layer
| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| OkHttp | 4.12.x | HTTP client for scraping + YouTube API calls | Battle-tested; coroutine-compatible via `enqueue` or `executeAsync` (suspend extension available); connection pooling handles the burst of 150 concurrent artist searches without spawning 150 threads; used by Retrofit under the hood so a single shared instance serves both |
| Jsoup | 1.17.x | HTML parsing of EveryNoise pages | Industry-standard Java/Kotlin HTML parser; CSS selector API (`doc.select(".playlistEntry")`) is the right tool for extracting artist names from static HTML; EveryNoise renders HTML server-side so no JS execution is needed, making Jsoup sufficient — no headless browser required |
| Retrofit | 2.11.x | Type-safe HTTP client for YouTube Data API v3 | YouTube Data API v3 is a plain JSON REST API — no Android SDK exists for it (the Java client library is too heavy and designed for server environments); Retrofit with a Moshi or Kotlin Serialization converter gives typed request/response models and suspend function support; much cleaner than raw OkHttp for the 4+ endpoint types needed (search.list, playlists.insert, playlists.update, playlistItems.insert) |
| kotlinx.serialization | 1.7.x | JSON deserialization of YouTube API responses | Kotlin-native, works with Kotlin Serialization Retrofit converter (`retrofit2-kotlinx-serialization-converter`); avoids Gson's reflection-based approach which can conflict with R8/ProGuard; pairs with K2 compiler naturally |
### Persistence Layer
| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Room | 2.8.4 | Artist history storage | (See Core Technologies above) Use Room for the artist history table — it is the right tool when you need querying ("which artists have been used before?") and the data set can grow unbounded over time |
| DataStore Preferences | 1.2.1 | OAuth token caching + app preferences | Stores the user's OAuth access/refresh tokens between sessions and lightweight preferences (last playlist ID, generation timestamp); DataStore replaces SharedPreferences for coroutine-aware, type-safe key-value storage |
### UI Layer
| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Compose BOM | 2026.03.00 | UI framework | Single button + progress indicator is this app's entire UI surface; Compose is more than sufficient and avoids XML View inflation overhead |
| activity-compose | 1.13.0 | Compose host Activity | `ComponentActivity` + `setContent {}` entry point; required for edge-to-edge and Compose interop |
| lifecycle-viewmodel-compose | 2.10.0 | ViewModel scoping in Compose | `viewModel()` composable connects the UI to the ViewModel holding the generation state (idle / running / done / error) |
| lifecycle-runtime-ktx | 2.10.0 | Lifecycle-aware coroutine scopes | `lifecycleScope` and `repeatOnLifecycle` for collecting StateFlow from the ViewModel in a lifecycle-safe way |
| navigation-compose | 2.9.7 | Screen navigation | Likely overkill for a single-screen app; **include conditionally** — if the app grows to Settings or History screens, add it then |
### Dependency Injection
| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Hilt | 2.52 (Dagger-Hilt) + androidx.hilt 1.3.0 | Dependency injection | Hilt is the standard DI framework for Android (per Google's architecture guidance); it wires OkHttp, Retrofit, Room DAO, DataStore, and Repository instances without manual factory boilerplate; critical for testability of the network layer |
### Build Tooling
| Tool | Version | Purpose | Notes |
|------|---------|---------|-------|
| KSP (Kotlin Symbol Processing) | 2.3.20-x | Code generation for Room + Hilt | Replaces KAPT; required for Room 2.7+ and strongly recommended for Hilt with Kotlin 2.x; version must match Kotlin version prefix |
| Android Gradle Plugin | 9.1.0 | Build | Requires Gradle 9.3.1, JDK 17, compileSdk 36 |
## Installation (build.gradle.kts snippets)
## Alternatives Considered
| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Retrofit + kotlinx.serialization | Ktor Client | Ktor is more idiomatic Kotlin-multiplatform; use it if the project ever targets iOS/Desktop; for Android-only Retrofit has wider community examples for YouTube API patterns |
| Retrofit + kotlinx.serialization | Google API Java Client Library (google-api-client-android) | The Google client library wraps YouTube API nicely but ships with outdated Guava and Jackson dependencies that conflict with modern Android apps and bloat the APK; avoid unless the project migrates to server-side |
| Jsoup | Playwright / Puppeteer (via subprocess) | Only needed if EveryNoise rendered content via JavaScript. EveryNoise serves static HTML — Jsoup is sufficient. Do NOT add a headless browser. |
| Room | SQLite directly | Room's type-safety and coroutine support eliminate entire categories of threading bugs; raw SQLite is only justified if Room's annotation processor overhead is unacceptable (it is not for this app size) |
| DataStore Preferences | SharedPreferences | SharedPreferences is synchronous and not safe for storing auth tokens on a background thread; DataStore is the official replacement |
| Hilt | Koin | Koin has simpler syntax but is runtime-based (errors at runtime, not compile time); Hilt catches DI misconfigurations at compile time via KSP — critical for a project using multiple repositories and async operations |
| Credential Manager | Legacy Google Sign-In SDK (play-services-auth < 20.7) | The legacy SDK was deprecated in 2023 and removed from active maintenance; it does not support passkeys or the new OS-level credential picker; do not use it |
## What NOT to Use
| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Legacy Google Sign-In (`com.google.android.gms:play-services-auth` pre-Credential Manager) | Deprecated 2023; does not return YouTube-scoped access tokens via the new flow; Google's migration guide explicitly says to move to Credential Manager | `androidx.credentials` + `googleid` library |
| Google API Java Client Library for Android (`google-api-services-youtube`) | Ships Guava, Apache HttpClient, Jackson — all conflict with modern Android build tooling; causes 64K dex method limit issues and D8/R8 warnings; designed for JVM servers, not Android | Plain Retrofit + kotlinx.serialization against the REST endpoints directly |
| KAPT | Deprecated; does not work correctly with Kotlin 2.x K2 compiler; significantly slower than KSP for annotation processing | KSP (`com.google.devtools.ksp`) |
| LiveData | Superseded by Kotlin StateFlow/SharedFlow for new Compose projects; LiveData is lifecycle-aware but StateFlow integrates more naturally with coroutines and Compose `collectAsStateWithLifecycle()` | `StateFlow` + `collectAsStateWithLifecycle()` |
| XML Views / Fragments | PROJECT.md mandates Jetpack Compose; Fragments add lifecycle complexity without benefit in a single-screen app | Compose only |
| WorkManager | PROJECT.md explicitly states on-demand generation only (no background scheduling); WorkManager is for deferrable background tasks | None — run everything in a coroutine triggered by the button tap using `viewModelScope.launch` |
| RxJava | Kotlin coroutines + Flow replace RxJava for all use cases in this app; RxJava adds a large dependency and steep learning curve for no benefit | Kotlin Coroutines + Flow |
## Stack Patterns by Variant
- Fire a single `OkHttpClient.get()` call per genre page (3 calls total)
- Parse with Jsoup using CSS selectors (`document.select(".playlistEntry span")` — verify exact selector at implementation time by inspecting the live page)
- Run all 3 in parallel via `async { }` in a `coroutineScope`
- Batch calls using `async { }` within `coroutineScope` with a semaphore (`Semaphore(10)`) to cap concurrent API calls at 10 to avoid quota exhaustion
- YouTube Data API v3 `search.list` endpoint: `q = "$artistName", type = "video", videoCategoryId = "10"` (Music category)
- Each call costs 100 quota units; 150 searches = 15,000 quota units; default daily quota is 10,000 — **this exceeds the default quota** — see PITFALLS.md
- Credential Manager gives an ID token (for authentication) but NOT a YouTube-scoped access token
- Use the `com.google.auth:google-auth-library-oauth2-http` approach or manually implement the OAuth authorization code flow with PKCE targeting the `https://www.googleapis.com/auth/youtube` scope
- Store the resulting access_token + refresh_token in DataStore; refresh the access token proactively before it expires (3600s)
## Version Compatibility
| Package | Compatible With | Notes |
|---------|-----------------|-------|
| Room 2.8.4 | KSP 2.3.20-x, Kotlin 2.3.20 | Use KSP, not KAPT; KAPT is broken with K2 |
| Hilt 2.52 | KSP 2.3.20-x, Kotlin 2.3.20 | Hilt added KSP support in 2.48; use KSP plugin |
| Compose BOM 2026.03.00 | AGP 9.1.0, Kotlin 2.3.20 | Compose compiler is now bundled with the Kotlin plugin since Kotlin 2.0; no separate `compose-compiler` version needed |
| androidx.credentials 1.5.0 | minSdk 23+ | 1.6.0-rc02 also requires minSdk 23; rc02 not yet stable as of March 2026 |
| navigation-compose 2.9.7 | Compose BOM 2026.03.00 | Navigation 2.9+ uses type-safe routes with kotlinx.serialization; add the serialization plugin |
| kotlinx-serialization 1.7.x | Kotlin 2.3.20 | Serialization plugin version must match Kotlin version |
## Sources
- `https://developer.android.com/jetpack/compose/bom/bom-mapping` — Compose BOM 2026.03.00 verified (HIGH confidence)
- `https://developer.android.com/jetpack/androidx/releases/credentials` — credentials 1.5.0 stable / 1.6.0-rc02 verified (HIGH confidence)
- `https://developer.android.com/jetpack/androidx/releases/room` — Room 2.8.4 verified (HIGH confidence)
- `https://developer.android.com/jetpack/androidx/releases/lifecycle` — lifecycle 2.10.0 verified (HIGH confidence)
- `https://developer.android.com/jetpack/androidx/releases/navigation` — navigation-compose 2.9.7 verified (HIGH confidence)
- `https://developer.android.com/jetpack/androidx/releases/hilt` — androidx.hilt 1.3.0 verified (HIGH confidence)
- `https://developer.android.com/jetpack/androidx/releases/datastore` — datastore-preferences 1.2.1 verified (HIGH confidence)
- `https://developer.android.com/jetpack/androidx/releases/activity` — activity-compose 1.13.0 verified (HIGH confidence)
- `https://developer.android.com/build/releases/gradle-plugin` — AGP 9.1.0 verified (HIGH confidence)
- `https://kotlinlang.org/docs/releases.html` — Kotlin 2.3.20 verified (HIGH confidence)
- `https://developer.android.com/build/migrate-to-ksp` — KSP plugin confirmed; exact 2.3.20 patch suffix needs verification against https://github.com/google/ksp/releases (MEDIUM confidence)
- OkHttp 4.12.x — Training data only; could not verify via WebFetch; check https://square.github.io/okhttp/ before implementation (LOW confidence)
- Jsoup 1.17.2 — Training data only; check https://jsoup.org/ before implementation (LOW confidence)
- Retrofit 2.11.x — Training data only; check https://square.github.io/retrofit/ before implementation (LOW confidence)
- `com.google.android.libraries.identity.googleid:googleid:1.0.0` — version confirmed via official credentials release notes (MEDIUM confidence; newer patch may exist)
- YouTube Data API v3 quota costs (100 units/search) — Training data; verify at https://developers.google.com/youtube/v3/determine_quota_cost (MEDIUM confidence)
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd:quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd:debug` for investigation and bug fixing
- `/gsd:execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->



<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd:profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
