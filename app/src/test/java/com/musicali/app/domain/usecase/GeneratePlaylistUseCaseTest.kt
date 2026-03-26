package com.musicali.app.domain.usecase

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.musicali.app.auth.TokenStore
import com.musicali.app.data.remote.Genre
import com.musicali.app.data.remote.youtube.FakeYouTubeRepository
import com.musicali.app.domain.repository.ArtistHistoryRepository
import com.musicali.app.fake.FakeArtistDao
import com.musicali.app.fake.FakeScrapingRepository
import com.musicali.app.fake.InMemorySharedPreferences
import com.musicali.app.feature.playlist.GenerationError
import com.musicali.app.feature.playlist.GenerationProgress
import com.musicali.app.feature.playlist.Stage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.io.IOException

/**
 * Unit tests for GeneratePlaylistUseCase pipeline orchestration.
 * Uses real ArtistSelectionUseCase and ArtistHistoryRepository with fakes at the leaf level.
 * Covers: stage ordering, search progress, success counts, error types, D-11 (atomic history write).
 */
class GeneratePlaylistUseCaseTest {

    private lateinit var fakeScrapingRepo: FakeScrapingRepository
    private lateinit var fakeArtistDao: FakeArtistDao
    private lateinit var fakeYouTubeRepo: FakeYouTubeRepository
    private lateinit var artistHistoryRepo: ArtistHistoryRepository
    private lateinit var artistSelectionUseCase: ArtistSelectionUseCase
    private lateinit var tokenStore: TokenStore
    private lateinit var useCase: GeneratePlaylistUseCase

