package com.localbuds.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance

/**
 * The colour roles Material 3 has no slot for. Read through `LocalBudsTheme.colors`.
 */
@Immutable
data class LocalBudsColors(
    val accent: Color,
    val onAccent: Color,
    /** The battery dial at a healthy level. */
    val dial: Color,
    /** The active part of a slider track. */
    val fill: Color,
    /** The inactive part of a track; the segmented control's ground. */
    val track: Color,
    /** 1 dp borders on cards and controls. */
    val hairline: Color,
    /** Status chips. */
    val chip: Color,
    val onChip: Color,
    /** The play button and the chosen segment. */
    val control: Color,
    val onControl: Color,
    /** Dot grid tint (unused in glass/glow themes, kept for backdrop compatibility). */
    val dotGrid: Color = Color.Transparent,
    /** Success text and ticks. */
    val ok: Color,
    /** Gradient brushes for lit accents, sliders, and toggles. */
    val accentBrush: Brush = SolidColor(accent),
    val controlBrush: Brush = SolidColor(control),
    val fillBrush: Brush = SolidColor(fill),
    /** Specular rim border brush for glass cards. */
    val cardBorderBrush: Brush = SolidColor(hairline),
    /** Ends of the accent gradient for sweep brushes (battery dial). */
    val gradientFrom: Color = accent,
    val gradientTo: Color = accent,
    /** Soft halo behind lit controls. */
    val glow: Color = Color.Transparent,
    /** Translucent card fill; Unspecified = Material `surfaceContainer`. */
    val cardFill: Color = Color.Unspecified,
    /** Tab bar indicator and icon tint. */
    val navIndicator: Color = accent,
    val onNavIndicator: Color = onAccent,
    /** Corner backdrop glows behind every screen. */
    val glows: List<BackdropGlow> = emptyList(),
) {
    companion object {
        /** Roles derived from a dynamic Material You wallpaper scheme (System colours). */
        fun fromScheme(scheme: ColorScheme): LocalBudsColors = LocalBudsColors(
            accent = scheme.primary,
            onAccent = scheme.onPrimary,
            dial = scheme.primary,
            fill = scheme.primary,
            track = scheme.surfaceVariant,
            hairline = scheme.outlineVariant,
            chip = scheme.inverseSurface,
            onChip = scheme.inverseOnSurface,
            control = scheme.primary,
            onControl = scheme.onPrimary,
            dotGrid = Color.Transparent,
            ok = if (scheme.surface.luminance() < 0.5f) Color(0xFF7DD99A) else Color(0xFF1B7F3B),
        )
    }
}

val LocalLocalBudsColors = staticCompositionLocalOf { FrostedGlassDarkColors }
