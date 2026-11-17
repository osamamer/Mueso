package com.akshay.musicplayer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spotify-style "now playing" equalizer: three bars that bounce while [isPlaying]
 * and rest at staggered heights when paused.
 */
@Composable
fun NowPlayingBars(
    color: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp
) {
    val transition = rememberInfiniteTransition(label = "nowPlayingBars")
    val b1 by transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "b1")
    val b2 by transition.animateFloat(1f, 0.3f, infiniteRepeatable(tween(330, easing = LinearEasing), RepeatMode.Reverse), label = "b2")
    val b3 by transition.animateFloat(0.4f, 0.95f, infiniteRepeatable(tween(510, easing = LinearEasing), RepeatMode.Reverse), label = "b3")
    val heights = if (isPlaying) listOf(b1, b2, b3) else listOf(0.45f, 0.8f, 0.6f)

    Canvas(modifier = modifier.size(size)) {
        val gap = this.size.width * 0.14f
        val barWidth = (this.size.width - gap * 2) / 3f
        heights.forEachIndexed { i, fraction ->
            val h = this.size.height * fraction.coerceIn(0.15f, 1f)
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barWidth + gap), this.size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 3f, barWidth / 3f)
            )
        }
    }
}
