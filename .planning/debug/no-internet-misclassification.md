---
status: awaiting_human_verify
trigger: "First generation attempt showed 'No internet connection' after scraping/searching succeeded, failing in the BUILDING stage. Second attempt showed 'YouTube daily quota reached'."
created: 2026-03-26T00:00:00Z
updated: 2026-03-26T00:01:00Z
---

## Current Focus

hypothesis: CONFIRMED — AuthRepositoryImpl.refreshAccessToken() creates AuthorizationService on OkHttp background thread, causing RuntimeException that propagates as IOException
test: Read AuthInterceptor.kt, AuthRepositoryImpl.kt, chain of exception propagation
expecting: Fix: perform token refresh on main thread using Dispatchers.Main, or restructure refresh to not use AuthorizationService on background thread
next_action: Fix AuthRepositoryImpl.refreshAccessToken() to post the AuthorizationService creation and callback to the main thread via Dispatchers.Main

## Symptoms

expected: Generation completes successfully or shows a specific error (auth expired, quota exceeded) when playlist building fails
actual: First run shows "No internet connection" after scraping and YouTube searching completed fine. On retry, "YouTube daily quota reached" appears — confirming the first run already burned 6,500 quota units on searches before failing at BUILDING stage.
errors: "No internet connection" (mapped from GenerationError.NetworkError), then "YouTube daily quota reached" (mapped from GenerationError.QuotaExceeded / HTTP 403) on retry
reproduction: Run the app -> tap Generate Playlist -> wait for pipeline to complete scraping/searching -> observe "No internet connection" at BUILDING stage -> tap Retry -> observe "YouTube daily quota reached"
started: Phase 4 checkpoint UAT. First time the full pipeline ran end-to-end on a real device.

## Eliminated

- hypothesis: H3 — YouTubeRepositoryImpl wraps HttpException in catch block converting it to IOException
  evidence: deletePlaylist uses runCatching (swallows errors silently), but createPlaylist and addTrack do NOT wrap exceptions — HttpException from those propagates directly to GeneratePlaylistUseCase's catch(e: HttpException) block correctly. This is not the cause of NetworkError.
  timestamp: 2026-03-26T00:01:00Z

- hypothesis: H1 (partial) — OAuth access token expired and 401 was mis-mapped
  evidence: The token DID expire (pipeline takes significant time), but the mis-mapping is NOT from a 401 HTTP response — it's from the token refresh itself crashing on the wrong thread before any HTTP call is made.
  timestamp: 2026-03-26T00:01:00Z

## Evidence

- timestamp: 2026-03-26T00:01:00Z
  checked: AuthInterceptor.kt intercept() method
  found: Uses runBlocking { mutex.withLock { authRepository.getValidToken() } } on OkHttp background thread. When token is expired, calls refreshAccessToken().
  implication: refreshAccessToken() runs on OkHttp's thread pool thread (no Looper), not the main thread.

- timestamp: 2026-03-26T00:01:00Z
  checked: AuthRepositoryImpl.kt refreshAccessToken()
  found: Creates AuthorizationService(context) and calls tempService.performTokenRequest(...) on a background thread. AppAuth's performTokenRequest internally creates a Handler to deliver the callback — Handler() creation throws RuntimeException("Can't create handler inside thread ... that has not called Looper.prepare()") on a background thread.
  implication: The RuntimeException propagates out of runBlocking in AuthInterceptor.intercept(). OkHttp wraps non-IOException throwables from interceptors as IOException. Retrofit propagates the IOException to GeneratePlaylistUseCase's catch(e: IOException) block, which maps it to GenerationError.NetworkError — showing "No internet connection" instead of the real cause.

- timestamp: 2026-03-26T00:01:00Z
  checked: GeneratePlaylistUseCase.kt BUILDING stage catch blocks (lines 121-125)
  found: catch(e: HttpException) correctly maps to QuotaExceeded/AuthExpired; catch(e: IOException) maps to NetworkError. Any non-HTTP exception that leaks as IOException goes to NetworkError.
  implication: The token refresh crash produces a false "No internet connection" error. Second run shows QuotaExceeded because quota was burned in first run and auth worked on second run (token was already refreshed).

- timestamp: 2026-03-26T00:01:00Z
  checked: YouTubeRepositoryImpl.deletePlaylist() lines 51-56
  found: Uses runCatching that swallows ALL exceptions silently, including quota 403s.
  implication: Secondary bug — a 403 from deletePlaylist is hidden. This doesn't affect the primary bug but means quota errors during delete are invisible.

## Resolution

root_cause: AuthRepositoryImpl.refreshAccessToken() creates AuthorizationService and calls performTokenRequest() on an OkHttp background thread. AppAuth's Handler requires a Looper — creating it on a non-Looper thread throws RuntimeException. This propagates as IOException (OkHttp wraps non-IOExceptions from interceptors) and gets caught as GenerationError.NetworkError, showing "No internet connection" instead of the real auth failure message.
fix: (1) Switch refreshAccessToken() to use withContext(Dispatchers.Main) for the AuthorizationService creation and performTokenRequest call — ensures AppAuth's internal Handler is created on the main Looper, not an OkHttp background thread. (2) Fixed deletePlaylist() to re-throw non-404 HttpExceptions (quota 403, auth 401) instead of silently swallowing all errors via runCatching.
verification: Build passes (compileDebugKotlin), all 37 unit tests pass (testDebugUnitTest). Device verification needed.
files_changed: [app/src/main/java/com/musicali/app/auth/AuthRepositoryImpl.kt, app/src/main/java/com/musicali/app/data/remote/youtube/YouTubeRepositoryImpl.kt]
