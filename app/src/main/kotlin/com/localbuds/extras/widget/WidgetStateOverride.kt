package com.localbuds.extras.widget

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.localbuds.core.HeadphoneState
import com.localbuds.core.SpatialAudio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Persistent & reactive active-button state for LocalBuds AppWidgets.
 *
 * When the user taps any mode or spatial button on a home-screen widget, the tapped selection
 * is persisted immediately to SharedPreferences (`localbuds_widget_state`) and emitted on [flow]
 * before any Bluetooth round-trip begins, and only yields to hardware state once a genuine new
 * mode transition arrives from the headphones or app.
 */
internal object WidgetStateOverride {
    const val PREFS_NAME = "localbuds_widget_state"
    private const val KEY_MODE_INDEX = "active_mode_index"
    private const val KEY_SPATIAL_WIRE = "active_spatial_wire"
    private const val KEY_TAP_SEQ = "tap_seq"
    private const val KEY_CACHED_CUSTOM_MODES = "cached_custom_modes"
    private const val OVERRIDE_HOLD_MS = 15_000L

    val GLANCE_SEQ_KEY = longPreferencesKey("widget_tap_seq")
    val GLANCE_MODE_KEY = intPreferencesKey("widget_active_mode")
    val GLANCE_SPATIAL_KEY = intPreferencesKey("widget_active_spatial")

    data class Snapshot(
        val seq: Long = 0L,
        val tappedModeIndex: Int? = null,
        val tappedModeAtMs: Long = 0L,
        val tappedSpatial: SpatialAudio? = null,
        val tappedSpatialAtMs: Long = 0L,
        val cachedCustomModes: List<Pair<Int, String>> = emptyList(),
    )

    private val seqCounter = AtomicLong(System.currentTimeMillis())
    private val _flow = MutableStateFlow(Snapshot())
    val flow: StateFlow<Snapshot> = _flow.asStateFlow()

    @Volatile
    private var lastObservedHardwareMode: Int? = null

    @Volatile
    private var lastObservedHardwareSpatial: SpatialAudio? = null

    fun ensureLoaded(context: Context): Snapshot {
        val current = _flow.value
        if (current.seq != 0L) return current
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedMode = prefs.getInt(KEY_MODE_INDEX, -1).takeIf { it >= 0 }
        val savedSpatial = prefs.getInt(KEY_SPATIAL_WIRE, -1).takeIf { it >= 0 }?.let { SpatialAudio.fromWire(it) }
        val savedSeq = prefs.getLong(KEY_TAP_SEQ, 1L)
        val savedCustomModes = decodeCustomModes(prefs.getString(KEY_CACHED_CUSTOM_MODES, null))
        val loaded = Snapshot(
            seq = savedSeq,
            tappedModeIndex = savedMode,
            tappedModeAtMs = if (savedMode != null) System.currentTimeMillis() else 0L,
            tappedSpatial = savedSpatial,
            tappedSpatialAtMs = if (savedSpatial != null) System.currentTimeMillis() else 0L,
            cachedCustomModes = savedCustomModes,
        )
        _flow.value = loaded
        return loaded
    }

