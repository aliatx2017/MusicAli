---
phase: 04-orchestration-and-progress-ui
plan: 02
subsystem: ui-viewmodel
tags: [viewmodel, compose, tdd, hilt, stateflow, ui-spec]
dependency_graph:
  requires: [04-01-domain-orchestration]
  provides: [PlaylistScreen, PlaylistViewModel, PlaylistGenerator, UseCaseModule]
  affects: [device-verification]
tech_stack:
  added: [material-icons-extended (via Compose BOM 2026.03.00)]
  patterns: [MVVM, StateFlow, collectAsStateWithLifecycle, AnimatedContent, TDD-red-green, Hilt-Binds]
key_files:
  created:
    - app/src/main/java/com/musicali/app/di/UseCaseModule.kt
    - app/src/main/java/com/musicali/app/feature/playlist/PlaylistScreen.kt
    - app/src/test/java/com/musicali/app/feature/playlist/PlaylistViewModelTest.kt
  modified:
    - app/src/main/java/com/musicali/app/domain/usecase/GeneratePlaylistUseCase.kt
    - app/src/main/java/com/musicali/app/feature/playlist/PlaylistViewModel.kt
    - app/src/main/java/com/musicali/app/MainActivity.kt
    - app/build.gradle.kts
decisions:
  - "PlaylistGenerator interface extracted into GeneratePlaylistUseCase.kt for testability — ViewModel injects interface, not concrete class"
  - "UseCaseModule uses @Binds (abstract class) to wire GeneratePlaylistUseCase -> PlaylistGenerator"
  - "material-icons-extended added to Compose BOM to access RadioButtonUnchecked and ErrorOutline icons"
  - "TDD test 2 adjusted: FakePlaylistGenerator emits StageChanged before neverCompletes delay — UnconfinedTestDispatcher advances virtual time, so bare delay(Long.MAX_VALUE) without prior emission left ViewModel in Idle"
  - "hiltViewModel() deprecation warning (package move in hilt-navigation-compose 1.3.0) is pre-existing library behavior — out of scope, does not affect compilation or runtime"
metrics:
  duration_seconds: 720
  completed_date: "2026-03-26"
  tasks_completed: 2
  files_created: 3
  files_modified: 4
---

# Phase 04 Plan 02: UI Layer — PlaylistViewModel and PlaylistScreen Summary

**One-liner:** TDD PlaylistViewModel mapping channelFlow GenerationProgress events to PlaylistUiState with D-03/D-04/D-06 enforcement, plus full four-state Compose PlaylistScreen with stage chips, weighted progress bar, and typed error cards.

## What Was Built

### Task 1: PlaylistGenerator interface, PlaylistViewModel, and PlaylistViewModelTest (TDD)

**Full TDD red-green cycle (7 tests, all passing).**

**Interface extracted:**

`PlaylistGenerator` interface added to `GeneratePlaylistUseCase.kt`:
```kotlin
interface PlaylistGenerator {
    fun execute(): Flow<GenerationProgress>
}
```
`GeneratePlaylistUseCase` now implements it (adds `override` to `execute()`).

**Hilt binding module:**

`UseCaseModule.kt` (abstract class with `@Binds`) wires `GeneratePlaylistUseCase` as the singleton `PlaylistGenerator` implementation.

**PlaylistViewModel rewritten:**
- Injects `PlaylistGenerator` interface (not concrete class)
- `val uiState: StateFlow<PlaylistUiState>` backed by `MutableStateFlow`
- `fun generate()` — D-04 guard (early return if already `Generating`), collects `playlistGenerator.execute()` and maps each `GenerationProgress` event:
  - `StageChanged` → `Generating(stage, searchedCount preserved, progressFractionForStage())`
  - `SearchProgress(n, total)` → `Generating(SEARCHING, n, total, 0.10 + n/total * 0.80)`
  - `Success` → `PlaylistUiState.Success`
  - `Failed` → `PlaylistUiState.Error` via `toMessage()` and `toAction()`
- `fun dismissError()` — resets to `Idle`
- D-03 progress weights: SCRAPING=0.00, SELECTING=0.05, SEARCHING=dynamic, BUILDING=0.90
- D-06 error messages: exact strings from UI-SPEC §9.6

**PlaylistViewModelTest (7 tests, all green):**
1. `initialState_isIdle` — confirms `Idle` on construction
2. `generate_noOp_whenAlreadyGenerating` — D-04 guard: executeCallCount stays 1
3. `generate_mapsStageChanged_toGeneratingState` — SCRAPING maps to `Generating(SCRAPING, 0, 65, 0.0f)`
4. `generate_mapsSearchProgress_toGeneratingWithFraction` — `SearchProgress(23, 65)` maps to correct fraction
5. `generate_mapsSuccess_toSuccessState` — `Success(50, 15)` → `PlaylistUiState.Success(50, 15)`
6. `generate_mapsFailed_toErrorState` — `Failed(QuotaExceeded)` → `Error("YouTube daily quota reached", DismissOnly)`
7. `dismissError_resetsToIdle` — after Error, `dismissError()` → Idle

### Task 2: PlaylistScreen composable + MainActivity wiring

**PlaylistScreen.kt** — full four-state Compose screen per UI-SPEC:

