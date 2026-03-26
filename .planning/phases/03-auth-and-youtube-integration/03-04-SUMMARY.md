---
phase: 03-auth-and-youtube-integration
plan: 04
subsystem: auth
tags: [testing, unit-tests, auth, token-store, interceptor, mock-web-server]
dependency_graph:
  requires: [03-02, 03-03]
  provides: [auth-unit-test-coverage]
  affects: [full-test-suite]
tech_stack:
  added: []
  patterns:
    - FakeAuthRepository hand-written fake with AtomicInteger for thread-safe call counting
    - InMemorySharedPreferences pure JVM fake to bypass Keystore/Robolectric requirement
    - Injectable clock lambda on TokenStore internal constructor for deterministic time tests
    - MockWebServer (OkHttp) to capture outbound request headers in interceptor tests
key_files:
  created:
    - app/src/test/java/com/musicali/app/auth/FakeAuthRepository.kt
    - app/src/test/java/com/musicali/app/auth/AuthInterceptorTest.kt
    - app/src/test/java/com/musicali/app/auth/TokenStoreTest.kt
  modified: []
decisions:
  - InMemorySharedPreferences fake avoids Robolectric and Android Keystore in TokenStoreTest
  - FakeAuthRepository placed in its own file (not private class) to be reusable across future auth tests
metrics:
  duration_minutes: 5
  completed_date: "2026-03-26"
  tasks_completed: 2
  files_created: 3
  files_modified: 0
---

# Phase 03 Plan 04: Auth Unit Tests Summary

**One-liner:** Auth layer unit-tested via FakeAuthRepository + MockWebServer interceptor tests and InMemorySharedPreferences token store tests — 15 new tests, full suite green.

---

## What Was Built

Three test files covering the auth layer's pure-function behavior (interceptor header injection, Mutex serialization, token expiry logic, key persistence) without requiring a real device, Android Keystore, or live OAuth credentials.

### FakeAuthRepository (`FakeAuthRepository.kt`)

Shared test double implementing `AuthRepository`. Uses `AtomicInteger` for `getValidTokenCallCount` so concurrent test assertions are thread-safe. Designed as a top-level class (not private) so future test files can reuse it. Mirrors the `FakeArtistDao` / `FakeScrapingRepository` project pattern.

### AuthInterceptorTest (`AuthInterceptorTest.kt`)

5 tests using MockWebServer (OkHttp 4.12.0) to capture the outbound request headers:

1. `interceptor adds Bearer Authorization header to request` — confirms "Bearer {token}" header format
2. `interceptor calls getValidToken exactly once per request` — confirms no extra refresh calls
3. `interceptor works with a long token value` — 205-char token (ya29. + 200 chars) passes through intact
4. `interceptor adds token even when server returns 401` — header added before response code known
5. `Mutex ensures getValidToken called exactly N times for N parallel requests` — 5 concurrent Dispatchers.IO calls all get headers; `getValidTokenCallCount` == 5 (Mutex serializes, doesn't deduplicate)

### TokenStoreTest (`TokenStoreTest.kt`)

10 tests split across expiry logic (5) and key persistence (5):

**Expiry logic:**
- `isTokenExpired returns true when no token stored (expiry is 0)` — default 0L case
- `isTokenExpired returns false when token expires well in future` — 5-min future, 60s grace window
- `isTokenExpired returns true when token expires within grace window` — 30s future, 60s grace window
- `isTokenExpired returns true when token already expired` — 10s past
- `isTokenExpired with graceWindowMs=0 is false when token valid` — boundary: now+1ms, no grace

**Key persistence:**
- `saveTokens persists access token readable via getAccessToken`
- `getAccessToken returns null before any tokens saved`
- `savePlaylistId and getPlaylistId round-trip`
- `getPlaylistId returns null before any playlist saved`
- `clearAll removes all keys`

`InMemorySharedPreferences` (private class within the test file) provides a full `SharedPreferences` + `Editor` implementation backed by a `HashMap`. Avoids Robolectric and the Android Keystore entirely, enabling pure JVM execution.

---

## Deviations from Plan

None — plan executed exactly as written.

The `TokenStore` internal constructor with injectable clock lambda was already in place from 03-02 Task 1. All interfaces, constructor signatures, and test patterns matched the plan's `<interfaces>` block exactly.

---

## Test Results

| Suite | Tests | Passed | Failed |
|-------|-------|--------|--------|
| AuthInterceptorTest | 5 | 5 | 0 |
| TokenStoreTest | 10 | 10 | 0 |
| Full suite (all 8 suites) | — | all | 0 |

`./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL

---

## Known Stubs

None. These are pure test files with no data wired to UI.

---

## Self-Check: PASSED

- `/Users/alex.maksimchuk/claude/projects/MusicAli/app/src/test/java/com/musicali/app/auth/FakeAuthRepository.kt` — exists
- `/Users/alex.maksimchuk/claude/projects/MusicAli/app/src/test/java/com/musicali/app/auth/AuthInterceptorTest.kt` — exists
- `/Users/alex.maksimchuk/claude/projects/MusicAli/app/src/test/java/com/musicali/app/auth/TokenStoreTest.kt` — exists
- Commit `0650592` (Task 1: FakeAuthRepository + AuthInterceptorTest) — exists
- Commit `0247da7` (Task 2: TokenStoreTest) — exists
- Full test suite BUILD SUCCESSFUL confirmed
