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

    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnPreparedListener { mp ->
                _currentState.value = PlayerState.Playing(0, mp.duration)
                mp.start()
                startTrackingProgress()
            }
            setOnCompletionListener {
                _currentState.value = PlayerState.Playing(mediaPlayer?.duration ?: 0, mediaPlayer?.duration ?: 0)
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
            if (it.isPlaying) {
                it.pause()
                stopTrackingProgress()
                _currentState.value = PlayerState.Paused(it.currentPosition, it.duration)
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying && _currentState.value is PlayerState.Paused) {
                it.start()
                startTrackingProgress()
                _currentState.value = PlayerState.Playing(it.currentPosition, it.duration)
            }
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.let {
            try {
                it.seekTo(positionMs)
                val isPlaying = it.isPlaying
                val duration = it.duration
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
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        _currentState.value = PlayerState.Playing(mp.currentPosition, mp.duration)
                    }
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
                if (isPlaying) stop()
                release()
            } catch (e: Exception) {
                Log.e("MusicPlayerManager", "Error releasing MediaPlayer", e)
            }
        }
        mediaPlayer = null
        scope.cancel()
    }
}
