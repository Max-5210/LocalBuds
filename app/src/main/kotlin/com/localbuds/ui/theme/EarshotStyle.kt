package com.localbuds.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How a look draws the things Material has no word for: the shape of its controls, its backdrop
 * and whether short titles are set in capitals. Colours live in [LocalBudsColors], shapes in the
 * Material `Shapes`, fonts in the `Typography`; this is the rest. Read it through
 * `LocalBudsTheme.style`.
 */
@Immutable
data class LocalBudsStyle(
    val squareControls: Boolean,
    val tickDial: Boolean,
    val capsLegends: Boolean,
    val backdrop: Backdrop,
    val isGlass: Boolean = false,
) {
    enum class Backdrop {
        DOTS,
        GLOW,
    }

    companion object {
        val Glass = LocalBudsStyle(squareControls = false, tickDial = false, capsLegends = false, backdrop = Backdrop.GLOW, isGlass = true)
        val Aurora = LocalBudsStyle(squareControls = false, tickDial = false, capsLegends = false, backdrop = Backdrop.GLOW, isGlass = true)
        val System = LocalBudsStyle(squareControls = false, tickDial = false, capsLegends = false, backdrop = Backdrop.GLOW, isGlass = false)
    }
}

val LocalLocalBudsStyle = staticCompositionLocalOf { LocalBudsStyle.Glass }

/**
 * A short title, legend or name as the current look sets it. For display only: never
 * use it to normalise data (Bluetooth addresses keep `uppercase()`).
 */
@Composable
@ReadOnlyComposable
fun String.legend(): String = if (LocalBudsTheme.style.capsLegends) uppercase() else this
