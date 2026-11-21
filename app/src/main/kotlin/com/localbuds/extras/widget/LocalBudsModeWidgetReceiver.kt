package com.localbuds.extras.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.localbuds.appContainer
import com.localbuds.extras.ExtrasInitializer

/** Manifest entry point for the 4×1 quick mode control bar widget (`@xml/localbuds_mode_widget_info`). */
class LocalBudsModeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LocalBudsModeWidget()

    override fun onReceive(context: Context, intent: Intent) {
        runCatching { ExtrasInitializer.start(context, context.appContainer) }
        super.onReceive(context, intent)
    }
}
