package com.pappoe34.launcher.ui.wheel

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.pappoe34.launcher.model.Ring
import com.pappoe34.launcher.model.WheelTheme

/** How one ring is painted. */
data class RingStyle(
    val track: (center: Offset) -> Brush,
    val rim: Color,
    val glow: Color,
    val notch: Color,
)

data class WheelPalette(
    val rings: Map<Ring, RingStyle>,
    /** Background wash drawn over the wallpaper. */
    val scrimTop: Color,
    val scrimBottom: Color,
    val hub: Color,
    val hubContent: Color,
    val emptySlot: Color,
    val label: Color,
)

@Composable
fun rememberWheelPalette(theme: WheelTheme): WheelPalette {
    val scheme = MaterialTheme.colorScheme
    return when (theme) {
        WheelTheme.METALLIC -> metallic()
        WheelTheme.NEON -> neon()
        WheelTheme.GLASS -> glass()
        WheelTheme.DYNAMIC -> dynamic(scheme)
    }
}

private fun metallic(): WheelPalette {
    val brushed: (Offset) -> Brush = { center ->
        Brush.sweepGradient(
            colors = listOf(
                Color(0xFF8E939A), Color(0xFFD9DDE1), Color(0xFF5F646A), Color(0xFFC3C7CC),
                Color(0xFF6E737A), Color(0xFFE2E5E8), Color(0xFF7B8087), Color(0xFF8E939A),
            ),
            center = center,
        )
    }
    val style = RingStyle(track = brushed, rim = Color(0xFF23272B), glow = Color(0x33FFFFFF), notch = Color(0xB3303439))
    return WheelPalette(
        rings = Ring.entries.associateWith { style },
        scrimTop = Color(0x33000000),
        scrimBottom = Color(0x99000000),
        hub = Color(0xFF2B2F34),
        hubContent = Color(0xFFE6E8EB),
        emptySlot = Color(0x33000000),
        label = Color.White,
    )
}

private fun neon(): WheelPalette {
    val accents = mapOf(
        Ring.INNER to Color(0xFFB6FF3B),
        Ring.MIDDLE to Color(0xFFFF2BD6),
        Ring.OUTER to Color(0xFF00E5FF),
    )
    return WheelPalette(
        rings = Ring.entries.associateWith { ring ->
            val accent = accents.getValue(ring)
            RingStyle(
                track = { SolidColor(Color(0xCC0B0F14)) },
                rim = accent,
                glow = accent.copy(alpha = 0.45f),
                notch = accent.copy(alpha = 0.55f),
            )
        },
        scrimTop = Color(0x99000000),
        scrimBottom = Color(0xE6000000),
        hub = Color(0xFF0B0F14),
        hubContent = Color(0xFF00E5FF),
        emptySlot = Color(0x3300E5FF),
        label = Color(0xFFE0FBFF),
    )
}

private fun glass(): WheelPalette {
    val style = RingStyle(
        track = { SolidColor(Color(0x1FFFFFFF)) },
        rim = Color(0x66FFFFFF),
        glow = Color(0x14FFFFFF),
        notch = Color(0x4DFFFFFF),
    )
    return WheelPalette(
        rings = Ring.entries.associateWith { style },
        scrimTop = Color(0x1A000000),
        scrimBottom = Color(0x66000000),
        hub = Color(0x33FFFFFF),
        hubContent = Color.White,
        emptySlot = Color(0x26FFFFFF),
        label = Color.White,
    )
}

private fun dynamic(scheme: ColorScheme): WheelPalette {
    fun style(container: Color, accent: Color, onContainer: Color) = RingStyle(
        track = { SolidColor(container.copy(alpha = 0.88f)) },
        rim = accent,
        glow = accent.copy(alpha = 0.25f),
        notch = onContainer.copy(alpha = 0.35f),
    )
    return WheelPalette(
        rings = mapOf(
            Ring.INNER to style(scheme.tertiaryContainer, scheme.tertiary, scheme.onTertiaryContainer),
            Ring.MIDDLE to style(scheme.secondaryContainer, scheme.secondary, scheme.onSecondaryContainer),
            Ring.OUTER to style(scheme.primaryContainer, scheme.primary, scheme.onPrimaryContainer),
        ),
        scrimTop = scheme.surface.copy(alpha = 0.25f),
        scrimBottom = scheme.surface.copy(alpha = 0.7f),
        hub = scheme.primary,
        hubContent = scheme.onPrimary,
        emptySlot = scheme.onSurface.copy(alpha = 0.15f),
        label = scheme.onSurface,
    )
}
