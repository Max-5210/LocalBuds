package com.localbuds.ui.home

import com.localbuds.bt.BleScanAccess
import com.localbuds.core.ConnectionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * When Home asks for BLE scan access on Android 9–11 (audit R36): only for the headsets whose
 * information comes from a scan, and with the fix that applies (the permission, or location on).
 */
class BleScanPromptTest {

    private val connected = ConnectionStatus.CONNECTED
    private val down = ConnectionStatus.DISCONNECTED

    @Test fun `Bose-only build does not show AirPods or JBL BLE scan prompts`() {
        for (sdk in listOf(28, 30, 31, 36)) {
            assertNull(BleScanPrompt.reason(sdk, "Bose QC Ultra Earbuds", connected, earbuds = true))
            assertNull(BleScanPrompt.reason(sdk, "Bose QC Ultra Headphones", down, earbuds = false))
            assertNull(BleScanPrompt.reason(sdk, "Jo's AirPods Pro", connected, earbuds = true))
            assertNull(BleScanPrompt.reason(sdk, "JBL Wave Buds 2", down, earbuds = true))
            assertNull(BleScanPrompt.reason(sdk, null, connected, earbuds = true))
        }
    }

    /** The need follows BleScanAccess's own answer for the same grants. */
    private fun need(sdk: Int, fine: Boolean = false, coarse: Boolean = false, locationOn: Boolean = true) = BleScanPrompt.need(
        sdk,
        missing = BleScanAccess.missing(sdk, scanGranted = false, fineGranted = fine, coarseGranted = coarse, locationOn = locationOn),
        fineGranted = fine,
        coarseGranted = coarse,
    )

    @Test fun `Android 10 and 11 ask for precise location, then for location on`() {
        for (sdk in listOf(29, 30)) {
            assertEquals(BleScanPrompt.Need.PERMISSION, need(sdk))
            assertEquals(BleScanPrompt.Need.PERMISSION, need(sdk, coarse = true), "coarse is not enough there")
            assertEquals(BleScanPrompt.Need.LOCATION_OFF, need(sdk, fine = true, coarse = true, locationOn = false))
            assertNull(need(sdk, fine = true, coarse = true))
        }
    }

    @Test fun `Android 9 is satisfied by approximate location`() {
        assertEquals(BleScanPrompt.Need.PERMISSION, need(28))
        assertNull(need(28, coarse = true))
        assertEquals(BleScanPrompt.Need.LOCATION_OFF, need(28, coarse = true, locationOn = false))
    }

    @Test fun `Android 12 and later are not this banner's`() {
        assertNull(BleScanPrompt.need(31, missing = "the Nearby devices permission", fineGranted = false, coarseGranted = false))
    }

    @Test fun `one request covers both location permissions`() {
        assertEquals(
            setOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION),
            BleScanPrompt.PERMISSIONS.toSet(),
        )
    }
}
