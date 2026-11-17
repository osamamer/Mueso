package com.akshay.musicplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.ui.theme.LocalAccentColor
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Spotify-style "swipe right to add to queue" wrapper for a track row.
 *
 * Only rightward drags are claimed, so leftward swipes still reach a parent pager,
 * and vertical drags still reach the list. Release past the threshold to queue.
 */
@Composable
fun SwipeToQueue(
    onAddToQueue: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trackTitle: String? = null,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        Box(modifier) { content() }
        return
    }

    val accent = LocalAccentColor.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val triggerPx = with(density) { 84.dp.toPx() }
    val maxPx = with(density) { 150.dp.toPx() }
    var armed by remember { mutableStateOf(false) }
    val latestOnAdd by rememberUpdatedState(onAddToQueue)
    val latestTitle by rememberUpdatedState(trackTitle)

    val iconScale by animateFloatAsState(
        targetValue = if (armed) 1.15f else 0.85f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "queueIconScale"
    )

    Box(modifier.fillMaxWidth()) {
        if (offsetX.value > 0.5f) {
            val progress = (offsetX.value / triggerPx).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(if (armed) accent else accent.copy(alpha = 0.25f + 0.35f * progress)),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.padding(start = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                                alpha = progress
                            }
                    )
                    if (offsetX.value > triggerPx * 0.9f) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Add to queue",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var started = false
                        val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                            // Only claim the gesture when it starts moving to the right.
                            if (over > 0f) {
                                change.consume()
                                started = true
                                scope.launch { offsetX.snapTo(over.coerceAtMost(maxPx)) }
                            }
                        }
                        if (drag == null || !started) return@awaitEachGesture
                        armed = false
                        horizontalDrag(drag.id) { change ->
                            val dx = change.positionChange().x
                            val current = offsetX.value
                            // Rubber-band resistance past the trigger point
                            val resisted = if (current > triggerPx && dx > 0) dx * 0.35f else dx
                            val next = (current + resisted).coerceIn(0f, maxPx)
                            change.consume()
                            scope.launch { offsetX.snapTo(next) }
                            val nowArmed = next >= triggerPx
                            if (nowArmed != armed) {
                                armed = nowArmed
                                haptic.performHapticFeedback(
                                    if (nowArmed) HapticFeedbackType.GestureThresholdActivate
                                    else HapticFeedbackType.SegmentTick
                                )
                            }
                        }
                        if (armed) {
                            latestOnAdd()
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            val title = latestTitle
                            android.widget.Toast.makeText(
                                context,
                                if (title.isNullOrBlank()) "Added to queue" else "Added to queue: \"$title\"",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        armed = false
                        scope.launch {
                            offsetX.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
                        }
                    }
                }
        ) {
            content()
        }
    }
}

/**
 * Horizontal swipe on a surface (artwork, mini player) to skip tracks, Spotify style.
 * Swipe left → next, swipe right → previous. The content follows the finger and
 * slides out / back in on a committed skip.
 */
fun Modifier.swipeToSkip(
    offsetX: Animatable<Float, *>,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    thresholdFraction: Float = 0.28f,
    scope: kotlinx.coroutines.CoroutineScope
): Modifier = this.pointerInput(Unit) {
    val width = size.width.toFloat().coerceAtLeast(1f)
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()
            scope.launch { offsetX.snapTo(over) }
        } ?: return@awaitEachGesture
        var velocityHint = 0f
        horizontalDrag(drag.id) { change ->
            val dx = change.positionChange().x
            velocityHint = dx
            change.consume()
            scope.launch { offsetX.snapTo(offsetX.value + dx) }
        }
        val value = offsetX.value
        val flung = abs(velocityHint) > 18f && abs(value) > width * 0.12f
        val commit = abs(value) > width * thresholdFraction || flung
        scope.launch {
            if (commit) {
                val goingNext = value < 0f
                offsetX.animateTo(if (goingNext) -width else width, tween(140))
                if (goingNext) onNext() else onPrevious()
                offsetX.snapTo(if (goingNext) width * 0.6f else -width * 0.6f)
                offsetX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
            } else {
                offsetX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium))
            }
        }
    }
}
