package com.localbuds.ui.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.ui.graphics.vector.ImageVector
import com.localbuds.R

/**
 * Deep link target from notification, tile, or widget.
 */
data class NavRequest(val tab: Tab, val route: String? = null)

/**
 * Minimal navigation graph focused entirely on the single, clean Bose main control screen.
 */
object Routes {
    const val HOME = "home"
    const val MODES = "home/modes"
    const val SHORTCUTS_REMAPPER = "home/shortcuts"
    const val PREFERENCES = "home/preferences"
    const val DEVICES = "home/devices"
    const val ABOUT = "home/about"
    const val CONNECTION = "home/connection"
    const val CODEC = "home/codec"
    const val BACKUP = "home/backup"
    const val SOUND = "home"
    const val TOOLS = "home"
    const val MORE = "home"
}

enum class Tab(
    val route: String,
    @param:StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME(Routes.HOME, R.string.tab_home, Icons.Outlined.Headphones, Icons.Filled.Headphones),
    ;

    companion object {
        fun fromExtra(value: String?): Tab = HOME
        fun forRoute(route: String?): Tab = HOME
    }
}
