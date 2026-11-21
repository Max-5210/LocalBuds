package com.localbuds.ui.tools

import androidx.lifecycle.viewModelScope
import com.localbuds.core.ButtonAction
import com.localbuds.core.ButtonAssignment
import com.localbuds.core.HeadphoneRepository
import com.localbuds.core.HeadphoneState
import com.localbuds.extras.HeadphoneLabels
import com.localbuds.shortcut.AssistantCandidate
import com.localbuds.shortcut.ControlSignal
import com.localbuds.shortcut.DetectedShortcut
import com.localbuds.shortcut.DetectionSession
import com.localbuds.shortcut.FirstPressHint
import com.localbuds.shortcut.HandlerStatus
import com.localbuds.shortcut.HfpHeadset
import com.localbuds.shortcut.PhysicalControl
import com.localbuds.shortcut.PressStyle
import com.localbuds.shortcut.SecondaryButtonPolicy
import com.localbuds.shortcut.ShortcutAction
import com.localbuds.shortcut.ShortcutButtonPolicy
import com.localbuds.shortcut.ShortcutButtonSync
import com.localbuds.shortcut.ShortcutEngine
import com.localbuds.shortcut.ShortcutSettings
import com.localbuds.shortcut.SignalOrigin
import com.localbuds.ui.common.LocalBudsViewModel
import com.localbuds.ui.common.UiMessenger
import com.localbuds.virtual.DemoPresses
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The headset the remapper edits: shown at the top, its address keys the saved choice. */
data class RemapperHeadset(
    val address: String,
    val name: String?,
    val earbuds: Boolean,
    /** Connected through Earshot (a Bose headset): its Shortcut button can be set from here. */
    val viaEarshot: Boolean,
) {
    companion object {
        /** Earshot's connected headphones first, else the phone's current HFP headset, else none (the default is edited). */
        fun resolve(state: HeadphoneState, hfp: HfpHeadset?): RemapperHeadset? {
            val address = state.connection.deviceAddress
            if (state.isConnected && address != null) {
                return RemapperHeadset(
                    address, HeadphoneLabels.displayName(state) ?: state.connection.deviceName,
                    HeadphoneLabels.isEarbuds(HeadphoneLabels.formFactor(state)), viaEarshot = true,
                )
            }
            return hfp?.let { RemapperHeadset(it.address, it.name, HeadphoneLabels.isEarbuds(HeadphoneLabels.formFactorFromName(it.name)), viaEarshot = false) }
        }
    }
}

/** Demo mode's "press a control" buttons in the detect sheet. */
enum class DemoPress { PLAY_PAUSE, VOLUME, BUTTON, ASSISTANT }

