package com.localbuds.ui

import com.localbuds.core.LabReading
import com.localbuds.core.RawFrame
import com.localbuds.protocol.qcultra2.FunctionNames
import com.localbuds.service.lab.BmapLab
import com.localbuds.ui.tools.LabExport
import com.localbuds.ui.tools.LabUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The Lab screen's pure parts: labels, the text export and how the selection follows the headset. */
class LabLogicTest {

    private val bose = BmapLab.profile()

    @Test fun `the Bose export keeps its 0_5 format under a stack line`() {
        // Named as BmapLab names them: the BMAP catalogue's function and block names.
        val readings = listOf(
            LabReading(1, 7, FunctionNames.functionName(1, 7), listOf(RawFrame(1, 7, "STATUS", "f60a", false)), groupName = "Settings"),
            LabReading(1, 8, FunctionNames.functionName(1, 8), listOf(RawFrame(1, 8, "ERROR", "04", true, "FUNC_NOT_SUPPORTED")), groupName = "Settings"),
            LabReading(40, 0, FunctionNames.functionName(40, 0), emptyList(), groupName = FunctionNames.blockName(40)),
        )
        val text = LabExport.text(bose, readings).lines()
        assertEquals("# LocalBuds Lab · Bose BMAP", text[0])
        assertEquals("# Block 1 Settings", text[1])
        // Exactly the 0.5 line: "[b.f] name OPERATOR payload", the name from the BMAP catalogue.
        assertEquals("[1.7] ${FunctionNames.functionName(1, 7).orEmpty()} STATUS f60a", text[2])
        assertEquals("[1.8] ${FunctionNames.functionName(1, 8).orEmpty()} ERROR ERROR FUNC_NOT_SUPPORTED 04", text[3])
        assertEquals("# Block 40 ", text[4])
        assertEquals("Block 40 · ?", LabExport.heading(bose, 40, readings.drop(2)))
        assertEquals("Block 31 · AudioModes", LabExport.heading(bose, 31, emptyList()))
    }

    @Test fun `the selection starts at the defaults, keeps user picks on reconnect, and resets on stack change`() {
        var s = LabUiState().withHeadset(connected = true, deviceName = "QC Ultra", profile = bose)
        assertEquals(setOf(1, 2, 31), s.selectedGroups)
        assertEquals(3 * 41, s.scanReadCount)

        // The user's picks survive a disconnect and reconnect of the same stack.
        s = s.copy(selectedGroups = setOf(1, 4), selectionTouched = true)
        s = s.withHeadset(false, "QC Ultra", null)
        assertEquals(null, s.profile)
        s = s.withHeadset(true, "QC Ultra", bose)
        assertEquals(setOf(1, 4), s.selectedGroups)

        // Switching to a distinct profile starts fresh from defaults.
        val otherBose = bose.copy(stack = "Bose BMAP LE")
        s = s.withHeadset(true, "LE-QC Ultra", otherBose)
        assertEquals(setOf(1, 2, 31), s.selectedGroups)
        assertFalse(s.selectionTouched)
    }
}
