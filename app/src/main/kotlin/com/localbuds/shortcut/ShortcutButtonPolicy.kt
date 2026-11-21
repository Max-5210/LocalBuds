package com.localbuds.shortcut

import com.localbuds.core.ButtonAction
import com.localbuds.core.ButtonAssignment

/**
 * Which mode the Bose Shortcut button (`[1.9]`) must be in for the remapper's choice. Pure, so
 * every combination is unit-tested.
 *
 * On earbuds/firmware supporting `CLIENT_INTERACTION` (storedId = 15, App shortcut), LocalBuds
 * writes 15 for app-handled actions so long-press pushes `[2.12]` cleanly to `HeadphoneService`
 * without triggering HFP `AT+BVRA` timeout chimes ("duh-duh"). When 15 is absent, it falls back
 * to `VOICE_ASSISTANT` (1) (or `MODES_CAROUSEL` / `ANC_CYCLE` natively for [ShortcutAction.CycleModes]).
 * Only modes the headphones report in their supported list are ever written.
 */
object ShortcutButtonPolicy {
    /** The headphone-native options offered (Cycle Modes, Cycle ANC, Immersive Audio, Off). */
    val NATIVE_MODES: List<ButtonAction> = listOf(
        ButtonAction.MODES_CAROUSEL,
        ButtonAction.ANC_CYCLE,
        ButtonAction.SPATIAL_AUDIO,
        ButtonAction.DISABLED,
    )

    /** The dropdown-shown native modes (CycleModes has its own top-level entry). */
    fun nativeOptions(supported: Collection<ButtonAction>?): List<ButtonAction> =
        listOf(ButtonAction.SPATIAL_AUDIO, ButtonAction.DISABLED).filter { supported != null && it in supported }

    /**
     * The button mode [choice] needs, or null when nothing should be written:
     * - [ShortcutAction.HeadphoneButton]: its own native mode (offered and supported);
     * - [ShortcutAction.CycleModes]: native `MODES_CAROUSEL` (17) or `ANC_CYCLE` (2) if usable, else
     *   `CLIENT_INTERACTION` (15) or `VOICE_ASSISTANT` (1) for local cycling;
     * - Other LocalBuds actions: prefer `CLIENT_INTERACTION` (15) when supported & available,
     *   falling back to `VOICE_ASSISTANT` (1).
     */
    fun desiredMode(
        choice: ShortcutAction,
        supported: Collection<ButtonAction>,
        unavailable: Collection<ButtonAction> = emptyList(),
    ): ButtonAction? {
        fun usable(a: ButtonAction) = a in supported && a !in unavailable
        return when (choice) {
            is ShortcutAction.HeadphoneButton ->
                ButtonAction.fromStoredId(choice.mode).takeIf { it in NATIVE_MODES && it in supported }
            ShortcutAction.CycleModes -> when {
                usable(ButtonAction.CLIENT_INTERACTION) -> ButtonAction.CLIENT_INTERACTION
                usable(ButtonAction.VOICE_ASSISTANT) -> ButtonAction.VOICE_ASSISTANT
                usable(ButtonAction.MODES_CAROUSEL) -> ButtonAction.MODES_CAROUSEL
                usable(ButtonAction.ANC_CYCLE) -> ButtonAction.ANC_CYCLE
                ButtonAction.CLIENT_INTERACTION in supported -> ButtonAction.CLIENT_INTERACTION
                ButtonAction.VOICE_ASSISTANT in supported -> ButtonAction.VOICE_ASSISTANT
                ButtonAction.MODES_CAROUSEL in supported -> ButtonAction.MODES_CAROUSEL
                ButtonAction.ANC_CYCLE in supported -> ButtonAction.ANC_CYCLE
                else -> null
            }
            else -> when {
                usable(ButtonAction.CLIENT_INTERACTION) -> ButtonAction.CLIENT_INTERACTION
                ButtonAction.CLIENT_INTERACTION in supported && ButtonAction.VOICE_ASSISTANT in unavailable ->
                    ButtonAction.CLIENT_INTERACTION
                ButtonAction.VOICE_ASSISTANT in supported -> ButtonAction.VOICE_ASSISTANT
                ButtonAction.CLIENT_INTERACTION in supported -> ButtonAction.CLIENT_INTERACTION
                else -> null
            }
        }
    }

    /** [button] with [remembered] as its supported list when its own is empty (a frame without the mask). */
    fun withKnownSupport(button: ButtonAssignment, remembered: List<ButtonAction>?): ButtonAssignment =
        if (button.supportedActions.isEmpty() && !remembered.isNullOrEmpty()) button.copy(supportedActions = remembered) else button

    /** The write [choice] needs on [button] right now: [desiredMode] when it differs from the current mode, else null. */
    fun writeFor(choice: ShortcutAction, button: ButtonAssignment?): ButtonAction? {
        val b = button ?: return null
        return desiredMode(choice, b.supportedActions, b.unavailableActions)?.takeIf { it != b.action }
    }

    /**
     * Modes that do nothing at all while LocalBuds holds the connection.
     */
    val DEAD_WITH_EARSHOT: Set<ButtonAction> = setOf(ButtonAction.SPOTIFY_TAP)

    fun writeOnConnect(settings: ShortcutSettings, address: String?, button: ButtonAssignment?): ButtonAction? {
        val dead = button?.action in DEAD_WITH_EARSHOT
        if (!settings.hasExplicitChoice(address) && !dead) return null
        return writeFor(settings.actionFor(address), button)
    }

    /**
     * What the remapper shows as selected for the headset at [address].
     */
    fun displayedChoice(settings: ShortcutSettings, address: String?, button: ButtonAssignment?): ShortcutAction {
        if (!settings.hasExplicitChoice(address) && button != null) {
            if (button.action == ButtonAction.MODES_CAROUSEL) return ShortcutAction.CycleModes
            if (button.action in NATIVE_MODES) return ShortcutAction.HeadphoneButton(button.action.storedId)
        }
        return settings.actionFor(address)
    }
}
