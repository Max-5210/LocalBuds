package com.localbuds.ui.common

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Tactile Android HapticFeedback helper for crisp detents, pill drags, toggles, and confirmations.
 */
class LocalBudsHaptics(private val view: View) {

    /** Crisp detent tick on slider step changes and sliding pill compartment crossings. */
    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Clean tactile press on button taps, pill selection, and switch toggles. */
    fun click() {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** Tactile confirmation when a drag snaps to rest or a mode switches. */
    fun confirm() {
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        view.performHapticFeedback(code)
    }
}

@Composable
fun rememberHaptics(): LocalBudsHaptics {
    val view = LocalView.current
    return remember(view) { LocalBudsHaptics(view) }
}
