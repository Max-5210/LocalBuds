package com.localbuds.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.localbuds.ui.home.HomeScreen
import com.localbuds.ui.modes.ModesScreen
import com.localbuds.ui.more.AboutScreen
import com.localbuds.ui.more.DevicesScreen
import com.localbuds.ui.more.PreferencesScreen
import com.localbuds.ui.tools.ShortcutsRemapperScreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Streamlined navigation graph: opens directly onto the single, ultra-responsive
 * Bose-style main control screen.
 */
@Composable
fun EarshotNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
    tabRequests: StateFlow<NavRequest?>,
    onTabRequestHandled: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { fadeOut(tween(120)) },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    contentPadding = contentPadding,
                    onEditModes = { navController.navigate(Routes.MODES) },
                    onOpenPreferences = { navController.navigate(Routes.PREFERENCES) },
                    onOpenShortcuts = { navController.navigate(Routes.SHORTCUTS_REMAPPER) },
                    onOpenDevices = { navController.navigate(Routes.DEVICES) },
                )
            }
            composable(Routes.MODES) {
                ModesScreen(onBack = navController::popBackStack)
            }
            composable(Routes.SHORTCUTS_REMAPPER) {
                ShortcutsRemapperScreen(
                    onBack = navController::popBackStack,
                    onOpenDevices = { navController.navigate(Routes.DEVICES) }
                )
            }
            composable(Routes.PREFERENCES) {
                PreferencesScreen(onBack = navController::popBackStack)
            }
            composable(Routes.DEVICES) {
                DevicesScreen(onBack = navController::popBackStack)
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = navController::popBackStack)
            }
        }
    }

    LaunchedEffect(navController, tabRequests) {
        tabRequests.filterNotNull().collect { request ->
            request.route?.let { route ->
                runCatching { navController.navigate(route) { launchSingleTop = true } }
            }
            onTabRequestHandled()
        }
    }
}
