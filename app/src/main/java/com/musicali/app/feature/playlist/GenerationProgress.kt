package com.musicali.app.feature.playlist

sealed class GenerationProgress {
    data class StageChanged(val stage: Stage) : GenerationProgress()
    data class SearchProgress(val completed: Int, val total: Int) : GenerationProgress()
    data class Success(val songsAdded: Int, val artistsSkipped: Int) : GenerationProgress()
    data class Failed(val error: GenerationError) : GenerationProgress()
}

enum class Stage(val displayName: String) {
    SCRAPING("Scraping genres"),
    SELECTING("Selecting artists"),
    SEARCHING("Searching YouTube"),
    BUILDING("Building playlist")
}

sealed class GenerationError {
    data object ScrapeFailed : GenerationError()
    data object AuthExpired : GenerationError()
    data object QuotaExceeded : GenerationError()
    data object NetworkError : GenerationError()
}
