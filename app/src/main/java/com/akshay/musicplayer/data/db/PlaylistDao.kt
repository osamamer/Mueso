package com.akshay.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY isPinned DESC, CASE WHEN isPinned = 1 THEN sortOrder ELSE 0 END ASC, MAX(lastPlayedAt, dateCreated) DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET dateCreated = :timestamp WHERE id = :playlistId")
    fun updatePlaylistTimestamp(playlistId: Long, timestamp: Long = System.currentTimeMillis()): Int

    @Query("SELECT COALESCE(MAX(orderIndex), 0) FROM playlist_tracks WHERE playlistId = :playlistId")
    fun getMaxOrderIndex(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertTrackIntoPlaylist(crossRef: PlaylistTrackCrossRef): Long

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun getTrackIdsForPlaylist(playlistId: Long): Flow<List<Long>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun getPlaylistTrackRefs(playlistId: Long): Flow<List<PlaylistTrackCrossRef>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun getPlaylistTracksSync(playlistId: Long): List<PlaylistTrackCrossRef>

    @androidx.room.Update
    fun updatePlaylistTracks(tracks: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long): Int
    
    @Query("DELETE FROM playlists WHERE id = :playlistId")
    fun deletePlaylist(playlistId: Long): Int

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    fun renamePlaylist(playlistId: Long, newName: String): Int

    @Query("UPDATE playlists SET isPinned = :isPinned WHERE id = :playlistId")
    fun setPlaylistPinned(playlistId: Long, isPinned: Boolean): Int

    @Query("UPDATE playlists SET lastPlayedAt = :timestamp WHERE id = :playlistId")
    fun setPlaylistLastPlayed(playlistId: Long, timestamp: Long = System.currentTimeMillis()): Int

    @Query("SELECT playlistId, trackId FROM playlist_tracks")
    fun getAllTrackPairs(): List<PlaylistTrackPair>

    @Query("UPDATE playlists SET sortOrder = :sortOrder WHERE id = :playlistId")
    fun setPlaylistSortOrder(playlistId: Long, sortOrder: Int): Int
}
