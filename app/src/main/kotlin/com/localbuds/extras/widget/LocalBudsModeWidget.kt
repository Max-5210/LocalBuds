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
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.localbuds.MainActivity
import com.localbuds.appContainer
import com.localbuds.core.HeadphoneState
import com.localbuds.extras.HeadphoneLabels

/**
 * 4×1 Rigid Frosted Glass Quick Mode Control Bar Widget ([SizeMode.Exact]):
 * - Outer container is permanently locked to [GlanceModifier.fillMaxSize()].
 * - Always renders the full header + mode button row whether connected, using a single earbud, or disconnected.
 * - When one earbud is in the case, shows `--%` for the docked bud while keeping all mode buttons active.
 * - When disconnected, shows `L --% · R --%` and renders all mode buttons in their exact same positions
 *   in a muted / greyed-out disabled state with no active highlight.
 */
class LocalBudsModeWidget : GlanceAppWidget() {

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
                        .cornerRadius(22.dp)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HeaderRow(state)
                        Spacer(GlanceModifier.height(3.dp))
                        SingleRowModeBar(state, overrideSnap, heightDp = 28)
                    }
                }
            }
        }
    }

    @Composable
    private fun HeaderRow(state: HeadphoneState) {
        val connected = state.isConnected
        val title = HeadphoneLabels.displayName(state) ?: "Bose QC Ultra Earbuds"
        val battery = WidgetStateOverride.resolvedBatteryHeaderText(state)
        val headerAction = if (connected) actionStartActivity<MainActivity>() else actionRunCallback<ConnectAction>()

        Row(
            modifier = GlanceModifier.fillMaxWidth().height(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (connected) GlanceTheme.colors.onSurface else GlanceTheme.colors.onSurfaceVariant,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight().clickable(headerAction),
            )
            Text(
                text = battery,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (connected) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.outline,
                ),
                maxLines = 1,
                modifier = GlanceModifier.clickable(headerAction),
            )
        }
    }
}

/**
 * Single locked row for the 4×1 widget so vertical height is 100% constant and never gets clipped
 * by a 1-row launcher grid. Always falls back to the default/cached mode list (`Quiet`, `Aware`,
 * `Immersion`, plus cached custom presets) so mode buttons never disappear in single-bud or disconnected states.
 */
@Composable
internal fun SingleRowModeBar(
    state: HeadphoneState,
    overrideSnap: WidgetStateOverride.Snapshot,
    heightDp: Int = 28,
) {
    val primaryModes = WidgetStateOverride.resolvedPrimaryModes(state)
    val customModes = WidgetStateOverride.resolvedCustomModes(state, overrideSnap).take(2)
    val allModes = primaryModes + customModes

    val activeIndex = WidgetStateOverride.resolvedSelectedModeIndex(state, overrideSnap)
    val address = state.connection.deviceAddress

    ModeButtonRow(
        modes = allModes,
        activeIndex = activeIndex,
        address = address,
        enabled = state.isConnected,
        heightDp = heightDp,
    )
}

@Composable
internal fun ModeButtonRow(
    modes: List<Pair<Int, String>>,
    activeIndex: Int?,
    address: String?,
    enabled: Boolean = true,
    heightDp: Int = 28,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(heightDp.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        modes.forEachIndexed { i, (index, name) ->
            if (i > 0) Spacer(GlanceModifier.width(6.dp))
            val isSelected = enabled && activeIndex == index
            WidgetPillTile(
                text = name,
                selected = isSelected,
                enabled = enabled,
                onClick = actionRunCallback<SetModeAction>(SetModeAction.parameters(index, address)),
                heightDp = heightDp,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

@Composable
internal fun WidgetPillTile(
    text: String,
    selected: Boolean,
    onClick: Action,
    enabled: Boolean = true,
    heightDp: Int = 28,
    modifier: GlanceModifier = GlanceModifier,
) {
    val containerColor = when {
        !enabled -> GlanceTheme.colors.inverseSurface
        selected -> GlanceTheme.colors.primary
        else -> GlanceTheme.colors.secondaryContainer
    }
    val textColor = when {
        !enabled -> GlanceTheme.colors.outline
        selected -> GlanceTheme.colors.onPrimary
        else -> GlanceTheme.colors.onSecondaryContainer
    }
    Box(
        modifier = modifier
            .height(heightDp.dp)
            .background(containerColor)
            .cornerRadius(11.dp)
            .clickable(onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}
