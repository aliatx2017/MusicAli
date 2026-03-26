package com.musicali.app.data.remote

interface ScrapingRepository {
    suspend fun fetchArtists(genre: Genre): List<String>
    suspend fun getCachedArtists(genre: Genre): List<String>
    suspend fun cacheArtists(genre: Genre, artists: List<String>)
}
