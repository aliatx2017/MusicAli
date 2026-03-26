package com.musicali.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artist_history")
data class ArtistEntity(
    @PrimaryKey val normalizedName: String,  // name.trim().lowercase() — dedup key
    val displayName: String,                  // original capitalization for UI
    val last_played_run: Int,                 // run counter when last selected
    val last_played_at: Long                  // epoch millis when last selected
)
