package com.akshay.musicplayer.data.db

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "online_playlists")
data class OnlinePlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val artworkUrl: String? = null,
    val description: String? = null,
    val dateCreated: Long = System.currentTimeMillis(),
    val spotifyUrl: String? = null,
    @ColumnInfo(defaultValue = "0") val isPinned: Boolean = false,
    /** Manual position chosen by the user (0 = never ordered, shown first). */
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    /** Last time music from this playlist was actually played (epoch ms); 0 = never. */
    @ColumnInfo(defaultValue = "0") val lastPlayedAt: Long = 0L
) {
    /** Most recent activity: played, or created/imported. Drives "Recents" ordering. */
    val recentActivityAt: Long get() = maxOf(lastPlayedAt, dateCreated)
}
