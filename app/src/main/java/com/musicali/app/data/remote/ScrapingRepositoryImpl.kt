package com.musicali.app.data.remote

import androidx.room.withTransaction
import com.musicali.app.data.local.AppDatabase
import com.musicali.app.data.local.GenreCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import javax.inject.Inject

class ScrapingRepositoryImpl @Inject constructor(
    private val client: OkHttpClient,
    private val database: AppDatabase
) : ScrapingRepository {

    companion object {
        const val MIN_ARTIST_COUNT = 30

        internal fun parseArtists(html: String): List<String> {
            val doc = Jsoup.parse(html)
            return doc.select("div.genre")
                .map { it.ownText().trim() }
                .filter { it.isNotBlank() }
        }
    }

    override suspend fun fetchArtists(genre: Genre): List<String> {
        val html = fetchPage(genre.url)
        val artists = parseArtists(html)
        if (artists.size < MIN_ARTIST_COUNT) {
            throw IllegalStateException(
                "Scrape for ${genre.name} returned ${artists.size} artists (minimum: $MIN_ARTIST_COUNT). " +
                    "EveryNoise page structure may have changed."
            )
        }
        return artists
    }

    override suspend fun getCachedArtists(genre: Genre): List<String> =
        database.genreCacheDao().getArtistsByGenre(genre.name)

    override suspend fun cacheArtists(genre: Genre, artists: List<String>) {
        val now = System.currentTimeMillis()
        val entities = artists.map { name ->
            GenreCacheEntity(genre = genre.name, artistName = name, cachedAt = now)
        }
        database.withTransaction {
            database.genreCacheDao().deleteByGenre(genre.name)
            database.genreCacheDao().insertAll(entities)
        }
    }

    private suspend fun fetchPage(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("HTTP ${response.code} for $url")
            }
            response.body?.string() ?: throw java.io.IOException("Empty response body for $url")
        }
    }
}
