---
phase: 03-auth-and-youtube-integration
plan: 01
subsystem: infra
tags: [appauth, oauth, credentials, retrofit, kotlinx-serialization, buildconfig, ksp]

# Dependency graph
requires:
  - phase: 01-foundation
    provides: "KSP/Hilt/Room build foundation that Phase 3 deps integrate into"
provides:
  - "AppAuth 0.11.1 + security-crypto 1.1.0 on the compile classpath"
  - "Credential Manager 1.5.0 + googleid 1.0.0 on the compile classpath"
  - "Retrofit 3 BOM 3.0.0 + kotlinx-serialization converter on the compile classpath"
  - "kotlinx-serialization-json 1.7.3 on the compile classpath"
  - "BuildConfig.YOUTUBE_API_KEY and BuildConfig.GOOGLE_CLIENT_ID generated from local.properties"
  - "appAuthRedirectScheme manifest placeholder set to com.musicali.app"
  - "OkHttp MockWebServer 4.12.0 in testImplementation for AuthInterceptor tests"
  - "kotlinx.serialization plugin (2.3.20) wired for @Serializable YouTube response models"
affects: [03-02, 03-03, 03-04]

# Tech tracking
tech-stack:
  added:
    - "net.openid:appauth:0.11.1"
    - "androidx.security:security-crypto:1.1.0"
    - "androidx.credentials:credentials:1.5.0"
    - "androidx.credentials:credentials-play-services-auth:1.5.0"
    - "com.google.android.libraries.identity.googleid:googleid:1.0.0"
    - "com.squareup.retrofit2:retrofit-bom:3.0.0 (platform BOM)"
    - "com.squareup.retrofit2:retrofit"
    - "com.squareup.retrofit2:converter-kotlinx-serialization"
    - "org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3"
    - "com.squareup.okhttp3:mockwebserver:4.12.0 (testImplementation)"
    - "org.jetbrains.kotlin.plugin.serialization:2.3.20 (Gradle plugin)"
  patterns:
    - "Read local.properties at top-level android {} block using import java.util.Properties; Properties() must be declared outside defaultConfig {} to avoid Unresolved reference 'util' in Gradle Kotlin DSL script scope"
    - "BuildConfig fields injected via buildConfigField() referencing localProps; buildConfig = true required in buildFeatures block"
    - "Retrofit 3 uses BOM for version management — individual artifact versions omitted when BOM is declared as platform()"

key-files:
  created: []
  modified:
    - "app/build.gradle.kts"
    - "build.gradle.kts"

key-decisions:
  - "KSP version left at 2.3.6 (latest release using standalone version scheme) — KSP transitioned from kotlinVersion-kspPatch format to standalone versioning; current 2.3.6 already works with Kotlin 2.3.20 as proven by successful build"
  - "Properties() declared at android {} block level (not inside defaultConfig {}) to avoid Gradle Kotlin DSL scope limitation where java.util is Unresolved inside a nested block"
  - "Retrofit 3 BOM (3.0.0) chosen over pinning Retrofit 2.x — plan specified BOM approach for consistent dependency management"

patterns-established:
  - "import java.util.Properties at top of app/build.gradle.kts; declare val localProps at android {} level"
  - "buildFeatures { buildConfig = true } inside android {} is required for buildConfigField() to emit BuildConfig class"

requirements-completed: [AUTH-01, AUTH-02, AUTH-03, YT-01, YT-04, YT-05]

# Metrics
duration: 20min
completed: 2026-03-26
---

# Phase 3 Plan 01: GCP Setup + Phase 3 Dependencies Summary

**AppAuth, Credential Manager, Retrofit 3 BOM, and kotlinx-serialization added to app/build.gradle.kts with BuildConfig fields for YOUTUBE_API_KEY and GOOGLE_CLIENT_ID — project compiles cleanly**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-03-26T00:00:00Z (continuation from human-action checkpoint)
- **Completed:** 2026-03-26
- **Tasks:** 2 total (Task 1: human-action checkpoint complete; Task 2: auto — executed now)
- **Files modified:** 2

## Accomplishments