/** Everything the Controls screen shows. */
data class ShortcutsUiState(
    val handler: HandlerStatus = HandlerStatus.Unknown,
    val settings: ShortcutSettings = ShortcutSettings(),
    /** Null: nothing connected, the dropdown edits the default. */
    val headset: RemapperHeadset? = null,
    /** The Bose Shortcut button `[1.9]`, only for a headset connected through Earshot. */
    val button: ButtonAssignment? = null,
    /** Every `[1.9]` record, primary ([button]) first; earbuds may have one per bud. */
    val buttons: List<ButtonAssignment> = listOfNotNull(button),
    /** Other apps' VOICE_COMMAND / VOICE_ASSIST activities. */
    val assistants: List<AssistantCandidate> = emptyList(),
    /** What "automatic" resolves to on this phone right now. */
    val autoAssistant: AssistantCandidate? = null,
) {
    val choice: ShortcutAction get() = ShortcutButtonPolicy.displayedChoice(settings, headset?.address, button)
    val nativeModes: List<ButtonAction> get() = ShortcutButtonPolicy.nativeOptions(button?.supportedActions)
    val showFirstPressHint: Boolean
        get() = FirstPressHint.visible(choice, handler, settings.seenHeadsetPress) ||
            secondaryButtons.any { FirstPressHint.visible(secondaryChoiceFor(it), handler, settings.seenHeadsetPress) }
    /** Long-press secondary Bose buttons after the first (e.g., Right Earbud when Left Earbud is #1). */
    val secondaryButtons: List<ButtonAssignment> get() = buttons.filter { it.eventType in ShortcutButtonSync.LONG_PRESS_EVENTS }.ifEmpty { buttons }.drop(1)
    /** Two or more Bose buttons on Voice assistant: they run the same Earshot action. */
    val sharesAssistant: Boolean get() = SecondaryButtonPolicy.sharesAssistant(buttons)
    /** The last detection of this headset's assistant gesture, with the user's name for the control. */
    val detected: DetectedShortcut? get() = settings.detectedFor(headset?.address)

    /** Resolves what to show for a secondary bud (prefers explicit rich action, else legacy buttonChoice, else hardware state; never CycleModes or VoiceMemo). */
    fun secondaryChoiceFor(secButton: ButtonAssignment): ShortcutAction {
        settings.secondaryActionFor(headset?.address, secButton.buttonId)
            ?.takeIf { it != ShortcutAction.CycleModes && it != ShortcutAction.VoiceMemo }
            ?.let { return it }
        val legacy = settings.buttonChoiceFor(headset?.address, secButton.buttonId)
        val wire = SecondaryButtonPolicy.displayed(legacy, secButton)
        val fallbackAppAction = choice.takeIf {
            it != ShortcutAction.CycleModes && it != ShortcutAction.VoiceMemo && it !is ShortcutAction.HeadphoneButton
        } ?: ShortcutAction.OpenAssistant()
        return when (wire) {
            ButtonAction.MODES_CAROUSEL, ButtonAction.ANC_CYCLE -> fallbackAppAction
            ButtonAction.SPATIAL_AUDIO, ButtonAction.DISABLED -> ShortcutAction.HeadphoneButton(wire.storedId)
            ButtonAction.CLIENT_INTERACTION -> fallbackAppAction
            ButtonAction.VOICE_ASSISTANT -> fallbackAppAction
            else -> ShortcutAction.HeadphoneButton(wire.storedId)
        }
    }
}

