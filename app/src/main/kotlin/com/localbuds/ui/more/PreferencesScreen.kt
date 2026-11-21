@file:OptIn(ExperimentalLayoutApi::class)

package com.localbuds.ui.more

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localbuds.R
import com.localbuds.appContainer
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.localbuds.core.AppPreferences
import com.localbuds.core.Appearance
import com.localbuds.core.HeadphoneRepository
import com.localbuds.core.ListeningMode
import com.localbuds.core.ModeResolver
import com.localbuds.core.SecureSessionStatus
import com.localbuds.core.fake.FakeHeadphoneRepository
import com.localbuds.extras.tile.TileCycle
import com.localbuds.root.RootShell
import com.localbuds.root.RootState
import com.localbuds.root.RootTweaks
import com.localbuds.ui.common.EarshotPreview
import com.localbuds.ui.common.LocalBudsViewModel
import com.localbuds.ui.common.UiMessenger
import com.localbuds.ui.common.rememberHaptics
import com.localbuds.ui.common.LightDarkPreview
import com.localbuds.ui.common.SectionCard
import com.localbuds.ui.common.SubScreenScaffold
import com.localbuds.ui.common.SwitchRow
import com.localbuds.ui.common.ValueSlider
import com.localbuds.ui.common.earshotViewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PreferencesScreen(onBack: () -> Unit) {
    val vm = earshotViewModel { AutomationsViewModel(it.automation, it.headphones, it.tuning, it.messenger) }
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val headphones by vm.headphoneState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val rootState by RootShell.state.collectAsStateWithLifecycle()
    val rootEnabled by RootShell.enabledFlow.collectAsStateWithLifecycle()
    val showMediaOnOverlay by RootShell.showMediaOnOverlayFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val devicePrefs = remember(context) { context.appContainer.devicePrefs }
    val familyOverride by devicePrefs.familyOverrideFlow.collectAsStateWithLifecycle(initialValue = null)
    val actions = earshotViewModel { deps ->
        PreferencesActionsViewModel(
            headphones = deps.headphones,
            saveFamilyOverride = devicePrefs::setFamilyOverride,
            messenger = deps.messenger,
        )
    }
    PreferencesContent(
        prefs, headphones.modes, onBack, vm::setPreferences,
        address = headphones.connection.deviceAddress,
        canForgetPairing = headphones.secureSession.status != SecureSessionStatus.NOT_REQUIRED,
        onForgetPairing = actions::forgetPairing,
        familyOverride = familyOverride,
        onFamilyChange = actions::setFamilyOverride,
        rootState = rootState,
        rootEnabled = rootEnabled,
        showMediaOnOverlay = showMediaOnOverlay,
        onToggleRoot = { checked -> scope.launch { RootTweaks.setEnabled(context, checked) } },
        onReapplyRoot = { scope.launch { RootTweaks.enable(context) } },
        onToggleMediaOverlay = { checked -> RootTweaks.setShowMediaOnOverlay(context, checked) },
    )
}

/**
 * The Preferences actions that restart the connection.
 */
class PreferencesActionsViewModel(
    private val headphones: HeadphoneRepository,
    private val saveFamilyOverride: suspend (String?) -> Unit,
    messenger: UiMessenger,
) : LocalBudsViewModel(messenger) {

    /** The next connect pairs again. */
    fun forgetPairing() = changeThenReconnect { headphones.forgetPairing() }

    /** The family is chosen at the start of a connect: reconnect so the change applies now. */
    fun setFamilyOverride(key: String?) = changeThenReconnect { saveFamilyOverride(key) }

    private fun changeThenReconnect(change: suspend () -> Unit) {
        launchCatching {
            withContext(NonCancellable) {
                change()
                headphones.disconnect()
                headphones.connect()
            }
        }
    }
}

