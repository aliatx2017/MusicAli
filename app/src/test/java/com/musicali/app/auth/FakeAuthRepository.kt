package com.musicali.app.auth

import java.util.concurrent.atomic.AtomicInteger

/**
 * Hand-written fake implementing AuthRepository.
 * Used by AuthInterceptorTest to verify interceptor behavior in isolation.
 * Mirrors FakeArtistDao / FakeScrapingRepository project patterns.
 * Uses AtomicInteger for getValidTokenCallCount to be safe for concurrent test assertions.
 */
class FakeAuthRepository : AuthRepository {
    var token: String = "fake-access-token"
    var isSignedInValue: Boolean = true
    private val _getValidTokenCallCount = AtomicInteger(0)
    val getValidTokenCallCount: Int get() = _getValidTokenCallCount.get()

    fun resetCallCount() { _getValidTokenCallCount.set(0) }

    override suspend fun isSignedIn(): Boolean = isSignedInValue

    override suspend fun getValidToken(): String {
        _getValidTokenCallCount.incrementAndGet()
        return token
    }

    override suspend fun saveTokens(accessToken: String, refreshToken: String, expiryMs: Long) {
        token = accessToken
    }

    override suspend fun signOut() {
        isSignedInValue = false
        token = ""
    }
}
