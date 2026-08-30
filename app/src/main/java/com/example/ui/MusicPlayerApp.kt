package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
                        onPrev = { viewModel.playPrevious() },
                        onForwardStep = { viewModel.forward(3000) },
                        onRewindStep = { viewModel.rewind(3000) },
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
                    repeatModeFlow = viewModel.repeatMode,
                    isShuffleFlow = viewModel.isShuffle,
                    onToggleFavorite = { viewModel.toggleFavorite(currentTrack!!) },
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onNext = { viewModel.playNext() },
                    onPrev = { viewModel.playPrevious() },
                    onForwardStep = { viewModel.forward(3000) },
                    onRewindStep = { viewModel.rewind(3000) },
                    onSeek = { viewModel.seekTo(it) },
                    onToggleRepeat = { viewModel.toggleRepeatMode() },
                    onToggleShuffle = { viewModel.toggleShuffle() },
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
    val tracks by viewModel.filteredTracks.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterShortAudios by viewModel.filterShortAudios.collectAsStateWithLifecycle()
    val minDurationLimitSeconds by viewModel.minDurationLimitSeconds.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedTrackUrls by viewModel.selectedTrackUrls.collectAsStateWithLifecycle()

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteTracks.collectAsStateWithLifecycle()

    var customPathInput by remember { mutableStateOf("/storage/emulated/0/Music") }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var singleTrackToDelete by remember { mutableStateOf<Track?>(null) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            viewModel.scanLocalMusic(customPathInput.trim())
        }
    }

    val checkAndRequestPermissions = { onPermissionGranted: () -> Unit ->
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }

        val hasPermission = permissions.all { perm ->
            androidx.core.content.ContextCompat.checkSelfPermission(context, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            onPermissionGranted()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Prominent Scan Options Card at the top
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x3312151D)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1FFFFFFF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header with Icon badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0x2900ADB5), RoundedCornerShape(10.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = Color(0xFF00ADB5),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Escanear Música Local",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Añade tus canciones automáticamente sin duplicados",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        }
                    }

                    // Path Input Field with clear button
                    OutlinedTextField(
                        value = customPathInput,
                        onValueChange = { customPathInput = it },
                        label = { Text("Carpeta a escanear", color = Color.White.copy(alpha = 0.6f)) },
                        placeholder = { Text("/storage/emulated/0/Music", color = Color.White.copy(alpha = 0.35f)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = Color(0xFF00ADB5),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (customPathInput.isNotEmpty()) {
                                IconButton(onClick = { customPathInput = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Limpiar ruta",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00ADB5),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            cursorColor = Color(0xFF00ADB5),
                            focusedLabelColor = Color(0xFF00ADB5)
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )

                    // Quick Selection Folder Chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Accesos rápidos:",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.55f),
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val suggestions = listOf(
                                "Música" to "/storage/emulated/0/Music",
                                "Descargas" to "/storage/emulated/0/Download",
                                "Almacenamiento" to "/storage/emulated/0"
                            )
                            suggestions.forEach { (label, path) ->
                                val isSelected = customPathInput.trim() == path
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            color = if (isSelected) Color(0xFF00ADB5) else Color(0x1FFFFFFF),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { customPathInput = path }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Action Buttons (2 distinct, spacious and clean)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Scan Custom Route
                        Button(
                            onClick = {
                                checkAndRequestPermissions {
                                    viewModel.scanLocalMusic(customPathInput.trim())
                                }
                            },
                            enabled = !isScanning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00ADB5),
                                contentColor = Color.White,
                                disabledContainerColor = Color(0x3300ADB5)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Escanear Carpeta",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Complete storage sweep
                        Button(
                            onClick = {
                                checkAndRequestPermissions {
                                    viewModel.scanLocalMusic(null)
                                }
                            },
                            enabled = !isScanning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0x2EFFFFFF),
                                contentColor = Color.White,
                                disabledContainerColor = Color(0x11FFFFFF)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Escanear Todo",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 2. Feedback Card
        if (isScanning || scanMessage?.isNotEmpty() == true) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x40000000)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
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
        }

        // 2.5. Buscador y Filtro de Audio (con Ordenamiento por Fecha y Artista)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x1F000000)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header of search and filter section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF00ADB5),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Buscador, Filtro y Ordenamiento",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // 1. Search Bar (Lupa text field)
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Buscar por título o artista...", fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Limpiar búsqueda",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("music_search_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00ADB5),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            cursorColor = Color(0xFF00ADB5)
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )

                    // 2. Ordenamiento (Botón pequeño y bonito por Fecha / Artista)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = null,
                                    tint = Color(0xFF00ADB5),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Ordenar por:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            // Botón pequeño y bonito con Menú Desplegable
                            Box {
                                Surface(
                                    onClick = { sortMenuExpanded = true },
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0x2E00ADB5),
                                    border = BorderStroke(1.dp, Color(0xFF00ADB5).copy(alpha = 0.6f)),
                                    modifier = Modifier.testTag("sort_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = when (sortOrder) {
                                                TrackSortOrder.DATE_DESC, TrackSortOrder.DATE_ASC -> Icons.Default.CalendarMonth
                                                TrackSortOrder.ARTIST_ASC, TrackSortOrder.ARTIST_DESC -> Icons.Default.Person
                                                TrackSortOrder.TITLE_ASC -> Icons.Default.SortByAlpha
                                                TrackSortOrder.DEFAULT -> Icons.Default.Reorder
                                            },
                                            contentDescription = null,
                                            tint = Color(0xFF00ADB5),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = sortOrder.shortLabel,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = Color(0xFF00ADB5),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false },
                                    modifier = Modifier.background(Color(0xFF1E212D))
                                ) {
                                    TrackSortOrder.values().forEach { order ->
                                        val isCurrent = sortOrder == order
                                        DropdownMenuItem(
                                            text = {
                                                 Text(
                                                     text = order.displayName,
                                                     color = if (isCurrent) Color(0xFF00ADB5) else Color.White,
                                                     fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                     fontSize = 13.sp
                                                 )
                                            },
                                            leadingIcon = {
                                                val icon = when (order) {
                                                    TrackSortOrder.DATE_DESC, TrackSortOrder.DATE_ASC -> Icons.Default.CalendarMonth
                                                    TrackSortOrder.ARTIST_ASC, TrackSortOrder.ARTIST_DESC -> Icons.Default.Person
                                                    TrackSortOrder.TITLE_ASC -> Icons.Default.SortByAlpha
                                                    TrackSortOrder.DEFAULT -> Icons.Default.Reorder
                                                }
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = if (isCurrent) Color(0xFF00ADB5) else Color.White.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            onClick = {
                                                viewModel.setSortOrder(order)
                                                sortMenuExpanded = false
                                            },
                                            modifier = Modifier.testTag("sort_option_${order.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                        }

                        // Botones de acceso rápido para ordenar con un solo toque
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Chip: Fecha
                            val isDateActive = sortOrder == TrackSortOrder.DATE_DESC || sortOrder == TrackSortOrder.DATE_ASC
                            Surface(
                                onClick = {
                                    if (sortOrder == TrackSortOrder.DATE_DESC) {
                                        viewModel.setSortOrder(TrackSortOrder.DATE_ASC)
                                    } else {
                                        viewModel.setSortOrder(TrackSortOrder.DATE_DESC)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDateActive) Color(0xFF00ADB5) else Color(0x15FFFFFF),
                                border = BorderStroke(1.dp, if (isDateActive) Color(0xFF00ADB5) else Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier.weight(1f).testTag("quick_sort_date")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = if (isDateActive) Color.Black else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (sortOrder == TrackSortOrder.DATE_ASC) "Fecha ↑" else "Fecha ↓",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDateActive) Color.Black else Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }

                            // Chip: Artista
                            val isArtistActive = sortOrder == TrackSortOrder.ARTIST_ASC || sortOrder == TrackSortOrder.ARTIST_DESC
                            Surface(
                                onClick = {
                                    if (sortOrder == TrackSortOrder.ARTIST_ASC) {
                                        viewModel.setSortOrder(TrackSortOrder.ARTIST_DESC)
                                    } else {
                                        viewModel.setSortOrder(TrackSortOrder.ARTIST_ASC)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isArtistActive) Color(0xFF00ADB5) else Color(0x15FFFFFF),
                                border = BorderStroke(1.dp, if (isArtistActive) Color(0xFF00ADB5) else Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier.weight(1f).testTag("quick_sort_artist")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (isArtistActive) Color.Black else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (sortOrder == TrackSortOrder.ARTIST_DESC) "Artista Z-A" else "Artista A-Z",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isArtistActive) Color.Black else Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }

                            // Chip: Título
                            val isTitleActive = sortOrder == TrackSortOrder.TITLE_ASC
                            Surface(
                                onClick = { viewModel.setSortOrder(TrackSortOrder.TITLE_ASC) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isTitleActive) Color(0xFF00ADB5) else Color(0x15FFFFFF),
                                border = BorderStroke(1.dp, if (isTitleActive) Color(0xFF00ADB5) else Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier.weight(1f).testTag("quick_sort_title")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SortByAlpha,
                                        contentDescription = null,
                                        tint = if (isTitleActive) Color.Black else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Título",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isTitleActive) Color.Black else Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.1f))
                    )

                    // 3. Short Audio Excluder Filter Option
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ocultar audios cortos (ej. WhatsApp)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Filtra audios menores a un rango de tiempo",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = filterShortAudios,
                                onCheckedChange = { viewModel.setFilterShortAudios(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF00ADB5),
                                    checkedTrackColor = Color(0xFF00ADB5).copy(alpha = 0.4f),
                                    uncheckedThumbColor = Color.LightGray,
                                    uncheckedTrackColor = Color.DarkGray
                                ),
                                modifier = Modifier.testTag("filter_short_audios_switch")
                            )
                        }

                        // Slider to adjust range limit
                        if (filterShortAudios) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Excluir menores a:",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.width(130.dp)
                                )
                                Slider(
                                    value = minDurationLimitSeconds.toFloat(),
                                    onValueChange = { viewModel.setMinDurationLimitSeconds(it.toInt()) },
                                    valueRange = 5f..180f,
                                    steps = 34,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF00ADB5),
                                        activeTrackColor = Color(0xFF00ADB5),
                                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier.weight(1f).testTag("duration_filter_slider")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${minDurationLimitSeconds}s",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00ADB5),
                                    modifier = Modifier.width(40.dp)
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ajuste rápido:",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                                listOf(15 to "15s", 30 to "30s", 60 to "1 Min", 120 to "2 Min").forEach { (secs, label) ->
                                    val isSelected = minDurationLimitSeconds == secs
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (isSelected) Color(0xFF00ADB5) else Color.Transparent,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) Color(0xFF00ADB5) else Color.White.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable { viewModel.setMinDurationLimitSeconds(secs) }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Barra de Selección Múltiple y Administración de Canciones
        item {
            Spacer(modifier = Modifier.height(4.dp))
            if (isSelectionMode) {
                // Barra de control de selección activa
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E212D)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF00ADB5).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("selection_action_card")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF00ADB5),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${selectedTrackUrls.size} seleccionada${if (selectedTrackUrls.size != 1) "s" else ""}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            // Botón Salir / Cancelar selección
                            IconButton(
                                onClick = { viewModel.clearSelection() },
                                modifier = Modifier.size(28.dp).testTag("cancel_selection_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancelar selección",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Botón Seleccionar Todo / Deseleccionar
                            val isAllSelected = tracks.isNotEmpty() && selectedTrackUrls.size == tracks.size
                            OutlinedButton(
                                onClick = { viewModel.selectAllVisibleTracks(tracks) },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                modifier = Modifier.weight(1f).testTag("select_all_button")
                            ) {
                                Icon(
                                    imageVector = if (isAllSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isAllSelected) "Deseleccionar" else "Todas",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Botón Quitar canciones seleccionadas del reproductor
                            Button(
                                onClick = {
                                    if (selectedTrackUrls.isNotEmpty()) {
                                        singleTrackToDelete = null
                                        showDeleteConfirmDialog = true
                                    }
                                },
                                enabled = selectedTrackUrls.isNotEmpty(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD32F2F),
                                    disabledContainerColor = Color(0x33D32F2F)
                                ),
                                modifier = Modifier.weight(1.3f).testTag("remove_selected_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = if (selectedTrackUrls.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Quitar (${selectedTrackUrls.size})",
                                    color = if (selectedTrackUrls.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.4f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                // Header normal con botón para iniciar selección
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Biblioteca de Canciones",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        val localCount = tracks.count { !it.url.startsWith("http") }
                        Text(
                            text = "$localCount locales • ${tracks.size - localCount} streaming • ${tracks.size} en lista",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }

                    // Botón estilizado para activar modo selección
                    Surface(
                        onClick = { viewModel.toggleSelectionMode(true) },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x1EFFFFFF),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier.testTag("enable_selection_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = "Seleccionar canciones",
                                tint = Color(0xFF00ADB5),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Seleccionar",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 4. Songs catalog listing
        items(tracks, key = { it.url }) { track ->
            val isPlaying = currentTrack?.id == track.id && playerState is PlayerState.Playing
            val isSelected = currentTrack?.id == track.id
            val isFavorite = favorites.any { it.trackUrl == track.url }
            val isChecked = selectedTrackUrls.contains(track.url)

            TrackRowItem(
                track = track,
                isSelected = isSelected,
                isPlaying = isPlaying,
                isFavorite = isFavorite,
                isSelectionMode = isSelectionMode,
                isChecked = isChecked,
                onSelect = {
                    if (isSelectionMode) {
                        viewModel.toggleTrackSelection(track.url)
                    } else {
                        viewModel.playTrack(track, tracks)
                    }
                },
                onToggleFavorite = { viewModel.toggleFavorite(track) },
                onToggleCheck = { viewModel.toggleTrackSelection(track.url) },
                onLongClick = {
                    if (!isSelectionMode) {
                        viewModel.toggleSelectionMode(true)
                        viewModel.toggleTrackSelection(track.url)
                    }
                },
                onDeleteSingle = {
                    singleTrackToDelete = track
                    showDeleteConfirmDialog = true
                }
            )
        }

        // Spacer to clear the persistent bottom player
        item {
            Spacer(modifier = Modifier.height(110.dp))
        }
    }

    // Diálogo de Confirmación para Quitar Canciones
    if (showDeleteConfirmDialog) {
        val count = if (singleTrackToDelete != null) 1 else selectedTrackUrls.size
        val titleText = if (singleTrackToDelete != null) "¿Quitar canción del reproductor?" else "¿Quitar $count canciones?"
        val bodyText = if (singleTrackToDelete != null) {
            "¿Deseas quitar \"${singleTrackToDelete?.title}\" de ${singleTrackToDelete?.artist} del reproductor y la biblioteca?"
        } else {
            "¿Deseas quitar las $count canciones seleccionadas del reproductor y la biblioteca?"
        }

        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                singleTrackToDelete = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = titleText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = bodyText,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (singleTrackToDelete != null) {
                            viewModel.removeSingleTrack(singleTrackToDelete!!)
                        } else {
                            viewModel.removeSelectedTracks()
                        }
                        showDeleteConfirmDialog = false
                        singleTrackToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    modifier = Modifier.testTag("confirm_remove_button")
                ) {
                    Text("Quitar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        singleTrackToDelete = null
                    },
                    modifier = Modifier.testTag("cancel_remove_button")
                ) {
                    Text("Cancelar", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF222831),
            shape = RoundedCornerShape(18.dp)
        )
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

@Composable
fun EqualizerIcon(modifier: Modifier = Modifier) {
    val composition = rememberInfiniteTransition(label = "equalizer_anim")
    val pulseScale by composition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    Icon(
        imageVector = Icons.Default.Equalizer,
        contentDescription = null,
        tint = Color.White,
        modifier = modifier
            .size(24.dp)
            .rotate(pulseScale * 30)
    )
}

// --- List Composable Row Asset ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRowItem(
    track: Track,
    isSelected: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    isSelectionMode: Boolean = false,
    isChecked: Boolean = false,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleCheck: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onDeleteSingle: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isChecked) Color(0x3500ADB5) else if (isSelected) Color(0x55FFFFFF) else Color(0x1BFFFFFF)
        ),
        shape = RoundedCornerShape(14.dp),
        border = if (isChecked) BorderStroke(1.5.dp, Color(0xFF00ADB5)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onLongClick
            )
            .testTag("track_row_${track.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox en modo selección
            if (isSelectionMode) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { onToggleCheck?.invoke() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF00ADB5),
                        uncheckedColor = Color.White.copy(alpha = 0.6f),
                        checkmarkColor = Color.White
                    ),
                    modifier = Modifier.padding(end = 6.dp).testTag("track_checkbox_${track.id}")
                )
            }

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
                    EqualizerIcon()
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

            if (!isSelectionMode) {
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

                if (onDeleteSingle != null) {
                    IconButton(
                        onClick = onDeleteSingle,
                        modifier = Modifier.size(36.dp).testTag("delete_single_${track.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Quitar del reproductor",
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
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
}

// --- Real-time Equalizer canvas banner ---

@Composable
fun SoundwaveVisualizerCard(playerState: PlayerState) {
    val isPlaying = playerState is PlayerState.Playing
    if (isPlaying) {
        AnimatedSoundwaveVisualizer()
    } else {
        StaticSoundwaveVisualizer()
    }
}

@Composable
fun AnimatedSoundwaveVisualizer() {
    val transition = rememberInfiniteTransition(label = "wave_anim")
    val waveOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_offset"
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
                    val scaleFactor = 0.2f + 0.8f * kotlin.math.abs(sin(waveOffset + phase))
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
                text = "REPRODUCIENDO",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

@Composable
fun StaticSoundwaveVisualizer() {
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
                    val scaleFactor = 0.15f
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
                text = "DETENIDO",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

// --- Holdable Media Button with long-press continuous seeking ---

@Composable
fun HoldableMediaButton(
    onClick: () -> Unit,
    onHoldStep: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    iconSize: androidx.compose.ui.unit.Dp = 36.dp,
    containerSize: androidx.compose.ui.unit.Dp = 52.dp,
    testTag: String = ""
) {
    val coroutineScope = rememberCoroutineScope()
    var isHolding by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isHolding) 1.25f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "holdScale"
    )

    Box(
        modifier = modifier
            .size(containerSize)
            .clip(CircleShape)
            .testTag(testTag)
            .pointerInput(onClick, onHoldStep) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var didHold = false
                    val holdJob = coroutineScope.launch {
                        delay(350)
                        didHold = true
                        isHolding = true
                        while (isActive) {
                            onHoldStep()
                            delay(180)
                        }
                    }
                    val up = waitForUpOrCancellation()
                    holdJob.cancel()
                    isHolding = false
                    if (!didHold && up != null) {
                        onClick()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isHolding) Color(0xFF00ADB5) else tint,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer(scaleX = scale, scaleY = scale)
        )
    }
}

// --- Mini player float control card ---

@Composable
fun MiniPlayerPanel(
    track: Track,
    playerState: PlayerState,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onForwardStep: () -> Unit,
    onRewindStep: () -> Unit,
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
                    // Previous (Hold to rewind)
                    HoldableMediaButton(
                        onClick = onPrev,
                        onHoldStep = onRewindStep,
                        icon = Icons.Default.SkipPrevious,
                        contentDescription = "Anterior o mantener para retroceder",
                        tint = Color.White,
                        iconSize = 22.dp,
                        containerSize = 36.dp,
                        testTag = "mini_previous_button"
                    )

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

                    // Next (Hold to forward)
                    HoldableMediaButton(
                        onClick = onNext,
                        onHoldStep = onForwardStep,
                        icon = Icons.Default.SkipNext,
                        contentDescription = "Siguiente o mantener para adelantar",
                        tint = Color.White,
                        iconSize = 22.dp,
                        containerSize = 36.dp,
                        testTag = "mini_next_button"
                    )
                }
            }

            // Compact linear loader progress indicator line
            val progressWidth = when (playerState) {
                is PlayerState.Playing -> {
                    val duration = playerState.durationMs.coerceAtLeast(1)
                    (playerState.progressMs.toFloat() / duration).coerceIn(0f, 1f)
                }
                is PlayerState.Paused -> {
                    val duration = playerState.durationMs.coerceAtLeast(1)
                    (playerState.progressMs.toFloat() / duration).coerceIn(0f, 1f)
                }
                else -> 0f
            }

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
    repeatModeFlow: StateFlow<com.example.player.RepeatMode>,
    isShuffleFlow: StateFlow<Boolean>,
    onToggleFavorite: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onForwardStep: () -> Unit,
    onRewindStep: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onDismiss: () -> Unit
) {
    val isFavorite by isFavoriteFlow.collectAsStateWithLifecycle()
    val repeatMode by repeatModeFlow.collectAsStateWithLifecycle()
    val isShuffle by isShuffleFlow.collectAsStateWithLifecycle()
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
    var draggingProgress by remember { mutableStateOf<Float?>(null) }
    val currentRotationValue = animatedRotation
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            staticRotation = currentRotationValue
        }
    }
    val rotation = if (isPlaying) animatedRotation else staticRotation

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
                val currentProgressValue = draggingProgress ?: progressNormalized

                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = currentProgressValue,
                        onValueChange = { value ->
                            draggingProgress = value
                        },
                        onValueChangeFinished = {
                            draggingProgress?.let { value ->
                                onSeek((value * durationMs).toInt())
                                draggingProgress = null
                            }
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
                            text = formatTime((currentProgressValue * durationMs).toInt()),
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle button
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.size(48.dp).testTag("shuffle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = if (isShuffle) "Aleatorio activado" else "Aleatorio desactivado",
                            tint = if (isShuffle) Color(0xFF00ADB5) else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Previous button with Hold-to-Rewind
                    HoldableMediaButton(
                        onClick = onPrev,
                        onHoldStep = onRewindStep,
                        icon = Icons.Default.SkipPrevious,
                        contentDescription = "Anterior o mantener para retroceder",
                        tint = Color.White,
                        iconSize = 36.dp,
                        containerSize = 52.dp,
                        testTag = "previous_button"
                    )

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

                    // Next button with Hold-to-Forward
                    HoldableMediaButton(
                        onClick = onNext,
                        onHoldStep = onForwardStep,
                        icon = Icons.Default.SkipNext,
                        contentDescription = "Siguiente o mantener para adelantar",
                        tint = Color.White,
                        iconSize = 36.dp,
                        containerSize = 52.dp,
                        testTag = "next_button"
                    )

                    // Repeat Mode button
                    IconButton(
                        onClick = onToggleRepeat,
                        modifier = Modifier.size(48.dp).testTag("repeat_mode_button")
                    ) {
                        val repeatIcon = when (repeatMode) {
                            com.example.player.RepeatMode.ALL -> Icons.Default.Repeat
                            com.example.player.RepeatMode.ONE -> Icons.Default.RepeatOne
                            com.example.player.RepeatMode.OFF -> Icons.Default.Repeat
                        }
                        val repeatTint = when (repeatMode) {
                            com.example.player.RepeatMode.ALL, com.example.player.RepeatMode.ONE -> Color(0xFF00ADB5)
                            com.example.player.RepeatMode.OFF -> Color.White.copy(alpha = 0.5f)
                        }
                        Icon(
                            imageVector = repeatIcon,
                            contentDescription = repeatMode.getDisplayName(),
                            tint = repeatTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Repeat Mode & Playback order indicator badge
                Text(
                    text = "${repeatMode.getDisplayName()} • ${if (isShuffle) "Modo Aleatorio" else "Orden Secuencial"}",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )

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
