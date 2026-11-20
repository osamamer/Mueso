package com.akshay.musicplayer.data.profiles

import android.content.SharedPreferences
import com.akshay.musicplayer.BuildConfig

/**
 * Light profiles are separate installs of Nuclear (one product flavor each, with its own
 * application ID) that only show the playlists chosen for them in the full app. Being separate
 * packages, app blockers such as Claritard limit them independently of the full Nuclear app.
 */
object LightProfiles {
    const val MAIN_PACKAGE = "com.akshay.musicplayer"
    const val AUTHORITY = "com.akshay.musicplayer.lightprofiles"

    /** This build's profile name, or null in the full app. */
    val current: String? = BuildConfig.LIGHT_PROFILE.ifBlank { null }

    val isLight: Boolean get() = current != null

    /** Every light profile this project builds, so the full app can offer them in Settings. */
    val available: List<String> = BuildConfig.LIGHT_PROFILES.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun displayName(profile: String): String = "Nuclear ${profile.replaceFirstChar { it.uppercase() }}"
}

/** A playlist chosen for a light profile, either a local (device) or a saved online playlist. */
data class PlaylistKey(val isOnline: Boolean, val id: Long) {
    fun encode(): String = "${if (isOnline) "online" else "local"}:$id"

    companion object {
        fun decode(value: String): PlaylistKey? {
            val (kind, id) = value.split(":", limit = 2).takeIf { it.size == 2 } ?: return null
            val number = id.toLongOrNull() ?: return null
            return when (kind) {
                "online" -> PlaylistKey(true, number)
                "local" -> PlaylistKey(false, number)
                else -> null
            }
        }
    }
}

/** The full app's record of which playlists each light profile may play. */
class LightProfileSelections(private val prefs: SharedPreferences) {
    fun get(profile: String): Set<PlaylistKey> =
        prefs.getStringSet(key(profile), emptySet()).orEmpty().mapNotNull(PlaylistKey::decode).toSet()

    fun set(profile: String, playlists: Set<PlaylistKey>) {
        prefs.edit().putStringSet(key(profile), playlists.map { it.encode() }.toSet()).apply()
    }

    private fun key(profile: String) = "light_profile_playlists_$profile"
}
