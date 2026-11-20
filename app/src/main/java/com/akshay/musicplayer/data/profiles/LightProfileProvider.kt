package com.akshay.musicplayer.data.profiles

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import com.akshay.musicplayer.data.db.AppDatabase
import com.akshay.musicplayer.ui.theme.ThemePresets

/**
 * Serves `content://com.akshay.musicplayer.lightprofiles/<profile>` to the light profile apps:
 * one row with the profile's playlists as a [LightProfileSnapshot]. Only the full app declares
 * it, guarded by a signature permission so only Nuclear builds signed with the same key can read.
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
            val accent = prefs.getString(ACCENT_COLUMN, ThemePresets.SunsetOrange.id)
            MatrixCursor(arrayOf(COLUMN, ACCENT_COLUMN)).apply { addRow(arrayOf(snapshot, accent)) }
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
        const val ACCENT_COLUMN = "accent_color_id"
        private const val TAG = "LightProfileProvider"
    }
}

/** Run by a light profile app: copies its playlists from the full Nuclear app, keeping the last copy if that fails. */
object LightProfileSync {
    private const val TAG = "LightProfileSync"

    /** Returns the full app's accent after refreshing playlists. Blocking; call off the main thread. */
    fun pull(context: Context): Result<String?> = runCatching {
        val profile = checkNotNull(LightProfiles.current)
        val uri = Uri.parse("content://${LightProfiles.AUTHORITY}/$profile")
        val snapshot = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val accentIndex = cursor.getColumnIndex(LightProfileProvider.ACCENT_COLUMN)
            val accent = if (accentIndex >= 0) cursor.getString(accentIndex) else null
            cursor.getString(0) to accent
        } ?: error("No snapshot for light profile $profile")
        LightProfileSnapshot.apply(AppDatabase.getDatabase(context), snapshot.first)
        snapshot.second
    }.onFailure { error ->
        Log.e(TAG, "Could not refresh light profile from the full Nuclear app", error)
    }
}
