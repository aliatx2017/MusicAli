package com.musicali.app.auth

interface AuthRepository {
    /** Returns true if a valid (or refreshable) session exists in the token store. */
    suspend fun isSignedIn(): Boolean

    /**
     * Returns a valid access token, refreshing proactively if within 60s of expiry.
     * Suspends until refresh completes. Thread-safe via Mutex in AuthInterceptor.
     */
    suspend fun getValidToken(): String

    /**
     * Persists tokens received after a successful AppAuth code exchange.
     * Called from the Activity handling the OAuth redirect.
     */
    suspend fun saveTokens(accessToken: String, refreshToken: String, expiryMs: Long)

    /** Clears all stored tokens and playlist ID. Requires re-sign-in. */
    suspend fun signOut()
}
