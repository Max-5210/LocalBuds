package com.localbuds.extras.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

/**
 * Redraws every placed LocalBuds widget from the current repository + [WidgetStateOverride] state.
 * Bumps Glance's own [PreferencesGlanceStateDefinition] state before calling `update(app, id)` so
 * active Glance sessions are guaranteed to recompose and push fresh RemoteViews to AppWidgetManager.
 */
object WidgetUpdater {
    suspend fun update(context: Context, targetId: GlanceId? = null) {
        val app = context.applicationContext
        val snap = WidgetStateOverride.ensureLoaded(app)
        val manager = runCatching { GlanceAppWidgetManager(app) }.getOrNull()

        suspend fun refreshWidget(widget: GlanceAppWidget) {
            val ids = runCatching {
                manager?.getGlanceIds(widget.javaClass).orEmpty()
            }.getOrDefault(emptyList())
            val allIds = if (targetId != null && targetId !in ids) ids + targetId else ids
            for (id in allIds) {
                runCatching {
                    updateAppWidgetState(app, PreferencesGlanceStateDefinition, id) { prefs ->
                        prefs.toMutablePreferences().apply {
                            this[WidgetStateOverride.GLANCE_SEQ_KEY] = snap.seq
                            if (snap.tappedModeIndex != null) {
                                this[WidgetStateOverride.GLANCE_MODE_KEY] = snap.tappedModeIndex
                            }
                            if (snap.tappedSpatial != null) {
                                this[WidgetStateOverride.GLANCE_SPATIAL_KEY] = snap.tappedSpatial.wire
                            }
                        }
                    }
                    widget.update(app, id)
                }.onFailure {
                    Log.w("LocalBuds/Widget", "widget update failed for ${widget.javaClass.simpleName}", it)
                }
            }
        }

        refreshWidget(LocalBudsWidget())
        refreshWidget(LocalBudsModeWidget())
        refreshWidget(LocalBudsStatusWidget())
    }
}
