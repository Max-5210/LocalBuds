package com.localbuds.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * Frosted Glass Theme:
 * Sleek translucent multi-layered dark acrylic frosting with crisp silver-ice
 * specular borders, deep AMOLED contrast, and refined illumination.
 */

private val FrostedCharcoal = Color(0xFF0A0D14)
private val FrostedDarkSurface = Color(0xFF111620)
private val FrostedCardTranslucent = Color(0xCC18202D)
private val FrostedSilverIce = Color(0xFFF1F5F9)
private val FrostedAccentSteel = Color(0xFFCBD5E1)

val FrostedGlassSpecularBorder = Brush.verticalGradient(
    listOf(Color(0x5CFFFFFF), Color(0x16FFFFFF))
)

val FrostedGlassAccentGradient = Brush.linearGradient(
    listOf(FrostedSilverIce, FrostedAccentSteel)
)

val FrostedGlassDarkScheme: ColorScheme = darkColorScheme(
    primary = FrostedSilverIce,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E293B),
    onPrimaryContainer = FrostedSilverIce,
    inversePrimary = Color(0xFF64748B),
    secondary = FrostedAccentSteel,
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF222B3A),
    onSecondaryContainer = FrostedSilverIce,
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF261800),
    tertiaryContainer = Color(0xFF3D2A05),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = FrostedCharcoal,
    onBackground = Color.White,
    surface = FrostedDarkSurface,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E2634),
    onSurfaceVariant = Color(0xFFA8B3C5),
    surfaceContainerLowest = FrostedCharcoal,
    surfaceContainerLow = Color(0xFF0F141D),
    surfaceContainer = Color(0xFF141B26),
    surfaceContainerHigh = Color(0xFF1C2432),
    surfaceContainerHighest = Color(0xFF253040),
    outline = Color(0x3DFFFFFF),
    outlineVariant = Color(0x22FFFFFF),
    error = Color(0xFFF87171),
    onError = Color(0xFF3B0707),
    errorContainer = Color(0xFF4C1010),
    onErrorContainer = Color(0xFFFEE2E2),
)

val FrostedGlassDarkColors = LocalBudsColors(
    accent = FrostedSilverIce,
    onAccent = Color(0xFF0F172A),
    dial = FrostedSilverIce,
    fill = FrostedSilverIce,
    track = Color(0xFF232C3B),
    hairline = Color(0x38FFFFFF),
    chip = Color(0xFF1E2634),
    onChip = Color.White,
    control = FrostedSilverIce,
    onControl = Color(0xFF0F172A),
    dotGrid = Color.Transparent,
    ok = Color(0xFF4ADE80),
    accentBrush = FrostedGlassAccentGradient,
    controlBrush = FrostedGlassAccentGradient,
    fillBrush = FrostedGlassAccentGradient,
    cardBorderBrush = FrostedGlassSpecularBorder,
    gradientFrom = FrostedSilverIce,
    gradientTo = FrostedAccentSteel,
    glow = Color(0x2BFFFFFF),
    cardFill = FrostedCardTranslucent,
    glows = listOf(
        BackdropGlow(color = Color(0x24FFFFFF), x = 0f, y = 0f, radius = 260.dp),
        BackdropGlow(color = Color(0x18CBD5E1), x = 1f, y = 0.85f, radius = 320.dp),
    ),
)