class ShortcutsRemapperViewModel(
    private val engine: ShortcutEngine,
    private val headphones: HeadphoneRepository,
    messenger: UiMessenger,
) : LocalBudsViewModel(messenger) {

    private data class Lookups(
        val handler: HandlerStatus = HandlerStatus.Unknown,
        val assistants: List<AssistantCandidate> = emptyList(),
        val autoAssistant: AssistantCandidate? = null,
        val hfp: HfpHeadset? = null,
    )

    private val lookups = MutableStateFlow(Lookups())

    val state: StateFlow<ShortcutsUiState> = combine(engine.prefs.settings, headphones.state, lookups) { settings, hp, l ->
        val headset = RemapperHeadset.resolve(hp, l.hfp)
        ShortcutsUiState(
            handler = l.handler,
            settings = settings,
            headset = headset,
            button = engine.buttonSync.button(hp).takeIf { headset?.viaEarshot == true },
            buttons = if (headset?.viaEarshot == true) engine.buttonSync.buttons(hp) else emptyList(),
            assistants = l.assistants,
            autoAssistant = l.autoAssistant,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShortcutsUiState())

    /** The "Detect a button" session (shared with the trigger activity through the engine). */
    val detection: StateFlow<DetectionSession> = engine.detector.session

    fun startDetect() = engine.detector.start(state.value.headset?.address)

    fun stopDetect() = engine.detector.stop()

    /** The sheet closed: stop listening and forget the list. */
    fun closeDetect() = engine.detector.clear()

    /** The user's name for the control that sent the gesture, for the headset at the top. */
    fun nameDetected(control: PhysicalControl?, press: PressStyle?, customName: String?) = launchCatching {
        val address = state.value.headset?.address ?: return@launchCatching
        engine.prefs.update { it.withDetectedName(address, control, press, customName) }
    }

    /** A secondary Bose button (earbuds: the other bud): saved for that headset, then written. */
    fun chooseSecondary(buttonId: Int, mode: ButtonAction) = launchCatching {
        val headset = state.value.headset?.takeIf { it.viaEarshot } ?: return@launchCatching
        engine.prefs.update { it.withButtonChoice(headset.address, buttonId, mode.storedId).withSecondaryAction(headset.address, buttonId, null) }
        engine.buttonSync.applySecondary(buttonId, mode)
    }

    /** Full ShortcutAction selection on a secondary bud (e.g., Right Earbud Phone Assistant / LaunchApp / CycleEq). */
    fun chooseSecondaryAction(buttonId: Int, action: ShortcutAction) = launchCatching {
        val headset = state.value.headset ?: return@launchCatching
        val secButton = state.value.buttons.firstOrNull { it.buttonId == buttonId }
        engine.prefs.update { settings ->
            val updated = settings.withSecondaryAction(headset.address, buttonId, action)
            val supported = secButton?.supportedActions.orEmpty().ifEmpty {
                listOf(ButtonAction.VOICE_ASSISTANT, ButtonAction.SPATIAL_AUDIO, ButtonAction.DISABLED)
            }
            val wire = ShortcutButtonPolicy.desiredMode(action, supported, secButton?.unavailableActions.orEmpty())
            if (wire != null) updated.withButtonChoice(headset.address, buttonId, wire.storedId) else updated
        }
        if (headset.viaEarshot) {
            engine.buttonSync.applySecondaryAction(buttonId, action)
        }
    }

    fun simulate(press: DemoPress) {
        val hp = headphones.state.value
        val unit = DemoPresses.current()?.takeIf { hp.isConnected && hp.livePushEnabled }
        val now = System.currentTimeMillis()
        when (press) {
            DemoPress.ASSISTANT -> engine.simulateAssistantGesture()
            DemoPress.PLAY_PAUSE -> if (unit != null) unit.pressPlayPause() else {
                demoPlaying = !demoPlaying
                engine.detector.report(ControlSignal.PlayState(demoPlaying, SignalOrigin.DEMO, now))
            }
            DemoPress.VOLUME -> if (unit != null) unit.swipeVolume(+2) else {
                engine.detector.report(ControlSignal.Volume(demoVolume, demoVolume + 1, SignalOrigin.DEMO, now))
                demoVolume = (demoVolume + 1) % 15
            }
            DemoPress.BUTTON -> unit?.pressMainButton()
        }
    }

    private var demoPlaying = true
    private var demoVolume = 7

    override fun onCleared() {
        engine.detector.clear()
        super.onCleared()
    }

    init {
        headphones.state.map { it.isConnected to it.connection.deviceAddress }.distinctUntilChanged()
            .onEach { refreshHeadset() }
            .launchIn(viewModelScope)
    }

    /** Re-reads the package manager and the connected headset; called on every resume. */
    fun refresh() {
        viewModelScope.launch {
            val fresh = withContext(Dispatchers.IO) {
                Lookups(
                    handler = runCatching { engine.assistants.handlerStatus() }.getOrDefault(HandlerStatus.Unknown),
                    assistants = runCatching { engine.assistants.candidates() }.getOrDefault(emptyList()),
                    autoAssistant = runCatching { engine.assistants.forwardTarget(null) }.getOrNull(),
                    hfp = runCatching { engine.headsets.currentHeadset() }.getOrNull(),
                )
            }
            lookups.update { fresh }
        }
    }

    private fun refreshHeadset() {
        viewModelScope.launch {
            val hfp = runCatching { engine.headsets.currentHeadset() }.getOrNull()
            lookups.update { it.copy(hfp = hfp) }
        }
    }

    /**
     * Saves [action] for the headset at the top (or as the default with none), then puts a Bose
     * Shortcut button on the mode it needs.
     */
    fun choose(action: ShortcutAction) = launchCatching {
        val headset = state.value.headset
        if (action is ShortcutAction.HeadphoneButton && headset?.viaEarshot != true) return@launchCatching
        engine.prefs.update { it.withChoice(headset?.address, headset?.name, action) }
        if (headset?.viaEarshot == true) engine.buttonSync.applyChoice(action)
    }

    fun setSpokenFeedback(enabled: Boolean) = launchCatching { engine.prefs.update { it.copy(spokenFeedback = enabled) } }
}