    /** Called immediately inside the widget tap callback before any Bluetooth command runs. */
    fun recordModeTap(context: Context, index: Int): Snapshot {
        val now = System.currentTimeMillis()
        val nextSeq = seqCounter.incrementAndGet()
        val cur = ensureLoaded(context)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_MODE_INDEX, index)
            .remove(KEY_SPATIAL_WIRE)
            .putLong(KEY_TAP_SEQ, nextSeq)
            .commit()
        val snap = cur.copy(
            seq = nextSeq,
            tappedModeIndex = index,
            tappedModeAtMs = now,
            tappedSpatial = null,
            tappedSpatialAtMs = 0L,
        )
        _flow.value = snap
        return snap
    }

    /** Called immediately inside the widget spatial-button tap callback. */
    fun recordSpatialTap(context: Context, mode: SpatialAudio): Snapshot {
        val now = System.currentTimeMillis()
        val nextSeq = seqCounter.incrementAndGet()
        val cur = ensureLoaded(context)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_SPATIAL_WIRE, mode.wire)
            .putLong(KEY_TAP_SEQ, nextSeq)
            .commit()
        val snap = cur.copy(
            seq = nextSeq,
            tappedSpatial = mode,
            tappedSpatialAtMs = now,
        )
        _flow.value = snap
        return snap
    }

    /**
     * Called when [HeadphoneState] emits a new snapshot from the app or physical earbuds.
     * When hardware reports a genuine change in `currentModeIndex`, `spatialAudio`, or configured modes,
     * syncs the persisted widget highlight and cached mode list to match the hardware.
     */
    fun onHardwareStateChanged(context: Context, state: HeadphoneState) {
        val hwMode = state.currentModeIndex?.takeIf { it != 0xFF }
        val hwSpatial = state.noise?.spatialAudio
        val cur = ensureLoaded(context)
        val now = System.currentTimeMillis()

        var updatedMode = cur.tappedModeIndex
        var updatedModeAt = cur.tappedModeAtMs
        if (hwMode != null && hwMode != lastObservedHardwareMode) {
            lastObservedHardwareMode = hwMode
            // If no tap is within the hold window, or hardware confirmed/changed mode, sync it
            if (cur.tappedModeIndex == null || hwMode == cur.tappedModeIndex || now - cur.tappedModeAtMs > 1_500L) {
                updatedMode = hwMode
                updatedModeAt = now
            }
        }

        var updatedSpatial = cur.tappedSpatial
        var updatedSpatialAt = cur.tappedSpatialAtMs
        if (hwSpatial != null && hwSpatial != lastObservedHardwareSpatial) {
            lastObservedHardwareSpatial = hwSpatial
            if (cur.tappedSpatial == null || hwSpatial == cur.tappedSpatial || now - cur.tappedSpatialAtMs > 1_500L) {
                updatedSpatial = hwSpatial
                updatedSpatialAt = now
            }
        }

        val liveCustomModes = extractConfiguredCustomModes(state)
        val updatedCustomModes = if (state.modes.isNotEmpty() && liveCustomModes.isNotEmpty()) {
            liveCustomModes
        } else {
            cur.cachedCustomModes
        }

        if (
            updatedMode != cur.tappedModeIndex ||
            updatedSpatial != cur.tappedSpatial ||
            updatedCustomModes != cur.cachedCustomModes
        ) {
            val nextSeq = seqCounter.incrementAndGet()
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                if (updatedMode != null) putInt(KEY_MODE_INDEX, updatedMode)
                if (updatedSpatial != null) putInt(KEY_SPATIAL_WIRE, updatedSpatial.wire)
                if (updatedCustomModes != cur.cachedCustomModes) {
                    putString(KEY_CACHED_CUSTOM_MODES, encodeCustomModes(updatedCustomModes))
                }
                putLong(KEY_TAP_SEQ, nextSeq)
                apply()
            }
            _flow.value = Snapshot(
                seq = nextSeq,
                tappedModeIndex = updatedMode,
                tappedModeAtMs = updatedModeAt,
                tappedSpatial = updatedSpatial,
                tappedSpatialAtMs = updatedSpatialAt,
                cachedCustomModes = updatedCustomModes,
            )
        }
    }

    /**
     * Always returns the 3 primary built-in mode slots (`Quiet`, `Aware`, `Immersion`),
     * falling back to indices `0, 1, 2` when `state.modes` is empty or unavailable.
     */
    fun resolvedPrimaryModes(state: HeadphoneState): List<Pair<Int, String>> {
        val quietIndex = state.modes.firstOrNull { it.name.equals("Quiet", ignoreCase = true) }?.index ?: 0
        val awareIndex = state.modes.firstOrNull { it.name.equals("Aware", ignoreCase = true) }?.index ?: 1
        val immersionIndex = state.modes.firstOrNull { it.name.equals("Immersion", ignoreCase = true) }?.index ?: 2
        return listOf(
            quietIndex to "Quiet",
            awareIndex to "Aware",
            immersionIndex to "Immersion",
        )
    }

    /**
     * Returns configured custom modes from live [state] when available, falling back to
     * [Snapshot.cachedCustomModes] so custom mode buttons never disappear when one earbud is in
     * the case, during a mode transition, or while disconnected.
     */
    fun resolvedCustomModes(state: HeadphoneState, snap: Snapshot = _flow.value): List<Pair<Int, String>> {
        val live = extractConfiguredCustomModes(state)
        return if (live.isNotEmpty()) live else snap.cachedCustomModes
    }

    /**
     * Resolves Left and Right earbud battery percentages independently:
     * - When disconnected: returns `null to null` (both display `--%`).
     * - When per-bud battery detail is present (including single-earbud mode where one bud is in the case
     *   and reports `null`): returns `left?.percent to right?.percent` WITHOUT copying the active bud's
     *   percentage onto the bud inside the case.
     * - Otherwise falls back to `state.batteryPercent to state.batteryPercent`.
     */
    fun resolvedBudBatteries(state: HeadphoneState): Pair<Int?, Int?> {
        if (!state.isConnected) return null to null
        val b = state.battery
        if (b != null && (b.left != null || b.right != null)) {
            return b.left?.percent to b.right?.percent
        }
        return state.batteryPercent to state.batteryPercent
    }

    /**
     * Always formats both Left and Right earbud battery slots (`"L 85% · R --%"` in single-bud mode,
     * `"L --% · R --%"` when disconnected).
     */
    fun resolvedBatteryHeaderText(state: HeadphoneState): String {
        val (left, right) = resolvedBudBatteries(state)
        val lText = left?.let { "$it%" } ?: "--%"
        val rText = right?.let { "$it%" } ?: "--%"
        return "L $lText · R $rText"
    }

    private fun extractConfiguredCustomModes(state: HeadphoneState): List<Pair<Int, String>> {
        if (state.modes.isEmpty()) return emptyList()
        val quietIndex = state.modes.firstOrNull { it.name.equals("Quiet", ignoreCase = true) }?.index ?: 0
        val awareIndex = state.modes.firstOrNull { it.name.equals("Aware", ignoreCase = true) }?.index ?: 1
        val immersionIndex = state.modes.firstOrNull { it.name.equals("Immersion", ignoreCase = true) }?.index ?: 2
        val primaryIndices = setOf(quietIndex, awareIndex, immersionIndex)
        return state.modes
            .filter {
                it.isConfigured &&
                    it.index !in primaryIndices &&
                    it.name.isNotBlank() &&
                    !it.name.equals("None", ignoreCase = true)
            }
            .sortedBy { it.index }
            .map { it.index to it.name.trim() }
    }

    private fun encodeCustomModes(modes: List<Pair<Int, String>>): String =
        modes.joinToString("\n") { (idx, name) -> "$idx:${name.replace('\n', ' ')}" }

    private fun decodeCustomModes(raw: String?): List<Pair<Int, String>> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence()
            .mapNotNull { line ->
                val sep = line.indexOf(':')
                if (sep <= 0) return@mapNotNull null
                val idx = line.substring(0, sep).trim().toIntOrNull() ?: return@mapNotNull null
                val name = line.substring(sep + 1).trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                idx to name
            }
            .toList()
    }

    fun effectiveSpatial(state: HeadphoneState, snap: Snapshot = _flow.value): SpatialAudio {
        val tapped = snap.tappedSpatial
        if (tapped != null && (System.currentTimeMillis() - snap.tappedSpatialAtMs <= OVERRIDE_HOLD_MS || state.noise?.spatialAudio == null)) {
            return tapped
        }
        return state.noise?.spatialAudio ?: tapped ?: SpatialAudio.OFF
    }

    /**
     * Returns the highlighted spatial mode when connected, or `null` when disconnected so
     * disconnected widget buttons render in their greyed-out disabled state without an active highlight.
     */
    fun resolvedSelectedSpatial(state: HeadphoneState, snap: Snapshot = _flow.value): SpatialAudio? {
        if (!state.isConnected) return null
        return effectiveSpatial(state, snap)
    }

    /**
     * Resolves the single active mode index for widget highlighting.
     * Returns `null` when disconnected so all mode buttons render in their greyed-out disabled state.
     * When connected, prioritizes the user's tapped mode immediately, falling back to live [HeadphoneState].
     */
    fun resolvedSelectedModeIndex(state: HeadphoneState, snap: Snapshot = _flow.value): Int? {
        if (!state.isConnected) return null
        val tapped = snap.tappedModeIndex
        val now = System.currentTimeMillis()
        if (tapped != null && (now - snap.tappedModeAtMs <= OVERRIDE_HOLD_MS || state.currentModeIndex == null || state.currentModeIndex == 0xFF)) {
            return tapped
        }

        val raw = state.currentModeIndex
        if (raw != null && raw != 0xFF) return raw
        if (tapped != null) return tapped

        val quietIndex = state.modes.firstOrNull { it.name.equals("Quiet", ignoreCase = true) }?.index ?: 0
        val awareIndex = state.modes.firstOrNull { it.name.equals("Aware", ignoreCase = true) }?.index ?: 1
        val immersionIndex = state.modes.firstOrNull { it.name.equals("Immersion", ignoreCase = true) }?.index ?: 2

        val spatial = effectiveSpatial(state, snap)
        return when {
            spatial != SpatialAudio.OFF -> immersionIndex
            state.noise?.ancEnabled == false || (state.noise?.cancellingLevel ?: 10) <= 2 -> awareIndex
            raw == 0xFF -> quietIndex
            else -> quietIndex
        }
    }
}
