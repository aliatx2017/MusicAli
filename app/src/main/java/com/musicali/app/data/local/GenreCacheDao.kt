package com.musicali.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface GenreCacheDao {
    @Query("SELECT artistName FROM genre_cache WHERE genre = :genre")
    suspend fun getArtistsByGenre(genre: String): List<String>

    @Insert
    suspend fun insertAll(entities: List<GenreCacheEntity>)

    @Query("DELETE FROM genre_cache WHERE genre = :genre")
    suspend fun deleteByGenre(genre: String)
}
