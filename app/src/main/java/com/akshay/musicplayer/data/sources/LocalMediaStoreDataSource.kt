package com.akshay.musicplayer.data.sources

import android.content.Context
import android.content.ContentResolver
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.akshay.musicplayer.data.models.Track
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.CoroutineContext

class LocalMediaStoreDataSource(
    context: Context,
    private val ioDispatcher: CoroutineContext,
    private val getDownloadFolder: () -> String
) : MediaStoreDataSource {

    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    private fun getDownloadDirectory(): File? {
        val folderSetting = getDownloadFolder()
        if (folderSetting.startsWith("content://")) {
            return try {
                val documentId = DocumentsContract.getTreeDocumentId(Uri.parse(folderSetting))
                val volume = documentId.substringBefore(':', "primary")
                val relativePath = documentId.substringAfter(':', "").trim('/')
                val root = if (volume.equals("primary", ignoreCase = true)) {
                    Environment.getExternalStorageDirectory()
                } else {
                    File("/storage/$volume")
                }
                File(root, relativePath)
            } catch (_: Exception) {
                null
            }
        }

        return when {
            folderSetting == "Internal App Storage" ->
                appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: appContext.filesDir
            folderSetting == "Downloads" ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            folderSetting.startsWith("/") -> File(folderSetting)
            folderSetting.startsWith("Music/") -> File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                folderSetting.removePrefix("Music/")
            )
            folderSetting.equals("Music", ignoreCase = true) ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            else -> {
                val externalDir = Environment.getExternalStorageDirectory()
                val customDir = File(externalDir, folderSetting)
                if (customDir.exists() || folderSetting.contains('/')) customDir
                else File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), folderSetting)
            }
        }
    }

    private fun downloadPathPattern(): String? = getDownloadDirectory()
        ?.absolutePath
        ?.trimEnd(File.separatorChar)
        ?.replace("\\", "\\\\")
        ?.replace("%", "\\%")
        ?.replace("_", "\\_")
        ?.plus(File.separator + "%")

    override suspend fun getLocalTracks(): Result<List<Track>> = withContext(ioDispatcher) {
        try {
            val tracks = mutableListOf<Track>()
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DATE_ADDED
            )

            val downloadPathPattern = downloadPathPattern()
            val selection = if (downloadPathPattern != null) {
                "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND ${MediaStore.Audio.Media.DATA} LIKE ? ESCAPE '\\'"
            } else {
                // Fail closed: if Mueso's download location cannot be resolved, show no device-wide audio.
                "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND 1 = 0"
            }
            val selectionArgs = downloadPathPattern?.let { arrayOf(it) }
            val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val dateAddedCol = try { cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED) } catch (_: Exception) { -1 }

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown"
                    val artist = cursor.getString(artistCol) ?: "Unknown"
                    val album = cursor.getString(albumCol) ?: "Unknown"
                    val duration = cursor.getLong(durationCol)
                    val albumId = cursor.getLong(albumIdCol)
                    val data = cursor.getString(dataCol) ?: ""
                    val dateModified = cursor.getLong(dateModifiedCol)
                    val dateAdded = if (dateAddedCol != -1) try { cursor.getLong(dateAddedCol) } catch (_: Exception) { 0L } else 0L
                    var effectiveDate = maxOf(dateModified, dateAdded)
                    if (effectiveDate == 0L && data.isNotEmpty()) {
                        try {
                            val f = java.io.File(data)
                            if (f.exists()) {
                                effectiveDate = f.lastModified() / 1000L
                            }
                        } catch (_: Exception) {}
                    }

                    if (data.isNotEmpty()) {
                        tracks.add(
                            Track(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                duration = duration,
                                albumId = albumId,
                                data = data,
                                dateModified = effectiveDate
                            )
                        )
                    }
                }
            }
            if (getDownloadFolder() == "Internal App Storage") {
                tracks += getInternalStorageTracks()
            }
            Result.success(tracks.distinctBy { it.data })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTrackById(id: Long): Result<Track?> = withContext(ioDispatcher) {
        try {
            if (id < 0L && getDownloadFolder() == "Internal App Storage") {
                return@withContext Result.success(getInternalStorageTracks().firstOrNull { it.id == id })
            }
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DATE_ADDED
            )

            val downloadPathPattern = downloadPathPattern()
            val selection = if (downloadPathPattern != null) {
                "${MediaStore.Audio.Media._ID} = ? AND ${MediaStore.Audio.Media.DATA} LIKE ? ESCAPE '\\'"
            } else {
                "${MediaStore.Audio.Media._ID} = ? AND 1 = 0"
            }
            val selectionArgs = if (downloadPathPattern != null) {
                arrayOf(id.toString(), downloadPathPattern)
            } else {
                arrayOf(id.toString())
            }

            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "Unknown"
                    val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown"
                    val album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "Unknown"
                    val duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                    val albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
                    val data = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)) ?: ""
                    val dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED))
                    val dateAdded = try { cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)) } catch (_: Exception) { 0L }
                    var effectiveDate = maxOf(dateModified, dateAdded)
                    if (effectiveDate == 0L && data.isNotEmpty()) {
                        try {
                            val f = java.io.File(data)
                            if (f.exists()) {
                                effectiveDate = f.lastModified() / 1000L
                            }
                        } catch (_: Exception) {}
                    }

                    return@withContext Result.success(
                        Track(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            albumId = albumId,
                            data = data,
                            dateModified = effectiveDate
                        )
                    )
                }
            }
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getInternalStorageTracks(): List<Track> {
        val root = getDownloadDirectory() ?: return emptyList()
        if (!root.exists()) return emptyList()
        val supportedExtensions = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "mp4")
        return root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in supportedExtensions }
            .mapNotNull { file ->
                val metadata = MediaMetadataRetriever()
                try {
                    metadata.setDataSource(file.absolutePath)
                    val title = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
                    val artist = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
                    val album = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                        ?.takeIf { it.isNotBlank() } ?: "Unknown Album"
                    val duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val hash = file.absolutePath.hashCode().toLong()
                    val stableNegativeId = if (hash >= 0L) -(hash + 1L) else hash
                    Track(
                        id = stableNegativeId,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        albumId = 0L,
                        data = file.absolutePath,
                        dateModified = file.lastModified() / 1000L
                    )
                } catch (_: Exception) {
                    null
                } finally {
                    try { metadata.release() } catch (_: Exception) {}
                }
            }
            .toList()
            .sortedByDescending { it.dateModified }
    }
}
