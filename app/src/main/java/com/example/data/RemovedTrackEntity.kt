package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "removed_tracks")
data class RemovedTrackEntity(
    @PrimaryKey val trackUrl: String,
    val timestamp: Long = System.currentTimeMillis()
)
