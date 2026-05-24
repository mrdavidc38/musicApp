package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_history")
data class TrackHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackUrl: String,
    val title: String,
    val artist: String,
    val timestamp: Long = System.currentTimeMillis()
)
