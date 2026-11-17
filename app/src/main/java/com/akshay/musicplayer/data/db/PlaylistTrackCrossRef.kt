package com.akshay.musicplayer.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long,
    val orderIndex: Int = 0,
    /** When the song was added (epoch ms); 0 for rows created before this was tracked. */
    @ColumnInfo(defaultValue = "0") val dateAdded: Long = System.currentTimeMillis()
)
