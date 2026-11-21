package com.localbuds.shortcut

import android.util.Log
import com.localbuds.core.ButtonAction
import com.localbuds.core.ButtonAssignment
import com.localbuds.core.HeadphoneRepository
import com.localbuds.core.HeadphoneState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the Bose Shortcut button in the mode the remapper's choice needs ([ShortcutButtonPolicy]),
 * so the user never sets it up by hand.
 */
class ShortcutButtonSync(
    private val headphones: HeadphoneRepository,
    /** The saved settings ([ShortcutPrefs.current] in the app; a lambda so tests need no DataStore). */
    private val settings: suspend () -> ShortcutSettings,
    private val scope: CoroutineScope,
    /** Log sink; `android.util.Log` is a throwing stub in plain JVM tests. */
    private val log: (String) -> Unit = { Log.i(TAG, it) },
) {
    private data class Readiness(val address: String?, val ready: Boolean)

    /** The address already handled in the current connection (null while disconnected). */
    private var handledAddress: String? = null

    /**
     * Primary long-press Bose button record (`eventType` 9/5/10 preferred).
     */
    fun button(state: HeadphoneState): ButtonAssignment? {
        val all = buttons(state)
        return all.firstOrNull { it.eventType in LONG_PRESS_EVENTS } ?: all.firstOrNull()
    }

    /** Every `[1.9]` record (Left bud first, then Right bud), each with its remembered supported modes. */
    fun buttons(state: HeadphoneState): List<ButtonAssignment> = state.allShortcutButtons.map { withSupport(state, it) }

    /** Long-press `[1.9]` records only. */
    fun longPressButtons(state: HeadphoneState): List<ButtonAssignment> {
        val all = buttons(state)
        val longOnes = all.filter { it.eventType in LONG_PRESS_EVENTS }
        return longOnes.ifEmpty { all }
    }

    private fun withSupport(state: HeadphoneState, b: ButtonAssignment): ButtonAssignment {
        val address = state.connection.deviceAddress?.uppercase() ?: return b
        val key = (address to b.buttonId) to b.eventType
        if (b.supportedActions.isNotEmpty()) supportedByAddress[key] = b.supportedActions
        return ShortcutButtonPolicy.withKnownSupport(b, supportedByAddress[key])
    }

    /** Last non-empty supported list per headset, button, and event type. */
    private val supportedByAddress = ConcurrentHashMap<Pair<Pair<String, Int>, Int>, List<ButtonAction>>()

    fun start() {
        scope.launch {
            headphones.state
                .map { s -> Readiness(s.connection.deviceAddress.takeIf { s.isConnected }, s.isConnected && button(s) != null && !s.isBusy) }
                .distinctUntilChanged()
                .collect { r ->
                    if (r.address == null) { handledAddress = null; return@collect }
                    if (!r.ready || handledAddress.equals(r.address, ignoreCase = true)) return@collect
                    handledAddress = r.address
                    reapply(r.address)
                }
        }
    }

    private suspend fun reapply(address: String) {
        val saved = runCatching { settings() }.getOrNull() ?: return
        val state = headphones.state.value
        val primary = button(state)
        val multiRecord = state.allShortcutButtons.size > 1
        ShortcutButtonPolicy.writeOnConnect(saved, address, primary)?.let { mode ->
            log("setting the shortcut button to ${mode.name} for the saved choice")
            writeOnConnect {
                if (multiRecord && primary != null) headphones.setShortcutButton(primary.buttonId, primary.eventType, mode)
                else headphones.setShortcutButton(mode)
            }
        }
        // Secondary long-press buds/buttons: apply rich secondaryAction if saved, else legacy buttonChoice.
        for (b in longPressButtons(headphones.state.value).filter { it != primary }) {
            val rich = saved.secondaryActionFor(address, b.buttonId)
            val mode = if (rich != null) {
                ShortcutButtonPolicy.writeFor(rich, b)
            } else {
                SecondaryButtonPolicy.writeFor(saved.buttonChoiceFor(address, b.buttonId), b)
            } ?: continue
            log("setting button 0x%02x to ${mode.name} for the saved choice".format(b.buttonId))
            writeOnConnect { headphones.setShortcutButton(b.buttonId, b.eventType, mode) }
        }
    }

    private suspend fun writeOnConnect(write: suspend () -> Unit) {
        try {
            write()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("shortcut button write on connect failed: ${e.message}")
        }
    }

    /**
     * Writes [mode] to the secondary `[1.9]` record [buttonId] (with its own event type), if it
     * differs. Throws `HeadphoneCommandException` when refused.
     */
    suspend fun applySecondary(buttonId: Int, mode: ButtonAction) {
        val state = headphones.state.value
        if (!state.isConnected) return
        val b = longPressButtons(state).firstOrNull { it.buttonId == buttonId }
            ?: buttons(state).firstOrNull { it.buttonId == buttonId }
            ?: return
        if (b.action == mode || (b.supportedActions.isNotEmpty() && mode !in b.supportedActions)) return
        log("setting button 0x%02x to ${mode.name}".format(buttonId))
        headphones.setShortcutButton(b.buttonId, b.eventType, mode)
    }

    /** Writes the wire mode needed for [action] on secondary bud [buttonId]. */
    suspend fun applySecondaryAction(buttonId: Int, action: ShortcutAction) {
        val state = headphones.state.value
        if (!state.isConnected) return
        val b = longPressButtons(state).firstOrNull { it.buttonId == buttonId }
            ?: buttons(state).firstOrNull { it.buttonId == buttonId }
            ?: return
        val mode = ShortcutButtonPolicy.writeFor(action, b) ?: return
        log("setting button 0x%02x (event ${b.eventType}) to ${mode.name}".format(buttonId))
        headphones.setShortcutButton(b.buttonId, b.eventType, mode)
    }

    /**
     * Writes the mode [choice] needs to the primary connected headphones button, if it differs.
     */
    suspend fun applyChoice(choice: ShortcutAction) {
        val state = headphones.state.value
        if (!state.isConnected) return
        val primary = button(state) ?: return
        val mode = ShortcutButtonPolicy.writeFor(choice, primary) ?: return
        log("setting the shortcut button 0x%02x to ${mode.name}".format(primary.buttonId))
        if (state.allShortcutButtons.size > 1) {
            headphones.setShortcutButton(primary.buttonId, primary.eventType, mode)
        } else {
            headphones.setShortcutButton(mode)
        }
    }

    companion object {
        private const val TAG = "LocalBuds/Shortcut"
        val LONG_PRESS_EVENTS = setOf(5, 9, 10, 11, 12)
    }
}
