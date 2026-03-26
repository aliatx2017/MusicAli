package com.musicali.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.musicali.app.auth.AuthRepository
import com.musicali.app.auth.AuthRepositoryImpl
import com.musicali.app.feature.playlist.PlaylistScreen
import com.musicali.app.ui.auth.SignInScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var authRepositoryImpl: AuthRepositoryImpl

    private var isSignedIn by mutableStateOf(false)
    private var authError by mutableStateOf<String?>(null)
    private lateinit var authService: AuthorizationService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authService = AuthorizationService(this)

        // Handle redirect if process was killed and restarted via the redirect URI
        handleRedirectIntent(intent)

        lifecycleScope.launch {
            isSignedIn = authRepository.isSignedIn()
        }

        setContent {
            MaterialTheme {
                if (isSignedIn) {
                    PlaylistScreen(
                        onSignInRequired = {
                            isSignedIn = false
                        }
                    )
                } else {
                    Column {
                        authError?.let { Text("AUTH ERROR: $it", color = androidx.compose.ui.graphics.Color.Red) }
                    }
                    SignInScreen(
                        onSignInClick = { launchSignIn() }
                    )
                }
            }
        }
    }

    // Handles redirect when singleTop MainActivity is brought to front by the redirect URI intent
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRedirectIntent(intent)
    }

    private fun launchSignIn() {
        val authRequest = authRepositoryImpl.buildAuthRequest()
        // Persist the request JSON so we can reconstruct AuthorizationResponse after process death
        getSharedPreferences("auth_pending", Context.MODE_PRIVATE)
            .edit()
            .putString("request_json", authRequest.jsonSerializeString())
            .apply()
        Log.d("MusicAli", "Launching auth, saved request JSON")
        val authIntent = authService.getAuthorizationRequestIntent(authRequest)
        startActivity(authIntent)
    }

    /**
     * Called from both onCreate (process restarted by redirect) and onNewIntent (singleTop).
     * The intent data is the full redirect URI — parse code directly from it.
     */
    private fun handleRedirectIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val scheme = uri.scheme ?: return
        if (!scheme.startsWith("com.googleusercontent.apps")) return

        Log.d("MusicAli", "Got redirect URI: $uri")

        val error = uri.getQueryParameter("error")
        if (error != null) {
            authError = "Auth error: $error"
            Log.e("MusicAli", "Auth error in redirect: $error")
            return
        }

        val code = uri.getQueryParameter("code")
        if (code == null) {
            authError = "Auth error: no code in redirect"
            Log.e("MusicAli", "No code in redirect URI")
            return
        }

        val requestJson = getSharedPreferences("auth_pending", Context.MODE_PRIVATE)
            .getString("request_json", null)
        if (requestJson == null) {
            authError = "Auth error: no pending request found"
            Log.e("MusicAli", "No pending request in SharedPreferences")
            return
        }

        val savedRequest = AuthorizationRequest.jsonDeserialize(requestJson)
        val authResponse = AuthorizationResponse.Builder(savedRequest)
            .setAuthorizationCode(code)
            .setState(uri.getQueryParameter("state"))
            .build()

        Log.d("MusicAli", "Parsed redirect, exchanging code for tokens")
        lifecycleScope.launch {
            try {
                authRepositoryImpl.exchangeCodeForTokens(
                    authService,
                    authResponse.createTokenExchangeRequest()
                )
                isSignedIn = true
                Log.d("MusicAli", "Token exchange success")
                // Clear the pending request
                getSharedPreferences("auth_pending", Context.MODE_PRIVATE)
                    .edit().remove("request_json").apply()
            } catch (e: Exception) {
                authError = "Token exchange failed: ${e.javaClass.simpleName}: ${e.message}"
                Log.e("MusicAli", "Token exchange failed", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        authService.dispose()
    }
}
