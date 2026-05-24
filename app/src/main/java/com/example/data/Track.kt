package com.example.data

import androidx.compose.ui.graphics.Color

data class Track(
    val id: Int,
    val title: String,
    val artist: String,
    val url: String,
    val durationMs: Int,
    val description: String,
    val primaryColor: Color,
    val secondaryColor: Color
)

object TrackCatalog {
    val tracks = listOf(
        Track(
            id = 0,
            title = "Chill Horizon",
            artist = "SoundHelix",
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            durationMs = 372000,
            description = "Ecos de lofi relantes ideales para estudiar o concentrarte tranquilamente.",
            primaryColor = Color(0xFF5A3E62), // Rich purple/violet
            secondaryColor = Color(0xFF1E1B24) // Slate night
        ),
        Track(
            id = 1,
            title = "Electro Rhythm",
            artist = "SoundHelix",
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            durationMs = 423000,
            description = "Un pulso bailable, futurista y lleno de energía motivadora para tu entrenamiento.",
            primaryColor = Color(0xFFD61C5D), // Energetic hot pink
            secondaryColor = Color(0xFF0F1A2C) // Cyber navy
        ),
        Track(
            id = 2,
            title = "Summer Breeze",
            artist = "SoundHelix",
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            durationMs = 302000,
            description = "Melodía acústica optimista inspirada en atardeceres dorados de carretera.",
            primaryColor = Color(0xFFE5A63F), // Warm gold yellow
            secondaryColor = Color(0xFF3B1E15) // Cocoa terracotta
        ),
        Track(
            id = 3,
            title = "Synthwave Dreamer",
            artist = "SoundHelix",
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
            durationMs = 318000,
            description = "Estilo ochentero retro con bajos de neón clásicos y sintetizadores espaciales.",
            primaryColor = Color(0xFF00ADB5), // Cyan teal
            secondaryColor = Color(0xFF222831) // Charcoal slate
        ),
        Track(
            id = 4,
            title = "Cosmic Resonance",
            artist = "SoundHelix",
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-16.mp3",
            durationMs = 405000,
            description = "Paisajes sonoros ambientales profundos para meditación, meditación Zen u horas de sueño.",
            primaryColor = Color(0xFF3F4F6B), // Ambient steel blue
            secondaryColor = Color(0xFF080D1A) // Deep cosmic void
        )
    )

    fun getTrackById(id: Int): Track? = tracks.find { it.id == id }
    fun getTrackByUrl(url: String): Track? = tracks.find { it.url == url }
}
