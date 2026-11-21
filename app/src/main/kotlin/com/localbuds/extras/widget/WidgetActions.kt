package com.localbuds.extras.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import com.localbuds.appContainer
import com.localbuds.core.NoiseSettings
import com.localbuds.core.SpatialAudio
import com.localbuds.extras.ExtrasInitializer

/**
 * Widget button callbacks.
 * Persists the tapped button immediately via [WidgetStateOverride] and forces an instant
 * [WidgetUpdater.update] redraw BEFORE starting the asynchronous Bluetooth BMAP write,
 * and never reverts the tapped highlight in a `finally` block.
 */
class SetModeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val index = parameters[MODE_INDEX] ?: return
        val container = context.appContainer
        ExtrasInitializer.start(context, container)

        // 1. Persist tapped button immediately & force instant AppWidget redraw before BMAP runs
        WidgetStateOverride.recordModeTap(context, index)
        WidgetUpdater.update(context, glanceId)

        // 2. Dispatch BMAP command to earbuds (connecting first if currently disconnected)
        try {
            if (!container.headphones.state.value.isConnected) {
                container.headphones.connect()
            }
            container.headphones.setMode(index)
        } catch (t: Throwable) {
            Log.w(TAG, "Widget setMode($index) failed, attempting direct noise fallback: ${t.message}")
            runCatching {
                val fallbackNoise = container.headphones.state.value.modes
                    .firstOrNull { it.index == index }
                    ?.settings
                if (fallbackNoise != null) {
                    container.headphones.setNoise(fallbackNoise)
                }
            }
        }
        WidgetUpdater.update(context, glanceId)
    }

    companion object {
        val MODE_INDEX = ActionParameters.Key<Int>("mode_index")
        val ADDRESS = ActionParameters.Key<String>("mode_address")
        private const val TAG = "LocalBuds/Widget"

        fun parameters(index: Int, address: String?): ActionParameters =
            if (address == null) actionParametersOf(MODE_INDEX to index)
            else actionParametersOf(MODE_INDEX to index, ADDRESS to address)
    }
}

class SetSpatialAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val wire = parameters[SPATIAL_WIRE] ?: return
        val target = SpatialAudio.fromWire(wire)
        val container = context.appContainer
        ExtrasInitializer.start(context, container)

        // 1. Persist tapped spatial button immediately & force instant AppWidget redraw
        WidgetStateOverride.recordSpatialTap(context, target)
        WidgetUpdater.update(context, glanceId)

        // 2. Dispatch BMAP command to earbuds
        try {
            val base = container.headphones.state.value.noise ?: NoiseSettings()
            container.headphones.setNoise(base.copy(spatialAudio = target))
        } catch (t: Throwable) {
            Log.w(TAG, "Widget setSpatial($target) failed: ${t.message}")
        }
        WidgetUpdater.update(context, glanceId)
    }

    companion object {
        val SPATIAL_WIRE = ActionParameters.Key<Int>("spatial_wire")
        private const val TAG = "LocalBuds/Widget"

        fun parameters(mode: SpatialAudio): ActionParameters = actionParametersOf(SPATIAL_WIRE to mode.wire)
    }
}

class CycleSpatialAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val container = context.appContainer
        ExtrasInitializer.start(context, container)
        val snap = WidgetStateOverride.ensureLoaded(context)
        val current = WidgetStateOverride.effectiveSpatial(container.headphones.state.value, snap)
        val next = when (current) {
            SpatialAudio.OFF -> SpatialAudio.ROOM
            SpatialAudio.ROOM -> SpatialAudio.HEAD
            SpatialAudio.HEAD -> SpatialAudio.OFF
        }
        WidgetStateOverride.recordSpatialTap(context, next)
        WidgetUpdater.update(context, glanceId)

        try {
            val base = container.headphones.state.value.noise ?: NoiseSettings()
            container.headphones.setNoise(base.copy(spatialAudio = next))
        } catch (t: Throwable) {
            Log.w(TAG, "Widget cycleSpatial($next) failed: ${t.message}")
        }
        WidgetUpdater.update(context, glanceId)
    }

    private companion object {
        const val TAG = "LocalBuds/Widget"
    }
}

class ConnectAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val container = context.appContainer
        ExtrasInitializer.start(context, container)
        container.headphones.connect()
        WidgetUpdater.update(context, glanceId)
    }
}
