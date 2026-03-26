package com.musicali.app.feature.playlist

sealed class PlaylistUiState {
    data object Idle : PlaylistUiState()
    data class Generating(
        val stage: Stage,
        val searchedCount: Int,
        val totalArtists: Int,
        val progressFraction: Float
    ) : PlaylistUiState()
    data class Success(
        val songsAdded: Int,
        val artistsSkipped: Int
    ) : PlaylistUiState()
    data class Error(
        val message: String,
        val action: ErrorAction
    ) : PlaylistUiState()
}

sealed class ErrorAction {
    data object Retry : ErrorAction()
    data object SignIn : ErrorAction()
    data object DismissOnly : ErrorAction()
}
