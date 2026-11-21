package com.localbuds.ui

import com.localbuds.core.ButtonAction
import com.localbuds.core.HeadphoneState
import com.localbuds.core.fake.FakeHeadphoneRepository
import com.localbuds.shortcut.HandlerStatus
import com.localbuds.shortcut.HfpHeadset
import com.localbuds.shortcut.ShortcutAction
import com.localbuds.shortcut.ShortcutSettings
import com.localbuds.ui.tools.RemapperHeadset
import com.localbuds.ui.tools.ShortcutChoice
import com.localbuds.ui.tools.ShortcutsUiState
import com.localbuds.ui.tools.choice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Shortcuts remapper screen's decisions: which headset, which options, the hint. */
class RemapperLogicTest {
    private val buds = HfpHeadset("AA:BB:CC:DD:EE:01", "Pixel Buds Pro")

    @Test
    fun `the headset at the top is Earshot's, else the phone's HFP headset, else none`() {
        val bose = RemapperHeadset.resolve(FakeHeadphoneRepository.connectedState(), buds)
        assertEquals(FakeHeadphoneRepository.DEVICE_ADDRESS, bose?.address)
        assertTrue(bose!!.viaEarshot)
        assertFalse(bose.earbuds)

        val other = RemapperHeadset.resolve(HeadphoneState(), buds)
        assertEquals(RemapperHeadset(buds.address, buds.name, earbuds = true, viaEarshot = false), other)

        assertNull(RemapperHeadset.resolve(HeadphoneState(), null))
        assertTrue(RemapperHeadset.resolve(FakeHeadphoneRepository.connectedEarbudsState(), null)!!.earbuds)
    }

    @Test
    fun `dropdown offers Earshot's actions, then only the native modes the device reports`() {
        assertEquals(ShortcutChoice.EARSHOT, ShortcutChoice.options(emptyList(), ShortcutChoice.CYCLE_EQ))
        assertEquals(
            ShortcutChoice.EARSHOT + ShortcutChoice.IMMERSIVE_AUDIO + ShortcutChoice.OFF,
            ShortcutChoice.options(listOf(ButtonAction.SPATIAL_AUDIO, ButtonAction.DISABLED), ShortcutChoice.PHONE_ASSISTANT),
        )
        assertEquals(ShortcutChoice.EARSHOT + ShortcutChoice.OFF, ShortcutChoice.options(listOf(ButtonAction.DISABLED), ShortcutChoice.OFF))
        // A saved native choice stays visible while that headset isn't connected through Earshot.
        assertEquals(ShortcutChoice.EARSHOT + ShortcutChoice.IMMERSIVE_AUDIO, ShortcutChoice.options(emptyList(), ShortcutChoice.IMMERSIVE_AUDIO))
    }

    @Test
    fun `screen state ties the policy together`() {
        val hp = FakeHeadphoneRepository.connectedState() // button on Immersive Audio, QC Ultra 2 list
        val headset = RemapperHeadset.resolve(hp, null)
        val fresh = ShortcutsUiState(handler = HandlerStatus.Chooser, headset = headset, button = hp.shortcutButton)
        assertEquals(ShortcutChoice.IMMERSIVE_AUDIO, fresh.choice.choice())
        assertEquals(listOf(ButtonAction.SPATIAL_AUDIO, ButtonAction.DISABLED), fresh.nativeModes)
        assertFalse(fresh.showFirstPressHint)

        val eq = fresh.copy(settings = ShortcutSettings().withChoice(headset!!.address, headset.name, ShortcutAction.CycleEq))
        assertEquals(ShortcutAction.CycleEq, eq.choice)
        assertTrue(eq.showFirstPressHint)
        assertFalse(eq.copy(handler = HandlerStatus.Earshot).showFirstPressHint)
        assertFalse(eq.copy(settings = eq.settings.copy(seenHeadsetPress = true)).showFirstPressHint)

        // Not through Earshot: no native modes.
        assertTrue(ShortcutsUiState(headset = RemapperHeadset.resolve(HeadphoneState(), buds)).nativeModes.isEmpty())
    }

    @Test
    fun `one row per Bose record, the shared-assistant warning and the detected control`() {
        val hp = FakeHeadphoneRepository.connectedEarbudsState()
        val headset = RemapperHeadset.resolve(hp, null)!!
        val modes = listOf(ButtonAction.VOICE_ASSISTANT, ButtonAction.DISABLED, ButtonAction.SPATIAL_AUDIO)
        val right = com.localbuds.core.ButtonAssignment(0x03, 9, ButtonAction.VOICE_ASSISTANT, modes)
        val left = right.copy(buttonId = 0x04)
        val s = ShortcutsUiState(headset = headset, button = right, buttons = listOf(right, left))
        assertEquals(listOf(left), s.secondaryButtons)
        assertTrue(s.sharesAssistant)
        assertFalse(s.copy(buttons = listOf(right, left.copy(action = ButtonAction.DISABLED))).sharesAssistant)
        // A single-button product, or a headset without BMAP: no extra rows.
        assertTrue(ShortcutsUiState(headset = headset, button = right).secondaryButtons.isEmpty())
        assertTrue(ShortcutsUiState(headset = RemapperHeadset.resolve(HeadphoneState(), buds), buttons = emptyList()).secondaryButtons.isEmpty())

        val seen = s.copy(settings = ShortcutSettings().withDetected(headset.address, 1L))
        assertEquals(headset.address.uppercase(), seen.detected?.address)
        assertEquals(null, s.detected)
    }
}
