package com.example.ui

import android.app.Application
import android.content.ContentUris
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class MusicViewModel(
    application: Application,
    private val repository: MusicRepository
) : AndroidViewModel(application) {

    // --- Scanning States ---
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanMessage = MutableStateFlow<String?>(null)
    val scanMessage: StateFlow<String?> = _scanMessage.asStateFlow()

    // --- Dynamic Scanned Tracks & Combined Catalog Flow ---
    val allTracks: StateFlow<List<Track>> = repository.allScannedTracks
        .map { scannedList ->
            val localTracks = scannedList.mapIndexed { idx, scanned -> scanned.toTrack(idx) }
            TrackCatalog.tracks + localTracks
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrackCatalog.tracks)

    // --- Active Playback Queue State ---
    private val _playbackQueue = MutableStateFlow<List<Track>>(TrackCatalog.tracks)
    val playbackQueue: StateFlow<List<Track>> = _playbackQueue.asStateFlow()

    private val _currentTrackIndex = MutableStateFlow(0)
    val currentTrackIndex: StateFlow<Int> = _currentTrackIndex.asStateFlow()

    // --- Player Core ---
    private val playerManager = MusicPlayerManager(application) {
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
    val currentTrack: StateFlow<Track?> = combine(currentTrackUrl, allTracks, _playbackQueue, _currentTrackIndex) { url, tracks, queue, index ->
        url?.let { targetUrl ->
            tracks.find { it.url == targetUrl } ?: TrackCatalog.getTrackByUrl(targetUrl)
        } ?: queue.getOrNull(index)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrackCatalog.tracks.first())

    // --- Check if Current Song is Favorite ---
    val isCurrentTrackFavorite: StateFlow<Boolean> = combine(currentTrack, favoriteTracks) { track, favorites ->
        track?.let { current -> favorites.any { it.trackUrl == current.url } } ?: false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        // Automatically sync system queue to all tracks when they change or start
        viewModelScope.launch {
            allTracks.collect { tracksList ->
                // Only overwrite if playback queue is default catalog or empty
                if (_playbackQueue.value == TrackCatalog.tracks || _playbackQueue.value.isEmpty()) {
                    _playbackQueue.value = tracksList
                }
            }
        }
    }

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
    fun playTrack(track: Track, customQueue: List<Track> = allTracks.value) {
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

    fun scanLocalMusic() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            _scanMessage.value = "Iniciando escaneo de carpetas..."
            val audioList = mutableListOf<ScannedTrackEntity>()

            // 1. Query Android MediaStore
            try {
                val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATA
                )
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
                val contentResolver = getApplication<Application>().contentResolver

                contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val title = cursor.getString(titleCol) ?: "Canción Desconocida"
                        val artist = cursor.getString(artistCol) ?: "Artista Desconocido"
                        val duration = cursor.getInt(durationCol)
                        val path = cursor.getString(dataCol) ?: ""

                        if (path.isNotEmpty()) {
                            val trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                            audioList.add(
                                ScannedTrackEntity(
                                    path = trackUri,
                                    title = title,
                                    artist = artist,
                                    durationMs = if (duration > 0) duration else 180000
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error querying MediaStore", e)
            }

            _scanMessage.value = "Rebuscando carpetas físicas en el celular..."

            // 2. Direct folder scanning as a backup/enhancement
            val scannedFiles = mutableListOf<File>()
            val parentDirs = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStorageDirectory()
            )

            for (dir in parentDirs) {
                if (dir != null && dir.exists() && dir.isDirectory) {
                    searchMp3Files(dir, scannedFiles)
                }
            }

            if (scannedFiles.isNotEmpty()) {
                val retriever = MediaMetadataRetriever()
                for (file in scannedFiles) {
                    // Check if file is duplicate of MediaStore paths (avoid double counting same track)
                    if (audioList.any { it.path == file.absolutePath }) continue

                    try {
                        retriever.setDataSource(file.absolutePath)
                        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.nameWithoutExtension
                        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Artista Desconocido"
                        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val duration = durationStr?.toIntOrNull() ?: 180000

                        audioList.add(
                            ScannedTrackEntity(
                                path = file.absolutePath,
                                title = title,
                                artist = artist,
                                durationMs = duration
                            )
                        )
                    } catch (e: Exception) {
                        audioList.add(
                            ScannedTrackEntity(
                                path = file.absolutePath,
                                title = file.nameWithoutExtension,
                                artist = "Audio Local",
                                durationMs = 180000
                            )
                        )
                    }
                }
                try {
                    retriever.release()
                } catch (e: Exception) {}
            }

            // 3. Save to database
            if (audioList.isNotEmpty()) {
                repository.clearScannedTracks()
                repository.saveScannedTracks(audioList)
                _scanMessage.value = "¡Actualizado! Se cargaron ${audioList.size} canciones locales."
            } else {
                _scanMessage.value = "No se encontraron carpetas con MP3 ni pistas en la biblioteca."
            }
            _isScanning.value = false
        }
    }

    private fun searchMp3Files(dir: File, list: MutableList<File>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (!file.name.startsWith(".") && file.name != "Android") {
                    searchMp3Files(file, list)
                }
            } else if (file.isFile && file.name.endsWith(".mp3", ignoreCase = true)) {
                list.add(file)
                if (list.size >= 100) return // Limit scanned directory items to prevent choking
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}

// --- Dynamic Material 3 gradient color mapping helper ---
fun ScannedTrackEntity.toTrack(index: Int): Track {
    val hash = title.hashCode()
    val r = ((hash and 0xFF0000) shr 16).coerceIn(40, 200)
    val g = ((hash and 0x00FF00) shr 8).coerceIn(40, 200)
    val b = (hash and 0x0000FF).coerceIn(40, 200)

    return Track(
        id = 1000 + index,
        title = this.title,
        artist = this.artist,
        url = this.path,
        durationMs = this.durationMs,
        description = "Música local escaneada de tu dispositivo.",
        primaryColor = Color(r, g, b),
        secondaryColor = Color((r * 0.35).toInt(), (g * 0.35).toInt(), (b * 0.35).toInt())
    )
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
