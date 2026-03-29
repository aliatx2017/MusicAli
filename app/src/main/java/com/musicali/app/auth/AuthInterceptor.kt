package com.musicali.app.auth

import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * OkHttp interceptor that adds a valid Bearer token to every YouTube API request.
 * Uses a Mutex to prevent concurrent token refresh races when multiple coroutines
 * fire simultaneously (e.g., 65 parallel search calls — capped by semaphore but
 * the first 10 could all trigger refresh at once without this gate).
 * Per AUTH-03 and D-05.
 */
class AuthInterceptor @Inject constructor(
    private val authRepository: AuthRepository
) : Interceptor {

    private val mutex = Mutex()

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking {
            mutex.withLock {
                try {
                    authRepository.getValidToken()
                } catch (e: Exception) {
                    Log.e("AuthInterceptor", "getValidToken() threw ${e::class.simpleName}: ${e.message}", e)
                    throw AuthFailureException("Auth failed: ${e.message}", e)
                }
            }
        }
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}
