package com.musicali.app.domain.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.musicali.app.data.local.ArtistDao
import com.musicali.app.data.local.ArtistEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

private class FakeArtistDao : ArtistDao {
    val upsertedEntities = mutableListOf<ArtistEntity>()
    var eligibleAgainResult: List<ArtistEntity> = emptyList()
    var capturedCurrentRun: Int? = null
    var capturedCurrentTimeMs: Long? = null
    var capturedRunTtl: Int? = null
    var capturedDaysTtl: Long? = null

    override suspend fun getEligibleAgain(
        currentRun: Int,
        currentTimeMs: Long,
        runTtl: Int,
        daysTtl: Long
    ): List<ArtistEntity> {
        capturedCurrentRun = currentRun
        capturedCurrentTimeMs = currentTimeMs
        capturedRunTtl = runTtl
        capturedDaysTtl = daysTtl
        return eligibleAgainResult
    }

    override suspend fun getAllSeenNormalizedNames(): List<String> =
        upsertedEntities.map { it.normalizedName }

    override suspend fun upsertAll(artists: List<ArtistEntity>) {
        upsertedEntities.addAll(artists)
    }

    override suspend fun countSeen(): Int = upsertedEntities.size
}

@OptIn(ExperimentalCoroutinesApi::class)
@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class ArtistHistoryRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher + Job())
    private lateinit var fakeDao: FakeArtistDao
    private lateinit var repository: ArtistHistoryRepository
    private lateinit var dataStoreFile: File

    @Before
    fun setUp() {
        fakeDao = FakeArtistDao()
        dataStoreFile = File.createTempFile("test_datastore", ".preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { dataStoreFile }
        )
        repository = ArtistHistoryRepository(fakeDao, dataStore)
    }

    @After
    fun tearDown() {
        dataStoreFile.delete()
        testScope.cancel()
    }

    @Test
    fun getCurrentRun_returnsZeroWhenDataStoreIsEmpty() = testScope.runTest {
        val run = repository.getCurrentRun()
        assertEquals(0, run)
    }

    @Test
    fun incrementRun_incrementsAndPersistsCounter() = testScope.runTest {
        assertEquals(0, repository.getCurrentRun())
        repository.incrementRun()
        assertEquals(1, repository.getCurrentRun())
        repository.incrementRun()
        assertEquals(2, repository.getCurrentRun())
    }

    @Test
    fun markArtistsSeen_normalizesArtistNamesToLowercase() = testScope.runTest {
        repository.markArtistsSeen(
            artists = listOf("  Arcade Fire  ", "Bon Iver", "LCD SOUNDSYSTEM"),
            currentRun = 3
        )
        val normalizedNames = fakeDao.upsertedEntities.map { it.normalizedName }
        assertEquals("arcade fire", normalizedNames[0])
        assertEquals("bon iver", normalizedNames[1])
        assertEquals("lcd soundsystem", normalizedNames[2])
        // displayName preserves original capitalization
        assertEquals("  Arcade Fire  ", fakeDao.upsertedEntities[0].displayName)
        assertEquals(3, fakeDao.upsertedEntities[0].last_played_run)
    }

    @Test
    fun getEligiblePreviousArtists_delegatesToDaoWithCorrectCurrentRun() = testScope.runTest {
        fakeDao.eligibleAgainResult = listOf(
            ArtistEntity(
                normalizedName = "old artist",
                displayName = "Old Artist",
                last_played_run = 1,
                last_played_at = 0L
            )
        )

        val result = repository.getEligiblePreviousArtists(currentRun = 7)

        // Verify DAO was called with correct currentRun
        assertEquals(7, fakeDao.capturedCurrentRun)
        // Verify DAO was called with default TTL values from companion object
        assertEquals(ArtistHistoryRepository.DEFAULT_RUN_TTL, fakeDao.capturedRunTtl)
        assertEquals(ArtistHistoryRepository.DEFAULT_DAYS_TTL, fakeDao.capturedDaysTtl)
        // Verify result is mapped to displayName strings (not ArtistEntity objects)
        assertEquals(listOf("Old Artist"), result)
        // Verify currentTimeMs is a realistic epoch value (> year 2020 in ms)
        assertTrue(fakeDao.capturedCurrentTimeMs!! > 1_577_836_800_000L)
    }
}
