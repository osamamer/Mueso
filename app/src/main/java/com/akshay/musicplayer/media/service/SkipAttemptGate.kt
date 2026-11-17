package com.akshay.musicplayer.media.service

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Countdown shown to the user while a skip / track change is being held back.
 * [deadlineElapsedMs] is in [SystemClock.elapsedRealtime] time.
 */
data class SkipCountdown(
    val trackKey: String,
    val deadlineElapsedMs: Long,
    val durationMs: Long
) {
    fun remainingMs(now: Long = SystemClock.elapsedRealtime()): Long =
        (deadlineElapsedMs - now).coerceAtLeast(0L)

    val isReady: Boolean get() = remainingMs() == 0L
}

/**
 * Adds friction to manual skips and to starting a different track.
 *
 * Rules:
 *  - Nothing is blocked until a track has actually started playing in this process. A track that
 *    was merely restored from the last session (but never played) never blocks anything.
 *  - The first attempt to leave a playing/played track is simply refused (no countdown).
 *  - The second attempt is refused too and starts the countdown.
 *  - Attempts while the countdown is running are refused.
 *  - Once the countdown reaches zero the next attempt goes through.
 *  - When the current track changes (by any means), the gate is cleared.
 *  - Pausing starts the clock: [COUNTDOWN_MS] after a pause, anything goes through (skip,
 *    pick another song), even if the same song was resumed in the meantime.
 *
 *  - A song that is still within its first [EARLY_FREE_SKIP_MS] (and hasn't been seeked, so
 *    rewinding to the start can't be used as a loophole) can be skipped freely.
 *  - A song that has really been played (not fast-forwarded) past [FREE_SKIP_FRACTION] of its
 *    length, or a song longer than [LONG_TRACK_MS] played past [LONG_TRACK_FREE_SKIP_MS], can be
 *    skipped without any countdown.
 *
 * State lives only in memory, so a fresh app start is never blocked.
 */
object SkipAttemptGate {
    const val COUNTDOWN_MS = 5_000L
    const val FREE_SKIP_FRACTION = 0.60
    const val EARLY_FREE_SKIP_MS = 10_000L
    const val LONG_TRACK_MS = 10 * 60_000L
    const val LONG_TRACK_FREE_SKIP_MS = 7 * 60_000L

    /** True if the song has been played far enough (see class docs) to skip freely. */
    fun isEarlyEnoughToSkip(positionMs: Long): Boolean =
        positionMs in 0 until EARLY_FREE_SKIP_MS && !RewindGate.hasSeekedThisTrack()

    fun isPlayedEnoughToSkip(positionMs: Long, durationMs: Long): Boolean {
        if (positionMs <= 0L || durationMs <= 0L) return false
        val played = RewindGate.naturalPositionMs(positionMs)
        if (played > durationMs * FREE_SKIP_FRACTION) return true
        return durationMs > LONG_TRACK_MS && played > LONG_TRACK_FREE_SKIP_MS
    }

    /** Key of the track that has actually been playing; null = nothing to protect. */
    @Volatile
    private var engagedTrackKey: String? = null

    /** elapsedRealtime when the engaged track was paused; null while playing. */
    @Volatile
    private var pausedAtElapsedMs: Long? = null

    /** True once the engaged track has been paused for the full countdown. */
    @Volatile
    private var unlocked = false

    /** True once a plain (no-countdown) refusal has been given for the engaged track. */
    @Volatile
    private var firstAttemptRefused = false

    private val _countdown = MutableStateFlow<SkipCountdown?>(null)
    val countdown: StateFlow<SkipCountdown?> = _countdown.asStateFlow()

    /** Call whenever playback is observed to be actively running on [trackKey]. */
    @Synchronized
    fun onTrackPlaying(trackKey: String) {
        if (trackKey.isBlank()) return
        if (engagedTrackKey != trackKey) {
            engagedTrackKey = trackKey
            _countdown.value = null
            unlocked = false
            firstAttemptRefused = false
        } else {
            val pausedAt = pausedAtElapsedMs
            if (pausedAt != null && SystemClock.elapsedRealtime() - pausedAt >= COUNTDOWN_MS) {
                unlocked = true
            }
        }
        pausedAtElapsedMs = null
    }

    /** Call when playback of [trackKey] pauses; the 5-second clock starts now. */
    @Synchronized
    fun onTrackPaused(trackKey: String) {
        if (trackKey.isBlank() || engagedTrackKey != trackKey) return
        if (pausedAtElapsedMs == null) pausedAtElapsedMs = SystemClock.elapsedRealtime()
    }

    /** Call whenever the current track changes; drops any gate tied to the old track. */
    @Synchronized
    fun onCurrentTrackChanged(trackKey: String?) {
        if (trackKey != engagedTrackKey) {
            engagedTrackKey = null
            _countdown.value = null
            pausedAtElapsedMs = null
            unlocked = false
            firstAttemptRefused = false
        }
    }

    /** Releases the gate without changing tracks (e.g. the current track finished). */
    @Synchronized
    fun release() {
        engagedTrackKey = null
        _countdown.value = null
        pausedAtElapsedMs = null
        unlocked = false
        firstAttemptRefused = false
    }

    /**
     * Returns true if an attempt to leave [currentTrackKey] must be refused right now.
     * Starts the countdown on the first refused attempt. Pass the current [positionMs] and
     * [durationMs] so a song that has been played far enough is let through straight away.
     */
    @Synchronized
    fun shouldRefuse(currentTrackKey: String, positionMs: Long = -1L, durationMs: Long = -1L): Boolean {
        val engaged = engagedTrackKey
        if (currentTrackKey.isBlank() || engaged == null || engaged != currentTrackKey) return false
        if (unlocked) return false
        if (isEarlyEnoughToSkip(positionMs) || isPlayedEnoughToSkip(positionMs, durationMs)) {
            _countdown.value = null
            return false
        }

        val now = SystemClock.elapsedRealtime()
        val pausedAt = pausedAtElapsedMs
        if (pausedAt != null) {
            val pauseDeadline = pausedAt + COUNTDOWN_MS
            if (now >= pauseDeadline) {
                // Paused long enough: let anything through.
                unlocked = true
                _countdown.value = null
                return false
            }
            // Paused recently: only wait out what's left of the 5 seconds since the pause.
            val existingDeadline = _countdown.value?.takeIf { it.trackKey == currentTrackKey }?.deadlineElapsedMs
            val deadline = if (existingDeadline != null) minOf(existingDeadline, pauseDeadline) else pauseDeadline
            _countdown.value = SkipCountdown(currentTrackKey, deadline, COUNTDOWN_MS)
            return true
        }

        val existing = _countdown.value?.takeIf { it.trackKey == currentTrackKey }
        if (existing == null) {
            if (!firstAttemptRefused) {
                // First attempt: just refuse, the countdown starts on the next try.
                firstAttemptRefused = true
                return true
            }
            _countdown.value = SkipCountdown(currentTrackKey, now + COUNTDOWN_MS, COUNTDOWN_MS)
            return true
        }
        if (!existing.isReady) return true

        // Countdown finished: let this change through.
        engagedTrackKey = null
        _countdown.value = null
        firstAttemptRefused = false
        return false
    }
}
