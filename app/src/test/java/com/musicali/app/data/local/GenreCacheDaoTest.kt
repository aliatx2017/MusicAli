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
class GenreCacheDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: GenreCacheDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.genreCacheDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAll_and_getArtistsByGenre() = runTest {
        // Insert 3 rows for INDIETRONICA and 2 for NU_DISCO
        val now = System.currentTimeMillis()
        dao.insertAll(listOf(
            GenreCacheEntity("INDIETRONICA", "Bonobo", now),
            GenreCacheEntity("INDIETRONICA", "Moderat", now),
            GenreCacheEntity("INDIETRONICA", "Four Tet", now),
            GenreCacheEntity("NU_DISCO", "Daft Punk", now),
            GenreCacheEntity("NU_DISCO", "Parcels", now)
        ))

        val indietronica = dao.getArtistsByGenre("INDIETRONICA")
        assertEquals(3, indietronica.size)
        assertTrue(indietronica.containsAll(listOf("Bonobo", "Moderat", "Four Tet")))

        val nuDisco = dao.getArtistsByGenre("NU_DISCO")
        assertEquals(2, nuDisco.size)
        assertTrue(nuDisco.containsAll(listOf("Daft Punk", "Parcels")))
    }

    @Test
    fun deleteByGenre_removesOnlyTargetGenre() = runTest {
        val now = System.currentTimeMillis()
        dao.insertAll(listOf(
            GenreCacheEntity("INDIETRONICA", "Bonobo", now),
            GenreCacheEntity("INDIETRONICA", "Moderat", now),
            GenreCacheEntity("NU_DISCO", "Daft Punk", now),
            GenreCacheEntity("NU_DISCO", "Parcels", now)
        ))

        dao.deleteByGenre("INDIETRONICA")

        val indietronica = dao.getArtistsByGenre("INDIETRONICA")
        assertEquals(0, indietronica.size)

        val nuDisco = dao.getArtistsByGenre("NU_DISCO")
        assertEquals(2, nuDisco.size)
        assertTrue(nuDisco.containsAll(listOf("Daft Punk", "Parcels")))
    }

    @Test
    fun insertAll_compositeKeyPrevents_duplicates() = runTest {
        val now = System.currentTimeMillis()
        dao.insertAll(listOf(
            GenreCacheEntity("INDIETRONICA", "Ellie Goulding", now)
        ))

        var exceptionThrown = false
        try {
            // Same genre + artistName composite key, different cachedAt — should throw
            dao.insertAll(listOf(
                GenreCacheEntity("INDIETRONICA", "Ellie Goulding", now + 1000L)
            ))
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            exceptionThrown = true
        }
        assertTrue("Expected SQLiteConstraintException for duplicate composite PK", exceptionThrown)
    }

    @Test
    fun getArtistsByGenre_returnsEmptyForUnknownGenre() = runTest {
        val result = dao.getArtistsByGenre("UNKNOWN_GENRE")
        assertEquals(0, result.size)
    }
}
