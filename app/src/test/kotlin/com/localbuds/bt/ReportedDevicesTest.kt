package com.localbuds.bt

import com.localbuds.protocol.qcultra2.BoseModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Devices from GitHub issues, pinned so a later routing change cannot quietly bring their problem back. */
class ReportedDevicesTest {

    /**
     * Issue #1: the 2024 QuietComfort Earbuds, saved as "QCE (2024)", never answer BMAP (separate Bose
     * app, other protocol).
     */
    @Test
    fun `issue 1 - QCE 2024 earbuds do not match Bose BMAP heuristics or catalogue`() {
        assertFalse(DeviceFamily.looksLikeBose("QCE (2024)"))
        assertNull(BoseModel.fromName("QCE (2024)"), "not a catalogue match: no channel guess either")
    }
}
