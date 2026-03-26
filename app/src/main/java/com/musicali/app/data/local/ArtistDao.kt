package com.musicali.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ArtistDao {

    @Query("""
        SELECT * FROM artist_history
        WHERE ((:currentRun - last_played_run) >= :runTtl)
           OR ((:currentTimeMs - last_played_at) >= :daysTtl)
    """)
    suspend fun getEligibleAgain(
        currentRun: Int,
        currentTimeMs: Long,
        runTtl: Int = 5,
        daysTtl: Long = 7_776_000_000L  // 90 days in ms
    ): List<ArtistEntity>

    @Query("SELECT normalizedName FROM artist_history")
    suspend fun getAllSeenNormalizedNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(artists: List<ArtistEntity>)

    @Query("SELECT COUNT(*) FROM artist_history")
    suspend fun countSeen(): Int
}
