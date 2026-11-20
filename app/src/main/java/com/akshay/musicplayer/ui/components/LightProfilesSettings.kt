package com.akshay.musicplayer.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.data.profiles.LightProfileSelections
import com.akshay.musicplayer.data.profiles.LightProfiles
import com.akshay.musicplayer.data.profiles.PlaylistKey
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel
import java.util.Locale

private data class ChoosablePlaylist(val key: PlaylistKey, val name: String, val kind: String)

/** Lets the full app choose which playlists each light profile app may play. */
@Composable
fun LightProfilesSettingsSection(
    viewModel: PlayerViewModel,
    isDarkMode: Boolean,
    cardColor: Color,
    accentColor: Color,
    cornerRadius: Dp
) {
    val context = LocalContext.current
    val selections = remember(context) {
        LightProfileSelections(context.getSharedPreferences("mueso_prefs", Context.MODE_PRIVATE))
    }
    val chosenCounts = remember { mutableStateMapOf<String, Int>() }
    var editing by remember { mutableStateOf<String?>(null) }
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSub = if (isDarkMode) Color.White.copy(alpha = 0.58f) else Color(0xFF6E6E73)

    Column {
        Text(
            text = "Light profiles".uppercase(Locale.getDefault()),
            color = accentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius))
                .background(cardColor)
                .padding(vertical = 8.dp)
        ) {
            Text(
                "Each light profile is its own app that plays only the playlists you tick here. " +
                    "App blockers like Claritard treat it as a separate app, so you can block Nuclear and keep it.",
                color = textSub,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LightProfiles.available.forEach { profile ->
                val count = chosenCounts[profile] ?: selections.get(profile).size
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = profile }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(LightProfiles.displayName(profile), color = textPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (count == 1) "1 playlist" else "$count playlists",
                            color = textSub,
                            fontSize = 12.sp
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textSub)
                }
            }
        }
    }

    editing?.let { profile ->
        PlaylistPicker(
            profile = profile,
            viewModel = viewModel,
            initial = selections.get(profile),
            onDismiss = { editing = null },
            onSave = { chosen ->
                selections.set(profile, chosen)
                chosenCounts[profile] = chosen.size
                editing = null
            }
        )
    }
}

@Composable
private fun PlaylistPicker(
    profile: String,
    viewModel: PlayerViewModel,
    initial: Set<PlaylistKey>,
    onDismiss: () -> Unit,
    onSave: (Set<PlaylistKey>) -> Unit
) {
    val localPlaylists by viewModel.playlists.collectAsState()
    val onlinePlaylists by viewModel.onlinePlaylists.collectAsState()
    val choosable = localPlaylists.map { ChoosablePlaylist(PlaylistKey(false, it.id), it.name, "On device") } +
        onlinePlaylists.map { ChoosablePlaylist(PlaylistKey(true, it.id), it.name, "Online") }
    var chosen by remember(profile) { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playlists for ${LightProfiles.displayName(profile)}") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(choosable, key = { it.key.encode() }) { playlist ->
                    val isChosen = playlist.key in chosen
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { chosen = if (isChosen) chosen - playlist.key else chosen + playlist.key },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isChosen, onCheckedChange = null)
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(playlist.name, maxLines = 1)
                            Text(playlist.kind, fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(chosen) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
