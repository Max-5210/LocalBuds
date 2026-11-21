package com.localbuds.ui.onboarding

import com.localbuds.core.HeadphoneCommandException
import com.localbuds.ui.MainDispatcherRule
import com.localbuds.ui.RecordingHeadphones
import com.localbuds.ui.collectMessages
import com.localbuds.ui.common.UiMessage
import com.localbuds.ui.common.UiMessenger
import com.localbuds.ui.storeHolding
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The onboarding's commands run outside the screen's composition (audit R03). */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `a pick reaches the repository and its failure becomes a message`() = runTest {
        val messenger = UiMessenger()
        val messages = collectMessages(messenger)
        val picked = RecordingHeadphones()
        OnboardingViewModel(picked, messenger = messenger).chooseDevice("68:F2:1F:12:34:56", "Bose QC Ultra Headphones")
        advanceUntilIdle()
        assertEquals(listOf("choose 68:F2:1F:12:34:56"), picked.calls)

        val refused = RecordingHeadphones(onChoose = { _, _ -> throw HeadphoneCommandException("Bluetooth is off") })
        OnboardingViewModel(refused, messenger = messenger).chooseDevice("68:F2:1F:12:34:56", null)
        advanceUntilIdle()
        assertEquals(listOf<UiMessage>(UiMessage.Text("Bluetooth is off")), messages)
    }

    @Test
    fun `a pick cut short by the Activity going away shows no cancellation snackbar`() = runTest {
        val messenger = UiMessenger()
        val messages = collectMessages(messenger)
        val repo = RecordingHeadphones(onChoose = { _, _ -> awaitCancellation() })
        val vm = OnboardingViewModel(repo, messenger = messenger)
        val store = storeHolding(vm)

        vm.chooseDevice("68:F2:1F:12:34:56", null)
        store.clear()
        advanceUntilIdle()

        assertTrue(messages.isEmpty(), "unexpected messages: $messages")
    }
}
