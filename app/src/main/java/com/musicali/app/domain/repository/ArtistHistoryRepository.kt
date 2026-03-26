package com.musicali.app.domain.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.musicali.app.data.local.ArtistDao
import com.musicali.app.data.local.ArtistEntity
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ArtistHistoryRepository @Inject constructor(
    private val dao: ArtistDao,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val RUN_COUNTER = intPreferencesKey("run_counter")
        const val DEFAULT_RUN_TTL = 5
        const val DEFAULT_DAYS_TTL = 7_776_000_000L  // 90 days in ms
    }

    suspend fun getCurrentRun(): Int =
        dataStore.data.first()[RUN_COUNTER] ?: 0

    suspend fun incrementRun() {
        dataStore.edit { prefs ->
            prefs[RUN_COUNTER] = (prefs[RUN_COUNTER] ?: 0) + 1
        }
    }

    suspend fun getEligiblePreviousArtists(
        currentRun: Int,
        runTtl: Int = DEFAULT_RUN_TTL,
        daysTtl: Long = DEFAULT_DAYS_TTL
    ): List<String> =
        dao.getEligibleAgain(
            currentRun = currentRun,
            currentTimeMs = System.currentTimeMillis(),
            runTtl = runTtl,
            daysTtl = daysTtl
        ).map { it.displayName }

    suspend fun getSeenArtistNames(): List<String> =
        dao.getAllSeenNormalizedNames()

    suspend fun markArtistsSeen(artists: List<String>, currentRun: Int) {
        val now = System.currentTimeMillis()
        val entities = artists.map { name ->
            ArtistEntity(
                normalizedName = name.trim().lowercase(),
                displayName = name,
                last_played_run = currentRun,
                last_played_at = now
            )
        }
        dao.upsertAll(entities)
    }

    suspend fun countSeen(): Int = dao.countSeen()
}
