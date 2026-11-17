package com.akshay.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OnlinePlaylistDao {

    @Query("SELECT * FROM online_playlists ORDER BY isPinned DESC, CASE WHEN isPinned = 1 THEN sortOrder ELSE 0 END ASC, MAX(lastPlayedAt, dateCreated) DESC")
    fun getAllOnlinePlaylists(): Flow<List<OnlinePlaylistEntity>>

    @Query("SELECT * FROM online_playlists WHERE id = :playlistId")
    fun getOnlinePlaylistById(playlistId: Long): OnlinePlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOnlinePlaylist(playlist: OnlinePlaylistEntity): Long

    @Query("DELETE FROM online_playlists WHERE id = :playlistId")
    fun deleteOnlinePlaylist(playlistId: Long): Int

    @Query("UPDATE online_playlists SET name = :newName WHERE id = :playlistId")
    fun renameOnlinePlaylist(playlistId: Long, newName: String): Int

    @Query("UPDATE online_playlists SET name = :newName, description = :newDescription WHERE id = :playlistId")
    fun updateOnlinePlaylistDetails(playlistId: Long, newName: String, newDescription: String): Int

    @Query("UPDATE online_playlists SET description = :description WHERE id = :playlistId")
    fun updateOnlinePlaylistDescription(playlistId: Long, description: String?): Int

    @Query("UPDATE online_playlists SET dateCreated = :timestamp WHERE id = :playlistId")
    fun updateOnlinePlaylistTimestamp(playlistId: Long, timestamp: Long = System.currentTimeMillis()): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOnlineTrack(track: OnlinePlaylistTrackEntity): Long

    @Query("DELETE FROM online_playlist_tracks WHERE onlinePlaylistId = :playlistId AND trackId = :trackId")
    fun removeOnlineTrack(playlistId: Long, trackId: Long): Int

    @Query("SELECT * FROM online_playlist_tracks WHERE onlinePlaylistId = :playlistId ORDER BY orderIndex ASC")
    fun getOnlinePlaylistTracks(playlistId: Long): Flow<List<OnlinePlaylistTrackEntity>>

    @Query("SELECT * FROM online_playlist_tracks WHERE onlinePlaylistId = :playlistId ORDER BY orderIndex ASC")
    fun getOnlinePlaylistTracksSync(playlistId: Long): List<OnlinePlaylistTrackEntity>

    @Query("DELETE FROM online_playlist_tracks WHERE onlinePlaylistId = :playlistId")
    fun clearOnlinePlaylistTracks(playlistId: Long): Int

    @Query("SELECT * FROM online_playlists ORDER BY isPinned DESC, CASE WHEN isPinned = 1 THEN sortOrder ELSE 0 END ASC, MAX(lastPlayedAt, dateCreated) DESC")
    fun getAllOnlinePlaylistsSync(): List<OnlinePlaylistEntity>

    @Query("UPDATE online_playlists SET artworkUrl = :artworkUrl WHERE id = :playlistId")
    fun updateOnlinePlaylistArtwork(playlistId: Long, artworkUrl: String?): Int

    @Query("UPDATE online_playlists SET spotifyUrl = :spotifyUrl WHERE id = :playlistId")
    fun updateOnlinePlaylistSpotifyUrl(playlistId: Long, spotifyUrl: String?): Int

    @Query("UPDATE online_playlists SET isPinned = :isPinned WHERE id = :playlistId")
    fun setOnlinePlaylistPinned(playlistId: Long, isPinned: Boolean): Int

    @Query("UPDATE online_playlists SET sortOrder = :sortOrder WHERE id = :playlistId")
    fun setOnlinePlaylistSortOrder(playlistId: Long, sortOrder: Int): Int

    @Query("UPDATE online_playlists SET lastPlayedAt = :timestamp WHERE id = :playlistId")
    fun setOnlinePlaylistLastPlayed(playlistId: Long, timestamp: Long = System.currentTimeMillis()): Int

    @Query("SELECT onlinePlaylistId AS playlistId, trackId FROM online_playlist_tracks")
    fun getAllTrackPairs(): List<PlaylistTrackPair>

    @androidx.room.Update
    fun updateOnlinePlaylistTracks(tracks: List<OnlinePlaylistTrackEntity>)

    @Query("DELETE FROM online_playlists")
    fun deleteAllOnlinePlaylists(): Int

    @Query("DELETE FROM online_playlist_tracks")
    fun deleteAllOnlinePlaylistTracks(): Int
}
