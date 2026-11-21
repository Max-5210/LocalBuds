package com.localbuds.extras.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.localbuds.MainActivity
import com.localbuds.R
import com.localbuds.appContainer
import com.localbuds.core.HeadphoneState
import com.localbuds.core.SpatialAudio
import com.localbuds.extras.HeadphoneLabels

/**
 * 4×2 Full-Space Rigid Frosted Glass Dashboard Widget ([SizeMode.Exact]):
 * - Outer container is permanently locked to [GlanceModifier.fillMaxSize()].
 * - Always renders the full dashboard layout (Header, Left & Right earbud PNG battery cards, and 2-row
 *   mode grid) whether connected, using a single earbud, or disconnected.
 * - When one earbud is in the case, shows `--%` for the docked bud while keeping all mode buttons active.
 * - When disconnected, shows `--%` with dimmed battery bars for both buds and renders all mode buttons
 *   in their exact same positions in a muted / greyed-out disabled state with no active highlight.
 */
class LocalBudsStatusWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer
        val initialOverride = WidgetStateOverride.ensureLoaded(context)

        provideContent {
            val prefs = currentState<Preferences>()
            val _seq = prefs[WidgetStateOverride.GLANCE_SEQ_KEY]
            val state by container.headphones.state.collectAsState()
            val overrideSnap by WidgetStateOverride.flow.collectAsState(initial = initialOverride)

            GlanceTheme(colors = WidgetColors.providers()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.background)
                        .cornerRadius(24.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Header(state, overrideSnap)
                        Spacer(GlanceModifier.height(4.dp))
                        BatteryRow(state)
                        Spacer(GlanceModifier.height(5.dp))
                        TwoRowDashboardGrid(state, overrideSnap, rowHeightDp = 28)
                    }
                }
            }
        }
    }

    @Composable
    private fun Header(state: HeadphoneState, overrideSnap: WidgetStateOverride.Snapshot) {
        val connected = state.isConnected
        val activeIdx = WidgetStateOverride.resolvedSelectedModeIndex(state, overrideSnap)
        val allKnownModes = WidgetStateOverride.resolvedPrimaryModes(state) +
            WidgetStateOverride.resolvedCustomModes(state, overrideSnap)
        val activeModeName = if (!connected) {
            "--%"
        } else {
            state.modes.firstOrNull { it.index == activeIdx }?.name?.ifBlank { null }
                ?: allKnownModes.firstOrNull { it.first == activeIdx }?.second
                ?: state.currentMode?.name?.ifBlank { null }
                ?: "Quiet"
        }
        val headerAction = if (connected) actionStartActivity<MainActivity>() else actionRunCallback<ConnectAction>()

        Row(
            modifier = GlanceModifier.fillMaxWidth().height(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = HeadphoneLabels.displayName(state) ?: "Bose QC Ultra Earbuds",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (connected) GlanceTheme.colors.onSurface else GlanceTheme.colors.onSurfaceVariant,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight().clickable(headerAction),
            )
            Text(
                text = activeModeName,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (connected) GlanceTheme.colors.primary else GlanceTheme.colors.outline,
                ),
                maxLines = 1,
                modifier = GlanceModifier.clickable(headerAction),
            )
        }
    }

    @Composable
    private fun BatteryRow(state: HeadphoneState) {
        val connected = state.isConnected
        val charging = connected && state.isCharging == true
        val (left, right) = WidgetStateOverride.resolvedBudBatteries(state)
        val rowAction = if (connected) actionStartActivity<MainActivity>() else actionRunCallback<ConnectAction>()

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(42.dp)
                .clickable(rowAction),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BudBatteryTile(
                label = "Left",
                percent = left,
                charging = charging,
                enabled = connected && left != null,
                iconRes = R.drawable.earbud_left,
                tileHeightDp = 42,
                iconSizeDp = 22,
                barHeightDp = 4,
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(8.dp))
            BudBatteryTile(
                label = "Right",
                percent = right,
                charging = charging,
                enabled = connected && right != null,
                iconRes = R.drawable.earbud_right,
                tileHeightDp = 42,
                iconSizeDp = 22,
                barHeightDp = 4,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }

    @Composable
    private fun TwoRowDashboardGrid(
        state: HeadphoneState,
        overrideSnap: WidgetStateOverride.Snapshot,
        rowHeightDp: Int = 28,
    ) {
        val connected = state.isConnected
        val primaryButtons = WidgetStateOverride.resolvedPrimaryModes(state)
        val customModes = WidgetStateOverride.resolvedCustomModes(state, overrideSnap).take(4)

        val activeIndex = WidgetStateOverride.resolvedSelectedModeIndex(state, overrideSnap)
        val address = state.connection.deviceAddress

        ModeButtonRow(
            modes = primaryButtons,
            activeIndex = activeIndex,
            address = address,
            enabled = connected,
            heightDp = rowHeightDp,
        )
        Spacer(GlanceModifier.height(4.dp))
        if (customModes.isNotEmpty()) {
            ModeButtonRow(
                modes = customModes,
                activeIndex = activeIndex,
                address = address,
                enabled = connected,
                heightDp = rowHeightDp,
            )
        } else {
            SpatialButtonRow(state, overrideSnap, enabled = connected, heightDp = rowHeightDp)
        }
    }

    @Composable
    private fun SpatialButtonRow(
        state: HeadphoneState,
        overrideSnap: WidgetStateOverride.Snapshot,
        enabled: Boolean = true,
        heightDp: Int = 28,
    ) {
        val currentSpatial = WidgetStateOverride.resolvedSelectedSpatial(state, overrideSnap)
        val options = listOf(
            SpatialAudio.OFF to "Spatial Off",
            SpatialAudio.ROOM to "Still",
            SpatialAudio.HEAD to "Motion",
        )
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(heightDp.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { i, (spatial, label) ->
                if (i > 0) Spacer(GlanceModifier.width(6.dp))
                val isSelected = enabled && currentSpatial == spatial
                WidgetPillTile(
                    text = label,
                    selected = isSelected,
                    enabled = enabled,
                    onClick = actionRunCallback<SetSpatialAction>(SetSpatialAction.parameters(spatial)),
                    heightDp = heightDp,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
    }
}

class LocalBudsStatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LocalBudsStatusWidget()
}
