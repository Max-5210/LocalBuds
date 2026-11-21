package com.localbuds.extras.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.localbuds.appContainer
import com.localbuds.extras.ExtrasInitializer

/** Manifest entry point for the home-screen widget (`@xml/localbuds_widget_info`). */
class LocalBudsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LocalBudsWidget()

    override fun onReceive(context: Context, intent: Intent) {
        // A widget update may be the first thing that wakes the process; make sure the
        // refreshers are running so the widget does not stay frozen until the next 30-min tick.
        runCatching { ExtrasInitializer.start(context, context.appContainer) }
        super.onReceive(context, intent)
    }
}
