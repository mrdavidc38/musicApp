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
import com.example.player.RepeatMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

enum class TrackSortOrder(val displayName: String, val shortLabel: String) {
    DEFAULT("Por defecto", "Defecto"),
    DATE_DESC("Fecha (más recientes)", "Fecha ↓"),
    DATE_ASC("Fecha (más antiguas)", "Fecha ↑"),
    ARTIST_ASC("Artista (A - Z)", "Artista A-Z"),
    ARTIST_DESC("Artista (Z - A)", "Artista Z-A"),
    TITLE_ASC("Título (A - Z)", "Título A-Z")
}

class MusicViewModel(
    application: Application,
    private val repository: MusicRepository
) : AndroidViewModel(application) {

    // --- Scanning States ---
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanMessage = MutableStateFlow<String?>(null)
    val scanMessage: StateFlow<String?> = _scanMessage.asStateFlow()

    // --- Dynamic Scanned Tracks & Combined Catalog Flow with Absolute Deduplication & Removal filtering ---
    val allTracks: StateFlow<List<Track>> = combine(
        repository.allScannedTracks,
        repository.allRemovedTracks
    ) { scannedList, removedList ->
        val removedUrls = removedList.map { it.trackUrl.trim().lowercase() }.toSet()
        val seenUrls = mutableSetOf<String>()
        val seenKeys = mutableSetOf<String>()
        val uniqueTracks = mutableListOf<Track>()

        // 1. Add demo tracks (if not removed by user)
        for (track in TrackCatalog.tracks) {
            val urlKey = track.url.trim().lowercase()
            if (removedUrls.contains(urlKey)) continue

            val normTitle = track.title.trim().lowercase().replace(Regex("\\.(mp3|wav|aac|m4a|3gp|flac)$", RegexOption.IGNORE_CASE), "").trim()
            val normArtist = track.artist.trim().lowercase()
            val nameKey = "${normTitle}__${normArtist}"

            if (seenUrls.add(urlKey) && seenKeys.add(nameKey)) {
                uniqueTracks.add(track)
            }
        }

        // 2. Add local scanned tracks without any duplicates (if not removed by user)
        for (scanned in scannedList) {
            val urlKey = scanned.path.trim().lowercase()
            if (removedUrls.contains(urlKey)) continue

            val track = scanned.toTrack(uniqueTracks.size)
            val normTitle = track.title.trim().lowercase().replace(Regex("\\.(mp3|wav|aac|m4a|3gp|flac)$", RegexOption.IGNORE_CASE), "").trim()
            val normArtist = track.artist.trim().lowercase()
            val nameKey = "${normTitle}__${normArtist}"

            if (seenUrls.add(urlKey) && seenKeys.add(nameKey)) {
                uniqueTracks.add(track)
            }
        }

        uniqueTracks
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrackCatalog.tracks)

    // --- Search & Audio Duration & Sorting filter state ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterShortAudios = MutableStateFlow(false)
    val filterShortAudios: StateFlow<Boolean> = _filterShortAudios.asStateFlow()

    private val _minDurationLimitSeconds = MutableStateFlow(30)
    val minDurationLimitSeconds: StateFlow<Int> = _minDurationLimitSeconds.asStateFlow()

    private val _sortOrder = MutableStateFlow(TrackSortOrder.DEFAULT)
    val sortOrder: StateFlow<TrackSortOrder> = _sortOrder.asStateFlow()

    // --- Multi-Selection & Removal State ---
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedTrackUrls = MutableStateFlow<Set<String>>(emptySet())
    val selectedTrackUrls: StateFlow<Set<String>> = _selectedTrackUrls.asStateFlow()

    val filteredTracks: StateFlow<List<Track>> = combine(
        allTracks,
        _searchQuery,
        _filterShortAudios,
        _minDurationLimitSeconds,
        _sortOrder
    ) { tracks, query, filterShort, minSecs, sort ->
        var list = tracks
        if (filterShort) {
            val limitMs = minSecs * 1000L
            list = list.filter { it.durationMs >= limitMs }
        }
        if (query.isNotBlank()) {
            list = list.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true)
            }
        }
        when (sort) {
            TrackSortOrder.DEFAULT -> list
            TrackSortOrder.DATE_DESC -> list.sortedByDescending { it.timestamp }
            TrackSortOrder.DATE_ASC -> list.sortedBy { it.timestamp }
            TrackSortOrder.ARTIST_ASC -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist })
            TrackSortOrder.ARTIST_DESC -> list.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.artist })
            TrackSortOrder.TITLE_ASC -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterShortAudios(enabled: Boolean) {
        _filterShortAudios.value = enabled
    }

    fun setMinDurationLimitSeconds(seconds: Int) {
        _minDurationLimitSeconds.value = seconds
    }

    fun setSortOrder(order: TrackSortOrder) {
        _sortOrder.value = order
    }

    // --- Selection and Track Removal Methods ---
    fun toggleSelectionMode(enabled: Boolean? = null) {
        val newMode = enabled ?: !_isSelectionMode.value
        _isSelectionMode.value = newMode
        if (!newMode) {
            _selectedTrackUrls.value = emptySet()
        }
    }

    fun toggleTrackSelection(url: String) {
        val current = _selectedTrackUrls.value.toMutableSet()
        if (current.contains(url)) {
            current.remove(url)
        } else {
            current.add(url)
        }
        _selectedTrackUrls.value = current
        if (current.isNotEmpty() && !_isSelectionMode.value) {
            _isSelectionMode.value = true
        }
    }

    fun selectAllVisibleTracks(tracks: List<Track>) {
        val allUrls = tracks.map { it.url }.toSet()
        if (_selectedTrackUrls.value.containsAll(allUrls) && _selectedTrackUrls.value.isNotEmpty()) {
            _selectedTrackUrls.value = emptySet()
        } else {
            _selectedTrackUrls.value = allUrls
            _isSelectionMode.value = true
        }
    }

    fun clearSelection() {
        _selectedTrackUrls.value = emptySet()
        _isSelectionMode.value = false
    }

    fun removeSelectedTracks(urlsToRemove: Set<String> = _selectedTrackUrls.value) {
        if (urlsToRemove.isEmpty()) return
        viewModelScope.launch {
            val count = urlsToRemove.size
            repository.markTracksAsRemoved(urlsToRemove.toList())

            // Handle current playing track if removed
            val currentPlaying = currentTrack.value
            val wasPlayingRemoved = currentPlaying != null && urlsToRemove.contains(currentPlaying.url)

            val updatedQueue = playerManager.playbackQueue.value.filterNot { urlsToRemove.contains(it.url) }
            if (wasPlayingRemoved) {
                if (updatedQueue.isNotEmpty()) {
                    playerManager.setQueue(updatedQueue, 0)
                    playerManager.play(updatedQueue.first().url)
                } else {
                    playerManager.pause()
                    playerManager.setQueue(emptyList(), 0)
                }
            } else {
                val currentIdx = updatedQueue.indexOfFirst { it.url == currentPlaying?.url }.coerceAtLeast(0)
                playerManager.setQueue(updatedQueue, currentIdx)
            }

            _selectedTrackUrls.value = emptySet()
            _isSelectionMode.value = false
            _scanMessage.value = if (count == 1) "Se quitó 1 canción del reproductor" else "Se quitaron $count canciones del reproductor"
        }
    }

    fun removeSingleTrack(track: Track) {
        removeSelectedTracks(setOf(track.url))
    }

    // --- Player Core Singleton ---
    private val playerManager = MusicPlayerManager.getInstance(application)

    val playerState: StateFlow<PlayerState> = playerManager.currentState
    val currentTrack: StateFlow<Track?> = playerManager.currentTrack
    val currentTrackUrl: StateFlow<String?> = playerManager.currentTrackUrl
    val playbackQueue: StateFlow<List<Track>> = playerManager.playbackQueue
    val currentTrackIndex: StateFlow<Int> = playerManager.currentTrackIndex
    val repeatMode: StateFlow<RepeatMode> = playerManager.repeatMode
    val isShuffle: StateFlow<Boolean> = playerManager.isShuffle

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

    // --- Check if Current Song is Favorite ---
    val isCurrentTrackFavorite: StateFlow<Boolean> = combine(currentTrack, favoriteTracks) { track, favorites ->
        track?.let { current -> favorites.any { it.trackUrl == current.url } } ?: false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        playerManager.onTrackCompletedCallback = { completedTrack ->
            addToHistory(completedTrack)
        }

        // Initialize default track if none is selected
        if (playerManager.currentTrack.value == null && TrackCatalog.tracks.isNotEmpty()) {
            playerManager.setQueue(TrackCatalog.tracks, 0)
        }

        // Automatically sync system queue to all tracks when they change or start
        viewModelScope.launch {
            allTracks.collect { tracksList ->
                if (tracksList.isNotEmpty() && (playerManager.playbackQueue.value == TrackCatalog.tracks || playerManager.playbackQueue.value.isEmpty())) {
                    val currentIndex = playerManager.currentTrackIndex.value.coerceIn(0, (tracksList.size - 1).coerceAtLeast(0))
                    playerManager.setQueue(tracksList, currentIndex)
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
        playerManager.playTrack(track, customQueue)
        addToHistory(track)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun playNext() {
        playerManager.playNext()
    }

    fun playPrevious() {
        playerManager.playPrevious()
    }

    fun seekTo(positionMs: Int) {
        playerManager.seekTo(positionMs)
    }

    fun forward(offsetMs: Int = 5000) {
        playerManager.forward(offsetMs)
    }

    fun rewind(offsetMs: Int = 5000) {
        playerManager.rewind(offsetMs)
    }

    fun toggleRepeatMode() {
        playerManager.toggleRepeatMode()
    }

    fun setRepeatMode(mode: RepeatMode) {
        playerManager.setRepeatMode(mode)
    }

    fun toggleShuffle() {
        playerManager.toggleShuffle()
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
                    playerManager.setQueue(recommendedTracks, 0)
                }
            } catch (e: Exception) {
                _aiError.value = e.message ?: "Ocurrió un error inesperado al contactar con la IA."
            } finally {
                _isAiLoading.value = false
            }
        }
    }

    fun scanLocalMusic(customPath: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            val isCustomPath = !customPath.isNullOrEmpty()

            if (isCustomPath) {
                _scanMessage.value = "Verificando carpeta: $customPath"
            } else {
                _scanMessage.value = "Iniciando escaneo automático sin duplicados..."
            }

            val audioList = mutableListOf<ScannedTrackEntity>()
            val seenCanonicalPaths = mutableSetOf<String>()
            val seenTitleArtist = mutableSetOf<String>()

            // Add demo tracks to duplicate filters
            for (track in TrackCatalog.tracks) {
                seenCanonicalPaths.add(track.url.trim().lowercase())
                val normTitle = track.title.trim().lowercase().replace(Regex("\\.(mp3|wav|aac|m4a|3gp|flac)$", RegexOption.IGNORE_CASE), "").trim()
                val normArtist = track.artist.trim().lowercase()
                seenTitleArtist.add("${normTitle}__${normArtist}")
            }

            // If scanning a custom path, also load and keep existing tracks from DB
            if (isCustomPath) {
                val existing = repository.allScannedTracks.firstOrNull() ?: emptyList()
                for (item in existing) {
                    val canonical = try { File(item.path).canonicalPath.lowercase() } catch (e: Exception) { item.path.lowercase() }
                    val normTitle = item.title.trim().lowercase().replace(Regex("\\.(mp3|wav|aac|m4a|3gp|flac)$", RegexOption.IGNORE_CASE), "").trim()
                    val normArtist = item.artist.trim().lowercase()
                    val key = "${normTitle}__${normArtist}"

                    if (seenCanonicalPaths.add(canonical) && seenTitleArtist.add(key)) {
                        audioList.add(item)
                    }
                }
            }

            fun addIfUnique(path: String, title: String, artist: String, duration: Int): Boolean {
                val canonical = try { File(path).canonicalPath.lowercase() } catch (e: Exception) { path.lowercase() }
                val cleanTitle = title.trim().replace(Regex("\\.(mp3|wav|aac|m4a|3gp|flac)$", RegexOption.IGNORE_CASE), "").ifEmpty { "Canción Desconocida" }
                val cleanArtist = artist.trim().ifEmpty { "Artista Desconocido" }
                val normKey = "${cleanTitle.lowercase()}__${cleanArtist.lowercase()}"

                if (seenCanonicalPaths.add(canonical) && seenTitleArtist.add(normKey)) {
                    audioList.add(
                        ScannedTrackEntity(
                            path = path,
                            title = cleanTitle,
                            artist = cleanArtist,
                            durationMs = if (duration > 0) duration else 180000
                        )
                    )
                    return true
                }
                return false
            }

            // 1. If NOT a custom path scan, query Android MediaStore as a solid baseline
            if (!isCustomPath) {
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
                            val dataPath = cursor.getString(dataCol) ?: ""

                            val trackPath = if (dataPath.isNotEmpty() && File(dataPath).exists()) {
                                dataPath
                            } else {
                                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                            }

                            addIfUnique(trackPath, title, artist, duration)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Error querying MediaStore", e)
                }
            }

            // 2. Direct folder scanning with shared visited set across all root folders
            val scannedFiles = mutableListOf<File>()
            val visitedDirs = mutableSetOf<String>()

            if (isCustomPath) {
                val dir = File(customPath!!)
                if (dir.exists() && dir.isDirectory) {
                    _scanMessage.value = "Buscando archivos de audio en ${dir.name}..."
                    searchAudioFilesRecursive(dir, scannedFiles, visitedDirs, 0)
                } else {
                    _scanMessage.value = "Error: La carpeta '$customPath' no es válida o no existe."
                    _isScanning.value = false
                    return@launch
                }
            } else {
                _scanMessage.value = "Buscando archivos en carpetas de Audio..."
                val parentDirs = listOfNotNull(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    Environment.getExternalStorageDirectory()
                )

                for (dir in parentDirs) {
                    if (dir.exists() && dir.isDirectory) {
                        searchAudioFilesRecursive(dir, scannedFiles, visitedDirs, 0)
                    }
                }
            }

            // 3. Process direct file matches and extract metadata
            if (scannedFiles.isNotEmpty()) {
                val retriever = MediaMetadataRetriever()
                for ((index, file) in scannedFiles.withIndex()) {
                    val canonical = try { file.canonicalPath.lowercase() } catch (e: Exception) { file.absolutePath.lowercase() }
                    if (seenCanonicalPaths.contains(canonical)) continue

                    _scanMessage.value = "Leyendo metadatos: ${index + 1}/${scannedFiles.size} canciones"

                    try {
                        retriever.setDataSource(file.absolutePath)
                        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.nameWithoutExtension
                        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Artista Desconocido"
                        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val duration = durationStr?.toIntOrNull() ?: 180000

                        addIfUnique(file.absolutePath, title, artist, duration)
                    } catch (e: Throwable) {
                        Log.e("MusicViewModel", "Error leyendo metadatos de ${file.name}", e)
                        addIfUnique(file.absolutePath, file.nameWithoutExtension, "Audio Local", 180000)
                    }
                }
                try {
                    retriever.release()
                } catch (e: Exception) {}
            }

            // 4. Save found tracks to database (always clear and save deduplicated list)
            if (audioList.isNotEmpty()) {
                repository.clearScannedTracks()
                repository.saveScannedTracks(audioList)
                _scanMessage.value = "¡Actualizado! ${audioList.size} canciones encontradas sin duplicados."
            } else {
                if (isCustomPath) {
                    _scanMessage.value = "No se encontraron archivos de audio en $customPath"
                } else {
                    _scanMessage.value = "No se encontraron archivos de audio locales en el dispositivo."
                }
            }
            _isScanning.value = false
        }
    }

    private fun searchAudioFiles(dir: File, list: MutableList<File>) {
        val visited = mutableSetOf<String>()
        searchAudioFilesRecursive(dir, list, visited, 0)
    }

    private fun searchAudioFilesRecursive(dir: File, list: MutableList<File>, visited: MutableSet<String>, depth: Int) {
        if (depth > 10) return
        if (list.size >= 100) return // Limit scanning items
        val canonical = try { dir.canonicalPath } catch (e: Exception) { dir.absolutePath }
        if (!visited.add(canonical)) return

        val files = try {
            dir.listFiles()
        } catch (e: Exception) {
            null
        } ?: return

        val supportedExtensions = listOf(".mp3", ".wav", ".aac", ".m4a", ".3gp", ".flac")
        for (file in files) {
            try {
                if (file.isDirectory) {
                    val name = file.name
                    if (!name.startsWith(".") && name != "Android" && name != "cache" && name != "Self") {
                        searchAudioFilesRecursive(file, list, visited, depth + 1)
                    }
                } else if (file.isFile) {
                    val name = file.name
                    val hasMatch = supportedExtensions.any { ext -> name.endsWith(ext, ignoreCase = true) }
                    if (hasMatch) {
                        list.add(file)
                        if (list.size >= 100) return
                    }
                }
            } catch (e: Exception) {
                // Gracefully ignore individual file issues safely
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // PlayerManager is maintained as a long-lived player singleton so music playback continues in background.
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
        secondaryColor = Color((r * 0.35).toInt(), (g * 0.35).toInt(), (b * 0.35).toInt()),
        timestamp = this.timestamp
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
