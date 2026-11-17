package com.akshay.musicplayer.domain.models

import java.util.Locale
import java.util.UUID

enum class BlacklistTargetType(val label: String) {
    TRACK("Song"),
    ARTIST("Artist"),
    ALBUM("Album")
}

data class BlacklistRule(
    val id: String = UUID.randomUUID().toString(),
    val targetType: BlacklistTargetType,
    val value: String,
    val artistFilter: String = "",
    val trackId: Long? = null,
    val trackDurationMs: Long? = null,
    val startMinute: Int? = null,
    val endMinute: Int? = null
) {
    fun matches(track: TrackEntity, minuteOfDay: Int): Boolean {
        if (!isActiveAt(minuteOfDay)) return false

        return when (targetType) {
            BlacklistTargetType.TRACK -> {
                if (trackId != null && track.filePath.startsWith("online:")) {
                    track.id == trackId
                } else {
                    val durationMatches = trackDurationMs == null || track.duration <= 0L ||
                        kotlin.math.abs(track.duration - trackDurationMs) <= 2_500L
                    normalized(track.title) == normalized(value) &&
                        (artistFilter.isBlank() || normalized(track.artist) == normalized(artistFilter)) &&
                        durationMatches
                }
            }
            BlacklistTargetType.ARTIST -> normalized(track.artist) == normalized(value)
            BlacklistTargetType.ALBUM -> normalized(track.album) == normalized(value) &&
                (artistFilter.isBlank() || normalized(track.artist) == normalized(artistFilter))
        }
    }

    fun isActiveAt(minuteOfDay: Int): Boolean {
        val start = startMinute ?: return true
        val end = endMinute ?: return true
        if (start == end) return true
        return if (start < end) {
            minuteOfDay in start until end
        } else {
            minuteOfDay >= start || minuteOfDay < end
        }
    }

    companion object {
        private fun normalized(value: String): String = value
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
    }
}
