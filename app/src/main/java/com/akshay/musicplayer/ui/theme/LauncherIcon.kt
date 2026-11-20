package com.akshay.musicplayer.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.akshay.musicplayer.MainActivity

internal fun updateLauncherIcon(context: Context, accent: Color) {
    val accentLab = DoubleArray(3)
    ColorUtils.colorToLAB(accent.toArgb(), accentLab)
    val paletteLab = DoubleArray(3)
    val palettes = ThemePresets.AllPalettes
    val closest = palettes.minBy { palette ->
        ColorUtils.colorToLAB(palette.primary.toArgb(), paletteLab)
        ColorUtils.distanceEuclidean(accentLab, paletteLab)
    }
    val manager = context.packageManager
    val changes = (listOf(closest) + (palettes - closest)).mapNotNull { palette ->
        val component = ComponentName(
            context.packageName,
            "${MainActivity::class.java.name}Launcher_${palette.id}"
        )
        val enabled = palette == closest
        val enabledByDefault = palette == ThemePresets.SunsetOrange
        val state = when {
            enabled == enabledByDefault -> PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            enabled -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (manager.getComponentEnabledSetting(component) == state) null else component to state
    }

    if (changes.isEmpty()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        manager.setComponentEnabledSettings(changes.map { (component, state) ->
            PackageManager.ComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        })
    } else {
        // Enable the new alias first so a launcher entry always remains available.
        for ((component, state) in changes) {
            manager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }
}
