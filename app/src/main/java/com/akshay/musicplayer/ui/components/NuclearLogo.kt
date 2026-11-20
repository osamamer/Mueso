package com.akshay.musicplayer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.akshay.musicplayer.R
import com.akshay.musicplayer.ui.theme.LocalAccentColor

@Composable
fun NuclearLogo(modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(1f)) {
        Image(painterResource(R.drawable.ic_logo_plate), null, Modifier.matchParentSize())
        Image(
            painterResource(R.drawable.ic_logo_mark),
            "Nuclear logo",
            Modifier.matchParentSize(),
            colorFilter = ColorFilter.tint(LocalAccentColor.current)
        )
        Image(painterResource(R.drawable.ic_logo_highlight), null, Modifier.matchParentSize())
    }
}
