package com.akshay.musicplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.data.profiles.LightProfiles
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.components.DockedMiniPlayer
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.Flow

private data class LightPlaylist(val key: String, val name: String, val tracks: () -> Flow<List<TrackEntity>>)

/**
 * The whole UI of a light profile app: its chosen playlists, their songs, and the player.
 * There is deliberately no search, discovery, library or settings.
 */
@Composable
fun LightProfileScreen(viewModel: PlayerViewModel, isSyncing: Boolean, syncFailed: Boolean) {
    val localPlaylists by viewModel.playlists.collectAsState()
    val onlinePlaylists by viewModel.onlinePlaylists.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val activeAudioFormat by viewModel.activeAudioFormat.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    var openKey by remember { mutableStateOf<String?>(null) }
    var isPlayerExpanded by remember { mutableStateOf(false) }

    val playlists = localPlaylists.map { playlist ->
        LightPlaylist("local:${playlist.id}", playlist.name) { viewModel.getPlaylistTracks(playlist.id) }
    } + onlinePlaylists.map { playlist ->
        LightPlaylist("online:${playlist.id}", playlist.name) { viewModel.getOnlinePlaylistTracks(playlist.id) }
    }
    val openPlaylist = playlists.firstOrNull { it.key == openKey }
    val currentTrack = remember(playbackState.currentTrackId, viewModel.getQueueTracks()) {
        viewModel.getQueueTracks().firstOrNull { it.id == playbackState.currentTrackId }
    }

    BackHandler(enabled = openPlaylist != null && !isPlayerExpanded) { openKey = null }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (openPlaylist == null) {
            PlaylistList(
                playlists = playlists,
                isSyncing = isSyncing,
                syncFailed = syncFailed,
                onOpen = { openKey = it.key }
            )
        } else {
            PlaylistTracks(
                playlist = openPlaylist,
                onBack = { openKey = null },
                onPlay = { tracks, index -> viewModel.playOnlinePlaylist(tracks, index) }
            )
        }

        if (currentTrack != null && !isPlayerExpanded) {
            DockedMiniPlayer(
                track = currentTrack,
                playbackState = playbackState,
                audioFormat = activeAudioFormat,
                isDarkMode = isDarkMode,
                onExpandClick = { isPlayerExpanded = true },
                onPlayPauseClick = { viewModel.togglePlayPause() },
                onPreviousClick = { viewModel.playPreviousTrack() },
                onNextClick = { viewModel.playNextTrack() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp)
            )
        }

        if (currentTrack != null && isPlayerExpanded) {
            ClassicPlayerScreen(
                track = currentTrack,
                viewModel = viewModel,
                onCollapseClick = { isPlayerExpanded = false }
            )
        }
    }
}

@Composable
private fun PlaylistList(
    playlists: List<LightPlaylist>,
    isSyncing: Boolean,
    syncFailed: Boolean,
    onOpen: (LightPlaylist) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 240.dp),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                LightProfiles.displayName(LightProfiles.current.orEmpty()),
                color = colors.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            val note = when {
                isSyncing -> "Fetching your playlists from Nuclear…"
                syncFailed -> "Couldn't reach the full Nuclear app. Showing the playlists from last time."
                playlists.isEmpty() -> "No playlists yet. Choose some in Nuclear → Settings → Light profiles."
                else -> "Only the playlists chosen for this profile."
            }
            Text(note, color = colors.onBackground.copy(alpha = 0.6f), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
        }
        items(playlists, key = { it.key }) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { onOpen(playlist) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = colors.primary)
                Text(
                    playlist.name,
                    color = colors.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PlaylistTracks(
    playlist: LightPlaylist,
    onBack: () -> Unit,
    onPlay: (List<TrackEntity>, Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val tracksFlow = remember(playlist.key) { playlist.tracks() }
    val tracks by tracksFlow.collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 120.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.onBackground)
                }
                Text(
                    playlist.name,
                    color = colors.onBackground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(onClick = { onPlay(tracks, 0) }, enabled = tracks.isNotEmpty()) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Play")
                }
                OutlinedButton(onClick = { onPlay(tracks.shuffled(), 0) }, enabled = tracks.isNotEmpty()) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Shuffle")
                }
            }
        }
        itemsIndexed(tracks, key = { index, track -> "${track.id}-$index" }) { index, track ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onPlay(tracks, index) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(track.title, color = colors.onBackground, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    track.artist,
                    color = colors.onBackground.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
