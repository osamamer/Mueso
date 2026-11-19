package com.akshay.musicplayer.ui.components

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.akshay.musicplayer.data.VersionVariant
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.theme.LocalAccentColor
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Hosts the version picker for whichever track [PlayerViewModel.openVersionPicker] was called with. */
@Composable
fun VersionPickerHost(viewModel: PlayerViewModel, isDarkMode: Boolean) {
    val track by viewModel.versionPickerTrack.collectAsState()
    track?.let { current ->
        VersionPickerBottomSheet(
            track = current,
            viewModel = viewModel,
            isDarkMode = isDarkMode,
            onDismiss = { viewModel.closeVersionPicker() }
        )
    }
}

private data class VersionResults(val loading: Boolean, val items: List<TrackEntity>)

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionPickerBottomSheet(
    track: TrackEntity,
    viewModel: PlayerViewModel,
    isDarkMode: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = LocalAccentColor.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetBg = if (isDarkMode) Color(0xFF14141E) else Color(0xFFF9F9FB)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF111115)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF707078)
    val borderColor = if (isDarkMode) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.10f)

    var variant by remember { mutableStateOf(VersionVariant.ORIGINAL) }
    var results by remember { mutableStateOf(VersionResults(loading = true, items = emptyList())) }
    var selected by remember { mutableStateOf<TrackEntity?>(null) }
    var rememberChoice by remember { mutableStateOf(true) }
    var preferredId by remember { mutableStateOf(viewModel.preferredVersionId(track)) }
    val playingNowId = remember(track.id) { viewModel.currentVersionVideoId(track) }

    // Preview state
    val previewPlayer = remember { buildPreviewPlayer(context) }
    var previewId by remember { mutableStateOf<String?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var previewProgress by remember { mutableFloatStateOf(0f) }
    val failedPreviews = remember { mutableStateListOf<String>() }
    var previewJob by remember { mutableStateOf<Job?>(null) }
    var confirmed by remember { mutableStateOf(false) }

    fun stopPreview() {
        previewJob?.cancel()
        previewPlayer.stop()
        previewPlayer.clearMediaItems()
        previewId = null
        previewLoading = false
        previewProgress = 0f
    }

    fun togglePreview(videoId: String) {
        if (previewId == videoId) {
            stopPreview()
            return
        }
        stopPreview()
        viewModel.pauseForVersionPreview()
        previewId = videoId
        previewLoading = true
        failedPreviews.remove(videoId)
        previewJob = scope.launch {
            val url = viewModel.resolveVersionPreviewUrl(videoId)
            if (previewId != videoId) return@launch
            if (url == null) {
                failedPreviews.add(videoId)
                stopPreview()
                return@launch
            }
            previewPlayer.setMediaItem(MediaItem.fromUri(url))
            previewPlayer.prepare()
            previewPlayer.play()
        }
    }

    DisposableEffect(previewPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> previewLoading = false
                    Player.STATE_ENDED -> stopPreview()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                previewId?.let { failedPreviews.add(it) }
                stopPreview()
            }
        }
        previewPlayer.addListener(listener)
        onDispose {
            previewPlayer.removeListener(listener)
            previewPlayer.release()
            viewModel.endVersionPreview(resumeMain = !confirmed)
        }
    }

    LaunchedEffect(previewId, previewLoading) {
        while (previewId != null && !previewLoading) {
            val duration = previewPlayer.duration
            previewProgress = if (duration > 0) previewPlayer.currentPosition.toFloat() / duration else 0f
            delay(250)
        }
    }

    LaunchedEffect(track.id, variant) {
        results = VersionResults(loading = true, items = emptyList())
        results = VersionResults(loading = false, items = viewModel.findTrackVersions(track, variant))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text("Choose version", color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${track.title} · ${track.artist}",
                    color = textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Tap a cover to preview, then pick the one you want.",
                    color = textSecondary.copy(alpha = 0.75f),
                    fontSize = 12.sp
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(VersionVariant.entries) { option ->
                    FilterChip(
                        selected = option == variant,
                        onClick = {
                            if (option != variant) {
                                variant = option
                                selected = null
                                stopPreview()
                            }
                        },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = textSecondary,
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedLabelColor = accent
                        )
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 420.dp)) {
                when {
                    results.loading -> CircularProgressIndicator(
                        color = accent,
                        modifier = Modifier.align(Alignment.Center).size(32.dp)
                    )
                    results.items.isEmpty() -> Text(
                        "No versions found.",
                        color = textSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(results.items, key = { viewModel.versionVideoId(it) }) { candidate ->
                            val videoId = viewModel.versionVideoId(candidate)
                            val isSelected = selected?.let { viewModel.versionVideoId(it) } == videoId
                            val isPreviewing = previewId == videoId
                            VersionRow(
                                candidate = candidate,
                                isSelected = isSelected,
                                isPreviewing = isPreviewing,
                                isPreviewLoading = isPreviewing && previewLoading,
                                previewFailed = videoId in failedPreviews,
                                progress = if (isPreviewing) previewProgress else 0f,
                                isYourPick = videoId == preferredId,
                                isPlayingNow = videoId == playingNowId,
                                accent = accent,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                borderColor = borderColor,
                                onPreview = { togglePreview(videoId) },
                                onSelect = { selected = candidate }
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = rememberChoice,
                    onCheckedChange = { rememberChoice = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = accent)
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    "Always play this version of the song",
                    color = textPrimary.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (preferredId != null) {
                    TextButton(onClick = {
                        viewModel.forgetPreferredVersion(track)
                        preferredId = null
                    }) {
                        Text("Forget my pick", color = textSecondary)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = textSecondary)
                }
                Button(
                    enabled = selected != null,
                    onClick = {
                        val choice = selected ?: return@Button
                        confirmed = true
                        stopPreview()
                        viewModel.applyTrackVersion(track, choice, rememberChoice)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)
                ) {
                    Text("Play", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun VersionRow(
    candidate: TrackEntity,
    isSelected: Boolean,
    isPreviewing: Boolean,
    isPreviewLoading: Boolean,
    previewFailed: Boolean,
    progress: Float,
    isYourPick: Boolean,
    isPlayingNow: Boolean,
    accent: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    onPreview: () -> Unit,
    onSelect: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) accent.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.dp, if (isSelected) accent else borderColor, shape)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(borderColor)
                    .clickable(onClick = onPreview),
                contentAlignment = Alignment.Center
            ) {
                if (!candidate.artworkUrl.isNullOrBlank()) {
                    SmartArtworkImage(
                        artworkUrl = candidate.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        thumbnailQuality = "Low (Fast)",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Album, null, tint = textSecondary, modifier = Modifier.size(22.dp))
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            when {
                                previewFailed -> Color(0x99D32F2F)
                                else -> Color.Black.copy(alpha = if (isPreviewing) 0.55f else 0.30f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isPreviewLoading -> CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        previewFailed -> Icon(Icons.Default.ErrorOutline, "Preview failed", tint = Color.White, modifier = Modifier.size(20.dp))
                        isPreviewing -> Icon(Icons.Default.Stop, "Stop preview", tint = Color.White, modifier = Modifier.size(20.dp))
                        else -> Icon(Icons.Default.PlayArrow, "Preview", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSelect)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        candidate.title,
                        color = textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val meta = buildList {
                            if (candidate.artist.isNotBlank()) add(candidate.artist)
                            if (candidate.duration > 0) add(formatVersionDuration(candidate.duration))
                            add(if (candidate.isVideo) "Video" else "Song")
                        }.joinToString(" · ")
                        Text(
                            meta,
                            color = textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isYourPick) {
                            Text("Your pick", color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        } else if (isPlayingNow) {
                            Text("Current", color = accent.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (isSelected) {
                    Icon(Icons.Default.Check, null, tint = accent, modifier = Modifier.padding(start = 6.dp).size(20.dp))
                }
            }
        }
        if (isPreviewing && progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(accent)
            )
        }
    }
}

private fun formatVersionDuration(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

/** Separate little player so previewing never touches the main queue. Headers mirror the main player's for googlevideo. */
@OptIn(UnstableApi::class)
private fun buildPreviewPlayer(context: Context): ExoPlayer {
    val client = okhttp3.OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            val builder = request.newBuilder()
            if (url.contains("googlevideo.com")) {
                val ua = when {
                    url.contains("c=ANDROID") -> "com.google.android.youtube/21.03.36(Linux; U; Android 16; en_US; SM-S908E Build/TP1A.220624.014) gzip"
                    url.contains("c=TVHTML5") -> "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"
                    url.contains("c=WEB") -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    else -> "com.google.ios.youtube/20.11.6 (iPhone10,4; U; CPU iOS 16_7_7 like Mac OS X)"
                }
                builder.header("User-Agent", ua)
                if (url.contains("c=WEB") || url.contains("c=TVHTML5")) {
                    builder.header("Origin", "https://music.youtube.com")
                    builder.header("Referer", "https://music.youtube.com/")
                }
                if (request.header("Range") == null) builder.header("Range", "bytes=0-")
            }
            chain.proceed(builder.build())
        }
        .build()
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(client)))
        .build()
}
