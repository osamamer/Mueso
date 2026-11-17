package com.akshay.musicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.domain.models.TrackEntity
import com.akshay.musicplayer.ui.viewmodel.LyricsFetchStatus

/** Background colour for the Spotify-style lyrics card, derived from the accent. */
fun lyricsCardColor(accent: Color, isDarkMode: Boolean): Color =
    if (isDarkMode) lerp(accent, Color.Black, 0.45f) else lerp(accent, Color.Black, 0.15f)

/**
 * Lyrics preview card shown below the player controls. Synced lyrics follow playback;
 * tapping the card opens the full-screen lyrics view.
 */
@Composable
fun PlayerLyricsCard(
    track: TrackEntity,
    positionMs: Long,
    lyricsOffsetMs: Long,
    status: LyricsFetchStatus,
    accent: Color,
    isDarkMode: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColor = lyricsCardColor(accent, isDarkMode)
    val lyrics = track.lyrics
    val syncedLines = lyrics?.lines.orEmpty().filter { it.text.isNotBlank() }
    val plainLines = remember(lyrics) {
        lyrics?.rawText
            ?.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
            ?.lines()
            ?.map { it.replace(Regex("\\[\\d{1,2}:\\d{2}[.:]\\d{2,3}]"), "").trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }
    val hasSynced = syncedLines.isNotEmpty()
    val lyricsRtl = rememberLyricsRtl(lyrics)
    val hasPlain = !hasSynced && plainLines.isNotEmpty()
    val adjusted = (positionMs + lyricsOffsetMs).coerceAtLeast(0L)
    val activeIndex = if (hasSynced) syncedLines.indexOfLast { it.timestampMs <= adjusted } else -1

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .clickable(onClick = onExpand)
            .padding(top = 16.dp, start = 18.dp, end = 10.dp, bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lyrics",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasSynced || hasPlain) Icons.Default.OpenInFull else Icons.Default.Search,
                    contentDescription = "Open lyrics",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        val bodyHeight = 300.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (hasSynced || hasPlain) bodyHeight else 96.dp)
                .padding(end = 8.dp)
        ) {
            when {
                hasSynced -> SyncedLyricsPreview(
                    lines = syncedLines.map { it.text },
                    activeIndex = activeIndex,
                    isRtl = lyricsRtl
                )
                hasPlain -> Column {
                    plainLines.take(10).forEach { line ->
                        Text(
                            text = line,
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 28.sp,
                            textAlign = lyricsTextAlign(lyricsRtl),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        )
                    }
                }
                status == LyricsFetchStatus.FETCHING || status == LyricsFetchStatus.IDLE -> Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Looking for lyrics…", color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp)
                }
                else -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Lyrics aren't available for this song yet",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap to search for them",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                }
            }

            if (hasSynced || hasPlain) {
                // Soft fade at the bottom edge so lines dissolve into the card
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, cardColor)))
                )
            }
        }
    }
}

@Composable
private fun SyncedLyricsPreview(lines: List<String>, activeIndex: Int, isRtl: Boolean) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val leadPx = with(density) { 36.dp.roundToPx() }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            // Keep the active line roughly a third of the way down the card
            listState.animateScrollToItem(activeIndex, scrollOffset = -leadPx * 2)
        }
    }

    LazyColumn(
        state = listState,
        userScrollEnabled = false,
        contentPadding = PaddingValues(bottom = 200.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(lines) { index, line ->
            val target = when {
                index == activeIndex -> Color.White
                index < activeIndex -> Color.White.copy(alpha = 0.55f)
                else -> Color.Black.copy(alpha = 0.55f)
            }
            val color by animateColorAsState(target, tween(250), label = "lyricLine")
            Text(
                text = line,
                color = color,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 28.sp,
                textAlign = lyricsTextAlign(isRtl),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
            )
        }
    }
}

/** "Next in queue" card shown under the lyrics card. */
@Composable
fun UpNextCard(
    upcoming: List<Pair<Int, TrackEntity>>,
    isDarkMode: Boolean,
    onTrackClick: (Int) -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isDarkMode) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF111115)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF707078)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Next in queue",
                color = textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Open queue",
                color = textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onOpenQueue)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(6.dp))

        if (upcoming.isEmpty()) {
            Text(
                "Nothing queued. Swipe right on any song to add it.",
                color = textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            )
        } else {
            upcoming.forEach { (index, t) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTrackClick(index) }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val art = t.artworkUrl?.takeIf { it.isNotBlank() }
                        ?: if (t.albumId > 0L) "content://media/external/audio/albumart/${t.albumId}" else null
                    SmartArtworkImage(
                        artworkUrl = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        thumbnailQuality = "Low (Fast)",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cardBg)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.title,
                            color = textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            t.artist,
                            color = textSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
