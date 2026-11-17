package com.akshay.musicplayer.data.profiles

import com.akshay.musicplayer.data.db.AppDatabase
import com.akshay.musicplayer.data.db.OnlinePlaylistEntity
import com.akshay.musicplayer.data.db.OnlinePlaylistTrackEntity
import com.akshay.musicplayer.data.db.PlaylistEntity
import com.akshay.musicplayer.data.db.PlaylistTrackCrossRef
import org.json.JSONArray
import org.json.JSONObject

/**
 * Moves a light profile's playlists from the full app's database into the light app's. Local
 * playlists carry MediaStore track IDs, which are the same for both apps on one device.
 *
 * Must run off the main thread: it uses Room's blocking DAO calls.
 */
object LightProfileSnapshot {
    fun build(db: AppDatabase, playlists: Set<PlaylistKey>): String {
        val local = JSONArray()
        val online = JSONArray()
        val playlistDao = db.playlistDao()
        val onlineDao = db.onlinePlaylistDao()
        for (key in playlists) {
            if (key.isOnline) {
                val playlist = onlineDao.getOnlinePlaylistById(key.id) ?: continue
                online.put(onlineJson(playlist, onlineDao.getOnlinePlaylistTracksSync(key.id)))
            } else {
                val playlist = playlistDao.getPlaylistById(key.id) ?: continue
                local.put(localJson(playlist, playlistDao.getPlaylistTracksSync(key.id)))
            }
        }
        return JSONObject().put("local", local).put("online", online).toString()
    }

    /** Replaces every playlist in `db` with the snapshot's, in one transaction. */
    fun apply(db: AppDatabase, snapshot: String) {
        val root = JSONObject(snapshot)
        val playlistDao = db.playlistDao()
        val onlineDao = db.onlinePlaylistDao()
        db.runInTransaction {
            playlistDao.deleteAllPlaylistTracks()
            playlistDao.deleteAllPlaylists()
            onlineDao.deleteAllOnlinePlaylistTracks()
            onlineDao.deleteAllOnlinePlaylists()
            root.optJSONArray("local").forEachObject { playlist ->
                val id = playlistDao.insertPlaylist(
                    PlaylistEntity(name = playlist.getString("name"), dateCreated = playlist.optLong("dateCreated"))
                )
                playlist.optJSONArray("tracks").forEachObject { track ->
                    playlistDao.insertTrackIntoPlaylist(
                        PlaylistTrackCrossRef(
                            playlistId = id,
                            trackId = track.getLong("trackId"),
                            orderIndex = track.optInt("orderIndex")
                        )
                    )
                }
            }
            root.optJSONArray("online").forEachObject { playlist ->
                val id = onlineDao.insertOnlinePlaylist(
                    OnlinePlaylistEntity(
                        name = playlist.getString("name"),
                        description = playlist.optStringOrNull("description"),
                        artworkUrl = playlist.optStringOrNull("artworkUrl"),
                        dateCreated = playlist.optLong("dateCreated")
                    )
                )
                playlist.optJSONArray("tracks").forEachObject { track ->
                    onlineDao.insertOnlineTrack(
                        OnlinePlaylistTrackEntity(
                            onlinePlaylistId = id,
                            trackId = track.getLong("trackId"),
                            title = track.getString("title"),
                            artist = track.optString("artist"),
                            artworkUrl = track.optStringOrNull("artworkUrl"),
                            filePath = track.getString("filePath"),
                            duration = track.optLong("duration"),
                            orderIndex = track.optInt("orderIndex")
                        )
                    )
                }
            }
        }
    }

    private fun localJson(playlist: PlaylistEntity, tracks: List<PlaylistTrackCrossRef>) = JSONObject()
        .put("name", playlist.name)
        .put("dateCreated", playlist.dateCreated)
        .put("tracks", JSONArray(tracks.map { JSONObject().put("trackId", it.trackId).put("orderIndex", it.orderIndex) }))

    private fun onlineJson(playlist: OnlinePlaylistEntity, tracks: List<OnlinePlaylistTrackEntity>) = JSONObject()
        .put("name", playlist.name)
        .put("description", playlist.description)
        .put("artworkUrl", playlist.artworkUrl)
        .put("dateCreated", playlist.dateCreated)
        .put("tracks", JSONArray(tracks.map {
            JSONObject()
                .put("trackId", it.trackId)
                .put("title", it.title)
                .put("artist", it.artist)
                .put("artworkUrl", it.artworkUrl)
                .put("filePath", it.filePath)
                .put("duration", it.duration)
                .put("orderIndex", it.orderIndex)
        }))

    private inline fun JSONArray?.forEachObject(action: (JSONObject) -> Unit) {
        if (this == null) return
        for (index in 0 until length()) action(getJSONObject(index))
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
}
