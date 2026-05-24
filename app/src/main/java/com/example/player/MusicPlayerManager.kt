package com.example.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class PlayerState {
    object Idle : PlayerState()
    object Buffering : PlayerState()
    data class Playing(val progressMs: Int, val durationMs: Int) : PlayerState()
    data class Paused(val progressMs: Int, val durationMs: Int) : PlayerState()
    data class Error(val message: String) : PlayerState()
}

class MusicPlayerManager(
    private val context: Context,
    private val onTrackCompleted: () -> Unit
) {
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    private val _currentState = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val currentState: StateFlow<PlayerState> = _currentState.asStateFlow()

    private val _currentTrackUrl = MutableStateFlow<String?>(null)
    val currentTrackUrl: StateFlow<String?> = _currentTrackUrl.asStateFlow()

    init {
        initMediaPlayer()
    }

    private fun safeIsPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun safeCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun safeDuration(): Int {
        return try {
            mediaPlayer?.duration ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnPreparedListener { mp ->
                val duration = safeDuration()
                _currentState.value = PlayerState.Playing(0, duration)
                try {
                    mp.start()
                } catch (e: Exception) {
                    Log.e("MusicPlayerManager", "Error starting player", e)
                }
                startTrackingProgress()
            }
            setOnCompletionListener {
                val duration = safeDuration()
                _currentState.value = PlayerState.Playing(duration, duration)
                stopTrackingProgress()
                onTrackCompleted()
            }
            setOnErrorListener { _, what, extra ->
                val errorMsg = "Error de reproducción: Código $what (extra: $extra)"
                _currentState.value = PlayerState.Error(errorMsg)
                Log.e("MusicPlayerManager", errorMsg)
                stopTrackingProgress()
                false
            }
        }
    }

    fun play(url: String) {
        scope.launch {
            try {
                stopTrackingProgress()
                _currentTrackUrl.value = url
                _currentState.value = PlayerState.Buffering

                mediaPlayer?.apply {
                    reset()
                    if (url.startsWith("content://")) {
                        setDataSource(context, Uri.parse(url))
                    } else {
                        setDataSource(url)
                    }
                    prepareAsync() 
                }
            } catch (e: Exception) {
                _currentState.value = PlayerState.Error("No se pudo cargar la canción")
                Log.e("MusicPlayerManager", "Error setting datasource", e)
            }
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (safeIsPlaying()) {
                try {
                    it.pause()
                } catch (e: Exception) {
                    Log.e("MusicPlayerManager", "Error pausing player", e)
                }
                stopTrackingProgress()
                _currentState.value = PlayerState.Paused(safeCurrentPosition(), safeDuration())
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            if (!safeIsPlaying() && _currentState.value is PlayerState.Paused) {
                try {
                    it.start()
                } catch (e: Exception) {
                    Log.e("MusicPlayerManager", "Error starting player in resume", e)
                }
                startTrackingProgress()
                _currentState.value = PlayerState.Playing(safeCurrentPosition(), safeDuration())
            }
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.let {
            try {
                it.seekTo(positionMs)
                val isPlaying = safeIsPlaying()
                val duration = safeDuration()
                if (isPlaying) {
                    _currentState.value = PlayerState.Playing(positionMs, duration)
                } else {
                    _currentState.value = PlayerState.Paused(positionMs, duration)
                }
            } catch (e: Exception) {
                Log.e("MusicPlayerManager", "Error seeking", e)
            }
        }
    }

    private fun startTrackingProgress() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                try {
                    if (safeIsPlaying()) {
                        val currentPos = safeCurrentPosition()
                        val duration = safeDuration()
                        _currentState.value = PlayerState.Playing(currentPos, duration)
                    }
                } catch (e: Exception) {
                    Log.e("MusicPlayerManager", "Exception in tracking loop", e)
                }
                delay(250) // Poll four times a second for highly responsive visual updates
            }
        }
    }

    private fun stopTrackingProgress() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopTrackingProgress()
        mediaPlayer?.apply {
            try {
                if (safeIsPlaying()) stop()
            } catch (e: Exception) {
                Log.e("MusicPlayerManager", "Error stopping player on release", e)
            }
            try {
                release()
            } catch (e: Exception) {
                Log.e("MusicPlayerManager", "Error releasing MediaPlayer", e)
            }
        }
        mediaPlayer = null
        scope.cancel()
    }
}
