package com.musicali.app.fake

import com.musicali.app.data.remote.Genre
import com.musicali.app.data.remote.ScrapingRepository
import java.io.IOException

/**
 * Shared fake ScrapingRepository for use across multiple test classes.
 * Mirrors the private FakeScrapingRepository in ArtistSelectionUseCaseTest.
 */
class FakeScrapingRepository : ScrapingRepository {
    var artistsByGenre: Map<Genre, List<String>> = emptyMap()
    var cachedByGenre: Map<Genre, List<String>> = emptyMap()
    var fetchFailures: Set<Genre> = emptySet()
    val cachedGenres = mutableMapOf<Genre, List<String>>()

    override suspend fun fetchArtists(genre: Genre): List<String> {
        if (genre in fetchFailures) throw IOException("Network error")
        return artistsByGenre[genre] ?: emptyList()
    }

    override suspend fun getCachedArtists(genre: Genre): List<String> =
        cachedByGenre[genre] ?: emptyList()

    override suspend fun cacheArtists(genre: Genre, artists: List<String>) {
        cachedGenres[genre] = artists
    }
}
