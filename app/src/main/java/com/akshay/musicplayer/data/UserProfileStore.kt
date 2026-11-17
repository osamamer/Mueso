package com.akshay.musicplayer.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** The user's customizable profile shown on the home page. */
data class UserProfile(
    val name: String = "",
    /** Absolute path to a copied profile photo inside app storage, or null. */
    val photoPath: String? = null,
    /** Index into [UserProfileStore.COLORS] for the initials avatar. */
    val colorIndex: Int = 0
)

/** Stores the profile in SharedPreferences, with the photo copied into app-private storage. */
object UserProfileStore {
    private const val PREFS = "mueso_prefs"
    private const val KEY_NAME = "profile_name"
    private const val KEY_PHOTO = "profile_photo_path"
    private const val KEY_COLOR = "profile_color_index"

    /** Avatar background colours (ARGB). */
    val COLORS = listOf(
        0xFFE91E63, 0xFFFF7043, 0xFFFFB300, 0xFF43A047,
        0xFF00ACC1, 0xFF1E88E5, 0xFF8E24AA, 0xFF6D4C41
    )

    private val _profile = MutableStateFlow(UserProfile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    @Volatile
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val path = prefs.getString(KEY_PHOTO, null)?.takeIf { File(it).exists() }
            _profile.value = UserProfile(
                name = prefs.getString(KEY_NAME, "").orEmpty(),
                photoPath = path,
                colorIndex = prefs.getInt(KEY_COLOR, 0).coerceIn(0, COLORS.lastIndex)
            )
            loaded = true
        }
    }

    /**
     * Saves the profile. [newPhoto] replaces the photo; [removePhoto] clears it.
     * Copies the picked image so it survives the picker's temporary permission.
     */
    suspend fun save(
        context: Context,
        name: String,
        colorIndex: Int,
        newPhoto: Uri?,
        removePhoto: Boolean
    ) = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        val old = _profile.value
        var photoPath = old.photoPath

        if (removePhoto || newPhoto != null) {
            photoPath?.let { runCatching { File(it).delete() } }
            photoPath = null
        }
        if (newPhoto != null) {
            // New file name each time so image caches never show the old photo
            val target = File(context.filesDir, "profile_photo_${System.currentTimeMillis()}.jpg")
            val copied = runCatching {
                context.contentResolver.openInputStream(newPhoto)?.use { input ->
                    target.outputStream().use { input.copyTo(it) }
                } != null
            }.getOrDefault(false)
            photoPath = if (copied) target.absolutePath else null
        }

        val updated = UserProfile(
            name = name.trim(),
            photoPath = photoPath,
            colorIndex = colorIndex.coerceIn(0, COLORS.lastIndex)
        )
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_NAME, updated.name)
            .putString(KEY_PHOTO, updated.photoPath)
            .putInt(KEY_COLOR, updated.colorIndex)
            .apply()
        _profile.value = updated
    }
}
