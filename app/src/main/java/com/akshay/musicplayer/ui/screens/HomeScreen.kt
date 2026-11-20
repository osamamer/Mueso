package com.akshay.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.akshay.musicplayer.data.db.OnlinePlaylistEntity
import com.akshay.musicplayer.data.db.PlaylistEntity
import com.akshay.musicplayer.ui.components.NowPlayingBars
import com.akshay.musicplayer.ui.components.PlaylistCollageArt
import com.akshay.musicplayer.ui.components.ProfileAvatar
import com.akshay.musicplayer.ui.components.rememberUserProfile
import com.akshay.musicplayer.ui.theme.LocalAccentColor
import com.akshay.musicplayer.ui.theme.LocalIsPureBlack
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel
import java.util.Calendar

/** A Mueso (online) or local playlist, shown together on the home page. */
private sealed interface HomePlaylist {
    val key: String
    val name: String
    val isPinned: Boolean
    val recency: Long
    val pinnedOrder: Int

    data class Mueso(val playlist: OnlinePlaylistEntity) : HomePlaylist {
        override val key get() = "mueso_${playlist.id}"
        override val name get() = playlist.name
        override val isPinned get() = playlist.isPinned
        override val recency get() = playlist.recentActivityAt
        override val pinnedOrder get() = playlist.sortOrder
    }

    data class Local(val playlist: PlaylistEntity) : HomePlaylist {
        override val key get() = "local_${playlist.id}"
        override val name get() = playlist.name
        override val isPinned get() = playlist.isPinned
        override val recency get() = playlist.recentActivityAt
        override val pinnedOrder get() = playlist.sortOrder
    }
}

private enum class HomeFilter(val label: String) { ALL("All"), MUESO("Nuclear"), LOCAL("Local") }

