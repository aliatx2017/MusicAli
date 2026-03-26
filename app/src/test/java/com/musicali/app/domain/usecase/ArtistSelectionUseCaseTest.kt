package com.musicali.app.domain.usecase

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.musicali.app.data.local.ArtistDao
import com.musicali.app.data.local.ArtistEntity
import com.musicali.app.data.remote.Genre
import com.musicali.app.data.remote.ScrapingRepository
import com.musicali.app.domain.repository.ArtistHistoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

private class FakeScrapingRepository : ScrapingRepository {
    var artistsByGenre: Map<Genre, List<String>> = emptyMap()
    var cachedByGenre: Map<Genre, List<String>> = emptyMap()
    var fetchFailures: Set<Genre> = emptySet()
    val cachedGenres = mutableMapOf<Genre, List<String>>()

    override suspend fun fetchArtists(genre: Genre): List<String> {
        if (genre in fetchFailures) throw java.io.IOException("Network error")
        return artistsByGenre[genre] ?: emptyList()
    }

    override suspend fun getCachedArtists(genre: Genre): List<String> =
        cachedByGenre[genre] ?: emptyList()

    override suspend fun cacheArtists(genre: Genre, artists: List<String>) {
        cachedGenres[genre] = artists
    }
}

private class FakeArtistDao : ArtistDao {
    var seenNames: List<String> = emptyList()
    private val artists = mutableListOf<ArtistEntity>()

    override suspend fun getAllSeenNormalizedNames(): List<String> = seenNames
    override suspend fun getEligibleAgain(
        currentRun: Int,
        currentTimeMs: Long,
        runTtl: Int,
        daysTtl: Long
    ): List<ArtistEntity> = emptyList()

    override suspend fun upsertAll(artists: List<ArtistEntity>) {
        this.artists.addAll(artists)
    }

    override suspend fun countSeen(): Int = seenNames.size
}

class ArtistSelectionUseCaseTest {

    private lateinit var fakeScrapingRepo: FakeScrapingRepository
    private lateinit var fakeArtistDao: FakeArtistDao
    private lateinit var useCase: ArtistSelectionUseCase

