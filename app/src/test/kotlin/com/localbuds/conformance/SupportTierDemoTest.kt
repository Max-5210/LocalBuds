package com.localbuds.conformance

import com.localbuds.bt.BtLog
import com.localbuds.core.HeadphoneState
import com.localbuds.core.SupportTier
import com.localbuds.service.ProtocolLog
import com.localbuds.virtual.DemoDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Every Bose demo device, connected the way its repository connects it ([rigFor]), shows the tier its
 * model has. Exhaustive on purpose, like [rigFor]: a new demo device doesn't compile until it says its tier here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class SupportTierDemoTest(private val device: DemoDevice) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun devices(): List<DemoDevice> = DemoDevice.entries.toList()

        /** The tier each demo device's model has. */
        fun expected(d: DemoDevice): SupportTier = when (d) {
            DemoDevice.QC_ULTRA -> SupportTier.TESTED
            DemoDevice.QC35_II -> SupportTier.BATTERY_ONLY
            DemoDevice.QC_ULTRA_EARBUDS_1, DemoDevice.QC_ULTRA_EARBUDS_2, DemoDevice.QC_EARBUDS_2,
            DemoDevice.QC_EARBUDS, DemoDevice.QC45, DemoDevice.NC700, DemoDevice.ULTRA_OPEN,
            -> SupportTier.EXPERIMENTAL
        }
    }

    @Before
    fun quiet() {
        BtLog.sink = { _, _, _ -> }
        ProtocolLog.sink = { _, _, _ -> }
    }

    @Test
    fun `the connected demo device shows its model's tier`() {
        val state = connectedThroughRig()
        assertEquals(expected(device), SupportTier.of(state), "$device")
    }

    private fun connectedThroughRig(): HeadphoneState {
        val scope = TestScope()
        val rig = assertNotNull(rigFor(device, scope), "$device has no rig")
        var state: HeadphoneState? = null
        scope.runTest {
            rig.connect()
            state = rig.settle()
        }
        return assertNotNull(state, "$device: no state after connect")
    }
}
