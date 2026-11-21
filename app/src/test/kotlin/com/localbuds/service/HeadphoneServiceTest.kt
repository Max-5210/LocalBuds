package com.localbuds.service

import com.localbuds.core.EqPreset
import com.localbuds.core.HeadphoneEq
import com.localbuds.core.HeadphoneState
import com.localbuds.core.ListeningMode
import org.junit.Test
import kotlin.test.assertEquals

class HeadphoneServiceTest {

    private fun mode(index: Int, name: String = "M$index", favorite: Boolean = false, configured: Boolean = true) =
        ListeningMode(index, name, isPreset = index < 3, isEditable = index >= 3, isConfigured = configured, isFavorite = favorite)

    @Test
    fun `cycle restricts strictly to core built-in modes Quiet Aware Immersion and never advances into custom slots 4 plus`() {
        val modes = listOf(
            mode(0, "Quiet", favorite = true),
            mode(1, "Aware", favorite = true),
            mode(2, "Immersion", favorite = true),
            mode(3, "Custom 1", favorite = false, configured = true),
            mode(4, "Custom 2", favorite = false, configured = true),
            mode(5, "", favorite = false, configured = false),
        )
        // Core 3-mode cycle: Quiet (0) -> Aware (1) -> Immersion (2), stopping at Immersion (never advancing to 3/4+) and wrapping to Quiet (0)
        assertEquals(listOf(0, 1, 2), HeadphoneService.orderedCycleModes(HeadphoneState(modes = modes)))
        assertEquals(1, HeadphoneService.nextModeIndex(HeadphoneState(currentModeIndex = 0, modes = modes)))
        assertEquals(2, HeadphoneService.nextModeIndex(HeadphoneState(currentModeIndex = 1, modes = modes)))
        assertEquals(0, HeadphoneService.nextModeIndex(HeadphoneState(currentModeIndex = 2, modes = modes)))
        // If already on a custom mode (slot 3 or 4), recovers cleanly to Quiet (0)
        assertEquals(0, HeadphoneService.nextModeIndex(HeadphoneState(currentModeIndex = 3, modes = modes)))
        assertEquals(0, HeadphoneService.nextModeIndex(HeadphoneState(currentModeIndex = 4, modes = modes)))
    }

    @Test
    fun `cycle falls back to Quiet Aware Immersion when no custom presets are configured`() {
        val modes = listOf(
            mode(0, "Quiet", favorite = false, configured = true),
            mode(1, "Aware", favorite = false, configured = true),
            mode(2, "Immersion", favorite = false, configured = true),
            mode(3, "", favorite = false, configured = false),
        )
        assertEquals(1, HeadphoneService.nextModeIndex(HeadphoneState(currentModeIndex = 0, modes = modes)))
        assertEquals(2, HeadphoneService.nextModeIndex(HeadphoneState(currentModeIndex = 1, modes = modes)))
        assertEquals(0, HeadphoneService.nextModeIndex(HeadphoneState(currentModeIndex = 2, modes = modes)))
        assertEquals(0, HeadphoneService.nextModeIndex(HeadphoneState(currentModeIndex = 0xFF, modes = modes)))
    }

    @Test
    fun `deterministic EQ preset lookup by name and cycle order includes Game preset`() {
        assertEquals(HeadphoneEq(0, 0, 0), EqPreset.byName("Bose QC Ultra")?.toHeadphoneEq())
        assertEquals(HeadphoneEq(6, -1, 1), EqPreset.byName("Bass Boost")?.toHeadphoneEq())
        assertEquals(HeadphoneEq(3, 2, 5), EqPreset.byName("Game")?.toHeadphoneEq())
        assertEquals(HeadphoneEq(0, 1, 6), EqPreset.byName("Treble Sparkle")?.toHeadphoneEq())
        assertEquals(HeadphoneEq(-2, 5, 3), EqPreset.byName("Vocal Clarity")?.toHeadphoneEq())

        val step1 = EqPreset.nextAfter(HeadphoneEq(0, 0, 0))
        assertEquals(EqPreset.BASS_BOOST, step1)
        val step2 = EqPreset.nextAfter(step1.toHeadphoneEq())
        assertEquals(EqPreset.GAME, step2)
        val step3 = EqPreset.nextAfter(step2.toHeadphoneEq())
        assertEquals(EqPreset.TREBLE_SPARKLE, step3)
        val step4 = EqPreset.nextAfter(step3.toHeadphoneEq())
        assertEquals(EqPreset.VOCAL_CLARITY, step4)
        val step5 = EqPreset.nextAfter(step4.toHeadphoneEq())
        assertEquals(EqPreset.BOSE_QC_ULTRA, step5)
    }
}
