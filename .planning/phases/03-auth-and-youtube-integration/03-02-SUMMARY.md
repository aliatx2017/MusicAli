---
phase: 03-auth-and-youtube-integration
plan: 02
subsystem: auth
tags: [auth, oauth, appauth, encrypted-shared-preferences, okhttp, interceptor, compose]
dependency_graph:
  requires: [03-01]
  provides: [AuthRepository, TokenStore, AuthInterceptor, AuthModule, SignInScreen]
  affects: [NetworkModule, MainActivity, AndroidManifest]
tech_stack:
  added: []
  patterns:
    - AppAuth PKCE code exchange via Custom Tabs (AuthRepositoryImpl)
    - EncryptedSharedPreferences token storage with injectable clock (TokenStore)
    - OkHttp interceptor with Mutex for thread-safe proactive token refresh (AuthInterceptor)
    - Hilt abstract module with @Binds + companion @Provides (AuthModule)
    - Eager sign-in gate in MainActivity using mutableStateOf + lifecycleScope
key_files:
  created:
    - app/src/main/java/com/musicali/app/auth/TokenStore.kt
    - app/src/main/java/com/musicali/app/auth/AuthRepository.kt
    - app/src/main/java/com/musicali/app/auth/AuthRepositoryImpl.kt
    - app/src/main/java/com/musicali/app/auth/AuthInterceptor.kt
    - app/src/main/java/com/musicali/app/di/AuthModule.kt
    - app/src/main/java/com/musicali/app/ui/auth/SignInScreen.kt
  modified:
    - app/src/main/java/com/musicali/app/di/NetworkModule.kt
    - app/src/main/java/com/musicali/app/MainActivity.kt
    - app/src/main/AndroidManifest.xml
decisions:
  - TokenStore uses internal constructor with injectable clock lambda — enables unit testing in 03-04 without Robolectric or Android Keystore
  - AuthRepositoryImpl.refreshAccessToken() creates a temporary AuthorizationService and calls dispose() to prevent ServiceConnection leak (Pitfall 3)
  - No bare OkHttpClient() in token refresh path — AppAuth's performTokenRequest() used throughout (per RESEARCH.md §Don't Hand-Roll)
  - AuthInterceptor Mutex gates concurrent refresh races across the 10-concurrent search coroutines (Pitfall 4)
metrics:
  duration: ~8 minutes
  completed_date: "2026-03-26"
  tasks_completed: 3
  tasks_total: 3
  files_created: 6
  files_modified: 3
---

# Phase 3 Plan 2: Auth Layer — Token Store, PKCE Implementation, OkHttp Interceptor, Sign-In Screen

**One-liner:** Complete auth layer: EncryptedSharedPreferences TokenStore with injectable clock, AppAuth PKCE via performTokenRequest(), Mutex-gated OkHttp interceptor for proactive refresh, Hilt bindings, and eager sign-in Compose screen.

## Objective

Implement all auth infrastructure required by AUTH-01, AUTH-02, and AUTH-03: token persistence, OAuth code exchange, proactive refresh, and the sign-in gate. Phase 4 injects AuthRepository into the generation pipeline without needing to handle unauthenticated state.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | TokenStore + AuthRepository interface | 469aee1 | TokenStore.kt, AuthRepository.kt |
| 2 | AuthRepositoryImpl + AuthInterceptor + AuthModule | 026e558 | AuthRepositoryImpl.kt, AuthInterceptor.kt, AuthModule.kt, NetworkModule.kt |
| 3 | SignInScreen + MainActivity auth gate + AndroidManifest redirect | 0bf8171 | SignInScreen.kt, MainActivity.kt, AndroidManifest.xml |

## Decisions Made

