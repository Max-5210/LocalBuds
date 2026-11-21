package com.localbuds.ui.more

import com.localbuds.core.AutomationAction
import com.localbuds.core.ListeningMode
import com.localbuds.core.ModeMeaning
import com.localbuds.core.ModeRef
import com.localbuds.core.fake.FakeHeadphoneRepository
import kotlin.test.Test
import kotlin.test.assertEquals

/** What the automation editor writes when a mode is picked, and what it shows for a saved one. */
class AutomationModeChoicesTest {

    private val fake = FakeHeadphoneRepository.connectedState()
    private val bose = fake.connection.deviceAddress!!
    private val other = "AC:80:0A:00:00:01"

    private val threeModeExplicit = listOf(
        ListeningMode(0, "ANC", isPreset = true, isEditable = false, isConfigured = true, meaning = ModeMeaning.QUIET),
        ListeningMode(1, "Transparency", isPreset = true, isEditable = false, isConfigured = true, meaning = ModeMeaning.AWARE),
        ListeningMode(2, "Off", isPreset = true, isEditable = false, isConfigured = true, meaning = ModeMeaning.OFF),
    )

    @Test fun `a pick writes the meaning, the headset and the slot`() {
        val choices = AutomationModeChoices.choices(fake.modes, bose)
        // Only configured slots: the fake's empty 5..10 are not offered.
        assertEquals(listOf(0, 1, 2, 3, 4), choices.map { it.modeIndex })
        assertEquals(AutomationAction.SetMode(2, ModeRef(ModeMeaning.CUSTOM, "Immersion", bose, 2)), choices[2])
        assertEquals(AutomationAction.SetMode(0, ModeRef(ModeMeaning.QUIET, "Quiet", bose, 0)), AutomationModeChoices.default(fake.modes, bose))
    }

    @Test fun `with no headset the generic meanings are offered`() {
        val choices = AutomationModeChoices.choices(emptyList(), null)
        assertEquals(listOf(ModeMeaning.QUIET, ModeMeaning.AWARE, ModeMeaning.OFF), choices.map { it.mode?.meaning })
        // modeIndex is only for a downgraded 0.75 build.
        assertEquals(listOf(0, 1, 2), choices.map { it.modeIndex })
        assertEquals("Noise cancelling", AutomationModeChoices.label(choices[0], emptyList(), null))
    }

    @Test fun `a saved mode shows as what it means on the connected headset`() {
        val choices = AutomationModeChoices.choices(threeModeExplicit, other)
        val aware = AutomationAction.SetMode(1, ModeRef(ModeMeaning.AWARE, "Aware", bose, 1))
        val shown = AutomationModeChoices.shown(aware, choices, threeModeExplicit, other)
        assertEquals(choices[1], shown)
        assertEquals("Transparency", AutomationModeChoices.label(shown, threeModeExplicit, other))
        // One it doesn't have stays itself, under its saved name.
        val immersion = AutomationAction.SetMode(2, ModeRef(ModeMeaning.CUSTOM, "Immersion", bose, 2))
        assertEquals(immersion, AutomationModeChoices.shown(immersion, choices, threeModeExplicit, other))
        assertEquals("Immersion", AutomationModeChoices.label(immersion, threeModeExplicit, other))
        // An unbound 0.75 rule shows by slot, as 0.75 did.
        assertEquals(choices[2], AutomationModeChoices.shown(AutomationAction.SetMode(2), choices, threeModeExplicit, other))
    }
}
