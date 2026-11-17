package com.akshay.musicplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign
import com.akshay.musicplayer.domain.models.LyricsData

/** True for characters from right-to-left scripts (Arabic, Hebrew, Syriac, Thaana, N'Ko, …). */
private fun Char.isRtlLetter(): Boolean {
    val dir = Character.getDirectionality(this)
    return dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
        dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
}

private fun Char.isLtrLetter(): Boolean =
    Character.getDirectionality(this) == Character.DIRECTIONALITY_LEFT_TO_RIGHT

/** True when most of the letters in [text] belong to a right-to-left script. */
fun isMostlyRtl(text: String): Boolean {
    var rtl = 0
    var ltr = 0
    for (c in text) {
        if (c.isRtlLetter()) rtl++ else if (c.isLtrLetter()) ltr++
    }
    return rtl > 0 && rtl >= ltr
}

/** Whether a song's lyrics should be laid out right to left (decided once for the whole song). */
fun LyricsData?.isRtl(): Boolean {
    if (this == null) return false
    val sample = if (lines.isNotEmpty()) lines.joinToString(" ") { it.text } else rawText.orEmpty()
    return isMostlyRtl(sample)
}

@Composable
fun rememberLyricsRtl(lyrics: LyricsData?): Boolean = remember(lyrics) { lyrics.isRtl() }

/** Text alignment for lyric lines: right-aligned for RTL songs, left-aligned otherwise. */
fun lyricsTextAlign(isRtl: Boolean): TextAlign = if (isRtl) TextAlign.Right else TextAlign.Left