1. **TokenStore internal constructor with injectable clock:** The `internal constructor` accepts `SharedPreferences` and a `() -> Long` clock lambda. The production path uses `TokenStore.create(context)` which calls `EncryptedSharedPreferences.create()`. This pattern enables 03-04 unit tests to verify `isTokenExpired()` logic without Robolectric or the Android Keystore. Chosen over persisting AuthState (AppAuth's built-in state object) because direct ESP storage gives explicit control over token values and avoids serialization complexity.

2. **Temporary AuthorizationService in refreshAccessToken():** Instead of a singleton `AuthorizationService`, `refreshAccessToken()` creates a temporary instance and calls `dispose()` in a `finally` block. This prevents the ServiceConnection leak documented in Pitfall 3. The alternative (injecting a singleton AuthorizationService) would require tying its lifecycle to the Application, adding complexity.

3. **No bare OkHttpClient() in token refresh:** The `RESEARCH.md §Don't Hand-Roll` table explicitly prohibits hand-rolling the token refresh HTTP call. AppAuth's `performTokenRequest()` is used for both the initial code exchange and refresh grant. This ensures correct RFC 6749 request format, proper error handling, and timeout behavior.

4. **Mutex in AuthInterceptor:** The `Mutex` in `AuthInterceptor` ensures that when multiple coroutines (up to 10 concurrent via the Semaphore in the scraping layer) all find an expired token simultaneously, only one coroutine performs the refresh. Others suspend and use the freshly-stored token when the lock releases. This prevents the concurrent refresh race from Pitfall 4, which could invalidate Google's refresh token.

5. **mutableStateOf for isSignedIn in MainActivity:** `var isSignedIn by mutableStateOf(false)` is used rather than a ViewModel StateFlow because the auth state is a one-time check at launch that doesn't require the full ViewModel lifecycle. Phase 4 will likely move this to the ViewModel when the Generate UI is wired.

## Verification Results

- `./gradlew :app:assembleDebug` exits 0 after all three tasks
- `grep -r "AuthRepository" AuthModule.kt` finds `@Binds abstract fun bindAuthRepository`
- `grep "addInterceptor" NetworkModule.kt` finds `addInterceptor(authInterceptor)`
- `grep "RedirectUriReceiverActivity" AndroidManifest.xml` finds the activity entry
- `grep "= OkHttpClient()" AuthRepositoryImpl.kt` returns nothing — no bare client

**Manual test required (D-16 — cannot be automated):**
1. Install app on device/emulator with Google Play Services (API 28+)
2. Tap "Sign in with Google" — verify Custom Tab opens (not WebView)
3. Select Google account and grant YouTube permissions
4. App should show "MusicAli — Ready to generate"
5. Kill app and reopen — verify sign-in screen does NOT appear (tokens persisted)

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

**MainActivity.kt — placeholder main content:**
- File: `app/src/main/java/com/musicali/app/MainActivity.kt`
- Line: ~68 — `Text("MusicAli — Ready to generate")`
- Reason: Phase 3 scope ends here per D-14. The full Generate UI (button, progress, playlist result) is wired in Phase 4. The placeholder confirms the auth gate works but does not deliver any playlist generation functionality.
- Resolving plan: Phase 4 (03-03 or equivalent)

This stub is intentional and documented in the plan objective. The plan's goal (working auth gate, injectable AuthRepository, wired AuthInterceptor) is fully achieved.

## Self-Check: PASSED

Files verified:
- FOUND: app/src/main/java/com/musicali/app/auth/TokenStore.kt
- FOUND: app/src/main/java/com/musicali/app/auth/AuthRepository.kt
- FOUND: app/src/main/java/com/musicali/app/auth/AuthRepositoryImpl.kt
- FOUND: app/src/main/java/com/musicali/app/auth/AuthInterceptor.kt
- FOUND: app/src/main/java/com/musicali/app/di/AuthModule.kt
- FOUND: app/src/main/java/com/musicali/app/ui/auth/SignInScreen.kt

Commits verified:
- 469aee1: feat(03-02): add TokenStore and AuthRepository interface
- 026e558: feat(03-02): add AuthRepositoryImpl, AuthInterceptor, AuthModule; wire interceptor into OkHttpClient
- 0bf8171: feat(03-02): add SignInScreen, auth gate in MainActivity, AppAuth redirect in AndroidManifest
