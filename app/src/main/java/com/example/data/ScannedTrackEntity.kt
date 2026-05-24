package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scanned_tracks")
data class ScannedTrackEntity(
    @PrimaryKey val path: String,
    val title: String,
    val artist: String,
    val durationMs: Int,
    val timestamp: Long = System.currentTimeMillis()
)
