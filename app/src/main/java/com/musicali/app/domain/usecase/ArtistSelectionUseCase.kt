package com.musicali.app.domain.usecase

import com.musicali.app.data.remote.Genre
import com.musicali.app.data.remote.ScrapingRepository
import com.musicali.app.domain.repository.ArtistHistoryRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import javax.inject.Inject
import kotlin.math.roundToInt

class ArtistSelectionUseCase @Inject constructor(
    private val scrapingRepository: ScrapingRepository,
    private val artistHistoryRepository: ArtistHistoryRepository
) {
    companion object {
        const val TARGET_ARTIST_COUNT = 65
    }

    suspend fun selectArtists(): List<String> {
        // Step 1: Fetch all genres in parallel (D-09)
        val artistsByGenre = fetchAllGenres()

        // Step 2: Get seen artist names for dedup (D-04: exclude first)
        val seenNames = artistHistoryRepository.getSeenArtistNames().toSet()

        // Step 3: Exclude seen artists (D-04)
        val eligibleByGenre = artistsByGenre.mapValues { (_, artists) ->
            artists.filter { it.trim().lowercase() !in seenNames }
        }

        // Step 4: Weighted proportional sampling (D-05, D-06)
        return selectWeighted(eligibleByGenre, TARGET_ARTIST_COUNT)
    }

    private suspend fun fetchAllGenres(): Map<Genre, List<String>> = coroutineScope {
        val results = Genre.entries.associateWith { genre ->
            async {
                try {
                    val artists = scrapingRepository.fetchArtists(genre)
                    // Cache successful scrape (D-03)
                    scrapingRepository.cacheArtists(genre, artists)
                    artists
                } catch (e: IOException) {
                    // Fallback to cache (SCRP-04)
                    scrapingRepository.getCachedArtists(genre)
                } catch (e: IllegalStateException) {
                    // Min-count validation failed -- fallback to cache
                    scrapingRepository.getCachedArtists(genre)
                }
            }
        }
        results.mapValues { (_, deferred) -> deferred.await() }
    }

    internal fun selectWeighted(
        eligibleByGenre: Map<Genre, List<String>>,
        targetTotal: Int = TARGET_ARTIST_COUNT
    ): List<String> {
        val total = eligibleByGenre.values.sumOf { it.size }
        if (total == 0) return emptyList()                      // D-06
        if (total <= targetTotal) return eligibleByGenre.values.flatten().shuffled()  // D-06

        // First pass: proportional quotas
        val quotas = eligibleByGenre.mapValues { (_, artists) ->
            (artists.size.toDouble() / total * targetTotal).roundToInt()
        }.toMutableMap()

        // Fix rounding drift
        val diff = targetTotal - quotas.values.sum()
        val largestGenre = eligibleByGenre.maxByOrNull { it.value.size }!!.key
        quotas[largestGenre] = quotas[largestGenre]!! + diff

        // Second pass: handle shortfalls (D-05)
        val result = mutableListOf<String>()
        var deficit = 0
        val shortfallGenres = mutableSetOf<Genre>()

        eligibleByGenre.forEach { (genre, artists) ->
            val quota = quotas[genre]!!
            if (artists.size < quota) {
                result.addAll(artists.shuffled())
                deficit += quota - artists.size
                shortfallGenres.add(genre)
            }
        }

        // Redistribute deficit to non-shortfall genres
        if (deficit > 0 && shortfallGenres.size < eligibleByGenre.size) {
            val redistributionGenres = eligibleByGenre.filter { it.key !in shortfallGenres }
            val redistributionTotal = redistributionGenres.values.sumOf { it.size }
            var redistributed = 0
            val redistEntries = redistributionGenres.entries.toList()

            redistEntries.forEachIndexed { index, (genre, artists) ->
                val extraQuota = if (index == redistEntries.lastIndex) {
                    deficit - redistributed  // last genre absorbs remainder
                } else {
                    (artists.size.toDouble() / redistributionTotal * deficit).roundToInt()
                }
                redistributed += extraQuota
                val finalQuota = quotas[genre]!! + extraQuota
                result.addAll(artists.shuffled().take(finalQuota))
            }
        } else {
            // No shortfall genres -- take from all genres at their quota
            eligibleByGenre.filter { it.key !in shortfallGenres }.forEach { (genre, artists) ->
                result.addAll(artists.shuffled().take(quotas[genre]!!))
            }
        }

        return result.distinct().shuffled().take(targetTotal)
    }
}
