package com.example.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log
import com.example.data.Track
import com.example.data.TrackCatalog
import com.example.service.MusicPlaybackService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RepeatMode {
    ALL,
    ONE,
    OFF;

    fun next(): RepeatMode = when (this) {
        ALL -> ONE
        ONE -> OFF
        OFF -> ALL
    }

    fun getDisplayName(): String = when (this) {
        ALL -> "Repetir Todo"
        ONE -> "Repetir 1"
        OFF -> "Sin Repetición"
    }
}

sealed class PlayerState {
    object Idle : PlayerState()
    object Buffering : PlayerState()
    data class Playing(val progressMs: Int, val durationMs: Int) : PlayerState()
    data class Paused(val progressMs: Int, val durationMs: Int) : PlayerState()
    data class Error(val message: String) : PlayerState()
}

class MusicPlayerManager private constructor(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    private var wifiLock: WifiManager.WifiLock? = null

    private val _currentState = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val currentState: StateFlow<PlayerState> = _currentState.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _currentTrackUrl = MutableStateFlow<String?>(null)
    val currentTrackUrl: StateFlow<String?> = _currentTrackUrl.asStateFlow()

    private val _playbackQueue = MutableStateFlow<List<Track>>(TrackCatalog.tracks)
    val playbackQueue: StateFlow<List<Track>> = _playbackQueue.asStateFlow()

    private val _currentTrackIndex = MutableStateFlow(0)
    val currentTrackIndex: StateFlow<Int> = _currentTrackIndex.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.ALL)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    var onTrackCompletedCallback: ((Track) -> Unit)? = null

    companion object {
        @Volatile
        private var instance: MusicPlayerManager? = null

        fun getInstance(context: Context): MusicPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: MusicPlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        initWifiLock()
        initMediaPlayer()
    }

    private fun initWifiLock() {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MusicPlayer:WifiLock")
        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "Could not create wifi lock", e)
        }
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
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {}

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            // Keep CPU awake while playing even if screen is off
            try {
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            } catch (e: Exception) {
                Log.w("MusicPlayerManager", "WakeLock not set on MediaPlayer", e)
            }

            setOnPreparedListener { mp ->
                val duration = safeDuration()
                _currentState.value = PlayerState.Playing(0, duration)
                try {
                    mp.start()
                } catch (e: Exception) {
                    Log.e("MusicPlayerManager", "Error starting player in onPrepared", e)
                }
                startTrackingProgress()
                MusicPlaybackService.start(context)
            }

            setOnCompletionListener {
                val duration = safeDuration()
                _currentState.value = PlayerState.Playing(duration, duration)
                stopTrackingProgress()

                val completedTrack = _currentTrack.value
                if (completedTrack != null) {
                    onTrackCompletedCallback?.invoke(completedTrack)
                }

                handleTrackCompletion()
            }

            setOnErrorListener { _, what, extra ->
                val errorMsg = "Error de reproducción: Código $what (extra: $extra)"
                _currentState.value = PlayerState.Error(errorMsg)
                Log.e("MusicPlayerManager", errorMsg)
                stopTrackingProgress()
                releaseWifiLock()
                false
            }
        }
    }

    private fun acquireWifiLock() {
        try {
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "Error acquiring wifi lock", e)
        }
    }

    private fun releaseWifiLock() {
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (e: Exception) {
            Log.e("MusicPlayerManager", "Error releasing wifi lock", e)
        }
    }

    private fun handleTrackCompletion() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                // Replay exact current track
                val current = _currentTrack.value
                if (current != null) {
                    play(current.url)
                }
            }
            RepeatMode.ALL -> {
                playNext()
            }
            RepeatMode.OFF -> {
                val queue = _playbackQueue.value
                val currentIndex = _currentTrackIndex.value
                if (queue.isNotEmpty() && currentIndex + 1 < queue.size) {
                    playNext()
                } else {
                    // Reached end of list without repeat
                    val duration = safeDuration()
                    _currentState.value = PlayerState.Paused(duration, duration)
                    MusicPlaybackService.start(context)
                }
            }
        }
    }

    fun setQueue(queue: List<Track>, startingIndex: Int = 0) {
        _playbackQueue.value = queue
        if (startingIndex in queue.indices) {
            _currentTrackIndex.value = startingIndex
            _currentTrack.value = queue[startingIndex]
        }
    }

    fun playTrack(track: Track, customQueue: List<Track>? = null) {
        if (customQueue != null && customQueue.isNotEmpty()) {
            _playbackQueue.value = customQueue
            val index = customQueue.indexOfFirst { it.url == track.url }
            _currentTrackIndex.value = if (index != -1) index else 0
        } else {
            val index = _playbackQueue.value.indexOfFirst { it.url == track.url }
            if (index != -1) {
                _currentTrackIndex.value = index
            }
        }
        _currentTrack.value = track
        play(track.url)
    }

    fun play(url: String) {
        scope.launch(Dispatchers.Main) {
            try {
                stopTrackingProgress()
                _currentTrackUrl.value = url
                _currentState.value = PlayerState.Buffering

                if (url.startsWith("http://") || url.startsWith("https://")) {
                    acquireWifiLock()
                } else {
                    releaseWifiLock()
                }

                // If track object isn't updated, resolve from queue or catalog
                if (_currentTrack.value?.url != url) {
                    _currentTrack.value = _playbackQueue.value.find { it.url == url }
                        ?: TrackCatalog.getTrackByUrl(url)
                }

                if (mediaPlayer == null) {
                    initMediaPlayer()
                }

                mediaPlayer?.apply {
                    reset()
                    if (url.startsWith("content://")) {
                        setDataSource(context, Uri.parse(url))
                    } else {
                        setDataSource(url)
                    }
                    prepareAsync()
                }

                MusicPlaybackService.start(context)
            } catch (e: Exception) {
                _currentState.value = PlayerState.Error("No se pudo cargar la canción")
                Log.e("MusicPlayerManager", "Error setting datasource", e)
                releaseWifiLock()
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
                releaseWifiLock()
                MusicPlaybackService.start(context)
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            if (!safeIsPlaying()) {
                try {
                    it.start()
                    startTrackingProgress()
                    _currentState.value = PlayerState.Playing(safeCurrentPosition(), safeDuration())
                    if (_currentTrackUrl.value?.startsWith("http") == true) {
                        acquireWifiLock()
                    }
                    MusicPlaybackService.start(context)
                } catch (e: Exception) {
                    Log.e("MusicPlayerManager", "Error resuming player", e)
                    // If resume failed, re-play the track
                    _currentTrack.value?.let { track -> play(track.url) }
                }
            }
        } ?: run {
            _currentTrack.value?.let { track -> play(track.url) }
        }
    }

    fun togglePlayPause() {
        when (val state = _currentState.value) {
            is PlayerState.Playing -> pause()
            is PlayerState.Paused -> resume()
            is PlayerState.Idle, is PlayerState.Error -> {
                val track = _currentTrack.value ?: _playbackQueue.value.getOrNull(_currentTrackIndex.value)
                if (track != null) {
                    playTrack(track)
                }
            }
            else -> {}
        }
    }

    fun playNext() {
        val queue = _playbackQueue.value
        if (queue.isEmpty()) return

        val nextIndex = if (_isShuffle.value && queue.size > 1) {
            var rand = (queue.indices).random()
            while (rand == _currentTrackIndex.value && queue.size > 1) {
                rand = (queue.indices).random()
            }
            rand
        } else {
            (_currentTrackIndex.value + 1) % queue.size
        }

        _currentTrackIndex.value = nextIndex
        val nextTrack = queue[nextIndex]
        _currentTrack.value = nextTrack
        play(nextTrack.url)
    }

    fun playPrevious() {
        val queue = _playbackQueue.value
        if (queue.isEmpty()) return

        // If played more than 3 seconds, replay from start of current track first
        val currentPos = safeCurrentPosition()
        if (currentPos > 3000) {
            seekTo(0)
            return
        }

        var prevIndex = _currentTrackIndex.value - 1
        if (prevIndex < 0) {
            prevIndex = queue.size - 1
        }

        _currentTrackIndex.value = prevIndex
        val prevTrack = queue[prevIndex]
        _currentTrack.value = prevTrack
        play(prevTrack.url)
    }

    fun forward(offsetMs: Int = 5000) {
        val current = safeCurrentPosition()
        val duration = safeDuration()
        if (duration > 0) {
            val newPos = (current + offsetMs).coerceAtMost(duration)
            seekTo(newPos)
        }
    }

    fun rewind(offsetMs: Int = 5000) {
        val current = safeCurrentPosition()
        val newPos = (current - offsetMs).coerceAtLeast(0)
        seekTo(newPos)
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

    fun toggleRepeatMode(): RepeatMode {
        val newMode = _repeatMode.value.next()
        _repeatMode.value = newMode
        MusicPlaybackService.start(context)
        return newMode
    }

    fun setRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
        MusicPlaybackService.start(context)
    }

    fun toggleShuffle(): Boolean {
        val newShuffle = !_isShuffle.value
        _isShuffle.value = newShuffle
        return newShuffle
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
                delay(250)
            }
        }
    }

    private fun stopTrackingProgress() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopTrackingProgress()
        releaseWifiLock()
        mediaPlayer?.apply {
            try {
                if (safeIsPlaying()) stop()
            } catch (e: Exception) {}
            try {
                release()
            } catch (e: Exception) {}
        }
        mediaPlayer = null
    }
}
