package com.localbuds.service

import com.localbuds.core.SupportTier
import com.localbuds.protocol.qcultra2.BoseModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Where Bose tiers come from: the facts set from [BoseModel] and [ConnectionFacts], read through
 * the same [SupportTier] rule the UI uses. The demo devices end to end: `SupportTierDemoTest`.
 */
class SupportTierDriversTest {

    @Test fun `of the Bose catalogue only the QC Ultra Headphones 2nd Gen is Tested`() {
        assertEquals(listOf(BoseModel.QC_ULTRA_2), BoseModel.entries.filter { it.tested })
        assertEquals(SupportTier.TESTED, SupportTier.of(BoseModel.QC_ULTRA_2.toCapabilities()))
    }

    @Test fun `a Bose layout bosectl confirmed is still Experimental until LocalBuds runs on it`() {
        for (model in listOf(BoseModel.QC45, BoseModel.QC_HEADPHONES, BoseModel.QC_ULTRA_EARBUDS_2, BoseModel.QC_EARBUDS)) {
            val caps = model.toCapabilities()
            assertTrue(caps.verified, "$model: bosectl-verified")
            assertEquals(SupportTier.EXPERIMENTAL, SupportTier.of(caps), "$model")
        }
        assertEquals(SupportTier.EXPERIMENTAL, SupportTier.of(BoseModel.NC700.toCapabilities()), "basic tier")
    }

    @Test fun `an unknown Bose product on the QC Ultra 2 defaults is not Tested`() {
        val caps = ConnectionFacts(catalogued = false, unknownProductName = "Bose Foo Earbuds").capabilities(NoisePath.LIVE, modesAvailable = true)
        assertFalse(caps.tested)
        assertEquals(SupportTier.EXPERIMENTAL, SupportTier.of(caps))
    }

    @Test fun `the recognised-but-unsupported Bose models are Battery only`() {
        for (model in BoseModel.entries.filter { it.isUnsupported }) {
            assertEquals(SupportTier.BATTERY_ONLY, SupportTier.of(model.toCapabilities()), "$model")
        }
    }
}
