package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.GeminiClient
import com.example.api.PlaylistRecommendation
import com.example.data.*
import com.example.player.MusicPlayerManager
import com.example.player.PlayerState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MusicViewModel(
    application: Application,
    private val repository: MusicRepository
) : AndroidViewModel(application) {

    // --- Active Playback Queue State ---
    private val _playbackQueue = MutableStateFlow<List<Track>>(TrackCatalog.tracks)
    val playbackQueue: StateFlow<List<Track>> = _playbackQueue.asStateFlow()

    private val _currentTrackIndex = MutableStateFlow(0)
    val currentTrackIndex: StateFlow<Int> = _currentTrackIndex.asStateFlow()

    // --- Player Core ---
    private val playerManager = MusicPlayerManager {
        playNext()
    }

    val playerState: StateFlow<PlayerState> = playerManager.currentState
    val currentTrackUrl: StateFlow<String?> = playerManager.currentTrackUrl

    // --- Room Database Observers ---
    val favoriteTracks: StateFlow<List<FavoriteTrackEntity>> = repository.allFavorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playbackHistory: StateFlow<List<TrackHistoryEntity>> = repository.getRecentHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- UI Layout Navigation States ---
    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

    private val _selectedTab = MutableStateFlow("explorer") // explorer, favorites, history, ai_assistant
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    // --- Gemini AI Assistant State ---
    private val _userMoodInput = MutableStateFlow("")
    val userMoodInput: StateFlow<String> = _userMoodInput.asStateFlow()

    private val _aiRecommendation = MutableStateFlow<PlaylistRecommendation?>(null)
    val aiRecommendation: StateFlow<PlaylistRecommendation?> = _aiRecommendation.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    // --- Active Track Helper ---
    val currentTrack: StateFlow<Track?> = combine(currentTrackUrl, _playbackQueue, _currentTrackIndex) { url, queue, index ->
        url?.let { TrackCatalog.getTrackByUrl(it) } ?: queue.getOrNull(index)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrackCatalog.tracks.first())

    // --- Check if Current Song is Favorite ---
    val isCurrentTrackFavorite: StateFlow<Boolean> = combine(currentTrack, favoriteTracks) { track, favorites ->
        track?.let { current -> favorites.any { it.trackUrl == current.url } } ?: false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun selectTab(tab: String) {
        _selectedTab.value = tab
    }

    fun setPlayerExpanded(expanded: Boolean) {
        _isPlayerExpanded.value = expanded
    }

    fun updateMoodInput(input: String) {
        _userMoodInput.value = input
    }

    // --- Playback Controls ---
    fun playTrack(track: Track, customQueue: List<Track> = TrackCatalog.tracks) {
        _playbackQueue.value = customQueue
        val index = customQueue.indexOfFirst { it.url == track.url }
        _currentTrackIndex.value = if (index != -1) index else 0

        playerManager.play(track.url)
        addToHistory(track)
    }

    fun togglePlayPause() {
        when (val state = playerState.value) {
            is PlayerState.Playing -> playerManager.pause()
            is PlayerState.Paused -> playerManager.resume()
            is PlayerState.Idle -> {
                // If IDLE, start playing the currently selected track
                currentTrack.value?.let { playTrack(it, _playbackQueue.value) }
            }
            else -> {}
        }
    }

    fun playNext() {
        val queue = _playbackQueue.value
        if (queue.isEmpty()) return
        val nextIndex = (_currentTrackIndex.value + 1) % queue.size
        _currentTrackIndex.value = nextIndex
        playTrack(queue[nextIndex], queue)
    }

    fun playPrevious() {
        val queue = _playbackQueue.value
        if (queue.isEmpty()) return
        var prevIndex = _currentTrackIndex.value - 1
        if (prevIndex < 0) {
            prevIndex = queue.size - 1
        }
        _currentTrackIndex.value = prevIndex
        playTrack(queue[prevIndex], queue)
    }

    fun seekTo(positionMs: Int) {
        playerManager.seekTo(positionMs)
    }

    // --- Favorites management ---
    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            val isFav = favoriteTracks.value.any { it.trackUrl == track.url }
            if (isFav) {
                repository.removeFavoriteByUrl(track.url)
            } else {
                repository.addFavorite(
                    FavoriteTrackEntity(
                        trackUrl = track.url,
                        title = track.title,
                        artist = track.artist,
                        duration = track.durationMs / 1000
                    )
                )
            }
        }
    }

    // --- History management ---
    private fun addToHistory(track: Track) {
        viewModelScope.launch {
            repository.addHistoryRecord(
                TrackHistoryEntity(
                    trackUrl = track.url,
                    title = track.title,
                    artist = track.artist
                )
            )
        }
    }

    fun clearPlaybackHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // --- Gemini AI Music Assistant Flow ---
    fun generateAiPlaylist() {
        val mood = _userMoodInput.value.trim()
        if (mood.isEmpty()) {
            _aiError.value = "Por favor escribe cómo te sientes."
            return
        }

        viewModelScope.launch {
            _isAiLoading.value = true
            _aiError.value = null
            _aiRecommendation.value = null

            try {
                // Fetch direct API Key securely from injected secrets BuildConfig
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    throw Exception("API Key de Gemini no configurada. Agrega GEMINI_API_KEY en la pestaña de Secretos en AI Studio.")
                }

                val recommendation = GeminiClient.getMoodPlaylist(mood, apiKey)
                _aiRecommendation.value = recommendation

                // Map recommended indices to track objects
                val recommendedTracks = recommendation.recommendedOrder.mapNotNull { index ->
                    TrackCatalog.getTrackById(index)
                }.distinct()

                if (recommendedTracks.isNotEmpty()) {
                    // Update active queue to matching tracks suggested by AI DJ
                    _playbackQueue.value = recommendedTracks
                    _currentTrackIndex.value = 0
                }
            } catch (e: Exception) {
                _aiError.value = e.message ?: "Ocurrió un error inesperado al contactar con la IA."
            } finally {
                _isAiLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}

// --- Traditional Android ViewModel Factory ---
class MusicViewModelFactory(
    private val application: Application,
    private val repository: MusicRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MusicViewModel::class.java)) {
            return MusicViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
