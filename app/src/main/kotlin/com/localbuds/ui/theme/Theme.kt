package com.localbuds.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.localbuds.core.Appearance

/*
 * LocalBuds themes:
 * - Frosted Glass (default): Cool charcoal dark acrylic with silver-ice frosting
 * - Aurora: Emerald-to-cyan glow with frosted glass
 * - System: Dynamic wallpaper-based Material You palette (Android 12+)
 */

/** Rounded pill shapes for the Frosted Glass / Aurora aesthetic. */
private val GlassShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Soft Aurora shapes. */
private val AuroraShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Accessor for the roles Material has no slot for, next to `MaterialTheme.colorScheme`. */
object LocalBudsTheme {
    val colors: LocalBudsColors
        @Composable @ReadOnlyComposable get() = LocalLocalBudsColors.current

    val style: LocalBudsStyle
        @Composable @ReadOnlyComposable get() = LocalLocalBudsStyle.current

    /** A card's fill: glass where the look has one, else the scheme's `surfaceContainer`. */
    val cardFill: Color
        @Composable @ReadOnlyComposable get() = colors.cardFill.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.surfaceContainer

    /** The outline of a chosen chip or tile. */
    val chosenOutline: Color
        @Composable @ReadOnlyComposable get() = colors.accent
}

@Composable
fun LocalBudsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appearance: Appearance = Appearance.FROSTED_GLASS,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val aurora = appearance == Appearance.AURORA
    val frostedGlass = appearance == Appearance.FROSTED_GLASS
    val dynamic = appearance == Appearance.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme: ColorScheme = when {
        frostedGlass -> FrostedGlassDarkScheme
        aurora -> if (darkTheme) AuroraDarkScheme else AuroraLightScheme
        dynamic -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> FrostedGlassDarkScheme
    }
    val colors = when {
        frostedGlass -> FrostedGlassDarkColors
        aurora -> if (darkTheme) AuroraDarkColors else AuroraLightColors
        dynamic -> LocalBudsColors.fromScheme(colorScheme)
        else -> FrostedGlassDarkColors
    }
    val style = when {
        frostedGlass -> LocalBudsStyle.Glass
        aurora -> LocalBudsStyle.Aurora
        else -> LocalBudsStyle.System
    }
    val shapes: Shapes = if (aurora) AuroraShapes else GlassShapes
    val typography: Typography = AuroraTypography

    CompositionLocalProvider(
        LocalLocalBudsColors provides colors,
        LocalLocalBudsStyle provides style,
        LocalContentColor provides colorScheme.onBackground,
    ) {
        MaterialTheme(colorScheme = colorScheme, shapes = shapes, typography = typography, content = content)
    }
}
