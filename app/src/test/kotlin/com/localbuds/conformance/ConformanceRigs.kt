package com.localbuds.conformance

import com.localbuds.virtual.DemoDevice
import com.localbuds.virtual.virtualProfile
import kotlinx.coroutines.test.TestScope

/**
 * The rig for each demo device: the test-side driver registry. Exhaustive on purpose: a new
 * [DemoDevice] does not compile until it has a rig here, so every Bose virtual device runs the
 * same conformance checks.
 */
fun rigFor(d: DemoDevice, test: TestScope): ConformanceRig? = when (d) {
    DemoDevice.QC_ULTRA_EARBUDS_1, DemoDevice.QC_ULTRA, DemoDevice.QC_ULTRA_EARBUDS_2,
    DemoDevice.QC_EARBUDS_2, DemoDevice.QC_EARBUDS, DemoDevice.QC45, DemoDevice.NC700,
    DemoDevice.ULTRA_OPEN, DemoDevice.QC35_II,
    -> BoseRig(test, d, checkNotNull(d.virtualProfile()) { "$d has no virtual profile" }, seed = d.ordinal + 1)
}
