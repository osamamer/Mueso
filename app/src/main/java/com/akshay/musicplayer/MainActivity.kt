package com.akshay.musicplayer

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.akshay.musicplayer.data.repository.TrackRepositoryImpl
import com.akshay.musicplayer.data.sources.LocalMediaStoreDataSource
import com.akshay.musicplayer.domain.usecase.GetLocalTracksUseCase
import com.akshay.musicplayer.media.player.ExoPlayerController
import com.akshay.musicplayer.data.profiles.LightProfileSync
import com.akshay.musicplayer.data.profiles.LightProfiles
import com.akshay.musicplayer.ui.screens.LightProfileScreen
import com.akshay.musicplayer.ui.screens.MainScreen
import com.akshay.musicplayer.ui.screens.SplashScreen
import com.akshay.musicplayer.ui.screens.WelcomeScreen
import com.akshay.musicplayer.ui.theme.LocalAccentColor
import com.akshay.musicplayer.ui.theme.MusicPlayerTheme
import com.akshay.musicplayer.ui.theme.updateLauncherIcon
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel
import com.akshay.musicplayer.util.PermissionHelper
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {

    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var mediaPlayerController: ExoPlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        AppContainer.initialize(applicationContext)
        com.akshay.musicplayer.data.remote.stream.OnlineStreamExtractor.init(applicationContext)

        // Setup ViewModel
        setupViewModel()

        try {
            val serviceIntent = android.content.Intent(this, com.akshay.musicplayer.media.service.MusicPlayerService::class.java)
            startService(serviceIntent)
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed to startService: ${e.message}")
        }

        handleNotificationIntent(intent)

        setContent {
            val isDarkMode by playerViewModel.isDarkMode.collectAsState()
            val themeMode by playerViewModel.themeMode.collectAsState()
            val usePureBlack by playerViewModel.usePureBlack.collectAsState()
            val accentColorId by playerViewModel.accentColorId.collectAsState()
            val fontScaleOption by playerViewModel.fontScaleOption.collectAsState()
            val cornerRadiusOption by playerViewModel.cornerRadiusOption.collectAsState()
            val lyricsFontSizeOption by playerViewModel.lyricsFontSizeOption.collectAsState()

            val showOnLockscreen by playerViewModel.showOnLockscreen.collectAsState()
            val highRefreshRate by playerViewModel.highRefreshRate.collectAsState()

            val systemInDark = androidx.compose.foundation.isSystemInDarkTheme()
            val isEffectiveDark = when (com.akshay.musicplayer.ui.theme.ThemeMode.fromId(themeMode)) {
                com.akshay.musicplayer.ui.theme.ThemeMode.SYSTEM -> systemInDark
                com.akshay.musicplayer.ui.theme.ThemeMode.DARK -> true
                com.akshay.musicplayer.ui.theme.ThemeMode.LIGHT -> false
            }

            val view = androidx.compose.ui.platform.LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as android.app.Activity).window
                    val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                    insetsController.isAppearanceLightStatusBars = !isEffectiveDark
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        insetsController.isAppearanceLightNavigationBars = !isEffectiveDark
                    }
                }
            }

            val pendingDeleteIntent by playerViewModel.pendingDeleteIntent.collectAsState()
            val pendingWriteIntent by playerViewModel.pendingWriteIntent.collectAsState()

            val deleteLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    android.widget.Toast.makeText(this, "Song deleted from device", android.widget.Toast.LENGTH_SHORT).show()
                }
                playerViewModel.clearPendingDeleteIntent()
            }

            val writeLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    android.widget.Toast.makeText(this, "Permission granted", android.widget.Toast.LENGTH_SHORT).show()
                }
                playerViewModel.clearPendingWriteIntent()
            }

            LaunchedEffect(pendingDeleteIntent) {
                pendingDeleteIntent?.let { sender ->
                    deleteLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(sender).build()
                    )
                }
            }

            LaunchedEffect(pendingWriteIntent) {
                pendingWriteIntent?.let { sender ->
                    writeLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(sender).build()
                    )
                }
            }

            LaunchedEffect(showOnLockscreen) {
                updateLockScreenDisplay(showOnLockscreen)
            }
            LaunchedEffect(highRefreshRate) {
                updateRefreshRate(highRefreshRate)
            }

            val context = androidx.compose.ui.platform.LocalContext.current
            val muesoPrefs = remember(context) { getSharedPreferences("mueso_prefs", android.content.Context.MODE_PRIVATE) }
            var hasCompletedOnboarding by remember {
                mutableStateOf(
                    muesoPrefs.getBoolean("has_completed_welcome_onboarding", false)
                )
            }

            MusicPlayerTheme(
                themeMode = themeMode,
                usePureBlack = usePureBlack,
                accentColorId = accentColorId,
                fontScaleOption = fontScaleOption,
                cornerRadiusOption = cornerRadiusOption,
                lyricsFontSizeOption = lyricsFontSizeOption
            ) {
                val accent = LocalAccentColor.current
                LaunchedEffect(accent) { updateLauncherIcon(applicationContext, accent) }
                var lastCrash by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(CrashReport.get(applicationContext))
                }
                lastCrash?.let { report ->
                    val dismissCrash = {
                        CrashReport.clear(applicationContext)
                        lastCrash = null
                    }
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = dismissCrash,
                        title = { androidx.compose.material3.Text("Mueso crashed last time") },
                        text = {
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                androidx.compose.material3.Text(
                                    report,
                                    fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
                                    modifier = androidx.compose.ui.Modifier
                                        .heightIn(max = androidx.compose.ui.unit.Dp(320f))
                                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                                )
                            }
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = dismissCrash) {
                                androidx.compose.material3.Text("Dismiss")
                            }
                        }
                    )
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                ) {
                    var showSplash by remember { mutableStateOf(!LightProfiles.isLight) }

                    if (LightProfiles.isLight) {
                        LightProfileContent()
                    } else if (showSplash) {
                        SplashScreen(
                            onAnimationFinished = {
                                showSplash = false
                                if (hasCompletedOnboarding) {
                                    playerViewModel.restoreLastPlaybackStateOrOffline(context)
                                }
                            }
                        )
                    } else {
                        AnimatedContent(
                            targetState = hasCompletedOnboarding,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(350, easing = FastOutSlowInEasing)) +
                                 slideInHorizontally(
                                     initialOffsetX = { fullWidth -> fullWidth / 4 },
                                     animationSpec = tween(350, easing = FastOutSlowInEasing)
                                 ))
                                 .togetherWith(
                                     fadeOut(animationSpec = tween(250, easing = FastOutSlowInEasing)) +
                                     slideOutHorizontally(
                                         targetOffsetX = { fullWidth -> -fullWidth / 4 },
                                         animationSpec = tween(250, easing = FastOutSlowInEasing)
                                     )
                                 )
                            },
                            label = "WelcomeToMainTransition"
                        ) { onboarded ->
                            if (!onboarded) {
                                WelcomeScreen(
                                    viewModel = playerViewModel,
                                    isDarkMode = isEffectiveDark,
                                    onContinue = {
                                        muesoPrefs.edit()
                                            .putBoolean("has_completed_welcome_onboarding", true)
                                            .putBoolean("has_prompted_battery_optimization", true)
                                            .putBoolean("has_seen_google_onboarding", true)
                                            .apply()
                                        hasCompletedOnboarding = true
                                        playerViewModel.restoreLastPlaybackStateOrOffline(context)
                                    }
                                )
                            } else {
                                MainScreen(viewModel = playerViewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun LightProfileContent() {
        var isSyncing by remember { mutableStateOf(true) }
        var syncFailed by remember { mutableStateOf(false) }
        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) playerViewModel.loadLocalTracks(forceReload = true)
        }
        LaunchedEffect(Unit) {
            val refresh = kotlinx.coroutines.withContext(Dispatchers.IO) { LightProfileSync.pull(applicationContext) }
            refresh.getOrNull()?.let(playerViewModel::setAccentColorId)
            syncFailed = refresh.isFailure
            isSyncing = false
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, audioPermission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) playerViewModel.loadLocalTracks(forceReload = true) else permissionLauncher.launch(audioPermission)
        }
        LightProfileScreen(viewModel = playerViewModel, isSyncing = isSyncing, syncFailed = syncFailed)
    }

    private fun setupViewModel() {
        val appContext = applicationContext
        val prefs = appContext.getSharedPreferences("mueso_prefs", android.content.Context.MODE_PRIVATE)
        val mediaStoreDataSource = LocalMediaStoreDataSource(
            context = appContext,
            ioDispatcher = Dispatchers.IO,
            getDownloadFolder = { prefs.getString("download_folder", "Music/Mueso") ?: "Music/Mueso" }
        )
        val trackRepository = TrackRepositoryImpl(mediaStoreDataSource)
        val getLocalTracksUseCase = GetLocalTracksUseCase(trackRepository)
        val db = com.akshay.musicplayer.data.db.AppDatabase.getDatabase(appContext)
        val playlistDao = db.playlistDao()
        val onlinePlaylistDao = db.onlinePlaylistDao()

        playerViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    val controller = ExoPlayerController.getInstance(appContext)
                    return PlayerViewModel(
                        getLocalTracksUseCase,
                        controller,
                        playlistDao,
                        onlinePlaylistDao,
                        prefs
                    ) as T
                }
            }
        ).get(PlayerViewModel::class.java)

        mediaPlayerController = playerViewModel.mediaPlayerController as ExoPlayerController
    }

    override fun onResume() {
        super.onResume()
        if (::playerViewModel.isInitialized) {
            playerViewModel.checkAndResumePendingInstall(this)
        }
    }

    private fun setupLockScreenDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun updateLockScreenDisplay(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(enabled)
            setTurnScreenOn(enabled)
        } else {
            @Suppress("DEPRECATION")
            if (enabled) {
                window.addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            } else {
                window.clearFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
        }
    }

    private fun updateRefreshRate(enabled: Boolean) {
        val displayManager = getSystemService(android.content.Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else displayManager?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val maxMode = display?.supportedModes?.maxByOrNull { it.refreshRate }
        val params = window.attributes
        params.preferredDisplayModeId = if (enabled && maxMode != null) maxMode.modeId else 0
        window.attributes = params
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: android.content.Intent?) {
        if (intent == null) return
        if (com.akshay.musicplayer.data.remote.SpotifyAuth.isCallback(intent.data)) {
            playerViewModel.handleSpotifyLoginCallback(this, intent.data!!)
            // Don't handle the same redirect again after a configuration change.
            intent.data = null
            return
        }
        when (intent.action) {
            com.akshay.musicplayer.media.notification.NotificationHelper.ACTION_OPEN_OFFLINE_LIBRARY -> {
                playerViewModel.setOfflineLibraryTab(1)
            }
            com.akshay.musicplayer.media.notification.NotificationHelper.ACTION_PLAY_DOWNLOADED -> {
                val filePath = intent.getStringExtra(com.akshay.musicplayer.media.notification.NotificationHelper.EXTRA_FILE_PATH)
                if (!filePath.isNullOrBlank()) {
                    playerViewModel.playLocalTrackByPath(filePath)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        playerViewModel.saveCurrentPlaybackPosition()
    }

    override fun onStop() {
        super.onStop()
        playerViewModel.saveCurrentPlaybackPosition()
    }

    override fun onDestroy() {
        playerViewModel.saveCurrentPlaybackPosition()
        super.onDestroy()
        if (isFinishing) {
            val isPlaying = mediaPlayerController.playbackState().value.isPlaying ||
                    com.akshay.musicplayer.media.service.MediaSessionBridge.isOnlinePlaying ||
                    com.akshay.musicplayer.media.service.MediaSessionBridge.isServiceRunning
            if (!isPlaying) {
                mediaPlayerController.release()
            }
        }
    }
}
