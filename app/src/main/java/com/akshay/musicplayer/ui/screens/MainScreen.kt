@file:Suppress("DEPRECATION")
package com.akshay.musicplayer.ui.screens

import com.akshay.musicplayer.ui.components.SwipeToQueue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import com.akshay.musicplayer.ui.screens.ClassicPlayerScreen
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

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
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
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

    val playerLayoutStyle by viewModel.playerLayoutStyle.collectAsState()
    var showLibrary by remember { mutableStateOf(false) }
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

    val navigateToPlayer: () -> Unit = {
        playlistToReturnTo = null
        muesoPlaylistToReturnTo = null
        onlinePlaylistToReturnTo = null
        if (playerLayoutStyle == "classic") {
            isClassicPlayerExpanded = true
        } else {
            coroutineScope.launch { pagerState.animateScrollToPage(1) }
        }
    }

    val navigateToPlayerFromLocalPlaylist: () -> Unit = {
        playlistToReturnTo = selectedPlaylist
        selectedPlaylist = null
        if (playerLayoutStyle == "classic") {
            isClassicPlayerExpanded = true
        } else {
            coroutineScope.launch { pagerState.animateScrollToPage(1) }
        }
    }

    val navigateToPlayerFromMuesoPlaylist: () -> Unit = {
        muesoPlaylistToReturnTo = selectedMuesoPlaylist
        selectedMuesoPlaylist = null
        if (playerLayoutStyle == "classic") {
            isClassicPlayerExpanded = true
        } else {
            coroutineScope.launch { pagerState.animateScrollToPage(1) }
        }
    }

    val navigateToPlayerFromOnlinePlaylist: (com.akshay.musicplayer.ui.screens.SelectedOnlinePlaylist) -> Unit = { playlist ->
        onlinePlaylistToReturnTo = playlist
        viewModel.closeSelectedOnlinePlaylist()
        if (playerLayoutStyle == "classic") {
            isClassicPlayerExpanded = true
        } else {
            coroutineScope.launch { pagerState.animateScrollToPage(1) }
        }
    }

    androidx.activity.compose.BackHandler(enabled = showLibrary && playerLayoutStyle == "classic") {
        showLibrary = false
    }

    androidx.activity.compose.BackHandler(enabled = isClassicPlayerExpanded) {
        isClassicPlayerExpanded = false
    }

    val canReturnToPlaylist =
        (playerLayoutStyle == "classic" && (playlistToReturnTo != null || muesoPlaylistToReturnTo != null || onlinePlaylistToReturnTo != null)) ||
            (playerLayoutStyle != "classic" && pagerState.currentPage == 1 && (playlistToReturnTo != null || muesoPlaylistToReturnTo != null || onlinePlaylistToReturnTo != null))
    androidx.activity.compose.BackHandler(enabled = canReturnToPlaylist) {
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
        if (playerLayoutStyle != "classic") {
            coroutineScope.launch { pagerState.animateScrollToPage(0) }
        }
    }

    androidx.activity.compose.BackHandler(enabled = selectedArtistPage != null) {
        viewModel.closeArtist()
    }

    androidx.activity.compose.BackHandler(enabled = selectedOnlinePlaylist != null) {
        viewModel.closeSelectedOnlinePlaylist()
    }

    androidx.activity.compose.BackHandler(enabled = selectedMuesoPlaylist != null) {
        selectedMuesoPlaylist = null
    }

    androidx.activity.compose.BackHandler(enabled = showSettingsScreen) {
        showSettingsScreen = false
    }

    androidx.activity.compose.BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        viewModel.setSearchQuery("")
    }

    androidx.activity.compose.BackHandler(enabled = playerLayoutStyle != "classic" && pagerState.currentPage != 1 && selectedPlaylist == null && selectedMuesoPlaylist == null && selectedArtistPage == null && selectedOnlinePlaylist == null && !isSearchActive && !showSettingsScreen) {
        coroutineScope.launch { pagerState.animateScrollToPage(1) }
    }

    androidx.activity.compose.BackHandler(enabled = (playerLayoutStyle == "classic" || pagerState.currentPage == 1) && !isClassicPlayerExpanded && selectedPlaylist == null && selectedMuesoPlaylist == null && selectedArtistPage == null && selectedOnlinePlaylist == null && !isSearchActive && !showSettingsScreen && !(showLibrary && playerLayoutStyle == "classic") && hasUnbackedUpChanges && googleAccount != null) {
        viewModel.performDriveBackup(context)
        (context as? android.app.Activity)?.moveTaskToBack(true)
    }

    var isOnlineDetailActive by remember { mutableStateOf(false) }

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
            val isClassic = playerLayoutStyle == "classic"
            val isOnlineActive = if (isClassic) false else pagerState.currentPage == 2

            if (isClassic) {
                // ─── CLASSIC: Spotify-style home, library behind the profile picture ───
                val googlePhotoUrl = googleAccount?.photoUrl?.toString()
                HomeScreen(
                    viewModel = viewModel,
                    googlePhotoUrl = googlePhotoUrl,
                    onOpenLibrary = { showLibrary = true },
                    onOpenSettings = { showSettingsScreen = true },
                    onSearchClick = { isSearchActive = true },
                    onPlaylistClick = { selectedPlaylist = it },
                    onOnlinePlaylistClick = { selectedMuesoPlaylist = it }
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = showLibrary,
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
                        onBack = { showLibrary = false },
                        onOpenSettings = { showSettingsScreen = true },
                        onPlaylistClick = { selectedPlaylist = it },
                        onOnlinePlaylistClick = { selectedMuesoPlaylist = it }
                    )
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
                            onCollapseClick = { isClassicPlayerExpanded = false }
                        )
                    }
                }

            } else {
                // ─── REELS VERTICAL SWIPER & 3-PAGE PAGER ARCHITECTURE ───
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !isSearchActive
                ) { page ->
                    when (page) {
                        0 -> OfflineLibraryScreen(
                            viewModel = viewModel,
                            onNavigateToPlayer = navigateToPlayer,
                            onPlaylistClick = { selectedPlaylist = it },
                            onOnlinePlaylistClick = { selectedMuesoPlaylist = it }
                        )
                        1 -> PlayerScreen(viewModel = viewModel)
                        2 -> OnlinePlaylistsScreen(
                            viewModel = viewModel,
                            onNavigateToPlayer = navigateToPlayer,
                            onDetailVisibilityChanged = { isOnlineDetailActive = it }
                        )
                    }
                }
            }

            val activeQueue by viewModel.activeQueue.collectAsState()
            val hasTrackPlaying = activeQueue.isNotEmpty() || currentTrack != null

            // Top Navigation Bar with integrated search (hidden when viewing a playlist detail on active page, settings, or full player)
            val shouldHideTopBar = if (isClassic) {
                !isSearchActive || (selectedPlaylist != null) || (selectedMuesoPlaylist != null) || (selectedArtistPage != null) || (selectedOnlinePlaylist != null) || isClassicPlayerExpanded
            } else {
                (selectedPlaylist != null) || (selectedMuesoPlaylist != null) || (selectedArtistPage != null) || (selectedOnlinePlaylist != null) || (pagerState.currentPage == 2 && isOnlineDetailActive)
            }

            if (isClassic && !shouldHideTopBar) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isDarkMode) BgDark else Color.White)
                )
            }

            if (!shouldHideTopBar) {
                TopNavigationBarWithSearch(
                    isOnlineActive = isOnlineActive,
                    isSearchActive = isSearchActive,
                    searchQuery = searchQuery,
                    isDarkMode = isDarkMode,
                    currentPage = if (isClassic) 0 else pagerState.currentPage,
                    hasTrackPlaying = hasTrackPlaying,
                    onSearchClick = { isSearchActive = true },
                    onSearchClose = {
                        isSearchActive = false
                        viewModel.setSearchQuery("")
                    },
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    onSettingsClick = { showSettingsScreen = true },
                    googleAccount = googleAccount,
                    modifier = Modifier.align(Alignment.TopCenter)
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

            // Scrim when search is active
            if (isSearchActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = if (searchQuery.isNotEmpty()) 500.dp else 60.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) {
                            isSearchActive = false
                            viewModel.setSearchQuery("")
                        }
                )
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

            // Keep the mini player pinned to the bottom everywhere: on top of playlist / artist pages
            // (classic layout, where the bottom bar is covered), and on every page of the swipe layout
            // except the player page itself.
            val isDetailOverlayOpen = selectedPlaylist != null || selectedMuesoPlaylist != null ||
                selectedOnlinePlaylist != null || selectedArtistPage != null
            val showBottomMiniPlayer = currentTrack != null && if (isClassic) {
                !isClassicPlayerExpanded
            } else {
                isDetailOverlayOpen || pagerState.currentPage != 1
            }
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

// ─── Top Navigation Bar ──────────────────────────────────────

@Composable
fun TopNavigationBarWithSearch(
    isOnlineActive: Boolean,
    isSearchActive: Boolean,
    searchQuery: String,
    isDarkMode: Boolean = true,
    currentPage: Int = 1,
    hasTrackPlaying: Boolean = false,
    onSearchClick: () -> Unit,
    onSearchClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    googleAccount: com.google.android.gms.auth.api.signin.GoogleSignInAccount? = null,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val isPage1 = currentPage == 1
    val isPage1NoTrackLightMode = isPage1 && !isDarkMode && !hasTrackPlaying

    val textColor = when {
        isPage1NoTrackLightMode -> Color(0xFF1D1D1F)
        isPage1 -> Color.White
        isDarkMode -> Color.White
        else -> Color(0xFF1D1D1F)
    }
    val textSub = when {
        isPage1NoTrackLightMode -> Color.Black.copy(alpha = 0.5f)
        isPage1 -> Color.White.copy(alpha = 0.6f)
        isDarkMode -> Color.White.copy(alpha = 0.4f)
        else -> Color.Black.copy(alpha = 0.4f)
    }
    val searchBg = when {
        isPage1NoTrackLightMode -> Color.Black.copy(alpha = 0.05f)
        isPage1 -> Color.White.copy(alpha = 0.2f)
        isDarkMode -> Color.White.copy(alpha = 0.1f)
        else -> Color.Black.copy(alpha = 0.05f)
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val topBarBg = if (isDarkMode) BgDark else Color.White

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isPage1) Modifier
                else Modifier.background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            topBarBg,
                            topBarBg.copy(alpha = 0.8f),
                            Color.Transparent
                        )
                    )
                )
            )
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Use a Box with weight(1f) so the layout size stays fixed during animation
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            // Left: App Symbol & Mueso Name (hide when search is active)
            androidx.compose.animation.AnimatedVisibility(
                visible = !isSearchActive,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSettingsClick() }
                        .padding(4.dp)
                ) {
                    if (googleAccount?.photoUrl != null) {
                        coil.compose.AsyncImage(
                            model = googleAccount.photoUrl,
                            contentDescription = "Settings & Profile",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = if (isPage1NoTrackLightMode) Color(0xFF1D1D1F) else AccentOrange,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        text = "Mueso",
                        color = textColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        letterSpacing = (-0.5).sp
                    )
                }
            }

            // Right: Search bar
            androidx.compose.animation.AnimatedVisibility(
                visible = isSearchActive,
                enter = expandHorizontally(
                    animationSpec = tween(300),
                    expandFrom = Alignment.End
                ) + fadeIn(tween(200)),
                exit = shrinkHorizontally(
                    animationSpec = tween(250),
                    shrinkTowards = Alignment.End
                ) + fadeOut(tween(150)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(searchBg)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = textSub,
                    modifier = Modifier.size(20.dp)
                )
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    textStyle = TextStyle(
                        color = textColor,
                        fontSize = 16.sp
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(AccentOrange),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        Box {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Search songs...",
                                    color = textSub,
                                    fontSize = 16.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = textSub,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onQueryChange("") }
                    )
                }
            }
        } // End AnimatedVisibility
        } // End Box

        // Action buttons: Settings & Search/Close toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Empty space for layout balance if needed, or just Search

            IconButton(
                onClick = {
                    if (isSearchActive) onSearchClose() else onSearchClick()
                }
            ) {
                Icon(
                    imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "Search",
                    tint = textColor
                )
            }
        }
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(title, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), style = MaterialTheme.typography.headlineMedium)
    }
}
