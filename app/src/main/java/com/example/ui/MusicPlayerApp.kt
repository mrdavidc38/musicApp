package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.FavoriteTrackEntity
import com.example.data.Track
import com.example.data.TrackCatalog
import com.example.data.TrackHistoryEntity
import com.example.player.PlayerState
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerApp(viewModel: MusicViewModel) {
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val isExpanded by viewModel.isPlayerExpanded.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()

    // Dynamic background colors smoothly shifting according to current track
    val basePrimary = currentTrack?.primaryColor ?: Color(0xFF1E1B24)
    val baseSecondary = currentTrack?.secondaryColor ?: Color(0xFF0F0D13)

    val animatedPrimary by animateColorAsState(
        targetValue = basePrimary,
        animationSpec = tween(durationMillis = 1000)
    )
    val animatedSecondary by animateColorAsState(
        targetValue = baseSecondary,
        animationSpec = tween(durationMillis = 1000)
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Column {
                // Persistent mini media player panel at the bottom of the screen
                currentTrack?.let { track ->
                    MiniPlayerPanel(
                        track = track,
                        playerState = playerState,
                        onTogglePlay = { viewModel.togglePlayPause() },
                        onNext = { viewModel.playNext() },
                        onClick = { viewModel.setPlayerExpanded(true) }
                    )
                }

                // Global M3 Tab Navigation Bar
                NavigationBar(
                    containerColor = Color(0x99000000),
                    tonalElevation = 8.dp,
                    modifier = Modifier.background(Color(0x99000000))
                ) {
                    NavigationBarItem(
                        selected = selectedTab == "explorer",
                        onClick = { viewModel.selectTab("explorer") },
                        icon = { Icon(Icons.Default.MusicNote, contentDescription = "Explorador") },
                        label = { Text("Biblioteca", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == "favorites",
                        onClick = { viewModel.selectTab("favorites") },
                        icon = { Icon(Icons.Default.Favorite, contentDescription = "Favoritos") },
                        label = { Text("Favoritos", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == "history",
                        onClick = { viewModel.selectTab("history") },
                        icon = { Icon(Icons.Default.History, contentDescription = "Historial") },
                        label = { Text("Recientes", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == "ai_assistant",
                        onClick = { viewModel.selectTab("ai_assistant") },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "DJ IA") },
                        label = { Text("DJ IA", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(animatedPrimary, animatedSecondary)
                    )
                )
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Header block
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicVideo,
                        contentDescription = "Logo",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reproductor de Música",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Beautiful interactive soundwave equalizer visualizer banner
                SoundwaveVisualizerCard(playerState = playerState)

                Spacer(modifier = Modifier.height(16.dp))

                // Tab Content Switcher
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        "explorer" -> ExplorerTabContent(viewModel = viewModel)
                        "favorites" -> FavoritesTabContent(viewModel = viewModel)
                        "history" -> HistoryTabContent(viewModel = viewModel)
                        "ai_assistant" -> AiAssistantTabContent(viewModel = viewModel)
                    }
                }
            }

            // Expanded Full Screen Audio Player Dialog/Sheet
            if (isExpanded && currentTrack != null) {
                ExpandedPlayerSheet(
                    track = currentTrack!!,
                    playerState = playerState,
                    isFavoriteFlow = viewModel.isCurrentTrackFavorite,
                    onToggleFavorite = { viewModel.toggleFavorite(currentTrack!!) },
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onNext = { viewModel.playNext() },
                    onPrev = { viewModel.playPrevious() },
                    onSeek = { viewModel.seekTo(it) },
                    onDismiss = { viewModel.setPlayerExpanded(false) }
                )
            }
        }
    }
}

// --- Content Views & Tabs ---

@Composable
fun ExplorerTabContent(viewModel: MusicViewModel) {
    val context = LocalContext.current
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanMessage by viewModel.scanMessage.collectAsStateWithLifecycle()
    val tracks by viewModel.allTracks.collectAsStateWithLifecycle()

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteTracks.collectAsStateWithLifecycle()

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            viewModel.scanLocalMusic()
        }
    }

    val requestAndScan = {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val hasPermission = permissions.all { perm ->
            androidx.core.content.ContextCompat.checkSelfPermission(context, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            viewModel.scanLocalMusic()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Biblioteca de Canciones",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f)
            )
            val localCount = tracks.count { !it.url.startsWith("http") }
            Text(
                text = "$localCount locales • ${tracks.size - localCount} streaming",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        Button(
            onClick = { requestAndScan() },
            enabled = !isScanning,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0x33FFFFFF),
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Actualizar",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Escanear", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (isScanning || scanMessage?.isNotEmpty() == true) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0x40000000)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            color = Color(0xFF00ADB5),
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.Green,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = scanMessage ?: "",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(tracks) { track ->
            val isPlaying = currentTrack?.id == track.id && playerState is PlayerState.Playing
            val isSelected = currentTrack?.id == track.id
            val isFavorite = favorites.any { it.trackUrl == track.url }

            TrackRowItem(
                track = track,
                isSelected = isSelected,
                isPlaying = isPlaying,
                isFavorite = isFavorite,
                onSelect = { viewModel.playTrack(track, tracks) },
                onToggleFavorite = { viewModel.toggleFavorite(track) }
            )
        }
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun FavoritesTabContent(viewModel: MusicViewModel) {
    val favorites by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    Text(
        text = "Tus Canciones Favoritas",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White.copy(alpha = 0.9f),
        modifier = Modifier.padding(bottom = 8.dp)
    )

    if (favorites.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Aún no tienes canciones favoritas",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
                Text(
                    text = "Marca con un corazón en la biblioteca.",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp
                )
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(favorites) { fav ->
                val track = viewModel.allTracks.value.find { it.url == fav.trackUrl } ?: TrackCatalog.getTrackByUrl(fav.trackUrl) ?: Track(
                    id = -1,
                    title = fav.title,
                    artist = fav.artist,
                    url = fav.trackUrl,
                    durationMs = fav.duration * 1000,
                    description = "",
                    primaryColor = Color.Gray,
                    secondaryColor = Color.Black
                )
                val isPlaying = currentTrack?.url == fav.trackUrl && playerState is PlayerState.Playing
                val isSelected = currentTrack?.url == fav.trackUrl

                TrackRowItem(
                    track = track,
                    isSelected = isSelected,
                    isPlaying = isPlaying,
                    isFavorite = true,
                    onSelect = { viewModel.playTrack(track) },
                    onToggleFavorite = { viewModel.toggleFavorite(track) }
                )
            }
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun HistoryTabContent(viewModel: MusicViewModel) {
    val history by viewModel.playbackHistory.collectAsStateWithLifecycle()
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Canciones Reproducidas Recientemente",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f)
        )
        if (history.isNotEmpty()) {
            IconButton(
                onClick = { viewModel.clearPlaybackHistory() },
                modifier = Modifier.testTag("clear_history_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Limpiar Historial",
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }

    if (history.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.HistoryToggleOff,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No has escuchado nada recientemente",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(history) { record ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x33FFFFFF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x22FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = record.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = record.artist,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = sdf.format(Date(record.timestamp)),
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun AiAssistantTabContent(viewModel: MusicViewModel) {
    val moodInput by viewModel.userMoodInput.collectAsStateWithLifecycle()
    val recommendation by viewModel.aiRecommendation.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    val errorMsg by viewModel.aiError.collectAsStateWithLifecycle()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // AI Title Header Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x2AFFFFFF)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DJ Inteligente Gemini 3.5",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Cuéntale a nuestro DJ de Inteligencia Artificial cómo estás, qué humor tienes o qué estás haciendo, y reordenará el catálogo para armar una playlist idónea para ti con una reseña personalizada.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Form Block
        item {
            Column {
                OutlinedTextField(
                    value = moodInput,
                    onValueChange = { viewModel.updateMoodInput(it) },
                    label = { Text("Escribe tu humor o actividad...", color = Color.White.copy(alpha = 0.6f)) },
                    placeholder = { Text("ej. Estoy algo melancólico con frío de lluvia", color = Color.White.copy(alpha = 0.4f)) },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                        focusedLabelColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("mood_input_field")
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.generateAiPlaylist() },
                    enabled = !isAiLoading && moodInput.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00ADB5),
                        disabledContainerColor = Color(0x3300ADB5)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("generate_playlist_button")
                ) {
                    if (isAiLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("La IA está curando...", color = Color.White)
                    } else {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Crear Playlist de Humor", color = Color.White)
                    }
                }
            }
        }

        // Error Banner
        if (errorMsg != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x80D32F2F)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMsg!!,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Response Render
        if (recommendation != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x44FFFFFF)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.animateContentSize()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = Color(0xFF00ADB5),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Comentario del DJ IA",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = recommendation!!.explanation,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Cola de reproducción estructurada:",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        recommendation!!.recommendedOrder.mapNotNull {
                            TrackCatalog.getTrackById(it)
                        }.forEachIndexed { idx, track ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        color = Color(0xFF00ADB5),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(20.dp),
                                        textAlign = TextAlign.Center
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = track.artist,
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "💡 La cola se ha cargado automáticamente. ¡Presiona Reproducir para comenzar!",
                            color = Color(0xFFFFD700),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Catalog Reference Block
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x1F000000)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Canciones de las que dispone la IA:",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TrackCatalog.tracks.forEach {
                        Text(
                            text = "• ${it.title} (${it.artist}) — ${it.description}",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// --- List Composable Row Asset ---

@Composable
fun TrackRowItem(
    track: Track,
    isSelected: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0x55FFFFFF) else Color(0x1BFFFFFF)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stylized album art placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(track.primaryColor, track.secondaryColor)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    val composition = rememberInfiniteTransition()
                    val pulseScale by composition.animateFloat(
                        initialValue = 0.7f,
                        targetValue = 1.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(400, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        )
                    )
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(pulseScale * 30)
                    )
                } else {
                    val isLocal = !track.url.startsWith("http")
                    Icon(
                        imageVector = if (isLocal) Icons.Default.FolderOpen else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Track details labels
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isLocal = !track.url.startsWith("http")
                    if (isLocal) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Archivo local",
                            tint = Color(0xFF00ADB5),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = track.artist,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Interactive action layouts
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.testTag("favorite_button_${track.id}")
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorito",
                    tint = if (isFavorite) Color(0xFFD61C5D) else Color.White.copy(alpha = 0.6f)
                )
            }

            Icon(
                imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                contentDescription = if (isPlaying) "Pausa" else "Reproducir",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// --- Real-time Equalizer canvas banner ---

@Composable
fun SoundwaveVisualizerCard(playerState: PlayerState) {
    val isPlaying = playerState is PlayerState.Playing
    val transition = rememberInfiniteTransition()

    // High performance soundwave coordinates
    val waveOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x22000000)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val midY = height / 2f
                val barCount = 30
                val barGap = 6.dp.toPx()
                val totalGap = barGap * (barCount - 1)
                val barWidth = (width - totalGap) / barCount

                for (i in 0 until barCount) {
                    val x = i * (barWidth + barGap) + barWidth / 2f
                    val phase = i * 0.3f
                    val scaleFactor = if (isPlaying) {
                        // Create interactive sine-wave motion for each individual spectrum bar
                        0.2f + 0.8f * kotlin.math.abs(sin(waveOffset + phase))
                    } else {
                        0.15f // Rested standard equalizer heights when paused
                    }

                    // Simulated audio frequencies
                    val barHeight = scaleFactor * (height * 0.6f)
                    val startY = midY - barHeight / 2f
                    val endY = midY + barHeight / 2f

                    drawLine(
                        color = Color.White.copy(alpha = 0.15f + scaleFactor * 0.65f),
                        start = Offset(x, startY),
                        end = Offset(x, endY),
                        strokeWidth = barWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
            Text(
                text = if (isPlaying) "REPRODUCIENDO" else "DETENIDO",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

// --- Mini player float control card ---

@Composable
fun MiniPlayerPanel(
    track: Track,
    playerState: PlayerState,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xDF110F14)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mini album thumbnail icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(track.primaryColor, track.secondaryColor)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Detail text
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Play-Pause
                    IconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("mini_play_pause_button")
                    ) {
                        val isPlaying = playerState is PlayerState.Playing
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausa" else "Reproducir",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Next
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Siguiente",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Compact linear loader progress indicator line
            val progressWidth = when (playerState) {
                is PlayerState.Playing -> playerState.progressMs.toFloat() / playerState.durationMs
                is PlayerState.Paused -> playerState.progressMs.toFloat() / playerState.durationMs
                else -> 0f
            }.coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressWidth)
                        .background(Color(0xFF00ADB5))
                )
            }
        }
    }
}

// --- Expanded Player Sheet Composable ---

@Composable
fun ExpandedPlayerSheet(
    track: Track,
    playerState: PlayerState,
    isFavoriteFlow: StateFlow<Boolean>,
    onToggleFavorite: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val isFavorite by isFavoriteFlow.collectAsStateWithLifecycle()
    val isPlaying = playerState is PlayerState.Playing

    // Dynamic rotation simulation animation for active vinyl play operations
    val rotationTransition = rememberInfiniteTransition()
    val animatedRotation by rotationTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    var staticRotation by remember { mutableStateOf(0f) }
    val rotation = if (isPlaying) animatedRotation else {
        remember(animatedRotation) {
            staticRotation = animatedRotation
        }
        staticRotation
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(track.primaryColor, track.secondaryColor)
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Title and swipe dismiss row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Cerrar",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = "REPRODUCIENDO AHORA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f),
                        letterSpacing = 1.sp
                    )
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.testTag("expanded_favorite_button")) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (isFavorite) Color(0xFFD61C5D) else Color.White
                        )
                    }
                }

                // Vinyl disk art piece
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .rotate(rotation)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    // Groove markers on disk surface
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = Color.White.copy(alpha = 0.05f), radius = size.minDimension / 2.1f)
                        drawCircle(color = Color.White.copy(alpha = 0.05f), radius = size.minDimension / 2.3f)
                        drawCircle(color = Color.White.copy(alpha = 0.05f), radius = size.minDimension / 2.6f)
                        drawCircle(color = Color.White.copy(alpha = 0.05f), radius = size.minDimension / 3.1f)
                    }

                    // Album Center Artwork Custom design
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.42f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(track.primaryColor, track.secondaryColor)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    }
                }

                // Songs Metadata Labels description information
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = track.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = track.artist,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = track.description,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }

                // Slider control layouts & progress timelines
                val progressMs = when (playerState) {
                    is PlayerState.Playing -> playerState.progressMs
                    is PlayerState.Paused -> playerState.progressMs
                    else -> 0
                }
                val durationMs = when (playerState) {
                    is PlayerState.Playing -> playerState.durationMs
                    is PlayerState.Paused -> playerState.durationMs
                    else -> track.durationMs
                }.coerceAtLeast(1)

                val progressNormalized = (progressMs.toFloat() / durationMs).coerceIn(0f, 1f)

                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = progressNormalized,
                        onValueChange = { value ->
                            onSeek((value * durationMs).toInt())
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(progressMs),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = formatTime(durationMs),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }

                // Button play bar controls setup triggers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPrev,
                        modifier = Modifier.size(56.dp).testTag("previous_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Anterior",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Central glowing Play/Pause ring indicator circle Layout
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable(onClick = onTogglePlay)
                            .testTag("expanded_play_pause_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        val stateIcon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow
                        Icon(
                            imageVector = stateIcon,
                            contentDescription = if (isPlaying) "Pausa" else "Reproducir",
                            tint = track.primaryColor,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.size(56.dp).testTag("next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Siguiente",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

// --- Dynamic clock/time calculations helper ---
fun formatTime(milliseconds: Int): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
