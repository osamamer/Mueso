package com.akshay.musicplayer.data.profiles

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import com.akshay.musicplayer.data.db.AppDatabase

/**
 * Serves `content://com.akshay.musicplayer.lightprofiles/<profile>` to the light profile apps:
 * one row with the profile's playlists as a [LightProfileSnapshot]. Only the full app declares
 * it, guarded by a signature permission so only Mueso builds signed with the same key can read.
 */
class LightProfileProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val context = context ?: return null
        val profile = uri.lastPathSegment?.takeIf { it in LightProfiles.available } ?: return null
        return try {
            val prefs = context.getSharedPreferences("mueso_prefs", Context.MODE_PRIVATE)
            val playlists = LightProfileSelections(prefs).get(profile)
            val snapshot = LightProfileSnapshot.build(AppDatabase.getDatabase(context), playlists)
            MatrixCursor(arrayOf(COLUMN)).apply { addRow(arrayOf(snapshot)) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build light profile $profile", e)
            null
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val COLUMN = "snapshot"
        private const val TAG = "LightProfileProvider"
    }
}

/** Run by a light profile app: copies its playlists from the full Mueso, keeping the last copy if that fails. */
object LightProfileSync {
    private const val TAG = "LightProfileSync"

    /** True when the playlists were refreshed. Blocking; call off the main thread. */
    fun pull(context: Context): Boolean {
        val profile = LightProfiles.current ?: return false
        return try {
            val uri = Uri.parse("content://${LightProfiles.AUTHORITY}/$profile")
            val snapshot = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: return false
            LightProfileSnapshot.apply(AppDatabase.getDatabase(context), snapshot)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not read light profile $profile from the full Mueso", e)
            false
        }
    }
}
