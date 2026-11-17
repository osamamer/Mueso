package com.akshay.musicplayer.media.service

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

object SkipRefusalFeedback {
    fun playTone(context: Context) {
        runCatching {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            if (toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 220)) {
                Handler(Looper.getMainLooper()).postDelayed({ toneGenerator.release() }, 300L)
            } else {
                toneGenerator.release()
            }
        }
    }
}