@Composable
fun PreferencesContent(
    prefs: AppPreferences,
    modes: List<ListeningMode>,
    onBack: () -> Unit,
    onChange: (AppPreferences) -> Unit,
    /** The connected headset, whose modes [modes] are. */
    address: String? = null,
    canForgetPairing: Boolean = false,
    onForgetPairing: () -> Unit = {},
    familyOverride: String? = null,
    onFamilyChange: (String?) -> Unit = {},
    rootState: RootState = RootState.UNKNOWN,
    rootEnabled: Boolean = true,
    showMediaOnOverlay: Boolean = true,
    onToggleRoot: (Boolean) -> Unit = {},
    onReapplyRoot: () -> Unit = {},
    onToggleMediaOverlay: (Boolean) -> Unit = {},
) {
    val haptics = rememberHaptics()
    SubScreenScaffold(title = stringResource(R.string.settings_preferences), onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = padding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "Root Access & Performance") {
                val isActive = rootState == RootState.GRANTED && rootEnabled
                val isChecking = rootState == RootState.CHECKING
                val subtitle = when {
                    !rootEnabled -> "Disabled. Toggle ON to activate superuser grants, Doze whitelist, and instant wakeups."
                    rootState == RootState.GRANTED -> "Active (Superuser granted) • Doze whitelisted, standby active, overlay & BT auto-granted."
                    rootState == RootState.CHECKING -> "Requesting superuser access..."
                    rootState == RootState.UNAVAILABLE -> "Root unavailable or denied. Tap to request Superuser."
                    else -> "Superuser not initialized. Tap to enable."
                }
                SwitchRow(
                    title = "Root Privileged Mode",
                    subtitle = subtitle,
                    checked = isActive,
                    enabled = !isChecking,
                    onCheckedChange = onToggleRoot,
                )
                if (isActive) {
                    SwitchRow(
                        title = "Now Playing Pill on Overlay",
                        subtitle = "Show active media info & quick playback controls on the case-open popup",
                        checked = showMediaOnOverlay,
                        onCheckedChange = onToggleMediaOverlay,
                    )
                    TextButton(onClick = onReapplyRoot, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Re-apply System Optimizations")
                    }
                }
            }
            SectionCard(title = stringResource(R.string.prefs_appearance)) {
                Text(
                    stringResource(R.string.prefs_appearance_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = prefs.appearance == Appearance.FROSTED_GLASS ||
                            (prefs.appearance == Appearance.SYSTEM && Build.VERSION.SDK_INT < Build.VERSION_CODES.S),
                        onClick = {
                            haptics.click()
                            onChange(prefs.copy(appearance = Appearance.FROSTED_GLASS))
                        },
                        label = { Text(stringResource(R.string.prefs_appearance_frosted_glass)) },
                    )
                    FilterChip(
                        selected = prefs.appearance == Appearance.AURORA,
                        onClick = {
                            haptics.click()
                            onChange(prefs.copy(appearance = Appearance.AURORA))
                        },
                        label = { Text(stringResource(R.string.prefs_appearance_aurora)) },
                    )
                    // Below Android 12 there is no wallpaper palette, so this choice would be a no-op: hidden.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) FilterChip(
                        selected = prefs.appearance == Appearance.SYSTEM,
                        onClick = {
                            haptics.click()
                            onChange(prefs.copy(appearance = Appearance.SYSTEM))
                        },
                        label = { Text(stringResource(R.string.prefs_appearance_system)) },
                    )
                }
            }
            SectionCard(title = stringResource(R.string.prefs_alerts)) {
                SwitchRow(
                    title = stringResource(R.string.prefs_low_battery),
                    subtitle = stringResource(R.string.prefs_low_battery_subtitle),
                    checked = prefs.lowBatteryThreshold != null,
                    onCheckedChange = { onChange(prefs.copy(lowBatteryThreshold = if (it) 20 else null)) },
                )
                val threshold = prefs.lowBatteryThreshold
                if (threshold != null) {
                    var drag by remember(threshold) { mutableFloatStateOf(threshold.toFloat()) }
                    ValueSlider(
                        label = stringResource(R.string.prefs_low_battery_threshold),
                        value = drag,
                        valueRange = 5f..50f,
                        steps = 8,
                        valueText = "${drag.roundToInt()} %",
                        onValueChange = { drag = it },
                        onValueChangeFinished = { onChange(prefs.copy(lowBatteryThreshold = drag.roundToInt())) },
                    )
                }
                SwitchRow(
                    title = stringResource(R.string.prefs_persistent_notification),
                    subtitle = stringResource(R.string.prefs_persistent_notification_subtitle),
                    checked = prefs.persistentNotification,
                    onCheckedChange = { onChange(prefs.copy(persistentNotification = it)) },
                )
                SwitchRow(
                    title = stringResource(R.string.prefs_auto_connect),
                    subtitle = stringResource(R.string.prefs_auto_connect_subtitle),
                    checked = prefs.autoConnectOnBluetooth,
                    onCheckedChange = { onChange(prefs.copy(autoConnectOnBluetooth = it)) },
                )
            }
            SectionCard(title = stringResource(R.string.prefs_tile)) {
                Text(stringResource(R.string.prefs_tile_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                // The choice is saved by meaning: what is shown here is what it means on this headset.
                val tileModes = modes.filter { it.isConfigured }
                val chosen = TileCycle.preferredSlots(prefs, modes, address).filter { slot -> tileModes.any { it.index == slot } }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tileModes.forEach { mode ->
                        FilterChip(
                            selected = mode.index in chosen,
                            onClick = {
                                haptics.click()
                                onChange(TileCycle.toggle(prefs, mode, modes, address))
                            },
                            label = { Text(mode.name.ifBlank { stringResource(R.string.mode_slot_number, mode.index) }) },
                        )
                    }
                }
                // With nothing connected there are no chips, and a saved choice is not "none selected".
                val nothingSaved = if (prefs.modeRefsMigrated) prefs.tileCycle.isEmpty() else prefs.tileCycleModes.isEmpty()
                if (nothingSaved || (tileModes.isNotEmpty() && chosen.isEmpty())) {
                    Text(stringResource(R.string.prefs_tile_default), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@LightDarkPreview
@Composable
private fun PreferencesPreview() {
    val fake = FakeHeadphoneRepository.connectedState()
    val prefs = AppPreferences().withTileCycle(fake.modes.take(2).map { ModeResolver.refFor(it, fake.connection.deviceAddress) })
    EarshotPreview { PreferencesContent(prefs, fake.modes, {}, {}, address = fake.connection.deviceAddress) }
}
