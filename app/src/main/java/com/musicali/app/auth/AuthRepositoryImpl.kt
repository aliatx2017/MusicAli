package com.musicali.app.auth

import android.content.Context
import android.net.Uri
import android.util.Log
import com.musicali.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    val tokenStore: TokenStore   // internal for tests; not exposed via interface
) : AuthRepository {

    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
        Uri.parse("https://oauth2.googleapis.com/token")
    )

    /** Build an AppAuth authorization request for the caller to launch via Custom Tabs. */
    fun buildAuthRequest(): AuthorizationRequest =
        AuthorizationRequest.Builder(
            serviceConfig,
            BuildConfig.GOOGLE_CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse("com.googleusercontent.apps.${BuildConfig.GOOGLE_CLIENT_ID.removeSuffix(".apps.googleusercontent.com")}:/oauth2redirect")
        ).setScope("https://www.googleapis.com/auth/youtube").build()

    /**
     * Exchange the authorization code for tokens using AppAuth.
     * Call from the Activity that handles the OAuth redirect result.
     * Uses AppAuth's performTokenRequest() — per RESEARCH.md §Don't Hand-Roll:
     * token request format, error handling are handled correctly by AppAuth.
     */
    suspend fun exchangeCodeForTokens(
        authService: AuthorizationService,
        tokenRequest: TokenRequest
    ) = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            authService.performTokenRequest(tokenRequest) { tokenResponse, exception ->
                when {
                    tokenResponse != null -> {
                        val accessToken = tokenResponse.accessToken ?: ""
                        val refreshToken = tokenResponse.refreshToken ?: ""
                        val expiryMs = tokenResponse.accessTokenExpirationTime ?: 0L
                        tokenStore.saveTokens(accessToken, refreshToken, expiryMs)
                        cont.resume(Unit)
                    }
                    exception != null -> cont.resumeWithException(exception)
                    else -> cont.resumeWithException(IllegalStateException("Token exchange failed with no error"))
                }
            }
        }
    }

    override suspend fun isSignedIn(): Boolean {
        val hasRefreshToken = tokenStore.getRefreshToken() != null
        val hasAccessToken = tokenStore.getAccessToken() != null
        return hasRefreshToken && hasAccessToken
    }

    override suspend fun getValidToken(): String = withContext(Dispatchers.IO) {
        if (tokenStore.isTokenExpired(graceWindowMs = 60_000L)) {
            refreshAccessToken()
        } else {
            tokenStore.getAccessToken() ?: throw IllegalStateException("No access token — user must sign in")
        }
    }

    /**
     * Refresh the access token using AppAuth's performTokenRequest().
     * Per RESEARCH.md §Don't Hand-Roll: do NOT use a bare OkHttpClient() for token refresh —
     * AppAuth handles the token request format and error responses correctly. A bare OkHttpClient
     * has no timeouts and can hang indefinitely.
     *
     * Note: AppAuth's performTokenRequest also works for refresh grants when given a TokenRequest
     * built from the refresh token. We build this manually since AuthState is not persisted here
     * (we store tokens in ESP directly).
     */
    private suspend fun refreshAccessToken(): String {
        val refreshToken = tokenStore.getRefreshToken()
            ?: run {
                Log.e("AuthRepo", "refreshAccessToken: NO REFRESH TOKEN stored — user must sign in")
                throw IllegalStateException("No refresh token — user must sign in")
            }

        // Build a refresh token grant request using AppAuth
        val tokenRequest = TokenRequest.Builder(
            serviceConfig,
            BuildConfig.GOOGLE_CLIENT_ID
        )
            .setGrantType(GrantTypeValues.REFRESH_TOKEN)
            .setRefreshToken(refreshToken)
            .build()

        Log.d("AuthRepo", "refreshAccessToken: switching to Main thread")
        // MUST run on main thread: AuthorizationService.performTokenRequest() creates a Handler
        // internally to deliver its callback. Handler() requires a Looper — creating it on an
        // OkHttp background thread (which has no Looper) throws RuntimeException, which OkHttp
        // wraps as IOException, producing a false "No internet connection" error in the UI.
        return withContext(Dispatchers.Main) {
            Log.d("AuthRepo", "refreshAccessToken: on thread=${Thread.currentThread().name}")
            val tempService = AuthorizationService(context)
            try {
                suspendCancellableCoroutine { cont ->
                    tempService.performTokenRequest(tokenRequest) { tokenResponse, exception ->
                        Log.d("AuthRepo", "performTokenRequest callback: hasResponse=${tokenResponse != null} exception=${exception?.message}")
                        when {
                            tokenResponse != null -> {
                                val newAccessToken = tokenResponse.accessToken
                                    ?: run { cont.resumeWithException(IllegalStateException("Token response missing access_token")); return@performTokenRequest }
                                val newRefreshToken = tokenResponse.refreshToken ?: refreshToken
                                val newExpiryMs = tokenResponse.accessTokenExpirationTime
                                    ?: (System.currentTimeMillis() + 3600_000L)
                                tokenStore.saveTokens(newAccessToken, newRefreshToken, newExpiryMs)
                                cont.resume(newAccessToken)
                            }
                            exception != null -> cont.resumeWithException(exception)
                            else -> cont.resumeWithException(IllegalStateException("Token refresh failed with no error"))
                        }
                    }
                }
            } finally {
                tempService.dispose()  // per Pitfall 3 — prevent ServiceConnection leak
            }
        }
    }

    override suspend fun saveTokens(accessToken: String, refreshToken: String, expiryMs: Long) {
        tokenStore.saveTokens(accessToken, refreshToken, expiryMs)
    }

    override suspend fun signOut() {
        tokenStore.clearAll()
    }
}
