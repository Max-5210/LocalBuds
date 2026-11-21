package com.localbuds.virtual

/**
 * Virtual Bose models used only by unit and conformance tests ([VirtualQcUltra2]).
 * Demo mode has been removed from the runtime app so only real Bose hardware connections exist.
 */
enum class DemoDevice(
    val displayName: String,
) {
    QC_ULTRA_EARBUDS_1("Bose QC Ultra Earbuds (1st Gen 0x4072)"),
    QC_ULTRA_EARBUDS_2("Bose QC Ultra Earbuds (2nd Gen)"),
    QC_ULTRA("Bose QC Ultra Headphones"),
    QC_EARBUDS_2("Bose QC Earbuds II"),
    QC_EARBUDS("Bose QC Earbuds (2020)"),
    QC45("Bose QuietComfort 45"),
    NC700("Bose Noise Cancelling Headphones 700"),
    ULTRA_OPEN("Bose Ultra Open Earbuds"),
    QC35_II("Bose QuietComfort 35 II"),
    ;
}

/** A generic virtual test headset fixture. */
class DemoHeadset(val name: String, val advert: ByteArray)

/**
 * A virtual unit that unit tests can press.
 */
interface DemoPressTarget {
    fun pressPlayPause()
    fun swipeVolume(delta: Int)
    fun pressMainButton()
}

/** Where unit tests find the virtual unit to press. */
object DemoPresses {
    fun current(): DemoPressTarget? = VirtualQcUltra2.current()
}
