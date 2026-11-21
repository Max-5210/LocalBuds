package com.localbuds.extras.widget

import android.content.Context
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.localbuds.MainActivity
import com.localbuds.R
import com.localbuds.appContainer

/**
 * Minimalistic, rigid 2×1 Left / Right Battery Widget ([SizeMode.Exact]):
 * Always renders the exact same full Left & Right earbud layout in both connected,
 * single-earbud, and disconnected states (`--%` and dimmed bars when a bud is in the case or disconnected).
 */
class LocalBudsWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer
        WidgetStateOverride.ensureLoaded(context)

        provideContent {
            val prefs = currentState<Preferences>()
            val _seq = prefs[WidgetStateOverride.GLANCE_SEQ_KEY]
            val state by container.headphones.state.collectAsState()
            val connected = state.isConnected
            val charging = connected && state.isCharging == true
            val (left, right) = WidgetStateOverride.resolvedBudBatteries(state)

            GlanceTheme(colors = WidgetColors.providers()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.background)
                        .cornerRadius(22.dp)
                        .padding(8.dp)
                        .clickable(
                            if (connected) actionStartActivity<MainActivity>()
                            else actionRunCallback<ConnectAction>(),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BudBatteryTile(
                            label = "L",
                            percent = left,
                            charging = charging,
                            enabled = connected && left != null,
                            iconRes = R.drawable.earbud_left,
                            tileHeightDp = 44,
                            iconSizeDp = 22,
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        BudBatteryTile(
                            label = "R",
                            percent = right,
                            charging = charging,
                            enabled = connected && right != null,
                            iconRes = R.drawable.earbud_right,
                            tileHeightDp = 44,
                            iconSizeDp = 22,
                            modifier = GlanceModifier.defaultWeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BudBatteryTile(
    label: String,
    percent: Int?,
    charging: Boolean,
    enabled: Boolean = percent != null,
    iconRes: Int? = null,
    tileHeightDp: Int = 46,
    iconSizeDp: Int = 24,
    barHeightDp: Int = 5,
    modifier: GlanceModifier = GlanceModifier,
) {
    val activeBud = enabled && percent != null
    Column(
        modifier = modifier
            .height(tileHeightDp.dp)
            .background(
                if (activeBud) GlanceTheme.colors.secondaryContainer
                else GlanceTheme.colors.inverseSurface,
            )
            .cornerRadius(14.dp)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height((iconSizeDp + 2).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconRes != null) {
                Image(
                    provider = ImageProvider(iconRes),
                    contentDescription = label,
                    modifier = GlanceModifier.size(iconSizeDp.dp),
                )
                Spacer(GlanceModifier.width(5.dp))
            }
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (activeBud) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.outline,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = if (activeBud) "$percent%" else "--%",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (activeBud) GlanceTheme.colors.onSurface else GlanceTheme.colors.outline,
                ),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(3.dp))
        BatteryBar(if (activeBud) percent else null, charging, heightDp = barHeightDp)
    }
}

@Composable
internal fun BatteryBar(percent: Int?, charging: Boolean, heightDp: Int = 5) {
    val pct = percent ?: 0
    val low = percent != null && pct <= 20 && !charging
    LinearProgressIndicator(
        progress = if (percent != null) (pct.coerceIn(0, 100) / 100f) else 0f,
        modifier = GlanceModifier.fillMaxWidth().height(heightDp.dp),
        color = if (low) GlanceTheme.colors.error else GlanceTheme.colors.tertiary,
        backgroundColor = GlanceTheme.colors.surfaceVariant,
    )
}

/**
 * Frosted Glass material colour providers shared across all LocalBuds home screen widgets.
 * - `inverseSurface` and `outline` provide the muted / greyed-out disabled pill & tile colours
 *   when disconnected or when an individual earbud is docked inside the charging case.
 */
internal object WidgetColors {
    private val FrostedGlassWidgetScheme = darkColorScheme(
        primary = Color(0xFFF1F5F9),
        onPrimary = Color(0xFF0F172A),
        secondaryContainer = Color(0xCC1E2634),
        onSecondaryContainer = Color(0xFFE2E8F0),
        tertiary = Color(0xFF4ADE80),
        background = Color(0xEA0C1018),
        onBackground = Color(0xFFF8FAFC),
        surface = Color(0xEA0C1018),
        onSurface = Color(0xFFF8FAFC),
        surfaceVariant = Color(0x80222B3B),
        onSurfaceVariant = Color(0xFFA8B3C5),
        inverseSurface = Color(0x66141A24),
        inverseOnSurface = Color(0xFF64748B),
        outline = Color(0xFF64748B),
        error = Color(0xFFF87171),
    )

    @Composable
    fun providers() = ColorProviders(light = FrostedGlassWidgetScheme, dark = FrostedGlassWidgetScheme)
}
