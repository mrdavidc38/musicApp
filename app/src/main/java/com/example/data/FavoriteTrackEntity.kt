package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_tracks")
data class FavoriteTrackEntity(
    @PrimaryKey val trackUrl: String,
    val title: String,
    val artist: String,
    val duration: Int,
    val timestamp: Long = System.currentTimeMillis()
)
