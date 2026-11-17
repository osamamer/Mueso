package com.akshay.musicplayer.data.db

/** Lightweight (playlist, track) row used to find which playlist a play queue came from. */
data class PlaylistTrackPair(
    val playlistId: Long,
    val trackId: Long
)
