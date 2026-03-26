package com.musicali.app.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicali.app.domain.usecase.PlaylistGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistGenerator: PlaylistGenerator
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlaylistUiState>(PlaylistUiState.Idle)
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    fun generate() {
        if (_uiState.value is PlaylistUiState.Generating) return  // D-04: no concurrent runs
        viewModelScope.launch {
            playlistGenerator.execute().collect { progress ->
                _uiState.value = when (progress) {
                    is GenerationProgress.StageChanged -> {
                        val current = _uiState.value
                        val searched = if (current is PlaylistUiState.Generating) current.searchedCount else 0
                        val total = (current as? PlaylistUiState.Generating)?.totalArtists ?: 65
                        PlaylistUiState.Generating(
                            stage = progress.stage,
                            searchedCount = searched,
                            totalArtists = total,
                            progressFraction = progressFractionForStage(progress.stage, searched, total)
                        )
                    }
                    is GenerationProgress.SearchProgress -> PlaylistUiState.Generating(
                        stage = Stage.SEARCHING,
                        searchedCount = progress.completed,
                        totalArtists = progress.total,
                        progressFraction = 0.10f + (progress.completed.toFloat() / progress.total.coerceAtLeast(1)) * 0.80f
                    )
                    is GenerationProgress.Success -> PlaylistUiState.Success(
                        songsAdded = progress.songsAdded,
                        artistsSkipped = progress.artistsSkipped
                    )
                    is GenerationProgress.Failed -> PlaylistUiState.Error(
                        message = progress.error.toMessage(),
                        action = progress.error.toAction()
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.value = PlaylistUiState.Idle
    }
}

// D-03 progress bar weights: Scraping 5%, Selecting 5%, Searching 80%, Building 10%
private fun progressFractionForStage(stage: Stage, searchedCount: Int = 0, totalArtists: Int = 65): Float {
    return when (stage) {
        Stage.SCRAPING -> 0.00f
        Stage.SELECTING -> 0.05f
        Stage.SEARCHING -> 0.10f + (searchedCount.toFloat() / totalArtists.coerceAtLeast(1)) * 0.80f
        Stage.BUILDING -> 0.90f
    }
}

// D-06 error message mapping (exact strings from UI-SPEC section 9.6)
private fun GenerationError.toMessage(): String = when (this) {
    GenerationError.ScrapeFailed -> "Could not load genre lists"
    GenerationError.AuthExpired -> "Sign-in required"
    GenerationError.QuotaExceeded -> "YouTube daily quota reached"
    GenerationError.NetworkError -> "No internet connection"
}

// D-06 error action mapping
private fun GenerationError.toAction(): ErrorAction = when (this) {
    GenerationError.ScrapeFailed -> ErrorAction.Retry
    GenerationError.AuthExpired -> ErrorAction.SignIn
    GenerationError.QuotaExceeded -> ErrorAction.DismissOnly
    GenerationError.NetworkError -> ErrorAction.Retry
}
