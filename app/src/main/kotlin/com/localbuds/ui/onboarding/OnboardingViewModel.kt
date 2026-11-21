package com.localbuds.ui.onboarding

import com.localbuds.core.HeadphoneRepository
import com.localbuds.ui.common.LocalBudsViewModel
import com.localbuds.ui.common.UiMessenger

/**
 * The onboarding's commands. They must not run in the screen's own coroutine scope: picking a
 * headset hands the connection to that headset's stack, whose idle state (DISCONNECTED) opens
 * the root's gate and disposes this screen while `chooseDevice` is still saving the pick (a
 * DataStore write). A composition scope was cancelled right there, so the connect never started
 * and the cancellation came up as a snackbar. The onboarding sits outside the NavHost, so this
 * ViewModel belongs to the Activity and outlives the screen; [launchCatching] also keeps a
 * cancellation out of the snackbar.
 */
class OnboardingViewModel(
    private val headphones: HeadphoneRepository,
    messenger: UiMessenger,
) : LocalBudsViewModel(messenger) {

    fun chooseDevice(address: String, name: String?) {
        launchCatching { headphones.chooseDevice(address, name) }
    }
}
