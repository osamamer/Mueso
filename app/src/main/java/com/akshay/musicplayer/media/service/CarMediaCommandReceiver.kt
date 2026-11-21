package com.akshay.musicplayer.media.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Many aftermarket car head units don't send steering-wheel buttons as standard media key
 * events. Instead they broadcast the legacy AOSP Music app commands
 * ("com.android.music.musicservicecommand"), which only the stock player listens for.
 * Forward those to the active session so the car's buttons control Mueso.
 */
class CarMediaCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val player = MusicPlayerService.activeSession?.player ?: return
        val command = when (intent.action) {
            ACTION_COMMAND -> intent.getStringExtra("command")
            else -> intent.action?.substringAfterLast('.')
        } ?: return
        Log.i(TAG, "Car media command: $command (action=${intent.action})")
        when (command) {
            "next" -> player.seekToNext()
            "previous" -> player.seekToPrevious()
            "togglepause" -> if (player.playWhenReady) player.pause() else player.play()
            "pause" -> player.pause()
            "play" -> player.play()
            "stop" -> player.pause()
        }
    }

    companion object {
        private const val TAG = "CarMediaCommand"
        const val ACTION_COMMAND = "com.android.music.musicservicecommand"
    }
}
