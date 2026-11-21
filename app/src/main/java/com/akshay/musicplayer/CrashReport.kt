package com.akshay.musicplayer

import android.content.Context

/**
 * Keeps the last crash so it can be shown on the next launch. Devices without debugging
 * access (e.g. car head units) otherwise give no way to tell a crash from the system
 * killing the app.
 */
object CrashReport {
    private const val PREFS = "crash_report"
    private const val KEY = "last_crash"

    fun save(context: Context, throwable: Throwable) {
        try {
            val text = buildString {
                append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()))
                append(" · ").append(BuildConfig.VERSION_NAME).append('\n')
                append(android.util.Log.getStackTraceString(throwable).take(6000))
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, text).commit()
        } catch (_: Throwable) {
        }
    }

    fun get(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
