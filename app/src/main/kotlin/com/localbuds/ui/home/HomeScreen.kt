package com.localbuds.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localbuds.core.*
import com.localbuds.core.fake.FakeHeadphoneRepository
import com.localbuds.ui.BoseIcons
import com.localbuds.ui.common.BoseUltraEarbudGraphic
import com.localbuds.ui.common.EarshotPreview
import com.localbuds.ui.common.LightDarkPreview
import com.localbuds.ui.common.ValueSlider
import com.localbuds.ui.common.earshotViewModel
import com.localbuds.ui.common.rememberHaptics
import com.localbuds.ui.theme.LocalBudsTheme
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onEditModes: () -> Unit,
    onOpenPreferences: () -> Unit = {},
    onOpenShortcuts: () -> Unit = {},
    onOpenDevices: () -> Unit = {},
) {
    val vm = earshotViewModel { HomeViewModel(it.headphones, it.messenger) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSwitcher by rememberSaveable { mutableStateOf(false) }

    HomeContent(
        state = state,
        contentPadding = contentPadding,
        onConnect = vm::connect,
        onRefresh = vm::refresh,
        onSelectMode = vm::setMode,
        onToggleFavorite = vm::toggleFavorite,
        onNoiseChange = vm::setNoise,
        onSpatialAudio = vm::setSpatialAudio,
        onEqChange = vm::setEq,
        onMultipointChange = vm::setMultipoint,
        onAutoPlayPauseChange = vm::setAutoPlayPause,
        onTouchControlsChange = vm::setTouchControls,
        onEditModes = onEditModes,
        onOpenPreferences = onOpenPreferences,
        onOpenShortcuts = onOpenShortcuts,
        onOpenDevices = onOpenDevices,
        onOpenSwitcher = { showSwitcher = true },
        onTransport = vm::transport,
        onVolume = { vm.setVolume(it) },
    )

    if (showSwitcher) {
        val candidates by produceState<List<HeadphoneCandidate>?>(initialValue = null) { value = vm.candidates() }
        DeviceSwitcherSheet(
            candidates = candidates,
            currentAddress = state.connection.deviceAddress,
            onPick = { candidate ->
                showSwitcher = false
                vm.chooseDevice(candidate.address, candidate.name)
            },
            onPairNew = {
                showSwitcher = false
                runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
            onDismiss = { showSwitcher = false },
        )
    }
}

/**
 * LocalBuds main control screen styled for Liquid Glass / Frosted Glass / Aurora:
 * - Translucent glass cards with luminous specular borders
 * - Custom Canvas Bose QC Ultra Earbuds + Case vector artwork
 * - Continuous draggable sliding pill selectors for Quiet / Aware / Immersion and Off / Still / Motion
 * - Frame-1 optimistic UI + BMAP command coalescing + crisp tactile haptics
 */
@Composable
fun HomeContent(
    state: HeadphoneState,
    contentPadding: PaddingValues,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onSelectMode: (Int) -> Unit,
    onToggleFavorite: (Int) -> Unit,
    onNoiseChange: (NoiseSettings) -> Unit,
    onSpatialAudio: (SpatialAudio) -> Unit = {},
    onEqChange: (HeadphoneEq, debounceMs: Long) -> Unit = { _, _ -> },
    onMultipointChange: (Boolean) -> Unit = {},
    onAutoPlayPauseChange: (Boolean) -> Unit = {},
    onTouchControlsChange: (Boolean) -> Unit = {},
    onEditModes: () -> Unit = {},
    onOpenPreferences: () -> Unit = {},
    onOpenShortcuts: () -> Unit = {},
    onOpenDevices: () -> Unit = {},
    onOpenSwitcher: () -> Unit = {},
    onTransport: (TransportControl) -> Unit = {},
    onVolume: (Int) -> Unit = {},
) {
    // Keep controls responsive during optimistic BMAP writes
    val controlsEnabled = state.isConnected
    val deviceName = state.info?.productName
        ?: state.connection.deviceName
        ?: "Bose QuietComfort Ultra Earbuds"
    val haptics = rememberHaptics()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ---- Top Header ----------------------------------------------------------------------
        BoseTopHeader(
            deviceName = deviceName,
            status = state.connection.status,
            isBusy = state.isBusy,
            onRefresh = {
                haptics.click()
                onRefresh()
            },
            onSwitch = {
                haptics.click()
                onOpenSwitcher()
            },
            onOpenPreferences = {
                haptics.click()
                onOpenPreferences()
            },
        )

        // ---- Disconnected State --------------------------------------------------------------
        AnimatedVisibility(visible = !state.isConnected) {
            BoseDisconnectedCard(
                deviceName = deviceName,
                onConnect = {
                    haptics.click()
                    onConnect()
                },
                onSwitch = {
                    haptics.click()
                    onOpenSwitcher()
                },
            )
        }

        // ---- Connected Controls --------------------------------------------------------------
        if (state.isConnected) {
            // 1. Earbud Batteries (Left / Right / Case) with detailed QC Ultra artwork
            EarbudBatteriesRow(state = state)

            // 2. Playback / Volume (if active)
            if (state.playback != null || state.volume != null) {
                NowPlayingRow(
                    playback = state.playback,
                    volume = state.volume,
                    volumeMax = state.volumeMax,
                    supportedControls = state.supportedTransportControls,
                    enabled = state.isConnected,
                    onTransport = onTransport,
                    onVolume = onVolume,
                )
            }

            // 3. Official Modes (Quiet / Aware / Immersion) + Immersive Audio (Off / Still / Motion)
            BoseModesSection(
                modes = state.modes,
                currentIndex = state.currentModeIndex,
                noise = state.noise ?: NoiseSettings(),
                enabled = controlsEnabled,
                onSelectMode = onSelectMode,
                onSpatialAudio = onSpatialAudio,
                onEditModes = onEditModes,
            )

            // 4. Quick Access: 3-band EQ, Multipoint, In-ear detection, Shortcuts
            BoseCoreFeaturesSection(
                state = state,
                enabled = controlsEnabled,
                onEqChange = onEqChange,
                onMultipointChange = onMultipointChange,
                onAutoPlayPauseChange = onAutoPlayPauseChange,
                onTouchControlsChange = onTouchControlsChange,
                onOpenShortcuts = onOpenShortcuts,
                onOpenDevices = onOpenDevices,
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ---- Subcomponents ----------------------------------------------------------------------------

@Composable
private fun BoseTopHeader(
    deviceName: String,
    status: ConnectionStatus,
    isBusy: Boolean,
    onRefresh: () -> Unit,
    onSwitch: () -> Unit,
    onOpenPreferences: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).clickable { onSwitch() }) {
            Text(
                text = deviceName,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                ),
                color = scheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dotColor = when (status) {
                    ConnectionStatus.CONNECTED -> LocalBudsTheme.colors.ok
                    ConnectionStatus.CONNECTING -> scheme.tertiary
                    else -> scheme.onSurfaceVariant
                }
                val statusText = when (status) {
                    ConnectionStatus.CONNECTED -> "Connected"
                    ConnectionStatus.CONNECTING -> "Connecting…"
                    ConnectionStatus.NO_PERMISSION -> "No Bluetooth Permission"
                    ConnectionStatus.NO_PAIRED_DEVICE -> "No Paired Earbuds"
                    ConnectionStatus.DISCONNECTED -> "Disconnected"
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = dotColor,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Switch device",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (status == ConnectionStatus.CONNECTED) {
                if (isBusy) {
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = LocalBudsTheme.colors.accent,
                        )
                    }
                } else {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = scheme.onBackground)
                    }
                }
            }
            IconButton(onClick = onOpenPreferences, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = scheme.onBackground)
            }
        }
    }
}

