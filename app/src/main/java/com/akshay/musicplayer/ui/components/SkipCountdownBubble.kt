package com.akshay.musicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akshay.musicplayer.media.service.SkipCountdown
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val BubbleRed = Color(0xFFE53935)
private val BubbleGreen = Color(0xFF43A047)

/** How long the green "ready" bubble stays on screen before hiding itself. */
private const val READY_VISIBLE_MS = 3_000L

/**
 * Floating, draggable bubble that shows the skip countdown: red with the remaining seconds while
 * a track change (or fast-forward) is being held back, green with a check once it is allowed.
 * The green bubble hides itself after [READY_VISIBLE_MS].
 */
@Composable
fun SkipCountdownBubble(
    countdown: SkipCountdown?,
    modifier: Modifier = Modifier,
    readyDescription: String = "Skip allowed"
) {
    var lastCountdown by remember { mutableStateOf(countdown) }
    if (countdown != null) lastCountdown = countdown
    val shown = lastCountdown

    var remainingMs by remember { mutableLongStateOf(countdown?.remainingMs() ?: 0L) }
    /** Set once the bubble has been green for [READY_VISIBLE_MS]; reset for each new countdown. */
    var dismissed by remember(countdown) { mutableStateOf(false) }
    LaunchedEffect(countdown) {
        if (countdown == null) return@LaunchedEffect
        while (true) {
            remainingMs = countdown.remainingMs()
            if (remainingMs <= 0L) break
            delay(100L)
        }
        delay(READY_VISIBLE_MS)
        dismissed = true
    }

    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }

    androidx.compose.animation.AnimatedVisibility(
        visible = countdown != null && !dismissed,
        enter = scaleIn(tween(180)) + fadeIn(tween(180)),
        exit = scaleOut(tween(180)) + fadeOut(tween(180)),
        modifier = modifier.offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
    ) {
        if (shown == null) return@AnimatedVisibility
        val ready = remainingMs <= 0L
        val color by animateColorAsState(
            targetValue = if (ready) BubbleGreen else BubbleRed,
            animationSpec = tween(250),
            label = "skipBubbleColor"
        )
        val progress = if (shown.durationMs > 0L) remainingMs.toFloat() / shown.durationMs else 0f

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(60.dp)
                .shadow(8.dp, CircleShape)
                .background(color, CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures { change, amount ->
                        change.consume()
                        dragX += amount.x
                        dragY += amount.y
                    }
                }
        ) {
            if (ready) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = readyDescription,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            } else {
                CircularProgressIndicator(
                    progress = { progress },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(52.dp)
                )
                Text(
                    text = ((remainingMs + 999L) / 1000L).toString(),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
