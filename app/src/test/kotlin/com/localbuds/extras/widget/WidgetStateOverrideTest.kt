package com.localbuds.extras.widget

import com.localbuds.core.BatteryDetail
import com.localbuds.core.BatteryLevel
import com.localbuds.core.ConnectionState
import com.localbuds.core.ConnectionStatus
import com.localbuds.core.HeadphoneState
import com.localbuds.core.ListeningMode
import com.localbuds.core.SpatialAudio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WidgetStateOverrideTest {

    private val connectedState = ConnectionState(
        status = ConnectionStatus.CONNECTED,
        deviceName = "Bose QC Ultra Earbuds",
        deviceAddress = "AA:BB:CC:DD:EE:FF",
    )

    @Test
    fun `single earbud in ear preserves null for bud in case and formats -- percent`() {
        val leftOnlyState = HeadphoneState(
            connection = connectedState,
            batteryPercent = 84,
            battery = BatteryDetail(
                left = BatteryLevel(percent = 84),
                right = null,
            ),
        )
        assertEquals(84 to null, WidgetStateOverride.resolvedBudBatteries(leftOnlyState))
        assertEquals("L 84% · R --%", WidgetStateOverride.resolvedBatteryHeaderText(leftOnlyState))

        val rightOnlyState = HeadphoneState(
            connection = connectedState,
            batteryPercent = 72,
            battery = BatteryDetail(
                left = null,
                right = BatteryLevel(percent = 72),
            ),
        )
        assertEquals(null to 72, WidgetStateOverride.resolvedBudBatteries(rightOnlyState))
        assertEquals("L --% · R 72%", WidgetStateOverride.resolvedBatteryHeaderText(rightOnlyState))
    }

    @Test
    fun `disconnected state resolves null batteries and no active mode highlight while keeping full mode list`() {
        val disconnectedState = HeadphoneState(
            connection = ConnectionState(status = ConnectionStatus.DISCONNECTED),
            batteryPercent = 90,
            battery = BatteryDetail(
                left = BatteryLevel(percent = 90),
                right = BatteryLevel(percent = 88),
            ),
            currentModeIndex = 0,
        )
        val snap = WidgetStateOverride.Snapshot(
            seq = 5L,
            tappedModeIndex = 1,
            tappedModeAtMs = System.currentTimeMillis(),
            tappedSpatial = SpatialAudio.ROOM,
            tappedSpatialAtMs = System.currentTimeMillis(),
            cachedCustomModes = listOf(3 to "Commute", 4 to "Focus"),
        )

        assertEquals(null to null, WidgetStateOverride.resolvedBudBatteries(disconnectedState))
        assertEquals("L --% · R --%", WidgetStateOverride.resolvedBatteryHeaderText(disconnectedState))
        assertNull(WidgetStateOverride.resolvedSelectedModeIndex(disconnectedState, snap))
        assertNull(WidgetStateOverride.resolvedSelectedSpatial(disconnectedState, snap))
        assertEquals(
            listOf(0 to "Quiet", 1 to "Aware", 2 to "Immersion"),
            WidgetStateOverride.resolvedPrimaryModes(disconnectedState),
        )
        assertEquals(
            listOf(3 to "Commute", 4 to "Focus"),
            WidgetStateOverride.resolvedCustomModes(disconnectedState, snap),
        )
    }

    @Test
    fun `mode list falls back to default primary modes and cached custom modes when live modes empty`() {
        val singleBudEmptyModesState = HeadphoneState(
            connection = connectedState,
            battery = BatteryDetail(left = BatteryLevel(percent = 80), right = null),
            modes = emptyList(),
        )
        val snap = WidgetStateOverride.Snapshot(
            seq = 10L,
            tappedModeIndex = 3,
            tappedModeAtMs = System.currentTimeMillis(),
            cachedCustomModes = listOf(3 to "Run"),
        )

        assertEquals(
            listOf(0 to "Quiet", 1 to "Aware", 2 to "Immersion"),
            WidgetStateOverride.resolvedPrimaryModes(singleBudEmptyModesState),
        )
        assertEquals(
            listOf(3 to "Run"),
            WidgetStateOverride.resolvedCustomModes(singleBudEmptyModesState, snap),
        )
        assertEquals(3, WidgetStateOverride.resolvedSelectedModeIndex(singleBudEmptyModesState, snap))
    }

    @Test
    fun `live configured custom modes override cached custom modes when present`() {
        val stateWithModes = HeadphoneState(
            connection = connectedState,
            modes = listOf(
                ListeningMode(index = 0, name = "Quiet", isPreset = true, isEditable = false, isConfigured = true),
                ListeningMode(index = 1, name = "Aware", isPreset = true, isEditable = false, isConfigured = true),
                ListeningMode(index = 2, name = "Immersion", isPreset = true, isEditable = false, isConfigured = true),
                ListeningMode(index = 3, name = "Gym", isPreset = false, isEditable = true, isConfigured = true),
            ),
        )
        val snap = WidgetStateOverride.Snapshot(
            cachedCustomModes = listOf(3 to "OldPreset"),
        )

        assertEquals(
            listOf(3 to "Gym"),
            WidgetStateOverride.resolvedCustomModes(stateWithModes, snap),
        )
    }
}