@Composable
private fun EarbudBatteriesRow(state: HeadphoneState) {
    val b = state.battery
    val left = b?.left?.percent ?: state.batteryPercent
    val right = b?.right?.percent ?: state.batteryPercent
    val charging = state.isCharging == true
    val accent = LocalBudsTheme.colors.accent

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EarbudBatteryTile(
            label = "Left",
            percentage = left,
            modifier = Modifier.weight(1f),
        ) {
            BoseUltraEarbudGraphic(
                isRight = false,
                modifier = Modifier.size(52.dp),
                accentColor = accent,
                isCharging = charging,
            )
        }
        EarbudBatteryTile(
            label = "Right",
            percentage = right,
            modifier = Modifier.weight(1f),
        ) {
            BoseUltraEarbudGraphic(
                isRight = true,
                modifier = Modifier.size(52.dp),
                accentColor = accent,
                isCharging = charging,
            )
        }
    }
}

@Composable
private fun EarbudBatteryTile(
    label: String,
    percentage: Int?,
    modifier: Modifier = Modifier,
    artwork: @Composable () -> Unit,
) {
    val pct = percentage ?: -1
    val colors = LocalBudsTheme.colors
    val scheme = MaterialTheme.colorScheme
    val barColor = when {
        pct < 0 -> scheme.onSurfaceVariant
        pct <= 20 -> scheme.error
        pct <= 40 -> scheme.tertiary
        else -> colors.ok
    }

    val animatedPercent by animateFloatAsState(
        targetValue = if (pct >= 0) pct.coerceIn(0, 100) / 100f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "battery_anim",
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = LocalBudsTheme.cardFill,
        border = BorderStroke(1.dp, colors.cardBorderBrush),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHigh.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                artwork()
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (pct >= 0) "$pct%" else "--",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = scheme.onSurface,
            )

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.track),
            ) {
                if (pct >= 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedPercent)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(barColor),
                    )
                }
            }
        }
    }
}

