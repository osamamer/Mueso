@file:Suppress("DEPRECATION")
package com.akshay.musicplayer.ui.screens

import com.akshay.musicplayer.ui.components.SwipeToQueue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.data.db.PlaylistEntity
import com.akshay.musicplayer.data.db.OnlinePlaylistEntity
import com.akshay.musicplayer.ui.components.DockedMiniPlayer
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel

private val BgDark: Color
    @Composable
    get() = if (com.akshay.musicplayer.ui.theme.LocalIsPureBlack.current) Color(0xFF000000) else MaterialTheme.colorScheme.background

private val AccentOrange: Color
    @Composable
    get() = com.akshay.musicplayer.ui.theme.LocalAccentColor.current

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: PlayerViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var selectedMuesoPlaylist by remember { mutableStateOf<OnlinePlaylistEntity?>(null) }
    var playlistToReturnTo by remember { mutableStateOf<PlaylistEntity?>(null) }
    var muesoPlaylistToReturnTo by remember { mutableStateOf<OnlinePlaylistEntity?>(null) }
    var onlinePlaylistToReturnTo by remember { mutableStateOf<com.akshay.musicplayer.ui.screens.SelectedOnlinePlaylist?>(null) }
    var selectedTrackForPlaylist by remember { mutableStateOf<com.akshay.musicplayer.domain.models.TrackEntity?>(null) }
    val onlinePlaylists by viewModel.onlinePlaylists.collectAsState()
    var isSearchActive by remember { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsState()

    val selectedArtistPage by viewModel.selectedArtistPage.collectAsState()
    val isLoadingArtistPage by viewModel.isLoadingArtistPage.collectAsState()
    val selectedOnlinePlaylist by viewModel.selectedOnlinePlaylist.collectAsState()

    var showSettingsScreen by remember { mutableStateOf(false) }
    val hasUnbackedUpChanges by viewModel.hasUnbackedUpChanges.collectAsState()
    val googleAccount by viewModel.googleAccount.collectAsState()

    var browsePage by remember { mutableStateOf<BrowsePage?>(null) }
    var isExploreDetailOpen by remember { mutableStateOf(false) }
    var isClassicPlayerExpanded by remember { mutableStateOf(false) }

    val playbackState by viewModel.playbackState.collectAsState()
    val skipRefusalEvent by viewModel.skipRefusalEvents.collectAsState()
    val shakeOffset = remember { Animatable(0f) }
    val shakeDistancePx = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.toPx() }
    LaunchedEffect(skipRefusalEvent) {
        if (skipRefusalEvent > 0L) {
            listOf(1f, -1f, 0.75f, -0.75f, 0.4f, -0.4f, 0f).forEach { amount ->
                shakeOffset.animateTo(amount * shakeDistancePx, animationSpec = tween(durationMillis = 45))
            }
        }
    }
    val activeAudioFormat by viewModel.activeAudioFormat.collectAsState()
    val currentTrack = remember(playbackState.currentTrackId, viewModel.getQueueTracks()) {
        viewModel.getQueueTracks().firstOrNull { it.id == playbackState.currentTrackId }
    }

    val clearPlayerReturn = {
        playlistToReturnTo = null
        muesoPlaylistToReturnTo = null
        onlinePlaylistToReturnTo = null
    }

    val navigateToPlayer: () -> Unit = {
        clearPlayerReturn()
        isClassicPlayerExpanded = true
    }

    val navigateToPlayerFromLocalPlaylist: () -> Unit = {
        playlistToReturnTo = selectedPlaylist
        selectedPlaylist = null
        isClassicPlayerExpanded = true
    }

    val navigateToPlayerFromMuesoPlaylist: () -> Unit = {
        muesoPlaylistToReturnTo = selectedMuesoPlaylist
        selectedMuesoPlaylist = null
        isClassicPlayerExpanded = true
    }

    val navigateToPlayerFromOnlinePlaylist: (com.akshay.musicplayer.ui.screens.SelectedOnlinePlaylist) -> Unit = { playlist ->
        onlinePlaylistToReturnTo = playlist
        viewModel.closeSelectedOnlinePlaylist()
        isClassicPlayerExpanded = true
    }

    androidx.activity.compose.BackHandler(enabled = browsePage != null) {
        browsePage = null
    }

    val returnFromPlayer: () -> Unit = {
        playlistToReturnTo?.let {
            selectedPlaylist = it
            playlistToReturnTo = null
        }
        muesoPlaylistToReturnTo?.let {
            selectedMuesoPlaylist = it
            muesoPlaylistToReturnTo = null
        }
        onlinePlaylistToReturnTo?.let {
            viewModel.setSelectedOnlinePlaylist(it)
            onlinePlaylistToReturnTo = null
        }
        isClassicPlayerExpanded = false
    }

    androidx.activity.compose.BackHandler(enabled = selectedArtistPage != null) {
        viewModel.closeArtist()
    }

    androidx.activity.compose.BackHandler(enabled = selectedOnlinePlaylist != null) {
        viewModel.closeSelectedOnlinePlaylist()
    }

    androidx.activity.compose.BackHandler(enabled = selectedPlaylist != null || selectedMuesoPlaylist != null) {
        selectedPlaylist = null
        selectedMuesoPlaylist = null
    }

    androidx.activity.compose.BackHandler(enabled = showSettingsScreen) {
        showSettingsScreen = false
    }

    androidx.activity.compose.BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        viewModel.setSearchQuery("")
    }

    androidx.activity.compose.BackHandler(enabled = browsePage == null && !isClassicPlayerExpanded && selectedPlaylist == null && selectedMuesoPlaylist == null && selectedArtistPage == null && selectedOnlinePlaylist == null && !isSearchActive && !showSettingsScreen && hasUnbackedUpChanges && googleAccount != null) {
        viewModel.performDriveBackup(context)
        (context as? android.app.Activity)?.moveTaskToBack(true)
    }

    val isDarkMode by viewModel.isDarkMode.collectAsState()

    val sharedPreferences = remember(context) { context.getSharedPreferences("mueso_prefs", android.content.Context.MODE_PRIVATE) }
    var showOnboardingDialog by remember { mutableStateOf(!sharedPreferences.getBoolean("has_seen_google_onboarding", false)) }

    LaunchedEffect(showOnboardingDialog) {
        android.util.Log.d("MUESO_ONBOARDING", "MainScreen state: showOnboardingDialog=$showOnboardingDialog, has_seen_google_onboarding=${sharedPreferences.getBoolean("has_seen_google_onboarding", false)}")
    }

    val googleSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        android.util.Log.d("MUESO_AUTH", "googleSignInLauncher activity result: resultCode=${result.resultCode}")
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            val email = account?.email
            android.util.Log.d("MUESO_AUTH", "Google Sign-In success: $email")

            if (!email.isNullOrBlank()) {
                android.widget.Toast.makeText(context, "Verifying Google account $email...", android.widget.Toast.LENGTH_SHORT).show()
                viewModel.connectAndBackupGoogleAccount(context, email) { success, msg ->
                    android.util.Log.d("MUESO_AUTH", "connectAndBackupGoogleAccount result: success=$success, msg=$msg")
                    if (success) {
                        viewModel.setGoogleAccount(account)
                        sharedPreferences.edit().putBoolean("has_seen_google_onboarding", true).apply()
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(context, "Backup failed: $msg", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            android.util.Log.w("MUESO_AUTH", "Google Sign-In failed: statusCode=${e.statusCode}", e)
            android.widget.Toast.makeText(context, "Sign-in cancelled", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initGoogleDriveAccount(context)
    }

    com.akshay.musicplayer.ui.components.VersionPickerHost(viewModel = viewModel, isDarkMode = isDarkMode)

    Scaffold(
        containerColor = if (isDarkMode) BgDark else Color.White,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .graphicsLayer { translationX = shakeOffset.value }
        ) {
            val googlePhotoUrl = googleAccount?.photoUrl?.toString()
            HomeScreen(
                viewModel = viewModel,
                googlePhotoUrl = googlePhotoUrl,
                onOpenLibrary = { browsePage = BrowsePage.Library },
                onOpenOffline = { browsePage = BrowsePage.Offline },
                onOpenExplore = {
                    isExploreDetailOpen = false
                    browsePage = BrowsePage.Explore
                },
                onOpenSettings = { showSettingsScreen = true },
                onSearchClick = { isSearchActive = true },
                onPlaylistClick = { selectedPlaylist = it },
                onOnlinePlaylistClick = { selectedMuesoPlaylist = it }
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = browsePage == BrowsePage.Library,
                enter = androidx.compose.animation.slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) + androidx.compose.animation.fadeIn(tween(200)),
                exit = androidx.compose.animation.slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(240, easing = FastOutSlowInEasing)
                ) + androidx.compose.animation.fadeOut(tween(180)),
                modifier = Modifier.fillMaxSize()
            ) {
                LibraryScreen(
                    viewModel = viewModel,
                    googlePhotoUrl = googlePhotoUrl,
                    onBack = { browsePage = null },
                    onOpenSettings = { showSettingsScreen = true },
                    onPlaylistClick = { selectedPlaylist = it },
                    onOnlinePlaylistClick = { selectedMuesoPlaylist = it }
                )
            }

            if (browsePage == BrowsePage.Offline) {
                OfflineLibraryScreen(
                    viewModel = viewModel,
                    onNavigateToPlayer = navigateToPlayer,
                    onPlaylistClick = { selectedPlaylist = it },
                    onOnlinePlaylistClick = { selectedMuesoPlaylist = it }
                )
            }
            if (browsePage == BrowsePage.Explore) {
                OnlinePlaylistsScreen(
                    viewModel = viewModel,
                    onNavigateToPlayer = navigateToPlayer,
                    onDetailVisibilityChanged = { isExploreDetailOpen = it }
                )
            }
            val showBrowseBack = browsePage == BrowsePage.Offline ||
                (browsePage == BrowsePage.Explore && !isExploreDetailOpen)
            if (showBrowseBack) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Row(
                        modifier = Modifier.fillMaxWidth().statusBarsPadding().height(60.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { browsePage = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to home")
                        }
                        Text(
                            if (browsePage == BrowsePage.Offline) "Offline library" else "Explore",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            if (isSearchActive) {
                Box(Modifier.fillMaxSize().background(if (isDarkMode) BgDark else Color.White))
                SearchBar(
                    searchQuery = searchQuery,
                    isDarkMode = isDarkMode,
                    onClose = {
                        isSearchActive = false
                        viewModel.setSearchQuery("")
                    },
                    onQueryChange = { viewModel.setSearchQuery(it) }
                )
            }

            // Floating search results dropdown
            AnimatedVisibility(
                visible = isSearchActive && searchQuery.isNotEmpty(),
                enter = expandVertically(
                    animationSpec = tween(250),
                    expandFrom = Alignment.Top
                ) + fadeIn(tween(200)),
                exit = shrinkVertically(
                    animationSpec = tween(200),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(tween(150)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 60.dp)
                    .imePadding()
            ) {
                val results by viewModel.searchResults.collectAsState()
                val artistResults by viewModel.artistResults.collectAsState()
                val playlistResults by viewModel.playlistResults.collectAsState()
                val isSearchingOnline by viewModel.isSearchingOnline.collectAsState()
                val searchCategory by viewModel.searchCategory.collectAsState()

                val hasAnyResults = when (searchCategory.trim().lowercase()) {
                    "artists", "artist" -> artistResults.isNotEmpty()
                    "albums", "album", "playlists", "playlist" -> playlistResults.isNotEmpty()
                    else -> results.isNotEmpty() || artistResults.isNotEmpty() || playlistResults.isNotEmpty()
                }

                val isPureBlack = com.akshay.musicplayer.ui.theme.LocalIsPureBlack.current
                val searchContainerBg = if (!isDarkMode) {
                    Color.White.copy(alpha = 0.98f)
                } else if (isPureBlack) {
                    Color(0xFF0F0F10).copy(alpha = 0.98f)
                } else {
                    Color(0xFF16161A).copy(alpha = 0.98f)
                }
                val searchBorderColor = if (isDarkMode) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)

                Column(
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .heightIn(max = 440.dp)
                        .shadow(16.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.5f))
                        .border(1.dp, searchBorderColor, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(searchContainerBg)
                        .padding(12.dp)
                ) {
                    // Search Category Filter Chips
                    val searchCategories = listOf("All", "Songs", "Videos", "Albums", "Playlists", "Artists")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(searchCategories) { cat ->
                                val isSelected = cat.equals(searchCategory, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (isSelected) AccentOrange
                                            else if (isDarkMode) Color.White.copy(alpha = 0.08f)
                                            else Color.Black.copy(alpha = 0.05f)
                                        )
                                        .clickable { viewModel.setSearchCategory(cat) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = cat,
                                        color = if (isSelected) Color.White else (if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f)),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }

                        if (isSearchingOnline) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(
                                color = AccentOrange,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    HorizontalDivider(
                        color = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    if (isSearchingOnline && !hasAnyResults) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = AccentOrange,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Searching $searchCategory online...", color = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f), fontSize = 14.sp)
                        }
                    } else if (!hasAnyResults) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No matching $searchCategory found", color = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f), fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Artist items
                            if (searchCategory.equals("Artists", ignoreCase = true) || (searchCategory.equals("All", ignoreCase = true) && artistResults.isNotEmpty())) {
                                if (searchCategory.equals("All", ignoreCase = true)) {
                                    item {
                                        Text(
                                            "Artists",
                                            color = AccentOrange,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                items(artistResults) { artist ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                viewModel.openArtist(artist.id, artist.name, artist.thumbnailUrl)
                                                isSearchActive = false
                                                viewModel.setSearchQuery("")
                                            }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(Color.White.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!artist.thumbnailUrl.isNullOrBlank()) {
                                                com.akshay.musicplayer.ui.components.SmartArtworkImage(
                                                    artworkUrl = artist.thumbnailUrl,
                                                    contentDescription = artist.name,
                                                    modifier = Modifier.fillMaxSize(),
                                                    thumbnailQuality = "Low (Fast)"
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.MusicNote,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.5f)
                                                )
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = artist.name,
                                                color = if (isDarkMode) Color.White else Color(0xFF1D1D1F),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val sub = if (artist.subscribers.isNotBlank()) "Artist • ${artist.subscribers}" else "Artist"
                                            Text(
                                                text = sub,
                                                color = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73),
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            // 2. Playlists / Albums items
                            if (searchCategory.equals("Albums", ignoreCase = true) || searchCategory.equals("Playlists", ignoreCase = true) || (searchCategory.equals("All", ignoreCase = true) && playlistResults.isNotEmpty())) {
                                if (searchCategory.equals("All", ignoreCase = true)) {
                                    item {
                                        Text(
                                            "Albums & Playlists",
                                            color = AccentOrange,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                items(playlistResults) { pl ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                viewModel.setSelectedOnlinePlaylist(
                                                    SelectedOnlinePlaylist(
                                                        id = pl.id,
                                                        title = pl.title,
                                                        subtitle = pl.subtitle,
                                                        artworkUrl = pl.artworkUrl
                                                    )
                                                )
                                                isSearchActive = false
                                                viewModel.setSearchQuery("")
                                            }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!pl.artworkUrl.isNullOrBlank()) {
                                                com.akshay.musicplayer.ui.components.SmartArtworkImage(
                                                    artworkUrl = pl.artworkUrl,
                                                    contentDescription = pl.title,
                                                    modifier = Modifier.fillMaxSize(),
                                                    thumbnailQuality = "Low (Fast)"
                                                )
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pl.title,
                                                color = if (isDarkMode) Color.White else Color(0xFF1D1D1F),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (pl.subtitle.isNotBlank()) {
                                                Text(
                                                    text = pl.subtitle,
                                                    color = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73),
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Songs / Tracks
                            if (!searchCategory.equals("Artists", ignoreCase = true) && !searchCategory.equals("Albums", ignoreCase = true) && !searchCategory.equals("Playlists", ignoreCase = true)) {
                                if (searchCategory.equals("All", ignoreCase = true) && (artistResults.isNotEmpty() || playlistResults.isNotEmpty())) {
                                    item {
                                        Text(
                                            "Songs",
                                            color = AccentOrange,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                items(results.size) { index ->
                                    val track = results[index]
                                    SwipeToQueue(onAddToQueue = { viewModel.addToQueue(track) }, trackTitle = track.title) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    viewModel.playTrack(track)
                                                    isSearchActive = false
                                                    viewModel.setSearchQuery("")
                                                    navigateToPlayer()
                                                }
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            val thumbModel = track.artworkUrl ?: "content://media/external/audio/albumart/${track.albumId}"
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.White.copy(alpha = 0.1f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                com.akshay.musicplayer.ui.components.SmartArtworkImage(
                                                    artworkUrl = thumbModel,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    thumbnailQuality = "Low (Fast)"
                                                )
                                            }
    
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = track.title,
                                                    color = if (isDarkMode) Color.White else Color(0xFF1D1D1F),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = track.artist,
                                                    color = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color(0xFF6E6E73),
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
    
                                            var showSearchMenu by remember { mutableStateOf(false) }
                                            Box {
                                                IconButton(onClick = { showSearchMenu = true }) {
                                                    Icon(
                                                        imageVector = Icons.Default.MoreVert,
                                                        contentDescription = "Options",
                                                        tint = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color(0xFF3A3A3C),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                androidx.compose.material3.MaterialTheme(
                                                    colorScheme = androidx.compose.material3.MaterialTheme.colorScheme.copy(
                                                        surface = if (isDarkMode) (if (isPureBlack) Color(0xFF141416) else Color(0xFF1E1E24)) else Color(0xFFFFFFFF)
                                                    ),
                                                    shapes = androidx.compose.material3.MaterialTheme.shapes.copy(
                                                        extraSmall = RoundedCornerShape(12.dp)
                                                    )
                                                ) {
                                                    DropdownMenu(
                                                        expanded = showSearchMenu,
                                                        onDismissRequest = { showSearchMenu = false }
                                                    ) {
                                                        DropdownMenuItem(
                                                            text = { Text("Play Next", color = if (isDarkMode) Color.White else Color(0xFF1D1D1F)) },
                                                            onClick = {
                                                                showSearchMenu = false
                                                                viewModel.playNext(track)
                                                                android.widget.Toast.makeText(context, "Playing next: \"${track.title}\"", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Add to Queue", color = if (isDarkMode) Color.White else Color(0xFF1D1D1F)) },
                                                            onClick = {
                                                                showSearchMenu = false
                                                                viewModel.addToQueue(track)
                                                                android.widget.Toast.makeText(context, "Added to queue: \"${track.title}\"", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Add to Playlist", color = if (isDarkMode) Color.White else Color(0xFF1D1D1F)) },
                                                            onClick = {
                                                                showSearchMenu = false
                                                                selectedTrackForPlaylist = track
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Overlay Playlist Detail
            if (selectedPlaylist != null) {
                PlaylistDetailScreen(
                    playlist = selectedPlaylist!!,
                    viewModel = viewModel,
                    onBack = { selectedPlaylist = null },
                    onNavigateToPlayer = navigateToPlayerFromLocalPlaylist
                )
            }

            if (selectedMuesoPlaylist != null) {
                val playlist = onlinePlaylists.firstOrNull { it.id == selectedMuesoPlaylist?.id } ?: selectedMuesoPlaylist!!
                val playlistTracksFlow = remember(playlist.id) { viewModel.getOnlinePlaylistTracks(playlist.id) }
                val playlistTracks by playlistTracksFlow.collectAsState(initial = emptyList())
                val playlistAddedDates by remember(playlist.id) { viewModel.getOnlinePlaylistTrackAddedDates(playlist.id) }
                    .collectAsState(initial = emptyMap())
                OnlinePlaylistDetailScreen(
                    title = playlist.name,
                    subtitle = "${playlistTracks.size} tracks",
                    description = playlist.description,
                    gradientColors = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
                    tracks = playlistTracks,
                    isLoading = false,
                    isCustomUserPlaylist = true,
                    isDarkMode = isDarkMode,
                    viewModel = viewModel,
                    spotifyUrl = playlist.spotifyUrl,
                    artworkUrl = playlist.artworkUrl,
                    onDownloadTrack = { track -> viewModel.downloadOnlineTrack(context, track) },
                    onRenamePlaylist = { viewModel.renameOnlinePlaylist(playlist.id, it) },
                    onDeletePlaylist = {
                        viewModel.deleteOnlinePlaylist(playlist.id)
                        selectedMuesoPlaylist = null
                    },
                    onBackClick = { selectedMuesoPlaylist = null },
                    onPlayAllClick = {
                        if (playlistTracks.isNotEmpty()) {
                            if (viewModel.playOnlinePlaylist(playlistTracks, 0)) {
                                navigateToPlayerFromMuesoPlaylist()
                            }
                        }
                    },
                    onShuffleClick = {
                        if (playlistTracks.isNotEmpty()) {
                            if (viewModel.playOnlineShuffle(playlistTracks)) {
                                navigateToPlayerFromMuesoPlaylist()
                            }
                        }
                    },
                    onTrackClick = { index ->
                        if (viewModel.playOnlinePlaylist(playlistTracks, index)) {
                            navigateToPlayerFromMuesoPlaylist()
                        }
                    },
                    onRemoveTrack = { track -> viewModel.removeTrackFromOnlinePlaylist(playlist.id, track.id) },
                    onMoveTrack = { fromIndex, toIndex -> viewModel.moveTrackInOnlinePlaylist(playlist.id, fromIndex, toIndex) },
                    sortKey = "mueso_${playlist.id}",
                    addedDates = playlistAddedDates,
                    onPlayTracks = { shown, index ->
                        if (viewModel.playOnlinePlaylist(shown, index)) {
                            navigateToPlayerFromMuesoPlaylist()
                        }
                    }
                )
            }

            // Overlay Artist Detail Screen
            androidx.compose.animation.AnimatedVisibility(
                visible = selectedArtistPage != null,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(tween(250)),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut(tween(200)),
                modifier = Modifier.fillMaxSize()
            ) {
                ArtistDetailScreen(
                    artistPage = selectedArtistPage,
                    isLoading = isLoadingArtistPage,
                    isDarkMode = isDarkMode,
                    viewModel = viewModel,
                    onBackClick = { viewModel.closeArtist() },
                    onArtistClick = { artist ->
                        viewModel.openArtist(artist.id, artist.name, artist.thumbnailUrl)
                    },
                    onPlaylistClick = { pl ->
                        viewModel.setSelectedOnlinePlaylist(pl)
                    },
                    onNavigateToPlayer = {
                        viewModel.closeArtist()
                        navigateToPlayer()
                    }
                )
            }

            // Overlay Selected Online Playlist / Album Details
            androidx.compose.animation.AnimatedVisibility(
                visible = selectedOnlinePlaylist != null,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(tween(250)),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut(tween(200)),
                modifier = Modifier.fillMaxSize()
            ) {
                if (selectedOnlinePlaylist != null) {
                    val pl = selectedOnlinePlaylist!!
                    var playlistTracks by remember(pl.id) { mutableStateOf<List<com.akshay.musicplayer.domain.models.TrackEntity>>(emptyList()) }
                    var isLoadingTracks by remember(pl.id) { mutableStateOf(true) }

                    LaunchedEffect(pl.id) {
                        isLoadingTracks = true
                        val query = if (pl.id == "LM") "browse:LM" else "browse:${pl.id}"
                        val fetched = viewModel.getCuratedPlaylistTracks(query)
                        playlistTracks = fetched
                        val trueDesc = viewModel.fetchPlaylistDescription(pl.id) ?: viewModel.getPlaylistDescription(pl.id)
                        if (!trueDesc.isNullOrBlank() && trueDesc != pl.description) {
                            viewModel.setSelectedOnlinePlaylist(pl.copy(description = trueDesc))
                        }
                        isLoadingTracks = false
                    }

                    OnlinePlaylistDetailScreen(
                        title = pl.title,
                        subtitle = pl.subtitle,
                        description = pl.description,
                        gradientColors = pl.gradientColors.map { Color(it) },
                        tracks = playlistTracks,
                        isLoading = isLoadingTracks,
                        isCustomUserPlaylist = pl.isYouTubeUserPlaylist,
                        isDarkMode = isDarkMode,
                        viewModel = viewModel,
                        onDownloadTrack = { track -> viewModel.downloadOnlineTrack(context, track) },
                        onBackClick = { viewModel.closeSelectedOnlinePlaylist() },
                        onPlayAllClick = {
                            if (playlistTracks.isNotEmpty()) {
                                if (viewModel.playOnlinePlaylist(playlistTracks, 0, pl)) {
                                    navigateToPlayerFromOnlinePlaylist(pl)
                                }
                            }
                        },
                        onShuffleClick = {
                            if (playlistTracks.isNotEmpty()) {
                                if (viewModel.playOnlinePlaylist(playlistTracks.shuffled(), 0, pl)) {
                                    navigateToPlayerFromOnlinePlaylist(pl)
                                }
                            }
                        },
                        onTrackClick = { idx ->
                            if (viewModel.playOnlinePlaylist(playlistTracks, idx, pl)) {
                                navigateToPlayerFromOnlinePlaylist(pl)
                            }
                        }
                    )
                }
            }

            val showBottomMiniPlayer = currentTrack != null && !isClassicPlayerExpanded && !isSearchActive
            run {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showBottomMiniPlayer,
                    enter = androidx.compose.animation.fadeIn(animationSpec = tween(180, delayMillis = 60)) +
                            androidx.compose.animation.scaleIn(initialScale = 0.88f, transformOrigin = TransformOrigin(0.5f, 0.5f)),
                    exit = androidx.compose.animation.fadeOut(animationSpec = tween(150)) +
                            androidx.compose.animation.scaleOut(targetScale = 0.88f, transformOrigin = TransformOrigin(0.5f, 0.5f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 10.dp)
                ) {
                    DockedMiniPlayer(
                        track = currentTrack,
                        playbackState = playbackState,
                        audioFormat = activeAudioFormat,
                        isDarkMode = isDarkMode,
                        onExpandClick = {
                            val onlinePl = selectedOnlinePlaylist
                            when {
                                selectedPlaylist != null -> navigateToPlayerFromLocalPlaylist()
                                selectedMuesoPlaylist != null -> navigateToPlayerFromMuesoPlaylist()
                                onlinePl != null -> navigateToPlayerFromOnlinePlaylist(onlinePl)
                                else -> {
                                    viewModel.closeArtist()
                                    navigateToPlayer()
                                }
                            }
                        },
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onPreviousClick = { viewModel.playPreviousTrack() },
                        onNextClick = { viewModel.playNextTrack() }
                    )
                }
            }

            // Full-screen Classic Player Expansion (Animates directly into/from floating mini player)
            androidx.compose.animation.AnimatedVisibility(
                visible = isClassicPlayerExpanded && currentTrack != null,
                modifier = Modifier.fillMaxSize(),
                enter = androidx.compose.animation.slideInVertically(
                    initialOffsetY = { (it * 0.80f).toInt() },
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = 0.85f,
                        stiffness = 400f
                    )
                ) + androidx.compose.animation.scaleIn(
                    initialScale = 0.40f,
                    transformOrigin = TransformOrigin(0.5f, 0.90f),
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = 0.85f,
                        stiffness = 400f
                    )
                ) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
                exit = androidx.compose.animation.slideOutVertically(
                    targetOffsetY = { (it * 0.80f).toInt() },
                    animationSpec = androidx.compose.animation.core.tween(260, easing = FastOutSlowInEasing)
                ) + androidx.compose.animation.scaleOut(
                    targetScale = 0.40f,
                    transformOrigin = TransformOrigin(0.5f, 0.90f),
                    animationSpec = androidx.compose.animation.core.tween(260, easing = FastOutSlowInEasing)
                ) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(220))
            ) {
                if (currentTrack != null) {
                    ClassicPlayerScreen(
                        track = currentTrack,
                        viewModel = viewModel,
                        onCollapseClick = returnFromPlayer,
                        onNavigateAway = {
                            clearPlayerReturn()
                            isClassicPlayerExpanded = false
                        }
                    )
                }
            }


            // Add to Playlist Bottom Sheet (YouTube, Online, Local)
            if (selectedTrackForPlaylist != null) {
                com.akshay.musicplayer.ui.components.AddToPlaylistBottomSheet(
                    track = selectedTrackForPlaylist!!,
                    viewModel = viewModel,
                    isDarkMode = isDarkMode,
                    onDismiss = { selectedTrackForPlaylist = null }
                )
            }

        }



        // Dedicated Full-Page Settings Screen Overlay with smooth slide-up animation
        androidx.compose.animation.AnimatedVisibility(
            visible = showSettingsScreen,
            enter = androidx.compose.animation.slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
            ) + androidx.compose.animation.fadeIn(tween(250)),
            exit = androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
            ) + androidx.compose.animation.fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = { showSettingsScreen = false }
            )
        }


        // Floating skip-countdown bubble (red while counting down, green once skipping is allowed)
        // plus a second bubble for the fast-forward wait. Both hide 3s after turning green.
        val skipCountdown by viewModel.skipCountdown.collectAsState()
        val forwardCountdown by viewModel.forwardCountdown.collectAsState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 72.dp, end = 16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                com.akshay.musicplayer.ui.components.SkipCountdownBubble(countdown = skipCountdown)
                com.akshay.musicplayer.ui.components.SkipCountdownBubble(
                    countdown = forwardCountdown,
                    readyDescription = "Fast-forward allowed"
                )
            }
        }

        // First-Launch Google Drive Backup Onboarding Dialog
        if (showOnboardingDialog) {
            com.akshay.musicplayer.ui.components.GoogleBackupOnboardingDialog(
                isDarkMode = isDarkMode,
                onSignInClick = {
                    showOnboardingDialog = false
                    val repo = com.akshay.musicplayer.data.backup.GoogleDriveBackupRepository(context)
                    googleSignInLauncher.launch(repo.getSignInIntent(context))
                },
                onSkipClick = {
                    showOnboardingDialog = false
                    sharedPreferences.edit().putBoolean("has_seen_google_onboarding", true).apply()
                }
            )
        }
    }
}

private enum class BrowsePage { Library, Offline, Explore }

@Composable
private fun SearchBar(
    searchQuery: String,
    isDarkMode: Boolean,
    onClose: () -> Unit,
    onQueryChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val textColor = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = textColor.copy(alpha = 0.6f)
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(60.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(textColor.copy(alpha = 0.08f))
                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = textSub)
            BasicTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                textStyle = TextStyle(color = textColor, fontSize = 16.sp),
                singleLine = true,
                cursorBrush = SolidColor(AccentOrange),
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box {
                        if (searchQuery.isEmpty()) Text("Search songs…", color = textSub, fontSize = 16.sp)
                        innerTextField()
                    }
                }
            )
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search", tint = textSub)
                }
            }
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close search", tint = textColor)
        }
    }
}