- All Phase 3 compile-time dependencies added: AppAuth 0.11.1, security-crypto 1.1.0, Credential Manager 1.5.0, Retrofit 3 BOM, kotlinx-serialization-json 1.7.3
- BuildConfig.YOUTUBE_API_KEY and BuildConfig.GOOGLE_CLIENT_ID generated from local.properties (verified in generated BuildConfig.java)
- appAuthRedirectScheme manifest placeholder wired to com.musicali.app
- kotlinx.serialization Gradle plugin added to both root and app build.gradle.kts
- OkHttp MockWebServer added to testImplementation for Phase 03-04 AuthInterceptor tests
- `./gradlew :app:assembleDebug` exits 0, BUILD SUCCESSFUL

## Task Commits

Each task was committed atomically:

1. **Task 1: Google Cloud Console setup** - human-action checkpoint (no commit — developer action)
2. **Task 2: Add Phase 3 dependencies, BuildConfig fields, and align KSP version** - `c0306b3` (chore)

**Plan metadata:** (see final commit below)

## Files Created/Modified

- `app/build.gradle.kts` - Added serialization plugin, BuildConfig fields from local.properties, appAuthRedirectScheme placeholder, Phase 3 deps (AppAuth, Credential Manager, Retrofit 3 BOM, serialization JSON, MockWebServer), buildFeatures block
- `build.gradle.kts` - Added kotlinx.serialization plugin (2.3.20) declaration with apply false

## Decisions Made

- **KSP version unchanged at 2.3.6**: KSP has adopted a standalone versioning scheme (no longer `kotlinVersion-kspPatch`); `2.3.6` is the latest release and already works with Kotlin 2.3.20 as demonstrated by the pre-existing passing build. The plan's instruction to update to `2.3.20-1.0.25` was based on the old versioning format which no longer applies.
- **Properties() at android {} level**: Gradle Kotlin DSL script scope inside `defaultConfig {}` cannot resolve `java.util.Properties()` — must declare it at the top-level `android {}` block with an explicit `import java.util.Properties` at the file top.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed `java.util.Properties()` Unresolved reference in Gradle DSL**
- **Found during:** Task 2 (first build attempt after adding BuildConfig fields)
- **Issue:** Plan specified placing `val localProps = java.util.Properties()` inside `defaultConfig {}`, but Gradle Kotlin DSL in that scope cannot resolve `java.util` — build error: "Unresolved reference 'util'"
- **Fix:** Added `import java.util.Properties` at the top of the file; moved `val localProps = Properties()` and `val localPropsFile` declarations to the `android {}` block level (outside `defaultConfig {}`), then referenced `localProps` inside `defaultConfig {}`
- **Files modified:** app/build.gradle.kts
- **Verification:** `./gradlew :app:assembleDebug` exits 0 after fix
- **Committed in:** c0306b3 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 — bug in plan's code placement)
**Impact on plan:** Necessary fix to make the specified approach work within Gradle Kotlin DSL constraints. Functionally identical outcome.

## Issues Encountered

- **KSP versioning scheme changed**: The plan expected KSP version format `2.3.20-1.0.25` but KSP GitHub releases show the project moved to standalone versioning (`2.3.6`, `2.3.5`, etc.). The current `2.3.6` is the latest and already working. No change needed.

## Auth Gates

- **Task 1 (Google Cloud Console setup)** was a `checkpoint:human-action`. The developer completed all four steps (enabled YouTube Data API v3, created Android OAuth client ID, created API key, populated local.properties). Confirmed by developer response "done" before this continuation agent was spawned.

## Next Phase Readiness

- All Phase 3 compile-time dependencies are on the classpath — Plans 03-02, 03-03, 03-04 can import and use AppAuth, Credential Manager, Retrofit, and kotlinx.serialization without additional build changes
- BuildConfig.YOUTUBE_API_KEY and BuildConfig.GOOGLE_CLIENT_ID available for any code that needs to reference credentials
- The appAuthRedirectScheme placeholder is set — the AndroidManifest intent-filter for AppAuth redirect can be added in Plan 03-02 without further build changes

## Known Stubs

None — this plan only modifies build configuration files, not application code.

---
*Phase: 03-auth-and-youtube-integration*
*Completed: 2026-03-26*

## Self-Check: PASSED

- FOUND: app/build.gradle.kts
- FOUND: build.gradle.kts
- FOUND: .planning/phases/03-auth-and-youtube-integration/03-01-SUMMARY.md
- FOUND commit: c0306b3
- All 10 acceptance criteria verified present in app/build.gradle.kts
- ./gradlew :app:assembleDebug exits 0 (BUILD SUCCESSFUL)