private data class ModePillOption(
    val label: String,
    val icon: ImageVector,
    val slotIndex: Int,
)

private data class SpatialPillOption(
    val label: String,
    val mode: SpatialAudio,
)

@Composable
private fun BoseModesSection(
    modes: List<ListeningMode>,
    currentIndex: Int?,
    noise: NoiseSettings,
    enabled: Boolean,
    onSelectMode: (Int) -> Unit,
    onSpatialAudio: (SpatialAudio) -> Unit,
    onEditModes: () -> Unit,
) {
    val colors = LocalBudsTheme.colors
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberHaptics()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = LocalBudsTheme.cardFill,
        border = BorderStroke(1.dp, colors.cardBorderBrush),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Listening Mode",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = scheme.onSurface,
                )
                TextButton(
                    onClick = {
                        haptics.click()
                        onEditModes()
                    },
                    enabled = enabled,
                ) {
                    Text(
                        text = "Edit Modes",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accent,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val quietIndex = modes.firstOrNull { it.name.equals("Quiet", ignoreCase = true) }?.index ?: 0
            val awareIndex = modes.firstOrNull { it.name.equals("Aware", ignoreCase = true) }?.index ?: 1
            val immersionIndex = modes.firstOrNull { it.name.equals("Immersion", ignoreCase = true) }?.index ?: 2
            val primaryIndices = remember(quietIndex, awareIndex, immersionIndex) {
                setOf(quietIndex, awareIndex, immersionIndex)
            }
            val customModes = remember(modes, primaryIndices) {
                modes.filter { it.isConfigured && it.index !in primaryIndices }
            }

            val modeOptions = remember(quietIndex, awareIndex, immersionIndex) {
                listOf(
                    ModePillOption("Quiet", BoseIcons.Quiet, quietIndex),
                    ModePillOption("Aware", BoseIcons.Aware, awareIndex),
                    ModePillOption("Immersion", BoseIcons.Immersion, immersionIndex),
                )
            }

            val selectedModePos = when (currentIndex) {
                quietIndex -> 0
                awareIndex -> 1
                immersionIndex -> 2
                0xFF -> when {
                    noise.spatialAudio != SpatialAudio.OFF -> 2
                    !noise.ancEnabled || noise.cancellingLevel <= 2 -> 1
                    else -> 0
                }
                else -> -1
            }

            // Smooth continuous draggable sliding pill selector for Quiet / Aware / Immersion
            SlidingPillSelector(
                items = modeOptions,
                selectedIndex = selectedModePos,
                onSelect = { pos -> onSelectMode(modeOptions[pos].slotIndex) },
                enabled = enabled,
                height = 68.dp,
                cornerRadius = 20.dp,
            ) { option, selectedFraction ->
                val tint = lerp(scheme.onSurfaceVariant, colors.onAccent, selectedFraction)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = option.label,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = tint,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Custom Listening Mode Presets row (e.g., Commute, Focus, Workout, Cinema, etc.)
            if (customModes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Custom Presets",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    customModes.forEach { custom ->
                        val isSelected = currentIndex == custom.index
                        val pillShape = RoundedCornerShape(14.dp)
                        val label = custom.name.ifBlank { "Mode ${custom.index + 1}" }
                        Box(
                            modifier = Modifier
                                .clip(pillShape)
                                .then(
                                    if (isSelected) Modifier.background(colors.accentBrush)
                                    else Modifier.background(scheme.surfaceContainerHigh.copy(alpha = 0.85f))
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) colors.accent.copy(alpha = 0.7f) else colors.hairline,
                                    shape = pillShape,
                                )
                                .clickable(enabled = enabled) {
                                    haptics.click()
                                    onSelectMode(custom.index)
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) colors.onAccent else scheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }

            // Immersive Audio Section (Off / Still / Motion)
            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = colors.hairline, thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Immersive Audio",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = scheme.onSurface,
            )
            Text(
                text = when (noise.spatialAudio) {
                    SpatialAudio.OFF -> "Off · Direct stereo soundstage"
                    SpatialAudio.ROOM -> "Still · Fixed room acoustic perspective"
                    SpatialAudio.HEAD -> "Motion · Tracks your head movement"
                },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )

            Spacer(modifier = Modifier.height(10.dp))

            val spatialOptions = remember {
                listOf(
                    SpatialPillOption("Off", SpatialAudio.OFF),
                    SpatialPillOption("Still", SpatialAudio.ROOM),
                    SpatialPillOption("Motion", SpatialAudio.HEAD),
                )
            }
            val selectedSpatialPos = spatialOptions.indexOfFirst { it.mode == noise.spatialAudio }.coerceAtLeast(0)

            // Full-width sliding pill selector with equal-weighted compartments so "Motion" never wraps or squashes
            SlidingPillSelector(
                items = spatialOptions,
                selectedIndex = selectedSpatialPos,
                onSelect = { pos -> onSpatialAudio(spatialOptions[pos].mode) },
                enabled = enabled,
                height = 46.dp,
                cornerRadius = 15.dp,
            ) { option, selectedFraction ->
                val textColor = lerp(scheme.onSurfaceVariant, colors.onAccent, selectedFraction)
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = textColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun BoseCoreFeaturesSection(
    state: HeadphoneState,
    enabled: Boolean,
    onEqChange: (HeadphoneEq, debounceMs: Long) -> Unit,
    onMultipointChange: (Boolean) -> Unit,
    onAutoPlayPauseChange: (Boolean) -> Unit,
    onTouchControlsChange: (Boolean) -> Unit,
    onOpenShortcuts: () -> Unit,
    onOpenDevices: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoseEqCard(
            eq = state.eq ?: HeadphoneEq(),
            enabled = enabled,
            onChange = onEqChange,
        )

        BoseFeatureCard(
            title = "Multipoint Connection",
            subtitle = "Connect two audio devices simultaneously",
            icon = BoseIcons.Multipoint,
            checked = state.multipointEnabled == true,
            enabled = enabled,
            onCheckedChange = onMultipointChange,
            onCardClick = onOpenDevices,
            trailingAction = {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Manage connected devices",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            },
        )

        BoseFeatureCard(
            title = "In-Ear Detection",
            subtitle = "Auto-pause media when earbuds are removed",
            icon = BoseIcons.InEarDetection,
            checked = state.autoPlayPause != false,
            enabled = enabled,
            onCheckedChange = onAutoPlayPauseChange,
        )

        BoseFeatureCard(
            title = "Touch & Shortcut Controls",
            subtitle = "Capacitive strip and touch-and-hold gestures",
            icon = BoseIcons.TouchShortcut,
            checked = state.touchControlsEnabled != false,
            enabled = enabled,
            onCheckedChange = onTouchControlsChange,
            onCardClick = onOpenShortcuts,
            trailingAction = {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Configure",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            },
        )
    }
}

@Composable
private fun BoseEqCard(
    eq: HeadphoneEq,
    enabled: Boolean,
    onChange: (HeadphoneEq, debounceMs: Long) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val colors = LocalBudsTheme.colors
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val activePreset = remember(eq) { com.localbuds.core.EqPreset.matching(eq) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = LocalBudsTheme.cardFill,
        border = BorderStroke(1.dp, colors.cardBorderBrush),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.click()
                        expanded = !expanded
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceContainerHigh.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = BoseIcons.Equalizer,
                            contentDescription = "Equalizer",
                            tint = scheme.onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Equalizer",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = scheme.onSurface,
                            )
                            Text(
                                text = activePreset?.label ?: "Custom",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.accent,
                            )
                        }
                        Text(
                            text = "Bass ${if (eq.bass >= 0) "+${eq.bass}" else eq.bass} · Mid ${if (eq.mid >= 0) "+${eq.mid}" else eq.mid} · Treble ${if (eq.treble >= 0) "+${eq.treble}" else eq.treble} dB",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (expanded) {
                        TextButton(
                            onClick = {
                                haptics.click()
                                val flat = com.localbuds.core.EqPreset.byName("Bose QC Ultra")!!
                                onChange(flat.toHeadphoneEq(), 0L)
                            },
                            enabled = enabled,
                        ) {
                            Text("Reset", style = MaterialTheme.typography.labelSmall, color = colors.accent)
                        }
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Smooth curved 3-band EQ frequency response graph
            BoseEqCurveGraph(
                eq = eq,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Deterministic preset-by-name pills (Bose QC Ultra, Bass Boost, Game, Treble Sparkle, Vocal Clarity)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                com.localbuds.core.EqPreset.entries.forEach { preset ->
                    val isSelected = activePreset == preset
                    val pillShape = RoundedCornerShape(14.dp)
                    Box(
                        modifier = Modifier
                            .clip(pillShape)
                            .then(
                                if (isSelected) Modifier.background(colors.accentBrush)
                                else Modifier.background(scheme.surfaceContainerHigh.copy(alpha = 0.85f)),
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) colors.accent.copy(alpha = 0.7f) else colors.hairline,
                                shape = pillShape,
                            )
                            .clickable(enabled = enabled) {
                                haptics.click()
                                val resolved = com.localbuds.core.EqPreset.byName(preset.label) ?: preset
                                onChange(resolved.toHeadphoneEq(), 0L)
                            }
                            .padding(horizontal = 13.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) colors.onAccent else scheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    var bassDrag by remember(eq.bass) { mutableFloatStateOf(eq.bass.toFloat()) }
                    ValueSlider(
                        label = "Bass",
                        value = bassDrag,
                        valueRange = -10f..10f,
                        steps = 19,
                        enabled = enabled,
                        onValueChange = { next ->
                            val prevInt = bassDrag.roundToInt()
                            bassDrag = next
                            val nextInt = next.roundToInt()
                            if (nextInt != prevInt) {
                                onChange(eq.copy(bass = nextInt), 110L)
                            }
                        },
                        onValueChangeFinished = {
                            onChange(eq.copy(bass = bassDrag.roundToInt()), 0L)
                        },
                        valueText = "${bassDrag.roundToInt()} dB",
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    var midDrag by remember(eq.mid) { mutableFloatStateOf(eq.mid.toFloat()) }
                    ValueSlider(
                        label = "Mid",
                        value = midDrag,
                        valueRange = -10f..10f,
                        steps = 19,
                        enabled = enabled,
                        onValueChange = { next ->
                            val prevInt = midDrag.roundToInt()
                            midDrag = next
                            val nextInt = next.roundToInt()
                            if (nextInt != prevInt) {
                                onChange(eq.copy(mid = nextInt), 110L)
                            }
                        },
                        onValueChangeFinished = {
                            onChange(eq.copy(mid = midDrag.roundToInt()), 0L)
                        },
                        valueText = "${midDrag.roundToInt()} dB",
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    var trebleDrag by remember(eq.treble) { mutableFloatStateOf(eq.treble.toFloat()) }
                    ValueSlider(
                        label = "Treble",
                        value = trebleDrag,
                        valueRange = -10f..10f,
                        steps = 19,
                        enabled = enabled,
                        onValueChange = { next ->
                            val prevInt = trebleDrag.roundToInt()
                            trebleDrag = next
                            val nextInt = next.roundToInt()
                            if (nextInt != prevInt) {
                                onChange(eq.copy(treble = nextInt), 110L)
                            }
                        },
                        onValueChangeFinished = {
                            onChange(eq.copy(treble = trebleDrag.roundToInt()), 0L)
                        },
                        valueText = "${trebleDrag.roundToInt()} dB",
                    )
                }
            }
        }
    }
}

@Composable
private fun BoseEqCurveGraph(
    eq: HeadphoneEq,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBudsTheme.colors
    val scheme = MaterialTheme.colorScheme
    val animBass by animateFloatAsState(
        targetValue = eq.bass.coerceIn(-10, 10).toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "eq_bass",
    )
    val animMid by animateFloatAsState(
        targetValue = eq.mid.coerceIn(-10, 10).toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "eq_mid",
    )
    val animTreble by animateFloatAsState(
        targetValue = eq.treble.coerceIn(-10, 10).toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "eq_treble",
    )

    val curveColor = colors.accent
    val zeroLineColor = scheme.onSurfaceVariant.copy(alpha = 0.25f)
    val cardBg = scheme.surfaceContainerHigh.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val midY = h / 2f
            val maxAmplitude = (h / 2f) * 0.78f

            fun yForDb(db: Float): Float = midY - (db / 10f) * maxAmplitude

            // 0 dB center reference line
            drawLine(
                color = zeroLineColor,
                start = androidx.compose.ui.geometry.Offset(0f, midY),
                end = androidx.compose.ui.geometry.Offset(w, midY),
                strokeWidth = 1.dp.toPx(),
            )

            val x0 = 0f
            val xBass = w * 0.16f
            val xMid = w * 0.50f
            val xTreble = w * 0.84f
            val xEnd = w

            val y0 = yForDb(animBass * 0.85f)
            val yBass = yForDb(animBass)
            val yMid = yForDb(animMid)
            val yTreble = yForDb(animTreble)
            val yEnd = yForDb(animTreble * 0.85f)

            val linePath = androidx.compose.ui.graphics.Path().apply {
                moveTo(x0, y0)
                cubicTo(
                    (x0 + xBass) / 2f, y0,
                    (x0 + xBass) / 2f, yBass,
                    xBass, yBass,
                )
                cubicTo(
                    (xBass + xMid) / 2f, yBass,
                    (xBass + xMid) / 2f, yMid,
                    xMid, yMid,
                )
                cubicTo(
                    (xMid + xTreble) / 2f, yMid,
                    (xMid + xTreble) / 2f, yTreble,
                    xTreble, yTreble,
                )
                cubicTo(
                    (xTreble + xEnd) / 2f, yTreble,
                    (xTreble + xEnd) / 2f, yEnd,
                    xEnd, yEnd,
                )
            }

            val fillPath = androidx.compose.ui.graphics.Path().apply {
                addPath(linePath)
                lineTo(xEnd, h)
                lineTo(x0, h)
                close()
            }

            drawPath(
                path = fillPath,
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(curveColor.copy(alpha = 0.28f), curveColor.copy(alpha = 0.02f)),
                ),
            )
            drawPath(
                path = linePath,
                color = curveColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.5.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
            )

            val dotRadius = 4.dp.toPx()
            drawCircle(color = curveColor, radius = dotRadius, center = androidx.compose.ui.geometry.Offset(xBass, yBass))
            drawCircle(color = curveColor, radius = dotRadius, center = androidx.compose.ui.geometry.Offset(xMid, yMid))
            drawCircle(color = curveColor, radius = dotRadius, center = androidx.compose.ui.geometry.Offset(xTreble, yTreble))
        }
    }
}

@Composable
private fun BoseFeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onCardClick: (() -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    val colors = LocalBudsTheme.colors
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberHaptics()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .then(
                if (onCardClick != null) {
                    Modifier.clickable {
                        haptics.click()
                        onCardClick()
                    }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(22.dp),
        color = LocalBudsTheme.cardFill,
        border = BorderStroke(1.dp, colors.cardBorderBrush),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceContainerHigh.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = scheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.padding(end = 8.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(
                    checked = checked,
                    onCheckedChange = { next ->
                        haptics.click()
                        onCheckedChange(next)
                    },
                    enabled = enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onAccent,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = scheme.onSurfaceVariant,
                        uncheckedTrackColor = colors.track,
                    ),
                )
                trailingAction?.invoke()
            }
        }
    }
}

@Composable
private fun BoseDisconnectedCard(
    deviceName: String,
    onConnect: () -> Unit,
    onSwitch: () -> Unit,
) {
    val colors = LocalBudsTheme.colors
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = LocalBudsTheme.cardFill,
        border = BorderStroke(1.dp, colors.cardBorderBrush),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceContainerHigh.copy(alpha = 0.78f)),
                    contentAlignment = Alignment.Center,
                ) {
                    BoseUltraEarbudGraphic(
                        isRight = false,
                        modifier = Modifier.size(52.dp),
                        accentColor = colors.accent,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceContainerHigh.copy(alpha = 0.78f)),
                    contentAlignment = Alignment.Center,
                ) {
                    BoseUltraEarbudGraphic(
                        isRight = true,
                        modifier = Modifier.size(52.dp),
                        accentColor = colors.accent,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = deviceName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Offline · 100% Zero-Cloud Direct Bluetooth",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
            ) {
                Text(
                    text = "Connect Earbuds",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onSwitch,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, colors.hairline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onSurface),
            ) {
                Text(text = "Choose Another Device")
            }
        }
    }
}

// ---- Previews ---------------------------------------------------------------------------------

@LightDarkPreview
@Composable
private fun HomePreview() {
    EarshotPreview {
        HomeContent(
            state = FakeHeadphoneRepository.connectedEarbudsState(),
            contentPadding = PaddingValues(0.dp),
            onConnect = {},
            onRefresh = {},
            onSelectMode = {},
            onToggleFavorite = {},
            onNoiseChange = {},
            onEqChange = { _, _ -> },
        )
    }
}
