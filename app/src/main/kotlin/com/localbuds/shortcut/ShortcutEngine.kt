package com.localbuds.shortcut

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.localbuds.MainActivity
import com.localbuds.R
import com.localbuds.bt.DevicePrefs
import com.localbuds.core.AudioTuningRepository
import com.localbuds.core.ButtonAction
import com.localbuds.core.ButtonPress
import com.localbuds.core.HeadphoneCommandException
import com.localbuds.core.HeadphoneEq
import com.localbuds.core.HeadphoneRepository
import com.localbuds.core.HeadphoneState
import com.localbuds.core.ModeResolver
import com.localbuds.core.NoiseSettings
import com.localbuds.core.SpatialAudio
import com.localbuds.dsp.EqPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Runs a [ShortcutAction]. Called by [ShortcutTriggerActivity] (the headset gesture) with a
 * foreground [Activity], or directly by [HeadphoneService] when BMAP `[2.12]` button push events arrive.
 */
class ShortcutEngine(
    private val context: Context,
    val prefs: ShortcutPrefs,
    private val tuning: AudioTuningRepository,
    private val headphones: HeadphoneRepository,
    private val devicePrefs: DevicePrefs,
    private val scope: CoroutineScope,
) {
    val assistants = AssistantDirectory(context)
    val headsets = HeadsetLookup(context)
    val feedback = SpokenFeedback(context)
    val buttonSync = ShortcutButtonSync(headphones, prefs::current, scope)

    /** "Detect a button" on the Controls screen; the trigger reports to it instead of running while it listens. */
    val detector = ControlDetector(scope, headphones.state, AndroidControlFeeds.all(context), log = { Log.i(TAG, it) })

    init {
        buttonSync.start()
        keepHeadsetTriggerInStep()
    }

    /** The `VOICE_COMMAND` entry is on only while a choice or a detection needs it (see [HeadsetTrigger]). */
    private fun keepHeadsetTriggerInStep() {
        combine(prefs.settings, detector.session.map { it.listening }.distinctUntilChanged()) { settings, detecting ->
            HeadsetTrigger.needed(settings, detecting)
        }
            .distinctUntilChanged()
            .onEach { needed ->
                if (HeadsetTrigger.isEnabled(context) != needed) {
                    runCatching { HeadsetTrigger.setEnabled(context, needed) }
                        .onSuccess { Log.i(TAG, "headset trigger ${if (needed) "on" else "off"}") }
                        .onFailure { Log.w(TAG, "could not switch the headset trigger: $it") }
                }
            }
            .launchIn(scope)
    }

    /** The experimental wired / USB-C trigger (the disabled-by-default activity-alias; no switch on the screen for now). */
    var wiredTriggerEnabled: Boolean
        get() = WiredTrigger.isEnabled(context)
        set(value) = WiredTrigger.setEnabled(context, value)

    /** A real headset press reached the trigger. In the app scope: the trigger activity finishes at once. */
    fun markHeadsetPressSeen() {
        scope.launch { runCatching { prefs.markHeadsetPressSeen() }.onFailure { Log.w(TAG, "could not store the first press: $it") } }
    }

    /**
     * The trigger fired while [detector] listens: works out the headset in the app scope (the
     * activity has already finished) and reports the gesture.
     */
    fun reportAssistantGesture(trigger: Intent?, wired: Boolean) {
        val at = System.currentTimeMillis()
        scope.launch {
            val present = runCatching { if (wired) headsets.wiredHeadsetPresent() else headsets.anyHfpConnected() != false }.getOrDefault(false)
            if (!present) {
                Log.w(TAG, "detect: gesture without a connected headset ignored")
                return@launch
            }
            val presser = if (wired) null else runCatching { headsets.resolve(trigger, savedAddress()) }.getOrNull()
                ?.takeIf { it.source != HeadsetSource.SAVED }?.address
            Log.i(TAG, "detect: assistant gesture from ${presser ?: if (wired) "a wired headset" else "an unknown headset"}")
            detector.report(ControlSignal.AssistantGesture(presser, wired, at))
            rememberDetection(presser ?: detector.session.value.headsetAddress, at)
        }
    }

    /** Demo mode's "Assistant gesture" button: the same report path, labelled as a demo. */
    fun simulateAssistantGesture() {
        val at = System.currentTimeMillis()
        detector.report(ControlSignal.AssistantGesture(null, wired = false, at = at, origin = SignalOrigin.DEMO))
        scope.launch { rememberDetection(detector.session.value.headsetAddress, at) }
    }

    private suspend fun rememberDetection(address: String?, at: Long) {
        if (address == null) return
        runCatching { prefs.markShortcutDetected(address, at) }.onFailure { Log.w(TAG, "could not store the detection: $it") }
    }

    /** The saved headphones' address, the last resort of [HeadsetResolution]. */
    suspend fun savedAddress(): String? =
        connectedBoseAddress() ?: runCatching { devicePrefs.savedDevice()?.address }.getOrNull()

    /** Address of the currently connected Bose headphones, if any. */
    fun connectedBoseAddress(): String? =
        headphones.state.value.takeIf { it.isConnected }?.connection?.deviceAddress

    /** True if Bose headphones are actively connected over BMAP. */
    fun isBoseConnected(): Boolean = headphones.state.value.isConnected

    /** True if a BMAP `[2.12]` button press already executed the shortcut action within [windowMs]. */
    fun wasRecentlyExecutedByBmap(windowMs: Long = 1_500L): Boolean {
        val at = lastBmapExecutedAt
        if (at <= 0L) return false
        return (SystemClock.elapsedRealtime() - at) in 0..windowMs
    }

    /** Button ID of the most recent BMAP `[2.12]` press within [windowMs], if any. */
    fun recentBmapButtonId(windowMs: Long = 3_000L): Int? {
        val at = lastBmapPressAt
        if (at <= 0L) return null
        return lastBmapButtonId.takeIf { (SystemClock.elapsedRealtime() - at) in 0..windowMs }
    }

    /**
     * Resolves which [ShortcutAction] should run for a headset trigger at [address], accounting
     * for both primary (Left Earbud Shortcut 1) and secondary (Right Earbud Shortcut 2) actions.
     */
    fun resolveTriggeredAction(settings: ShortcutSettings, address: String?): ShortcutAction {
        val state = headphones.state.value
        val effectiveAddress = address ?: state.connection.deviceAddress
        val primaryId = buttonSync.button(state)?.buttonId
        val buttons = buttonSync.longPressButtons(state)
        return settings.resolveVoiceTriggerAction(
            address = effectiveAddress,
            pressedButtonId = recentBmapButtonId(),
            primaryButtonId = primaryId,
            buttons = buttons,
        )
    }

    /**
     * Runs [action] from [activity]. [onDone] is called once the activity may finish.
     */
    fun run(
        activity: Activity,
        action: ShortcutAction,
        settings: ShortcutSettings,
        trigger: Intent?,
        headsetAddress: String?,
        onDone: () -> Unit,
    ) {
        Log.i(TAG, "running ${action.javaClass.simpleName}")
        when (action) {
            ShortcutAction.CycleModes -> {
                acknowledgeIfConfigured(headsetAddress)
                scope.launch {
                    try {
                        val modeName = cycleModes()
                        delay(500L)
                        feedback.say(modeName ?: "Listening modes unavailable", settings.spokenFeedback)
                    } catch (e: Exception) {
                        Log.w(TAG, "cycle modes failed: $e")
                        feedback.toast(e.message ?: context.getString(R.string.error_generic))
                    }
                }
                onDone()
            }
            ShortcutAction.CycleEq -> {
                acknowledgeIfConfigured(headsetAddress)
                scope.launch {
                    try {
                        val preset = cycleEq()
                        val label = preset?.name ?: context.getString(R.string.shortcut_no_presets)
                        feedback.toast("EQ: $label")
                        delay(500L)
                        feedback.say(label, settings.spokenFeedback)
                    } catch (e: Exception) {
                        Log.w(TAG, "cycle EQ failed: $e")
                        feedback.toast(e.message ?: context.getString(R.string.error_generic))
                    }
                }
                onDone()
            }
            ShortcutAction.VoiceMemo -> {
                val hasMic = ensureMicPermission(activity)
                if (!hasMic && VoiceMemoService.state.value == VoiceMemoService.MemoState.IDLE) {
                    acknowledgeIfConfigured(headsetAddress)
                    if (!launchExternalVoiceRecorder(activity)) {
                        feedback.toast(context.getString(R.string.memo_no_permission))
                        openEarshot(activity)
                    }
                } else {
                    VoiceMemoService.toggle(activity, settings.spokenFeedback, headsetAddress)
                }
                onDone()
            }
            is ShortcutAction.LaunchApp -> {
                acknowledgeIfConfigured(headsetAddress)
                launchApp(activity, action, onDone)
            }
            is ShortcutAction.OpenAssistant -> {
                forwardToAssistant(activity, action, trigger)
                onDone()
            }
            is ShortcutAction.HeadphoneButton -> {
                acknowledgeIfConfigured(headsetAddress)
                scope.launch {
                    runCatching { executeNativeFallback(action.mode, settings.spokenFeedback) }
                }
                onDone()
            }
        }
    }

    private fun ensureMicPermission(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        if (com.localbuds.root.RootShell.isActive()) {
            scope.launch {
                runCatching {
                    com.localbuds.root.RootShell.exec("pm grant ${context.packageName} android.permission.RECORD_AUDIO")
                }
            }
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun launchExternalVoiceRecorder(activity: Activity): Boolean {
        val recordIntent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolved = activity.packageManager.resolveActivity(recordIntent, 0)?.activityInfo
        if (resolved != null && resolved.packageName != context.packageName) {
            recordIntent.component = ComponentName(resolved.packageName, resolved.name)
            return runCatching { activity.startActivity(recordIntent); true }.getOrDefault(false)
        }
        return false
    }

    private suspend fun executeNativeFallback(storedMode: Int, spokenFeedback: Boolean) {
        when (ButtonAction.fromStoredId(storedMode)) {
            ButtonAction.MODES_CAROUSEL, ButtonAction.ANC_CYCLE -> {
                val modeName = cycleModes()
                if (modeName != null) {
                    delay(500L)
                    feedback.say(modeName, spokenFeedback)
                }
            }
            ButtonAction.SPATIAL_AUDIO -> {
                if (headphones.state.value.isConnected) {
                    val base = headphones.state.value.noise ?: NoiseSettings()
                    val next = when (base.spatialAudio) {
                        SpatialAudio.OFF -> SpatialAudio.ROOM
                        SpatialAudio.ROOM -> SpatialAudio.HEAD
                        SpatialAudio.HEAD -> SpatialAudio.OFF
                    }
                    headphones.setNoise(base.copy(spatialAudio = next))
                }
            }
            else -> Unit
        }
    }

    private fun acknowledgeIfConfigured(headsetAddress: String?) {
        @Suppress("KotlinConstantConditions", "SimplifyBooleanWithConstants")
        if (!HfpVoiceRecognition.ACKNOWLEDGE_VOICE_REQUEST || headsetAddress == null) {
            Log.i(TAG, "voice request left to time out (acknowledge off or no headset)")
            return
        }
        scope.launch { HfpVoiceRecognition.acknowledge(context, headsetAddress) }
    }

    /**
     * Handles BMAP `[2.12]` `STATUS_BUTTON` push events directly inside `HeadphoneService`:
     * supports `CLIENT_INTERACTION` (15), `VOICE_ASSISTANT` (1), `MODES_CAROUSEL` (17), and
     * `ANC_CYCLE` (2) for long-press gestures so each earbud (`Left 0x04` vs `Right 0x03`)
     * resolves and runs its configured [ShortcutAction] (including infinite mode cycling and
     * 500ms-delayed TTS announcement).
     */
    suspend fun handleBmapButtonPress(press: ButtonPress, state: HeadphoneState) {
        if (press.eventType !in ShortcutButtonSync.LONG_PRESS_EVENTS) return
        val allowedActions = setOf(
            ButtonAction.CLIENT_INTERACTION,
            ButtonAction.VOICE_ASSISTANT,
            ButtonAction.MODES_CAROUSEL,
            ButtonAction.ANC_CYCLE,
        )
        if (press.action !in allowedActions) return

        val now = SystemClock.elapsedRealtime()
        lastBmapButtonId = press.buttonId
        lastBmapPressAt = now
        if (now - lastBmapHandledAt < BMAP_DEBOUNCE_MS) return
        lastBmapHandledAt = now

        val settings = runCatching { prefs.current() }.getOrDefault(ShortcutSettings())
        val address = state.connection.deviceAddress
        val primaryId = buttonSync.button(state)?.buttonId
        val configuredAction = settings.actionForButton(address, press.buttonId, primaryId)
        val action: ShortcutAction = when {
            press.action == ButtonAction.MODES_CAROUSEL || press.action == ButtonAction.ANC_CYCLE -> {
                if (configuredAction == ShortcutAction.CycleModes || !settings.hasExplicitChoice(address)) {
                    ShortcutAction.CycleModes
                } else {
                    return
                }
            }
            else -> configuredAction
        }
        Log.i(TAG, "[2.12] ${press.action} on button=0x%02x event=${press.eventType} -> $action".format(press.buttonId))
        if (press.action == ButtonAction.VOICE_ASSISTANT && action !is ShortcutAction.OpenAssistant && action != ShortcutAction.VoiceMemo) {
            acknowledgeIfConfigured(address)
        }
        lastBmapExecutedAt = SystemClock.elapsedRealtime()
        executeFromService(action, settings, address)
    }

    private suspend fun executeFromService(action: ShortcutAction, settings: ShortcutSettings, address: String?) {
        when (action) {
            ShortcutAction.CycleModes -> {
                val modeName = runCatching { cycleModes() }.getOrNull()
                delay(500L)
                feedback.say(modeName ?: "Listening modes unavailable", settings.spokenFeedback)
            }
            ShortcutAction.CycleEq -> {
                val preset = runCatching { cycleEq() }.getOrNull()
                val label = preset?.name ?: context.getString(R.string.shortcut_no_presets)
                feedback.toast("EQ: $label")
                delay(500L)
                feedback.say(label, settings.spokenFeedback)
            }
            ShortcutAction.VoiceMemo -> {
                ensureMicPermission(context)
                launchInternalTrigger(action, address)
            }
            is ShortcutAction.OpenAssistant -> {
                val target = assistants.forwardTarget(action.component)
                val intent = target?.let { assistants.forwardIntent(it, null) }
                if (intent == null) {
                    feedback.toast(context.getString(R.string.shortcut_no_assistant))
                    return
                }
                com.localbuds.root.RootTweaks.wakeForOverlay(dismissKeyguard = true)
                val launched = runCatching { context.startActivity(intent); true }.getOrDefault(false)
                if (!launched) {
                    val rootOk = com.localbuds.root.RootTweaks.startActivityViaRoot(intent)
                    if (!rootOk) launchInternalTrigger(action, address)
                }
            }
            is ShortcutAction.LaunchApp -> {
                val pkg = action.packageName
                if (pkg.isNullOrBlank()) {
                    feedback.toast(context.getString(R.string.shortcut_no_app))
                    return
                }
                val launch = resolveLaunchIntent(pkg)
                if (launch == null) {
                    feedback.toast(context.getString(R.string.shortcut_app_missing))
                    return
                }
                val keyguard = context.getSystemService(KeyguardManager::class.java)
                if (keyguard?.isKeyguardLocked == true) {
                    if (com.localbuds.root.RootShell.isActive()) {
                        com.localbuds.root.RootTweaks.wakeForOverlay(dismissKeyguard = true)
                        val started = runCatching { context.startActivity(launch); true }.getOrDefault(false)
                        if (!started && !com.localbuds.root.RootTweaks.startActivityViaRoot(launch)) {
                            launchInternalTrigger(action, address)
                        }
                    } else {
                        launchInternalTrigger(action, address)
                    }
                } else {
                    val launched = runCatching { context.startActivity(launch); true }.getOrDefault(false)
                    if (!launched && !com.localbuds.root.RootTweaks.startActivityViaRoot(launch)) {
                        launchInternalTrigger(action, address)
                    }
                }
            }
            is ShortcutAction.HeadphoneButton -> {
                runCatching { executeNativeFallback(action.mode, settings.spokenFeedback) }
            }
        }
    }

    private fun resolveLaunchIntent(pkg: String): Intent? {
        if (pkg == context.packageName) return null
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return null
        if (launch.component == null) {
            val resolved = context.packageManager.resolveActivity(launch, 0)?.activityInfo
            if (resolved != null) {
                launch.component = ComponentName(resolved.packageName, resolved.name)
            }
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return launch
    }

    private fun launchInternalTrigger(action: ShortcutAction, address: String?) {
        val intent = Intent(context, ShortcutTriggerActivity::class.java).apply {
            action.let { putExtra(ShortcutTriggerActivity.EXTRA_ACTION_JSON, ShortcutCodec.encodeAction(it)) }
            address?.let { putExtra(BluetoothDeviceExtra.EXTRA_ADDRESS, it) }
            putExtra(TriggerCaller.EXTRA_INTERNAL, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private object BluetoothDeviceExtra {
        const val EXTRA_ADDRESS = "android.bluetooth.device.extra.ADDRESS"
    }

    /**
     * Cycles strictly through the three core built-in modes in exact order:
     * `Quiet (0) -> Aware (1) -> Immersion (2)` (stopping at Immersion and wrapping back to Quiet
     * without ever advancing into custom mode slots 3/4+ or touching Spatial Audio balance).
     */
    suspend fun cycleModes(): String? {
        val state = headphones.state.value
        val pool = ModeResolver.orderedCycleModes(state)
        if (pool.isEmpty()) return null

        val currentIdx = ModeResolver.resolveEffectiveModeIndex(state, pool, lastCycledModeIndex)
        val at = if (currentIdx != null) pool.indexOf(currentIdx) else -1

        for (offset in 1..pool.size) {
            val candidateIdx = pool[(at + offset) % pool.size]
            if (candidateIdx !in 0..2) continue
            val switched = try {
                headphones.setMode(candidateIdx)
                true
            } catch (e: Exception) {
                Log.w(TAG, "cycleModes: slot $candidateIdx rejected (${e.message}), advancing to next core slot")
                false
            }
            if (switched) {
                lastCycledModeIndex = candidateIdx
                return state.modes.firstOrNull { it.index == candidateIdx }?.name?.ifBlank { "Mode ${candidateIdx + 1}" }
            }
        }
        return null
    }

    /**
     * Cycles to the next EQ preset using [com.localbuds.core.EqPreset] as the single deterministic
     * source of truth: picks the next preset in order (`Bose QC Ultra -> Bass Boost -> Game ->
     * Treble Sparkle -> Vocal Clarity`), looks up that exact preset by name, writes its exact
     * `(bass, mid, treble)` values to `[1.7]`, syncs the DSP curve, and returns its exact name.
     */
    suspend fun cycleEq(): EqPreset? {
        val nextChoice = com.localbuds.core.EqPreset.nextAfter(
            currentEq = headphones.state.value.eq,
            currentName = lastCycledEqPresetName,
        )
        val canonical = com.localbuds.core.EqPreset.byName(nextChoice.label) ?: nextChoice
        lastCycledEqPresetName = canonical.label

        if (headphones.state.value.isConnected) {
            headphones.setEq(canonical.toHeadphoneEq())
        }
        if (tuning.state.value.isAvailable) {
            if (!tuning.state.value.settings.enabled) {
                runCatching { tuning.setEnabled(true) }
            }
            val matchingDsp = tuning.presets.value.firstOrNull {
                it.name.equals(canonical.label, ignoreCase = true) ||
                    (canonical == com.localbuds.core.EqPreset.BOSE_QC_ULTRA && it.id == "flat")
            }
            if (matchingDsp != null) {
                runCatching { tuning.applyPreset(matchingDsp.id) }
            }
        }
        return EqPreset(
            id = canonical.name.lowercase(),
            name = canonical.label,
            bands = emptyList(),
        )
    }

    private fun launchApp(activity: Activity, action: ShortcutAction.LaunchApp, onDone: () -> Unit) {
        val pkg = action.packageName
        val launch = pkg?.let { resolveLaunchIntent(it) }
        if (launch == null) {
            feedback.toast(context.getString(if (pkg == null) R.string.shortcut_no_app else R.string.shortcut_app_missing))
            onDone(); return
        }
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) {
            if (com.localbuds.root.RootShell.isActive()) {
                com.localbuds.root.RootTweaks.wakeForOverlay(dismissKeyguard = true)
                start(activity, launch)
                onDone()
                return
            }
            keyguard.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { start(activity, launch); onDone() }
                override fun onDismissCancelled() = onDone()
                override fun onDismissError() = onDone()
            })
        } else {
            start(activity, launch)
            onDone()
        }
    }

    private fun forwardToAssistant(activity: Activity, action: ShortcutAction.OpenAssistant, trigger: Intent?) {
        val target = assistants.forwardTarget(action.component)
        val intent = target?.let { assistants.forwardIntent(it, trigger) }
        if (target == null || intent == null) {
            feedback.toast(context.getString(R.string.shortcut_no_assistant))
            return
        }
        Log.i(TAG, "forwarding to ${target.component} (${target.kind})")
        start(activity, intent)
    }

    private fun start(activity: Activity, intent: Intent) {
        val component = intent.component
        if (component == null || component.packageName == context.packageName) {
            Log.w(TAG, "refusing to start $component")
            return
        }
        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "start failed: $e")
            if (com.localbuds.root.RootShell.isActive()) {
                scope.launch {
                    if (!com.localbuds.root.RootTweaks.startActivityViaRoot(intent)) {
                        feedback.toast(context.getString(R.string.shortcut_start_failed))
                    }
                }
            } else {
                feedback.toast(context.getString(R.string.shortcut_start_failed))
            }
        }
    }

    private fun openEarshot(activity: Activity) {
        val intent = Intent(activity, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TAB, "tools")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { activity.startActivity(intent) }
    }

    @Volatile
    private var lastBmapHandledAt = 0L

    @Volatile
    private var lastBmapPressAt = 0L

    @Volatile
    private var lastBmapButtonId: Int? = null

    @Volatile
    private var lastBmapExecutedAt = 0L

    @Volatile
    private var lastCycledModeIndex: Int? = null

    @Volatile
    private var lastCycledEqPresetName: String? = null

    private companion object {
        const val TAG = "LocalBuds/Shortcut"
        const val BMAP_DEBOUNCE_MS = 350L
    }
}
