package com.localbuds.ui.home

import androidx.lifecycle.viewModelScope
import com.localbuds.R
import com.localbuds.core.ButtonAction
import com.localbuds.core.ButtonPress
import com.localbuds.core.HeadphoneCandidate
import com.localbuds.core.HeadphoneEq
import com.localbuds.core.HeadphoneRepository
import com.localbuds.core.HeadphoneState
import com.localbuds.core.NoiseSettings
import com.localbuds.core.SpatialAudio
import com.localbuds.core.TransportControl
import com.localbuds.ui.common.LocalBudsViewModel
import com.localbuds.ui.common.UiMessenger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class HomeViewModel(
    private val headphones: HeadphoneRepository,
    messenger: UiMessenger,
) : LocalBudsViewModel(messenger) {

    private data class OptimisticValue<T>(val value: T, val token: Long)

    private data class OptimisticPatch(
        val modeIndex: OptimisticValue<Int>? = null,
        val noise: OptimisticValue<NoiseSettings>? = null,
        val eq: OptimisticValue<HeadphoneEq>? = null,
        val multipoint: OptimisticValue<Boolean>? = null,
        val autoPlayPause: OptimisticValue<Boolean>? = null,
        val touchControls: OptimisticValue<Boolean>? = null,
        val volume: OptimisticValue<Int>? = null,
    )

    private val nextToken = AtomicLong(0L)
    private val optimistic = MutableStateFlow(OptimisticPatch())

    private inner class OptimisticLane<T>(
        private val read: (OptimisticPatch) -> OptimisticValue<T>?,
        private val write: (OptimisticPatch, OptimisticValue<T>?) -> OptimisticPatch,
        private val onDispatchPatch: (OptimisticPatch) -> OptimisticPatch = { it },
    ) {
        private var job: Job? = null

        val hasActiveJob: Boolean get() = job?.isActive == true

        fun dispatch(
            value: T,
            debounceMs: Long = 0L,
            command: suspend () -> Unit,
        ) {
            val token = nextToken.incrementAndGet()
            optimistic.update { patch ->
                onDispatchPatch(write(patch, OptimisticValue(value, token)))
            }
            job?.cancel()
            job = viewModelScope.launch {
                if (debounceMs > 0L) delay(debounceMs)
                try {
                    command()
                    clearIfTokenMatches(token)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    clearIfTokenMatches(token)
                    messenger.report(e)
                }
            }
        }

        private fun clearIfTokenMatches(token: Long) {
            optimistic.update { patch ->
                if (read(patch)?.token == token) write(patch, null) else patch
            }
        }

        fun clearNow() {
            job?.cancel()
            job = null
            optimistic.update { patch -> write(patch, null) }
        }

        fun cancel() {
            job?.cancel()
            job = null
        }
    }

    private val modeLane = OptimisticLane(
        read = { it.modeIndex },
        write = { p, v -> p.copy(modeIndex = v) },
        onDispatchPatch = { it.copy(noise = null) },
    )
    private val noiseLane = OptimisticLane(
        read = { it.noise },
        write = { p, v -> p.copy(noise = v) },
        onDispatchPatch = { it.copy(modeIndex = null) },
    )
    private val eqLane = OptimisticLane(
        read = { it.eq },
        write = { p, v -> p.copy(eq = v) },
    )
    private val multipointLane = OptimisticLane(
        read = { it.multipoint },
        write = { p, v -> p.copy(multipoint = v) },
    )
    private val autoPlayPauseLane = OptimisticLane(
        read = { it.autoPlayPause },
        write = { p, v -> p.copy(autoPlayPause = v) },
    )
    private val touchControlsLane = OptimisticLane(
        read = { it.touchControls },
        write = { p, v -> p.copy(touchControls = v) },
    )
    private val volumeLane = OptimisticLane(
        read = { it.volume },
        write = { p, v -> p.copy(volume = v) },
    )

    private val allLanes = listOf(
        modeLane, noiseLane, eqLane, multipointLane, autoPlayPauseLane, touchControlsLane, volumeLane,
    )

    val state: StateFlow<HeadphoneState> = combine(headphones.state, optimistic) { repo, patch ->
        mergeOptimistic(repo, patch)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, headphones.state.value)

    init {
        val initial = headphones.state.value.lastButtonPress?.atMillis
        viewModelScope.launch {
            headphones.state.map { it.lastButtonPress }.filterNotNull()
                .distinctUntilChanged { a, b -> a.atMillis == b.atMillis }
                .filter { it.atMillis != initial }
                .collect { press -> messenger.show(R.string.button_press_line, buttonName(press), eventName(press), press.action.label) }
        }
        viewModelScope.launch {
            headphones.state.map { it.isConnected }.distinctUntilChanged().collect { connected ->
                if (!connected) {
                    allLanes.forEach { it.cancel() }
                    optimistic.value = OptimisticPatch()
                }
            }
        }
        // When physical earbuds push a mode or spatial change while no local write is in flight,
        // clear any leftover optimistic overlay so sliders immediately animate to match hardware.
        viewModelScope.launch {
            headphones.state.map { it.currentModeIndex to it.noise }.distinctUntilChanged().collect {
                if (!modeLane.hasActiveJob && !noiseLane.hasActiveJob) {
                    optimistic.update { p ->
                        if (p.modeIndex == null && p.noise == null) p else p.copy(modeIndex = null, noise = null)
                    }
                }
            }
        }
    }

    private fun mergeOptimistic(repo: HeadphoneState, patch: OptimisticPatch): HeadphoneState {
        if (!repo.isConnected) return repo
        var next = repo
        patch.modeIndex?.let { optMode ->
            val modeIdx = optMode.value
            next = next.copy(currentModeIndex = modeIdx)
            if (patch.noise == null) {
                val slotNoise = repo.modes.firstOrNull { it.index == modeIdx }?.settings
                if (slotNoise != null) {
                    next = next.copy(noise = slotNoise)
                } else {
                    val base = repo.noise ?: NoiseSettings()
                    val previewSpatial = when (modeIdx) {
                        0, 1 -> SpatialAudio.OFF
                        2 -> base.spatialAudio.takeIf { it != SpatialAudio.OFF } ?: SpatialAudio.HEAD
                        else -> base.spatialAudio
                    }
                    next = next.copy(noise = base.copy(spatialAudio = previewSpatial))
                }
            }
        }
        patch.noise?.let { next = next.copy(noise = it.value) }
        patch.eq?.let { next = next.copy(eq = it.value) }
        patch.multipoint?.let { next = next.copy(multipointEnabled = it.value) }
        patch.autoPlayPause?.let { next = next.copy(autoPlayPause = it.value) }
        patch.touchControls?.let { next = next.copy(touchControlsEnabled = it.value) }
        patch.volume?.let { next = next.copy(volume = it.value) }
        return next
    }

    fun connect() = headphones.connect()
    fun refresh() = launchCatching { headphones.refresh() }

    /** The header's device switcher: remember [address] as the headphones and connect to it. */
    fun chooseDevice(address: String, name: String?) = launchCatching { headphones.chooseDevice(address, name) }

    /** Bonded devices for the switcher; an empty list (not an error) when Bluetooth can't be read. */
    suspend fun candidates(): List<HeadphoneCandidate> = runCatching { headphones.deviceCandidates() }.getOrDefault(emptyList())

    fun setMode(index: Int) {
        modeLane.dispatch(index) { headphones.setMode(index) }
    }

    fun toggleFavorite(index: Int) = launchCatching {
        val current = headphones.state.value.modes.filter { it.isFavorite }.map { it.index }.toSet()
        headphones.setFavorites(if (index in current) current - index else current + index)
    }

    /** [31.10] applies immediately; slider drags coalesce with [debounceMs] and revert only on write error. */
    fun setNoise(settings: NoiseSettings, debounceMs: Long = 0L) {
        noiseLane.dispatch(settings, debounceMs) { headphones.setNoise(settings) }
    }

    fun setSpatialAudio(mode: SpatialAudio) {
        val current = state.value.noise ?: NoiseSettings()
        setNoise(current.copy(spatialAudio = mode))
    }

    fun setEq(eq: HeadphoneEq, debounceMs: Long = 0L) {
        eqLane.dispatch(eq, debounceMs) { headphones.setEq(eq) }
    }

    /**
     * Deterministic EQ preset selection by name: looks up the canonical [com.localbuds.core.EqPreset]
     * by [presetName] and unconditionally writes its exact `(bass, mid, treble)` values to `[1.7]`.
     */
    fun selectEqPreset(presetName: String) {
        val preset = com.localbuds.core.EqPreset.byName(presetName) ?: return
        setEq(preset.toHeadphoneEq(), 0L)
    }

    fun setMultipoint(enabled: Boolean) {
        multipointLane.dispatch(enabled) { headphones.setMultipoint(enabled) }
    }

    fun setAutoPlayPause(enabled: Boolean) {
        autoPlayPauseLane.dispatch(enabled) { headphones.setAutoPlayPause(enabled) }
    }

    fun setTouchControls(enabled: Boolean) {
        touchControlsLane.dispatch(enabled) { headphones.setTouchControls(enabled) }
    }

    fun setShortcutButton(action: ButtonAction) = launchCatching { headphones.setShortcutButton(action) }
    fun setVendorControl(id: String, value: Int) = launchCatching { headphones.setVendorControl(id, value) }

    fun transport(control: TransportControl) = launchCatching { headphones.transport(control) }

    fun setVolume(level: Int, debounceMs: Long = 0L) {
        volumeLane.dispatch(level, debounceMs) { headphones.setVolume(level) }
    }

    private fun buttonName(press: ButtonPress): String = press.buttonLabel ?: "Button ${press.buttonId}"
    private fun eventName(press: ButtonPress): String = (press.eventLabel ?: "event ${press.eventType}").lowercase()
}
