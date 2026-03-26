package com.musicali.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.musicali.app.auth.AuthRepository
import com.musicali.app.auth.AuthRepositoryImpl
import com.musicali.app.feature.playlist.PlaylistViewModel
import com.musicali.app.ui.auth.SignInScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var authRepositoryImpl: AuthRepositoryImpl

    private var isSignedIn by mutableStateOf(false)
    private lateinit var authService: AuthorizationService

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@registerForActivityResult
        val authResponse = AuthorizationResponse.fromIntent(data)
        val authException = AuthorizationException.fromIntent(data)
        if (authResponse != null) {
            lifecycleScope.launch {
                try {
                    authRepositoryImpl.exchangeCodeForTokens(
                        authService,
                        authResponse.createTokenExchangeRequest()
                    )
                    isSignedIn = true
                } catch (e: Exception) {
                    // Token exchange failed — remain on sign-in screen
                }
            }
        }
        // If authException != null, remain on sign-in screen (user cancelled or error)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authService = AuthorizationService(this)

        // Check sign-in state on launch (per D-04: eager gate)
        lifecycleScope.launch {
            isSignedIn = authRepository.isSignedIn()
        }

        setContent {
            val viewModel: PlaylistViewModel = hiltViewModel()
            MaterialTheme {
                if (isSignedIn) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Phase 4 wires the full Generate UI here
                            Text("MusicAli — Ready to generate")
                        }
                    }
                } else {
                    SignInScreen(
                        onSignInClick = {
                            val authRequest = authRepositoryImpl.buildAuthRequest()
                            val authIntent = authService.getAuthorizationRequestIntent(authRequest)
                            authLauncher.launch(authIntent)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        authService.dispose()  // per Pitfall 3 — prevent ServiceConnection leak
    }
}
