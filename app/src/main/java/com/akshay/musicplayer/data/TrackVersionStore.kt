package com.akshay.musicplayer.data

import android.content.SharedPreferences
import com.akshay.musicplayer.data.remote.OnlineMusicRepository
import com.akshay.musicplayer.domain.models.TrackEntity
import java.util.Locale

/** The kinds of version the picker can search for (same set as Nuclear's version picker). */
enum class VersionVariant(val label: String, val searchSuffix: String) {
    ORIGINAL("Original", ""),
    LIVE("Live", "live"),
    ACOUSTIC("Acoustic", "acoustic"),
    REMIX("Remix", "remix"),
    COVER("Cover", "cover"),
    INSTRUMENTAL("Instrumental", "instrumental"),
    REMASTERED("Remastered", "remastered"),
}

/**
 * Remembers which YouTube video the user picked for a song, keyed by title + primary artist,
 * so the same song plays that version wherever it shows up (playlists, queue, search).
 */
class TrackVersionStore(private val prefs: SharedPreferences) {

    fun preferredVideoId(track: TrackEntity): String? =
        prefs.getString(PREFIX + keyOf(track), null)?.takeIf { it.isNotBlank() }

    fun save(track: TrackEntity, videoId: String) {
        prefs.edit().putString(PREFIX + keyOf(track), videoId).apply()
    }

    fun forget(track: TrackEntity) {
        prefs.edit().remove(PREFIX + keyOf(track)).apply()
    }

    companion object {
        private const val PREFIX = "track_version_"

        fun keyOf(track: TrackEntity): String {
            val title = OnlineMusicRepository.cleanTitleForLyrics(track.title)
            val artist = OnlineMusicRepository.getPrimaryArtist(track.artist)
            return "${normalize(title)}|${normalize(artist)}"
        }

        private fun normalize(text: String): String =
            text.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    }
}
