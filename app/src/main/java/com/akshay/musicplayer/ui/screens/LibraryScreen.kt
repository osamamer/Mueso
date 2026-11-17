package com.akshay.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.data.db.OnlinePlaylistEntity
import com.akshay.musicplayer.data.db.PlaylistEntity
import com.akshay.musicplayer.ui.components.EditProfileDialog
import com.akshay.musicplayer.ui.components.ProfileAvatar
import com.akshay.musicplayer.ui.components.rememberUserProfile
import com.akshay.musicplayer.ui.theme.LocalIsPureBlack
import com.akshay.musicplayer.ui.viewmodel.PlayerViewModel

/** "Your Library": profile header + every playlist (sort, reorder, pin, create). */
@Composable
fun LibraryScreen(
    viewModel: PlayerViewModel,
    googlePhotoUrl: String?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlaylistClick: (PlaylistEntity) -> Unit,
    onOnlinePlaylistClick: (OnlinePlaylistEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isPureBlack = LocalIsPureBlack.current
    val playlists by viewModel.playlists.collectAsState()
    val onlinePlaylists by viewModel.onlinePlaylists.collectAsState()
    val profile = rememberUserProfile()
    var showEditProfile by remember { mutableStateOf(false) }

    val bg = if (isPureBlack) Color.Black else if (isDarkMode) MaterialTheme.colorScheme.background else Color.White
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF111115)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF6E6E73)
    val outline = if (isDarkMode) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.3f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textPrimary)
            }
            Text(
                "Your Library",
                color = textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = textPrimary)
            }
        }

        // ── Profile header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatar(
                profile = profile,
                size = 76.dp,
                googlePhotoUrl = googlePhotoUrl,
                onClick = { showEditProfile = true }
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = profile.name.ifBlank { "Your profile" },
                    color = textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${playlists.size + onlinePlaylists.size} playlists",
                    color = textSecondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Edit profile",
                    color = textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, outline, RoundedCornerShape(50))
                        .clickable { showEditProfile = true }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        PlaylistsTab(
            viewModel = viewModel,
            isDarkMode = isDarkMode,
            bottomPadding = 150.dp,
            onPlaylistClick = onPlaylistClick,
            onOnlinePlaylistClick = onOnlinePlaylistClick
        )
    }

    if (showEditProfile) {
        EditProfileDialog(
            profile = profile,
            googlePhotoUrl = googlePhotoUrl,
            isDarkMode = isDarkMode,
            onDismiss = { showEditProfile = false }
        )
    }
}
