package com.localbuds.ui.more

import com.localbuds.core.HeadphoneCommandException
import com.localbuds.ui.MainDispatcherRule
import com.localbuds.ui.RecordingHeadphones
import com.localbuds.ui.collectMessages
import com.localbuds.ui.common.UiMessage
import com.localbuds.ui.common.UiMessenger
import com.localbuds.ui.storeHolding
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Preferences actions that restart the connection (audit R42). */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesActionsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private fun actions(
        repo: RecordingHeadphones,
        messenger: UiMessenger,
        saveFamily: suspend (String?) -> Unit = { repo.calls += "family $it" },
    ) = PreferencesActionsViewModel(repo, saveFamily, messenger)

    @Test
    fun `each change is saved, then the connection restarts`() = runTest {
        val repo = RecordingHeadphones()
        val vm = actions(repo, UiMessenger())

        vm.setFamilyOverride("sony")
        advanceUntilIdle()

        assertEquals(
            listOf("family sony", "disconnect", "connect"),
            repo.calls,
        )
    }

    @Test
    fun `a failed forget is reported and keeps the old connection`() = runTest {
        val messenger = UiMessenger()
        val messages = collectMessages(messenger)
        val repo = RecordingHeadphones(onForget = { throw HeadphoneCommandException("No headphones are saved yet") })

        actions(repo, messenger).forgetPairing()
        advanceUntilIdle()

        assertEquals(emptyList(), repo.calls)
        assertEquals(listOf<UiMessage>(UiMessage.Text("No headphones are saved yet")), messages)
    }

    @Test
    fun `a forget that works reconnects so the next connect pairs again`() = runTest {
        val repo = RecordingHeadphones()

        actions(repo, UiMessenger()).forgetPairing()
        advanceUntilIdle()

        assertEquals(listOf("forget", "disconnect", "connect"), repo.calls)
    }

    @Test
    fun `a storage failure becomes a message, not a crash`() = runTest {
        val messenger = UiMessenger()
        val messages = collectMessages(messenger)
        val repo = RecordingHeadphones()

        actions(repo, messenger, saveFamily = { throw IOException("No space left on device") }).setFamilyOverride("bose")
        advanceUntilIdle()

        assertEquals(emptyList(), repo.calls)
        assertEquals(listOf<UiMessage>(UiMessage.Text("No space left on device")), messages)
    }

    @Test
    fun `leaving the screen mid-write still restarts the connection`() = runTest {
        val messenger = UiMessenger()
        val messages = collectMessages(messenger)
        val repo = RecordingHeadphones()
        val written = CompletableDeferred<Unit>()
        val vm = actions(repo, messenger, saveFamily = { written.await(); repo.calls += "family $it" })
        val store = storeHolding(vm)

        vm.setFamilyOverride("bose")
        store.clear() // back pressed while DataStore is still writing
        written.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("family bose", "disconnect", "connect"), repo.calls)
        assertTrue(messages.isEmpty(), "a cancellation is not an error: $messages")
    }
}