private fun greetingForNow(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

/**
 * Spotify-style home: profile + greeting, quick-access playlist grid,
 * search right below it, then a "Jump back in" carousel.
 */
@Composable
fun HomeScreen(
    viewModel: PlayerViewModel,
    googlePhotoUrl: String?,
    onOpenLibrary: () -> Unit,
    onOpenOffline: () -> Unit,
    onOpenExplore: () -> Unit,
    onOpenSettings: () -> Unit,
    onSearchClick: () -> Unit,
    onPlaylistClick: (PlaylistEntity) -> Unit,
    onOnlinePlaylistClick: (OnlinePlaylistEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isPureBlack = LocalIsPureBlack.current
    val accent = LocalAccentColor.current
    val playlists by viewModel.playlists.collectAsState()
    val onlinePlaylists by viewModel.onlinePlaylists.collectAsState()
    val profile = rememberUserProfile()

    val bg = if (isPureBlack) Color.Black else if (isDarkMode) MaterialTheme.colorScheme.background else Color.White
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF111115)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF6E6E73)
    val tileBg = if (isDarkMode) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f)

    var filter by rememberSaveable { mutableStateOf(HomeFilter.ALL) }
    val all: List<HomePlaylist> = remember(playlists, onlinePlaylists) {
        onlinePlaylists.map { HomePlaylist.Mueso(it) } + playlists.map { HomePlaylist.Local(it) }
    }
    val filtered = remember(all, filter) {
        when (filter) {
            HomeFilter.ALL -> all
            HomeFilter.MUESO -> all.filterIsInstance<HomePlaylist.Mueso>()
            HomeFilter.LOCAL -> all.filterIsInstance<HomePlaylist.Local>()
        }
    }
    // Quick-access grid: pinned first (in your pinned order), then most recently played/created
    val quickAccess = remember(filtered) {
        filtered.sortedWith(
            compareByDescending<HomePlaylist> { it.isPinned }
                .thenBy { if (it.isPinned) it.pinnedOrder else 0 }
                .thenByDescending { it.recency }
        ).take(8)
    }
    val jumpBackIn = remember(filtered) { filtered.sortedByDescending { it.recency }.take(12) }
    val playlistColumns = (LocalConfiguration.current.screenWidthDp / 200).coerceIn(2, 4)
    val showFilters = onlinePlaylists.isNotEmpty() && playlists.isNotEmpty()

    val open: (HomePlaylist) -> Unit = { item ->
        when (item) {
            is HomePlaylist.Mueso -> onOnlinePlaylistClick(item.playlist)
            is HomePlaylist.Local -> onPlaylistClick(item.playlist)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 150.dp)
    ) {
        // ── Header: profile, greeting, settings ──
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileAvatar(
                    profile = profile,
                    size = 38.dp,
                    googlePhotoUrl = googlePhotoUrl,
                    onClick = onOpenLibrary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = greetingForNow(),
                    color = textPrimary,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = textPrimary)
                }
            }
        }

        item(key = "browse") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    OutlinedButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Search")
                    }
                }
                item { OutlinedButton(onClick = onOpenLibrary) { Text("Library") } }
                item { OutlinedButton(onClick = onOpenOffline) { Text("Offline") } }
                item { OutlinedButton(onClick = onOpenExplore) { Text("Explore") } }
            }
        }

        if (showFilters) {
            item(key = "filters") {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HomeFilter.entries.forEach { f ->
                        val selected = f == filter
                        Text(
                            text = f.label,
                            color = if (selected) Color.White else textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) accent else tileBg)
                                .clickable { filter = f }
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // ── Quick-access playlist grid ──
        if (quickAccess.isEmpty()) {
            item(key = "empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tileBg)
                        .clickable(onClick = onOpenLibrary)
                        .padding(20.dp)
                ) {
                    Text("No playlists yet", color = textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Create or import a playlist in your library.", color = textSecondary, fontSize = 13.sp)
                }
            }
        } else {
            items(quickAccess.chunked(playlistColumns), key = { row -> row.joinToString("|") { it.key } }) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { item ->
                        QuickAccessTile(
                            item = item,
                            viewModel = viewModel,
                            tileBg = tileBg,
                            textColor = textPrimary,
                            accent = accent,
                            onClick = { open(item) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(playlistColumns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        // ── Jump back in ──
        if (jumpBackIn.isNotEmpty()) {
            item(key = "jump_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Jump back in",
                        color = textPrimary,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Show all",
                        color = textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onOpenLibrary)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            item(key = "jump_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(jumpBackIn, key = { it.key }) { item ->
                        PlaylistCard(
                            item = item,
                            viewModel = viewModel,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onClick = { open(item) }
                        )
                    }
                }
            }
        }

        item(key = "library_link") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tileBg)
                    .clickable(onClick = onOpenLibrary)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Your Library", color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${all.size} playlists · create, sort and pin",
                        color = textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun playlistTracks(item: HomePlaylist, viewModel: PlayerViewModel) = when (item) {
    is HomePlaylist.Mueso -> remember(item.playlist.id) { viewModel.getOnlinePlaylistTracks(item.playlist.id) }
    is HomePlaylist.Local -> remember(item.playlist.id) { viewModel.getPlaylistTracks(item.playlist.id) }
}.collectAsState(initial = emptyList())

/** Whether [item] is the playlist the current queue was started from. */
@Composable
private fun rememberIsActivePlaylist(item: HomePlaylist, viewModel: PlayerViewModel): Boolean {
    val activeKey by viewModel.activePlaylistKey.collectAsState()
    return activeKey == item.key
}

@Composable
private fun PlaylistArt(item: HomePlaylist, viewModel: PlayerViewModel, cornerRadius: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    val artworkUrl = (item as? HomePlaylist.Mueso)?.playlist?.artworkUrl
    if (!artworkUrl.isNullOrBlank()) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(cornerRadius))
        )
    } else {
        val tracks by playlistTracks(item, viewModel)
        PlaylistCollageArt(
            tracks = tracks,
            cornerRadius = cornerRadius,
            thumbnailQuality = "Medium (480p)",
            modifier = modifier
        )
    }
}

@Composable
private fun QuickAccessTile(
    item: HomePlaylist,
    viewModel: PlayerViewModel,
    tileBg: Color,
    textColor: Color,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = rememberIsActivePlaylist(item, viewModel)
    val isPlaying = viewModel.playbackState.collectAsState().value.isPlaying
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(tileBg)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaylistArt(item, viewModel, cornerRadius = 0.dp, modifier = Modifier.size(56.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = item.name,
            color = if (isActive) accent else textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f)
        )
        if (isActive) {
            NowPlayingBars(
                color = accent,
                isPlaying = isPlaying,
                modifier = Modifier.padding(end = 10.dp),
                size = 14.dp
            )
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun PlaylistCard(
    item: HomePlaylist,
    viewModel: PlayerViewModel,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    val tracks by playlistTracks(item, viewModel)
    val isActive = rememberIsActivePlaylist(item, viewModel)
    val isPlaying = viewModel.playbackState.collectAsState().value.isPlaying
    val accent = LocalAccentColor.current
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        PlaylistArt(
            item,
            viewModel,
            cornerRadius = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isActive) {
                NowPlayingBars(color = accent, isPlaying = isPlaying, size = 12.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                item.name,
                color = if (isActive) accent else textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = when (item) {
                is HomePlaylist.Mueso -> "Playlist · ${tracks.size} songs"
                is HomePlaylist.Local -> "Local · ${tracks.size} songs"
            },
            color = textSecondary,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}