    @Before
    fun setUp() {
        fakeScrapingRepo = FakeScrapingRepository()
        fakeArtistDao = FakeArtistDao()
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File.createTempFile("test_prefs", ".preferences_pb") }
        )
        val artistHistoryRepo = ArtistHistoryRepository(fakeArtistDao, dataStore)
        useCase = ArtistSelectionUseCase(fakeScrapingRepo, artistHistoryRepo)
    }

    @Test
    fun selectArtists_returns65_withProportionalWeighting() = runTest {
        // Set up: real-world genre sizes: 266/191/176
        fakeScrapingRepo.artistsByGenre = mapOf(
            Genre.INDIETRONICA to (1..266).map { "indie_$it" },
            Genre.NU_DISCO to (1..191).map { "nudisco_$it" },
            Genre.INDIE_SOUL to (1..176).map { "soul_$it" }
        )
        fakeArtistDao.seenNames = emptyList()

        val result = useCase.selectArtists()

        // Must return exactly 65
        assertEquals(65, result.size)

        // Verify proportionality within +/-3 tolerance
        val indiCount = result.count { it.startsWith("indie_") }
        val nuDiscoCount = result.count { it.startsWith("nudisco_") }
        val soulCount = result.count { it.startsWith("soul_") }

        // Expected: indie ~27 (42%), nudisco ~20 (30%), soul ~18 (28%)
        assertTrue("INDIETRONICA count $indiCount should be roughly 27 (±3)", indiCount in 24..30)
        assertTrue("NU_DISCO count $nuDiscoCount should be roughly 20 (±3)", nuDiscoCount in 17..23)
        assertTrue("INDIE_SOUL count $soulCount should be roughly 18 (±3)", soulCount in 15..21)
        assertEquals(65, indiCount + nuDiscoCount + soulCount)
    }

    @Test
    fun selectArtists_excludesSeenArtists() = runTest {
        fakeScrapingRepo.artistsByGenre = mapOf(
            Genre.INDIETRONICA to listOf("Artist A", "Artist B", "Artist C"),
            Genre.NU_DISCO to listOf("Artist D", "Artist E"),
            Genre.INDIE_SOUL to listOf("Artist F", "Artist G")
        )
        // Mark some as seen (normalized)
        fakeArtistDao.seenNames = listOf("artist a", "artist d", "artist f")

        val result = useCase.selectArtists()

        // None of the seen artists should appear
        assertTrue("Result should not contain Artist A", result.none { it.trim().lowercase() == "artist a" })
        assertTrue("Result should not contain Artist D", result.none { it.trim().lowercase() == "artist d" })
        assertTrue("Result should not contain Artist F", result.none { it.trim().lowercase() == "artist f" })
    }

    @Test
    fun selectArtists_redistributesDeficit() = runTest {
        // One genre has way fewer eligible artists than its quota would be
        // 200 indie, 200 nudisco, but only 5 soul
        // Soul's quota = roughly 5/405 * 65 ≈ 1 from full pool, but with redistribution logic
        // Let's make it very clear: give soul only 5 artists but rest have 200
        fakeScrapingRepo.artistsByGenre = mapOf(
            Genre.INDIETRONICA to (1..200).map { "indie_$it" },
            Genre.NU_DISCO to (1..200).map { "nudisco_$it" },
            Genre.INDIE_SOUL to (1..5).map { "soul_$it" }
        )
        fakeArtistDao.seenNames = emptyList()

        val result = useCase.selectArtists()

        // Should still return 65 (deficit redistributed to other genres)
        assertEquals(65, result.size)
    }

    @Test
    fun selectArtists_allExhausted_returnsWhateverAvailable() = runTest {
        // Only 10 total artists across all genres
        fakeScrapingRepo.artistsByGenre = mapOf(
            Genre.INDIETRONICA to listOf("indie_1", "indie_2", "indie_3"),
            Genre.NU_DISCO to listOf("nudisco_1", "nudisco_2", "nudisco_3", "nudisco_4"),
            Genre.INDIE_SOUL to listOf("soul_1", "soul_2", "soul_3")
        )
        fakeArtistDao.seenNames = emptyList()

        val result = useCase.selectArtists()

        // Per D-06: never hard-error, return all available
        assertEquals(10, result.size)
    }

    @Test
    fun selectArtists_fallsBackToCache_onFetchFailure() = runTest {
        // INDIETRONICA fetch fails, but cache has data
        fakeScrapingRepo.fetchFailures = setOf(Genre.INDIETRONICA)
        fakeScrapingRepo.cachedByGenre = mapOf(
            Genre.INDIETRONICA to (1..10).map { "cached_$it" }
        )
        fakeScrapingRepo.artistsByGenre = mapOf(
            Genre.NU_DISCO to (1..50).map { "nudisco_$it" },
            Genre.INDIE_SOUL to (1..50).map { "soul_$it" }
        )
        fakeArtistDao.seenNames = emptyList()

        val result = useCase.selectArtists()

        // Result should include cached artists from INDIETRONICA
        assertTrue(
            "Result should include at least one cached artist",
            result.any { it.startsWith("cached_") }
        )
    }

    @Test
    fun selectArtists_noDuplicates() = runTest {
        fakeScrapingRepo.artistsByGenre = mapOf(
            Genre.INDIETRONICA to (1..100).map { "indie_$it" },
            Genre.NU_DISCO to (1..100).map { "nudisco_$it" },
            Genre.INDIE_SOUL to (1..100).map { "soul_$it" }
        )
        fakeArtistDao.seenNames = emptyList()

        val result = useCase.selectArtists()

        assertEquals("Result must have no duplicates", result.size, result.distinct().size)
    }

    @Test
    fun selectArtists_zeroEligible_returnsEmptyList() = runTest {
        val allArtists = listOf("artist_1", "artist_2", "artist_3")
        fakeScrapingRepo.artistsByGenre = mapOf(
            Genre.INDIETRONICA to allArtists,
            Genre.NU_DISCO to emptyList(),
            Genre.INDIE_SOUL to emptyList()
        )
        // All scraped artists are "seen"
        fakeArtistDao.seenNames = allArtists.map { it.trim().lowercase() }

        val result = useCase.selectArtists()

        // Per D-06: return empty list, no error
        assertTrue("Result should be empty when all artists are seen", result.isEmpty())
    }
}
