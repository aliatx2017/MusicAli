package com.musicali.app.feature.playlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Main playlist generation screen. Renders four UI states driven by PlaylistViewModel:
 *   - Idle: Generate Playlist button
 *   - Generating: 4-stage chip row + weighted progress bar + search counter (SEARCHING only)
 *   - Success: summary (songs added / artists skipped) + Generate Again button
 *   - Error: inline error card with typed action button (D-06)
 *
 * AnimatedContent cross-fades between state sections per UI-SPEC §8.
 */
@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel = hiltViewModel(),
    onSignInRequired: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "MusicAli",
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(modifier = Modifier.height(48.dp))

            AnimatedContent(
                targetState = uiState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "playlist_state"
            ) { state ->
                when (state) {
                    is PlaylistUiState.Idle -> {
                        IdleContent(onGenerate = { viewModel.generate() })
                    }
                    is PlaylistUiState.Generating -> {
                        GeneratingContent(state = state)
                    }
                    is PlaylistUiState.Success -> {
                        SuccessContent(
                            state = state,
                            onGenerateAgain = { viewModel.generate() }
                        )
                    }
                    is PlaylistUiState.Error -> {
                        ErrorContent(
                            state = state,
                            onRetry = { viewModel.generate() },
                            onSignInRequired = onSignInRequired,
                            onDismiss = { viewModel.dismissError() }
                        )
                    }
                }
            }
        }
    }
}

// --- Idle State ---

@Composable
private fun IdleContent(onGenerate: () -> Unit) {
    Button(onClick = onGenerate) {
        Text("Generate Playlist")
    }
}

// --- Generating State ---

@Composable
private fun GeneratingContent(state: PlaylistUiState.Generating) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StageChipsRow(currentStage = state.stage)
        Spacer(modifier = Modifier.height(24.dp))
        LinearProgressIndicator(
            progress = { state.progressFraction },
            modifier = Modifier.fillMaxWidth()
        )
        if (state.stage == Stage.SEARCHING) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Searching YouTube \u2014 ${state.searchedCount}/${state.totalArtists} artists...",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Row of four stage chips — each shows icon + label reflecting done/active/pending state.
 * Per UI-SPEC §6.3:
 *   - Done: CheckCircle (primary tint), full opacity label
 *   - Active: CircularProgressIndicator (16dp), full opacity label
 *   - Pending: RadioButtonUnchecked (onSurfaceVariant tint), 60% opacity label
 */
@Composable
private fun StageChipsRow(currentStage: Stage) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stage.entries.forEach { stage ->
            StageChip(stage = stage, currentStage = currentStage)
        }
    }
}

@Composable
private fun StageChip(stage: Stage, currentStage: Stage) {
    val isDone = stage.ordinal < currentStage.ordinal
    val isActive = stage == currentStage
    val labelAlpha = if (isDone || isActive) 1.0f else 0.6f

    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            isDone -> Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "${stage.displayName} \u2014 complete",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            isActive -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            else -> Icon(
                imageVector = Icons.Default.RadioButtonUnchecked,
                contentDescription = "${stage.displayName} \u2014 pending",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stage.displayName,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.alpha(labelAlpha)
        )
    }
}

// --- Success State ---

@Composable
private fun SuccessContent(
    state: PlaylistUiState.Success,
    onGenerateAgain: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Success",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Playlist built!",
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "${state.songsAdded} songs added \u2022 ${state.artistsSkipped} artists skipped",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onGenerateAgain) {
            Text("Generate Again")
        }
    }
}

// --- Error State ---

@Composable
private fun ErrorContent(
    state: PlaylistUiState.Error,
    onRetry: () -> Unit,
    onSignInRequired: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            when (state.action) {
                is ErrorAction.Retry -> Button(onClick = onRetry) {
                    Text("Retry")
                }
                is ErrorAction.SignIn -> Button(onClick = onSignInRequired) {
                    Text("Sign in again")
                }
                is ErrorAction.DismissOnly -> TextButton(onClick = onDismiss) {
                    Text("Try again tomorrow")
                }
            }
        }
    }
}
