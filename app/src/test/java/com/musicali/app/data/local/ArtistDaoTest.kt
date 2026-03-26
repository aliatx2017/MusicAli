package com.musicali.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric 4.14.1 max supported SDK is 35; targetSdk=36 requires explicit config override
@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class ArtistDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: ArtistDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.artistDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertAll_persistsArtistsWithTimestampColumns() = runTest {
        val artists = listOf(
            ArtistEntity("artist one", "Artist One", last_played_run = 1, last_played_at = 1000L),
            ArtistEntity("artist two", "Artist Two", last_played_run = 2, last_played_at = 2000L),
            ArtistEntity("artist three", "Artist Three", last_played_run = 3, last_played_at = 3000L)
        )
        dao.upsertAll(artists)

        val count = dao.countSeen()
        assertEquals(3, count)

        val names = dao.getAllSeenNormalizedNames()
        assertEquals(3, names.size)
        assertTrue(names.contains("artist one"))
        assertTrue(names.contains("artist two"))
        assertTrue(names.contains("artist three"))
    }

    @Test
    fun upsertAll_replacesExistingArtistOnSameNormalizedName() = runTest {
        val original = ArtistEntity(
            normalizedName = "artist one",
            displayName = "Artist One",
            last_played_run = 1,
            last_played_at = 1000L
        )
        dao.upsertAll(listOf(original))

        val updated = ArtistEntity(
            normalizedName = "artist one",
            displayName = "Artist One Updated",
            last_played_run = 5,
            last_played_at = 9999L
        )
        dao.upsertAll(listOf(updated))

        val count = dao.countSeen()
        assertEquals(1, count)

        // Verify updated values are returned from eligibility query
        // After update: run=5, currentRun=10, delta=5 >= runTtl=5 -> eligible
        val eligible = dao.getEligibleAgain(
            currentRun = 10,
            currentTimeMs = 10000L,
            runTtl = 5,
            daysTtl = Long.MAX_VALUE
        )
        assertEquals(1, eligible.size)
        assertEquals("Artist One Updated", eligible.first().displayName)
        assertEquals(5, eligible.first().last_played_run)
        assertEquals(9999L, eligible.first().last_played_at)
    }

    @Test
    fun getEligibleAgain_returnsArtistAfterRunTtlElapsed() = runTest {
        // Artist with last_played_run=1; currentRun=6 -> delta=5 >= runTtl=5 -> eligible
        val artist = ArtistEntity(
            normalizedName = "old artist",
            displayName = "Old Artist",
            last_played_run = 1,
            last_played_at = System.currentTimeMillis()  // very recent — time TTL not elapsed
        )
        dao.upsertAll(listOf(artist))

        val eligible = dao.getEligibleAgain(
            currentRun = 6,
            currentTimeMs = System.currentTimeMillis(),
            runTtl = 5,
            daysTtl = 7_776_000_000L
        )
        assertEquals(1, eligible.size)
        assertEquals("Old Artist", eligible.first().displayName)
    }

    @Test
    fun getEligibleAgain_returnsArtistAfterDaysTtlElapsed() = runTest {
        // Artist with last_played_at=0; currentTimeMs=7_776_000_001 -> delta >= 90 days -> eligible
        val artist = ArtistEntity(
            normalizedName = "ancient artist",
            displayName = "Ancient Artist",
            last_played_run = 5,
            last_played_at = 0L
        )
        dao.upsertAll(listOf(artist))

        val eligible = dao.getEligibleAgain(
            currentRun = 6,                   // delta=1 run < runTtl=5 (run TTL not elapsed)
            currentTimeMs = 7_776_000_001L,   // 90 days + 1ms elapsed
            runTtl = 5,
            daysTtl = 7_776_000_000L
        )
        assertEquals(1, eligible.size)
        assertEquals("Ancient Artist", eligible.first().displayName)
    }

    @Test
    fun getEligibleAgain_excludesArtistWithinBothThresholds() = runTest {
        val now = System.currentTimeMillis()
        // last_played_run=3, currentRun=6: delta=3 < runTtl=5 (not enough runs)
        // last_played_at=now: delta~0 << daysTtl=90 days (not enough time)
        // Both conditions false -> NOT eligible
        val artist = ArtistEntity(
            normalizedName = "recent artist",
            displayName = "Recent Artist",
            last_played_run = 3,
            last_played_at = now
        )
        dao.upsertAll(listOf(artist))

        val eligible = dao.getEligibleAgain(
            currentRun = 6,
            currentTimeMs = now,
            runTtl = 5,
            daysTtl = 7_776_000_000L
        )
        assertEquals(0, eligible.size)
    }

    @Test
    fun getEligibleAgain_returnsArtistWhenOnlyRunTtlElapsed_hybridOr() = runTest {
        val now = System.currentTimeMillis()
        // last_played_run=1, currentRun=6: delta=5 >= runTtl=5 -> run TTL elapsed
        // last_played_at=now: delta~0 << daysTtl -> time TTL NOT elapsed
        // OR logic: run threshold alone is sufficient -> IS eligible
        val artist = ArtistEntity(
            normalizedName = "run eligible artist",
            displayName = "Run Eligible Artist",
            last_played_run = 1,
            last_played_at = now
        )
        dao.upsertAll(listOf(artist))

        val eligible = dao.getEligibleAgain(
            currentRun = 6,
            currentTimeMs = now,
            runTtl = 5,
            daysTtl = 7_776_000_000L
        )
        assertEquals(1, eligible.size)
        assertEquals("Run Eligible Artist", eligible.first().displayName)
    }
}
