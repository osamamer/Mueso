package com.akshay.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.components.SmartArtworkImage
import com.akshay.musicplayer.ui.state.PlaybackState
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel

@Composable
internal fun WidePlayerContent(
    track: TrackEntity,
    artworkUri: String?,
    playbackState: PlaybackState,
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenMenu: () -> Unit,
    quickActions: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val isShuffleEnabled by viewModel.isShuffleModeEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    var seekPosition by remember(track.id) { mutableStateOf<Float?>(null) }
    val duration = playbackState.durationMs.coerceAtLeast(0L)
    val position = (seekPosition ?: playbackState.currentPositionMs.toFloat()).coerceIn(0f, duration.toFloat())
    val playPauseDescription = if (playbackState.isPlaying) "Pause" else "Play"

    Column(modifier.padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to browsing")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.toggleShuffleMode() }) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = if (isShuffleEnabled) "Shuffle on" else "Shuffle off",
                        tint = if (isShuffleEnabled) colors.primary else colors.onBackground
                    )
                }
                IconButton(onClick = { viewModel.cycleRepeatMode(context) }) {
                    val repeatOne = repeatMode == Player.REPEAT_MODE_ONE
                    Icon(
                        if (repeatOne) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> "Repeat one"
                            Player.REPEAT_MODE_ALL -> "Repeat all"
                            else -> "Repeat off"
                        },
                        tint = if (repeatMode == Player.REPEAT_MODE_OFF) colors.onBackground else colors.primary
                    )
                }
                OutlinedButton(onClick = onOpenQueue, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Queue", modifier = Modifier.padding(start = 6.dp), maxLines = 1)
                }
                IconButton(onClick = onOpenMenu) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Track options")
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.widthIn(max = 1040.dp).fillMaxSize().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.weight(0.34f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier.widthIn(max = 340.dp).aspectRatio(1f)
                            .clip(MaterialTheme.shapes.large)
                            .background(colors.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(52.dp))
                        SmartArtworkImage(artworkUri, contentDescription = null, modifier = Modifier.fillMaxSize())
                    }
                }

                Box(Modifier.weight(0.66f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            track.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onBackground.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = viewModel::playPreviousTrack, modifier = Modifier.size(56.dp)) {
                                Icon(Icons.Default.SkipPrevious, "Previous track", Modifier.size(34.dp))
                            }
                            FilledIconButton(
                                onClick = viewModel::togglePlayPause,
                                modifier = Modifier.size(68.dp).semantics { contentDescription = playPauseDescription }
                            ) {
                                if (playbackState.isBuffering) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        color = colors.onPrimary,
                                        strokeWidth = 3.dp
                                    )
                                } else {
                                    Icon(
                                        if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                            IconButton(onClick = viewModel::playNextTrack, modifier = Modifier.size(56.dp)) {
                                Icon(Icons.Default.SkipNext, "Next track", Modifier.size(34.dp))
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = position,
                                onValueChange = { seekPosition = it },
                                onValueChangeFinished = {
                                    seekPosition?.let { viewModel.seekTo(it.toLong()) }
                                    seekPosition = null
                                },
                                enabled = duration > 0L,
                                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                                modifier = Modifier.weight(1f).semantics { contentDescription = "Playback position" }
                            )
                            if (duration > 0L) {
                                Text(
                                    "${formatTime(position.toLong())} / ${formatTime(duration)}",
                                    modifier = Modifier.padding(start = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                            }
                        }
                        quickActions()
                    }
                }
            }
        }
    }
}