- **Root:** `Surface` + `Column(padding=32.dp, Center alignment)` with `Text("MusicAli", headlineLarge)` and `AnimatedContent` cross-fade (fadeIn/fadeOut)
- **Idle:** `Button("Generate Playlist")` → `viewModel.generate()`
- **Generating:**
  - `StageChipsRow`: `Row(spacedBy(8.dp))` iterating `Stage.entries`; each chip: icon (done=CheckCircle/primary, active=CircularProgressIndicator/16dp, pending=RadioButtonUnchecked/onSurfaceVariant) + 4dp Spacer + `labelMedium` text (60% alpha when pending)
  - `LinearProgressIndicator(progress = { state.progressFraction })` full width
  - Search counter `Text` visible only when `stage == Stage.SEARCHING`
- **Success:** CheckCircle icon + "Playlist built!" (titleMedium) + songs/artists counts (bodyMedium/onSurfaceVariant) + `Button("Generate Again")`
- **Error:** `Card(errorContainer)` with ErrorOutline icon + error message (onErrorContainer) + typed action button: `Button("Retry")` / `Button("Sign in again")` / `TextButton("Try again tomorrow")`
- All icons have `contentDescription` per UI-SPEC §11

**MainActivity.kt updated:**
- Removed `val viewModel: PlaylistViewModel = hiltViewModel()` line
- Replaced `Surface > Box > Text("MusicAli — Ready to generate")` with `PlaylistScreen(onSignInRequired = { isSignedIn = false })`
- Removed unused imports: `Box`, `PlaylistViewModel`, `Surface`, `Alignment`, `hiltViewModel`
- Added import: `com.musicali.app.feature.playlist.PlaylistScreen`

**app/build.gradle.kts:**
- Added `material-icons-extended` via Compose BOM (needed for `RadioButtonUnchecked` and `ErrorOutline` icons)

## Commits

| Hash | Message |
|------|---------|
| `c621023` | feat(04-02): extract PlaylistGenerator interface, expand PlaylistViewModel, add PlaylistViewModelTest |
| `9e78d4f` | feat(04-02): create PlaylistScreen composable and wire into MainActivity |

## Task 3: PENDING CHECKPOINT

Task 3 is a `checkpoint:human-verify` gate. The human must:
1. Build and install: `./gradlew :app:installDebug`
2. Launch MusicAli on device/emulator, sign in with Google
3. Verify "MusicAli" title and "Generate Playlist" button visible
4. Tap "Generate Playlist" and observe 4-stage progress, search counter, and progress bar
5. Verify success screen: "Playlist built!" + counts + "Generate Again" button
6. Open YouTube Music to confirm "AliMusings" playlist exists with songs
7. (Optional) Test error handling: turn off network, tap Generate

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing] Added material-icons-extended dependency**
- **Found during:** Task 2 implementation
- **Issue:** `Icons.Default.RadioButtonUnchecked` and `Icons.Default.ErrorOutline` are in the extended icon set, not the core Material3 library
- **Fix:** Added `implementation("androidx.compose.material:material-icons-extended")` under the Compose BOM block in `app/build.gradle.kts`
- **Files modified:** `app/build.gradle.kts`
- **Commit:** `9e78d4f`

**2. [Rule 1 - Bug] TDD Test 2 adjusted for UnconfinedTestDispatcher behavior**
- **Found during:** Task 1 TDD GREEN phase (test 2 failing after all other tests passed)
- **Issue:** `FakePlaylistGenerator` with `neverCompletes=true` and no events left ViewModel in `Idle` (not `Generating`) because `UnconfinedTestDispatcher` advances virtual time, so `delay(Long.MAX_VALUE)` completed immediately without emitting any state transitions
- **Fix:** Updated `FakePlaylistGenerator` in Test 2 to emit `StageChanged(SCRAPING)` before the infinite delay, ensuring the ViewModel reaches `Generating` state before hanging
- **Files modified:** `PlaylistViewModelTest.kt`
- **Commit:** `c621023`

## Known Stubs

None — all data flows are wired end-to-end. `PlaylistScreen` collects from `PlaylistViewModel.uiState` which maps real `GenerationProgress` events from `GeneratePlaylistUseCase.execute()`. No placeholder data, no hardcoded empty values.

## Self-Check: PASSED

Files verified:
- `app/src/main/java/com/musicali/app/di/UseCaseModule.kt` — FOUND
- `app/src/main/java/com/musicali/app/feature/playlist/PlaylistScreen.kt` — FOUND
- `app/src/test/java/com/musicali/app/feature/playlist/PlaylistViewModelTest.kt` — FOUND
- `app/src/main/java/com/musicali/app/domain/usecase/GeneratePlaylistUseCase.kt` — FOUND (interface + override)
- `app/src/main/java/com/musicali/app/feature/playlist/PlaylistViewModel.kt` — FOUND (rewritten)
- `app/src/main/java/com/musicali/app/MainActivity.kt` — FOUND (PlaylistScreen wired)

Commits verified:
- `c621023` — feat(04-02): extract PlaylistGenerator interface, expand PlaylistViewModel, add PlaylistViewModelTest — FOUND
- `9e78d4f` — feat(04-02): create PlaylistScreen composable and wire into MainActivity — FOUND

Tests: `./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL (all suites green, no regressions)
Compile: `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL
