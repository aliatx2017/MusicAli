package com.musicali.app.fake

import com.musicali.app.data.local.ArtistDao
import com.musicali.app.data.local.ArtistEntity

/**
 * Shared fake ArtistDao for use across multiple test classes.
 * Mirrors the private FakeArtistDao in ArtistSelectionUseCaseTest.
 */
class FakeArtistDao : ArtistDao {
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

    override suspend fun countSeen(): Int = artists.size

    /** Returns all upserted entities for test verification. */
    fun getUpsertedArtists(): List<ArtistEntity> = artists.toList()
}
