package com.akshay.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.domain.models.TrackEntity

private val AccentOrange: Color
    @Composable
    get() = com.akshay.musicplayer.ui.theme.LocalAccentColor.current

/** How the songs inside a playlist are shown and played. */
enum class PlaylistTrackSort(val label: String, val shortLabel: String) {
    CUSTOM("Custom order (drag to arrange)", "Custom"),
    RECENTLY_ADDED("Recently added", "Recent"),
    OLDEST_ADDED("Oldest added", "Oldest"),
    TITLE("Title (A–Z)", "Title"),
    ARTIST("Artist (A–Z)", "Artist"),
    DURATION("Duration (shortest)", "Duration")
}

/**
 * Sorts [this] (which must be in the playlist's saved custom order) by [sort].
 * [addedDates] maps trackId -> time added; songs from before dates were tracked (0) fall back
 * to their position in the custom order, which matches the order they were added in.
 */
fun List<TrackEntity>.sortedForPlaylist(sort: PlaylistTrackSort, addedDates: Map<Long, Long>): List<TrackEntity> {
    val indexed = withIndex().toList()
    val byAdded = compareBy<IndexedValue<TrackEntity>>({ addedDates[it.value.id] ?: 0L }, { it.index })
    val ordered = when (sort) {
        PlaylistTrackSort.CUSTOM -> return this
        PlaylistTrackSort.RECENTLY_ADDED -> indexed.sortedWith(byAdded.reversed())
        PlaylistTrackSort.OLDEST_ADDED -> indexed.sortedWith(byAdded)
        PlaylistTrackSort.TITLE -> indexed.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.value.title })
        PlaylistTrackSort.ARTIST -> indexed.sortedWith(
            compareBy<IndexedValue<TrackEntity>, String>(String.CASE_INSENSITIVE_ORDER) { it.value.artist }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.value.title }
        )
        PlaylistTrackSort.DURATION -> indexed.sortedWith(compareBy({ it.value.duration }, { it.index }))
    }
    return ordered.map { it.value }
}

/** Sort choice for one playlist, remembered across app restarts. [key] must be unique per playlist. */
@Composable
fun rememberPlaylistTrackSort(key: String): MutableState<PlaylistTrackSort> {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("mueso_prefs", android.content.Context.MODE_PRIVATE) }
    val prefKey = "playlist_track_sort_$key"
    val state = remember(prefKey) {
        val stored = prefs.getString(prefKey, null)
        mutableStateOf(PlaylistTrackSort.entries.firstOrNull { it.name == stored } ?: PlaylistTrackSort.CUSTOM)
    }
    return remember(state, prefKey) {
        object : MutableState<PlaylistTrackSort> by state {
            override var value: PlaylistTrackSort
                get() = state.value
                set(newValue) {
                    state.value = newValue
                    prefs.edit().putString(prefKey, newValue.name).apply()
                }
        }
    }
}

@Composable
fun PlaylistTrackSortChip(
    current: PlaylistTrackSort,
    isDarkMode: Boolean,
    onChange: (PlaylistTrackSort) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val chipBg = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val textColor = if (isDarkMode) Color.White.copy(alpha = 0.8f) else Color(0xFF3A3A3C)
    val menuTextColor = if (isDarkMode) Color.White else Color(0xFF1D1D1F)

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(chipBg)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort songs", tint = textColor, modifier = Modifier.size(16.dp))
            Text(current.shortLabel, color = textColor, fontSize = 13.sp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = if (isDarkMode) Color(0xFF1A1A2E) else Color.White,
            shadowElevation = 16.dp
        ) {
            PlaylistTrackSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, color = menuTextColor) },
                    onClick = {
                        onChange(option)
                        expanded = false
                    },
                    trailingIcon = {
                        if (option == current) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(AccentOrange))
                        }
                    }
                )
            }
        }
    }
}
