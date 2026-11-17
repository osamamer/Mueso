package com.akshay.musicplayer.media.service

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Limits moving around inside a song (slider, lyric tap, notification scrubber, lock screen,
 * Bluetooth).
 *
 *  Going backwards:
 *  - Each song gets [REWINDS_PER_TRACK] backward seeks. After that, backward seeks are refused
 *    until the song changes.
 *
 *  Going forwards (fast-forwarding to a point):
 *  - Each song gets [FORWARDS_PER_TRACK] forward seek, and only after a wait.
 *  - The first forward attempt is refused and starts a [FORWARD_WAIT_MS] countdown.
 *  - Attempts while the countdown is running are refused.
 *  - Once the countdown has finished, the next forward seek goes through and uses up the budget.
 *  - Further forward seeks are refused until the song changes.
 *
 *  All budgets reset whenever the current song changes. The total amount fast-forwarded is kept
 *  so [naturalPositionMs] can tell how far the song has really been played (used by
 *  [SkipAttemptGate] to let near-finished songs be skipped freely).
 *
 * State lives only in memory and is tied to the current track.
 */
object RewindGate {
    const val REWINDS_PER_TRACK = 1
    const val FORWARDS_PER_TRACK = 1
    const val FORWARD_WAIT_MS = 10_000L

    /** Moves smaller than this are treated as slider jitter, not a real seek. */
    private const val JITTER_TOLERANCE_MS = 1_500L

    sealed interface Decision {
        data object Allowed : Decision
        /** Allowed, and this used up a rewind. [remaining] is what's left for this song. */
        data class AllowedUsedRewind(val remaining: Int) : Decision
        /** Allowed, and this used up this song's fast-forward. */
        data object AllowedUsedForward : Decision
        /** Rewind refused: none left for this song. */
        data object Refused : Decision
        /** Fast-forward refused: wait [remainingMs] more, then try again. */
        data class ForwardWaiting(val remainingMs: Long) : Decision
        /** Fast-forward refused: this song's fast-forward was already used. */
        data object ForwardRefused : Decision
    }

    @Volatile
    private var trackKey: String? = null
    private var rewindsUsed = 0
    private var forwardsUsed = 0
    /** elapsedRealtime when the fast-forward wait finishes; null = no wait started yet. */
    private var forwardUnlockAtElapsedMs: Long? = null
    /** Total ms jumped over by fast-forwarding in this song. */
    private var forwardSkippedMs = 0L

    private val _forwardCountdown = MutableStateFlow<SkipCountdown?>(null)
    /** Countdown until fast-forward unlocks for this song; null when no wait is running/shown. */
    val forwardCountdown: StateFlow<SkipCountdown?> = _forwardCountdown.asStateFlow()

    @Synchronized
    fun onCurrentTrackChanged(newTrackKey: String?) {
        if (newTrackKey != trackKey) {
            trackKey = newTrackKey
            rewindsUsed = 0
            forwardsUsed = 0
            forwardUnlockAtElapsedMs = null
            forwardSkippedMs = 0L
            _forwardCountdown.value = null
        }
    }

    @Synchronized
    fun check(currentPositionMs: Long, targetPositionMs: Long): Decision {
        val delta = targetPositionMs - currentPositionMs
        if (delta > JITTER_TOLERANCE_MS) return checkForward(delta)
        if (delta >= -JITTER_TOLERANCE_MS) return Decision.Allowed

        if (rewindsUsed >= REWINDS_PER_TRACK) return Decision.Refused
        rewindsUsed++
        return Decision.AllowedUsedRewind(REWINDS_PER_TRACK - rewindsUsed)
    }

    private fun checkForward(deltaMs: Long): Decision {
        if (forwardsUsed >= FORWARDS_PER_TRACK) return Decision.ForwardRefused
        val now = SystemClock.elapsedRealtime()
        val unlockAt = forwardUnlockAtElapsedMs
        if (unlockAt == null) {
            val deadline = now + FORWARD_WAIT_MS
            forwardUnlockAtElapsedMs = deadline
            _forwardCountdown.value = SkipCountdown(trackKey.orEmpty(), deadline, FORWARD_WAIT_MS)
            return Decision.ForwardWaiting(FORWARD_WAIT_MS)
        }
        if (now < unlockAt) {
            // Re-show the bubble if it had been hidden.
            _forwardCountdown.value = SkipCountdown(trackKey.orEmpty(), unlockAt, FORWARD_WAIT_MS)
            return Decision.ForwardWaiting(unlockAt - now)
        }

        forwardsUsed++
        _forwardCountdown.value = null
        forwardSkippedMs += deltaMs
        return Decision.AllowedUsedForward
    }

    /** True once this song has been rewound or fast-forwarded. */
    @Synchronized
    fun hasSeekedThisTrack(): Boolean = rewindsUsed > 0 || forwardsUsed > 0

    /** How far into the song playback has really got, not counting fast-forwarded stretches. */
    @Synchronized
    fun naturalPositionMs(currentPositionMs: Long): Long =
        (currentPositionMs - forwardSkippedMs).coerceAtLeast(0L)
}
