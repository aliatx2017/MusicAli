package com.musicali.app.domain.usecase

import com.musicali.app.auth.TokenStore
import com.musicali.app.data.remote.youtube.YouTubeRepository
import com.musicali.app.domain.repository.ArtistHistoryRepository
import com.musicali.app.feature.playlist.GenerationError
import com.musicali.app.feature.playlist.GenerationProgress
import com.musicali.app.feature.playlist.Stage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Orchestrates the full playlist generation pipeline, emitting progress events via channelFlow.
 *
 * Pipeline stages:
 *   SCRAPING → SELECTING: ArtistSelectionUseCase fetches+selects artists
 *   SEARCHING: 65 YouTube searches run concurrently (Semaphore(10) per CLAUDE.md)
 *   BUILDING: delete old playlist → create new → add tracks → write artist history (D-11)
 *
 * D-11: Artist history is written ONLY after the playlist is fully built. A failure at any
 * stage before markArtistsSeen() leaves history unchanged, allowing a clean retry.
 */
class GeneratePlaylistUseCase @Inject constructor(
    private val artistSelectionUseCase: ArtistSelectionUseCase,
    private val youTubeRepository: YouTubeRepository,
    private val artistHistoryRepository: ArtistHistoryRepository,
    private val tokenStore: TokenStore
) {
    fun execute(): Flow<GenerationProgress> = channelFlow {
        // Stage 1: Scraping (ArtistSelectionUseCase handles scraping internally)
        send(GenerationProgress.StageChanged(Stage.SCRAPING))

        // Stage 2: Selecting artists
        val artists: List<String>
        try {
            send(GenerationProgress.StageChanged(Stage.SELECTING))
            artists = artistSelectionUseCase.selectArtists()
        } catch (e: IOException) {
            send(GenerationProgress.Failed(GenerationError.ScrapeFailed))
            return@channelFlow
        }

        if (artists.isEmpty()) {
            send(GenerationProgress.Failed(GenerationError.ScrapeFailed))
            return@channelFlow
        }

        // Stage 3: Search YouTube for each artist concurrently
        // Semaphore(10) caps concurrent API calls per CLAUDE.md constraint
        send(GenerationProgress.StageChanged(Stage.SEARCHING))
        val semaphore = Semaphore(10)
        val completedCount = AtomicInteger(0)  // AtomicInteger for thread-safe counter (Pitfall 2)
        val totalArtists = artists.size

        val searchResults: List<Pair<String, String?>>
        try {
            searchResults = artists.map { artist ->
                async {
                    semaphore.withPermit {
                        // searchTopSong() uses runCatching internally and returns null on errors —
                        // auth/quota exceptions from search manifest as null results, not exceptions.
                        // HTTP exceptions from createPlaylist/addTrack ARE surfaced.
                        val videoId = youTubeRepository.searchTopSong(artist)
                        val count = completedCount.incrementAndGet()
                        send(GenerationProgress.SearchProgress(count, totalArtists))
                        artist to videoId
                    }
                }
            }.map { it.await() }
        } catch (e: HttpException) {
            send(GenerationProgress.Failed(httpErrorToGenerationError(e)))
            return@channelFlow
        } catch (e: IOException) {
            send(GenerationProgress.Failed(GenerationError.NetworkError))
            return@channelFlow
        }

        // Stage 4: Build playlist
        send(GenerationProgress.StageChanged(Stage.BUILDING))
        try {
            // Delete existing playlist if one was previously created (delete+recreate strategy per D-09)
            val existingPlaylistId = tokenStore.getPlaylistId()
            if (existingPlaylistId != null) {
                youTubeRepository.deletePlaylist(existingPlaylistId)
            }

            val newPlaylistId = youTubeRepository.createPlaylist("AliMusings")

            val found = searchResults.filter { it.second != null }
            val skipped = searchResults.count { it.second == null }

            // Add tracks sequentially (RESEARCH.md recommendation for addTrack calls)
            for ((_, videoId) in found) {
                youTubeRepository.addTrack(newPlaylistId, videoId!!)
            }

            // D-11: Write artist history ONLY after playlist is fully built.
            // Any failure before this line leaves history unchanged for clean retry.
            val currentRun = artistHistoryRepository.getCurrentRun() + 1
            artistHistoryRepository.incrementRun()
            artistHistoryRepository.markArtistsSeen(artists, currentRun)

            send(GenerationProgress.Success(songsAdded = found.size, artistsSkipped = skipped))
        } catch (e: HttpException) {
            send(GenerationProgress.Failed(httpErrorToGenerationError(e)))
        } catch (e: IOException) {
            send(GenerationProgress.Failed(GenerationError.NetworkError))
        }
    }.flowOn(Dispatchers.IO)

    private fun httpErrorToGenerationError(e: HttpException): GenerationError = when (e.code()) {
        401 -> GenerationError.AuthExpired
        403 -> GenerationError.QuotaExceeded
        else -> GenerationError.NetworkError
    }
}
