package com.akshay.musicplayer.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.akshay.musicplayer.data.UserProfile
import com.akshay.musicplayer.data.UserProfileStore
import com.akshay.musicplayer.ui.theme.LocalAccentColor
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun rememberUserProfile(): UserProfile {
    val context = LocalContext.current
    LaunchedEffect(Unit) { UserProfileStore.ensureLoaded(context.applicationContext) }
    val profile by UserProfileStore.profile.collectAsState()
    return profile
}

/**
 * Circular profile picture: the user's chosen photo, else their Google photo,
 * else a coloured circle with their initial.
 */
@Composable
fun ProfileAvatar(
    profile: UserProfile,
    size: Dp,
    modifier: Modifier = Modifier,
    googlePhotoUrl: String? = null,
    overridePhoto: Any? = null,
    overrideColorIndex: Int? = null,
    overrideName: String? = null,
    onClick: (() -> Unit)? = null
) {
    val colorIndex = overrideColorIndex ?: profile.colorIndex
    val bg = Color(UserProfileStore.COLORS[colorIndex.coerceIn(0, UserProfileStore.COLORS.lastIndex)])
    val name = overrideName ?: profile.name
    val photo: Any? = overridePhoto ?: profile.photoPath?.let { File(it) } ?: googlePhotoUrl

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "M",
            color = Color.White,
            fontSize = (size.value * 0.44f).sp,
            fontWeight = FontWeight.Bold
        )
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = "Profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun EditProfileDialog(
    profile: UserProfile,
    googlePhotoUrl: String?,
    isDarkMode: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = LocalAccentColor.current
    var name by remember { mutableStateOf(profile.name) }
    var colorIndex by remember { mutableIntStateOf(profile.colorIndex) }
    var pickedPhoto by remember { mutableStateOf<Uri?>(null) }
    var removePhoto by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            pickedPhoto = uri
            removePhoto = false
        }
    }

    val textPrimary = if (isDarkMode) Color.White else Color(0xFF1D1D1F)
    val textSecondary = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color(0xFF6E6E73)
    val hasOwnPhoto = pickedPhoto != null || (profile.photoPath != null && !removePhoto)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDarkMode) Color(0xFF1E1E24) else Color.White,
        title = { Text("Edit profile", color = textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    ProfileAvatar(
                        profile = if (removePhoto) profile.copy(photoPath = null) else profile,
                        size = 104.dp,
                        googlePhotoUrl = googlePhotoUrl,
                        overridePhoto = pickedPhoto,
                        overrideColorIndex = colorIndex,
                        overrideName = name,
                        onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "Choose photo", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Choose photo", color = accent) }
                    if (hasOwnPhoto) {
                        TextButton(onClick = {
                            pickedPhoto = null
                            removePhoto = true
                        }) { Text("Remove photo", color = textSecondary) }
                    }
                }

                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(32) },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        focusedBorderColor = accent,
                        focusedLabelColor = accent,
                        cursorColor = accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    "Avatar colour",
                    color = textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    UserProfileStore.COLORS.forEachIndexed { index, argb ->
                        val selected = index == colorIndex
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .then(
                                    if (selected) Modifier.border(2.dp, textPrimary, CircleShape) else Modifier
                                )
                                .clickable { colorIndex = index },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val appContext = context.applicationContext
                val photo = pickedPhoto
                val remove = removePhoto
                scope.launch {
                    UserProfileStore.save(appContext, name, colorIndex, photo, remove)
                }
                onDismiss()
            }) { Text("Save", color = accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = textSecondary) }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