    @Before
    fun setUp() {
        fakeScrapingRepo = FakeScrapingRepository()
        fakeArtistDao = FakeArtistDao()
        fakeYouTubeRepo = FakeYouTubeRepository()

        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File.createTempFile("test_prefs_${System.nanoTime()}", ".preferences_pb") }
        )
        artistHistoryRepo = ArtistHistoryRepository(fakeArtistDao, dataStore)
        artistSelectionUseCase = ArtistSelectionUseCase(fakeScrapingRepo, artistHistoryRepo)
        tokenStore = TokenStore(prefs = InMemorySharedPreferences())
        useCase = GeneratePlaylistUseCase(
            artistSelectionUseCase = artistSelectionUseCase,
            youTubeRepository = fakeYouTubeRepo,
            artistHistoryRepository = artistHistoryRepo,
            tokenStore = tokenStore
        )
    }

    private fun setupArtistsWithResults(vararg artists: Pair<String, String?>) {
        val artistNames = artists.map { it.first }
        fakeScrapingRepo.artistsByGenre = mapOf(
            Genre.INDIETRONICA to artistNames,
            Genre.NU_DISCO to emptyList(),
            Genre.INDIE_SOUL to emptyList()
        )
        fakeYouTubeRepo.searchResults = artists.toMap()
        fakeArtistDao.seenNames = emptyList()
    }

    @Test
    fun execute_emitsStagesInOrder() = runTest {
        setupArtistsWithResults(
            "Artist A" to "vid1",
            "Artist B" to "vid2",
            "Artist C" to "vid3"
        )

        val events = useCase.execute().toList()

        val stageChanges = events.filterIsInstance<GenerationProgress.StageChanged>().map { it.stage }
        val scrapingIdx = stageChanges.indexOf(Stage.SCRAPING)
        val selectingIdx = stageChanges.indexOf(Stage.SELECTING)
        val searchingIdx = stageChanges.indexOf(Stage.SEARCHING)
        val buildingIdx = stageChanges.indexOf(Stage.BUILDING)

        assertTrue("SCRAPING should appear", scrapingIdx >= 0)
        assertTrue("SELECTING should appear", selectingIdx >= 0)
        assertTrue("SEARCHING should appear", searchingIdx >= 0)
        assertTrue("BUILDING should appear", buildingIdx >= 0)
        assertTrue("SCRAPING < SELECTING", scrapingIdx < selectingIdx)
        assertTrue("SELECTING < SEARCHING", selectingIdx < searchingIdx)
        assertTrue("SEARCHING < BUILDING", searchingIdx < buildingIdx)

        assertTrue("Last event should be Success", events.last() is GenerationProgress.Success)
    }

    @Test
    fun execute_emitsSearchProgressPerArtist() = runTest {
        setupArtistsWithResults(
            "Artist 1" to "vid1",
            "Artist 2" to "vid2",
            "Artist 3" to "vid3",
            "Artist 4" to "vid4",
            "Artist 5" to "vid5"
        )

        val events = useCase.execute().toList()
        val progressEvents = events.filterIsInstance<GenerationProgress.SearchProgress>()

        assertEquals("Should emit 5 SearchProgress events", 5, progressEvents.size)
        assertEquals("All events should have total=5", setOf(5), progressEvents.map { it.total }.toSet())
        val completedValues = progressEvents.map { it.completed }.sorted()
        assertEquals("Completed values should be 1..5", listOf(1, 2, 3, 4, 5), completedValues)
    }

    @Test
    fun execute_success_countsMatchVideoIds() = runTest {
        // 3 found + 2 null
        setupArtistsWithResults(
            "Artist A" to "vid1",
            "Artist B" to null,
            "Artist C" to "vid3",
            "Artist D" to null,
            "Artist E" to "vid5"
        )

        val events = useCase.execute().toList()
        val success = events.filterIsInstance<GenerationProgress.Success>().first()

        assertEquals("songsAdded should be 3", 3, success.songsAdded)
        assertEquals("artistsSkipped should be 2", 2, success.artistsSkipped)
    }

    @Test
    fun execute_scrapeFailed_emitsError() = runTest {
        // All genres fail to fetch, no cache available
        fakeScrapingRepo.fetchFailures = Genre.entries.toSet()
        fakeScrapingRepo.cachedByGenre = emptyMap()
        fakeArtistDao.seenNames = emptyList()

        val events = useCase.execute().toList()
        val failed = events.filterIsInstance<GenerationProgress.Failed>().firstOrNull()

        assertTrue("Should emit Failed event", failed != null)
        assertEquals("Error should be ScrapeFailed", GenerationError.ScrapeFailed, failed!!.error)
    }

    @Test
    fun execute_quotaExceeded_emitsError() = runTest {
        setupArtistsWithResults("Artist A" to "vid1")
        fakeYouTubeRepo.addTrackException = makeHttpException(403)

        val events = useCase.execute().toList()
        val failed = events.filterIsInstance<GenerationProgress.Failed>().firstOrNull()

        assertTrue("Should emit Failed event", failed != null)
        assertEquals("Error should be QuotaExceeded", GenerationError.QuotaExceeded, failed!!.error)
    }

    @Test
    fun execute_authExpired_emitsError() = runTest {
        setupArtistsWithResults("Artist A" to "vid1")
        fakeYouTubeRepo.addTrackException = makeHttpException(401)

        val events = useCase.execute().toList()
        val failed = events.filterIsInstance<GenerationProgress.Failed>().firstOrNull()

        assertTrue("Should emit Failed event", failed != null)
        assertEquals("Error should be AuthExpired", GenerationError.AuthExpired, failed!!.error)
    }

    @Test
    fun execute_failure_noHistoryWritten() = runTest {
        // Cause a quota exceeded failure
        setupArtistsWithResults("Artist A" to "vid1")
        fakeYouTubeRepo.addTrackException = makeHttpException(403)

        useCase.execute().toList()

        // D-11: markArtistsSeen should NOT be called on failure
        assertEquals("No artists should be marked seen on failure", 0, fakeArtistDao.getUpsertedArtists().size)
    }

    @Test
    fun execute_deletesExistingPlaylist() = runTest {
        tokenStore.savePlaylistId("old-playlist-id")
        setupArtistsWithResults("Artist A" to "vid1")

        useCase.execute().toList()

        assertTrue(
            "deletePlaylist should be called with old-playlist-id",
            fakeYouTubeRepo.deletedPlaylistIds.contains("old-playlist-id")
        )
    }

    @Test
    fun execute_noExistingPlaylist_skipsDelete() = runTest {
        // No playlist ID in tokenStore
        setupArtistsWithResults("Artist A" to "vid1")

        useCase.execute().toList()

        assertTrue(
            "deletePlaylist should NOT be called when no playlist exists",
            fakeYouTubeRepo.deletedPlaylistIds.isEmpty()
        )
    }

    @Test
    fun execute_networkError_emitsNetworkError() = runTest {
        setupArtistsWithResults("Artist A" to "vid1")
        fakeYouTubeRepo.createPlaylistException = IOException("Network unavailable")

        val events = useCase.execute().toList()
        val failed = events.filterIsInstance<GenerationProgress.Failed>().firstOrNull()

        assertTrue("Should emit Failed event", failed != null)
        assertEquals("Error should be NetworkError", GenerationError.NetworkError, failed!!.error)
    }

    /** Helper to create an HttpException with a given HTTP status code. */
    private fun makeHttpException(code: Int): HttpException {
        val body = okhttp3.ResponseBody.create("text/plain".toMediaTypeOrNull(), "error")
        val response = Response.error<Any>(code, body)
        return HttpException(response)
    }
}
