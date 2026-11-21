package com.localbuds.root

/**
 * Shell commands executed via libsu root shell.
 * Includes Doze whitelisting, standby bucket optimization, Bluetooth permissions,
 * SYSTEM_ALERT_WINDOW auto-grant, screen/keyguard wake, overlay service fallback launch,
 * and Now Playing media session inspection on rooted devices (e.g., Pixel 10 Pro Fold).
 */
internal object RootCommands {

    /** One command with a label for logging and verification. */
    data class Step(val label: String, val command: String)

    private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
    private val MAC_ADDRESS = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")
    private val COMPONENT_NAME = Regex("^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$")

    fun isSafePackageName(packageName: String): Boolean = PACKAGE_NAME.matches(packageName)

    fun isSafeMacAddress(address: String): Boolean = MAC_ADDRESS.matches(address)

    fun isSafeComponent(component: String): Boolean = COMPONENT_NAME.matches(component)

    /**
     * Prevents process throttling or deferral:
     * - Doze allow-list (whitelist)
     * - RUN_IN_BACKGROUND & RUN_ANY_IN_BACKGROUND
     * - Standby bucket set to 'active'
     * - Wake lock & Foreground start
     * - Instantaneous Bluetooth connect/scan & notification permission auto-grant
     */
    fun keepAliveSteps(packageName: String): List<Step> = listOf(
        Step("Doze allow-list (cmd)", "cmd deviceidle whitelist +$packageName"),
        Step("Doze allow-list (dumpsys)", "dumpsys deviceidle whitelist +$packageName"),
        Step("Background: RUN_IN_BACKGROUND", "cmd appops set $packageName RUN_IN_BACKGROUND allow"),
        Step("Background: RUN_ANY_IN_BACKGROUND", "cmd appops set $packageName RUN_ANY_IN_BACKGROUND allow"),
        Step("Standby bucket: active", "am set-standby-bucket $packageName active"),
        Step("Wake Lock allow", "cmd appops set $packageName WAKE_LOCK allow"),
        Step("Foreground start allow", "cmd appops set $packageName START_FOREGROUND allow"),
        Step("Grant BLUETOOTH_CONNECT", "pm grant $packageName android.permission.BLUETOOTH_CONNECT"),
        Step("Grant BLUETOOTH_SCAN", "pm grant $packageName android.permission.BLUETOOTH_SCAN"),
        Step("Grant POST_NOTIFICATIONS", "pm grant $packageName android.permission.POST_NOTIFICATIONS"),
    )

    /** Auto-grants SYSTEM_ALERT_WINDOW so the connection overlay appears immediately over any app without manual prompts. */
    fun overlaySteps(packageName: String): List<Step> = listOf(
        Step("Overlay: SYSTEM_ALERT_WINDOW (appops)", "cmd appops set $packageName SYSTEM_ALERT_WINDOW allow"),
        Step("Overlay: SYSTEM_ALERT_WINDOW (pm)", "pm grant $packageName android.permission.SYSTEM_ALERT_WINDOW"),
    )

    /** Wakes the display via keyevent 224 (KEYCODE_WAKEUP), and optionally dismisses keyguard when launching shortcuts. */
    fun wakeForOverlaySteps(dismissKeyguard: Boolean): List<Step> = buildList {
        add(Step("Wake screen (input keyevent 224)", "input keyevent 224"))
        if (dismissKeyguard) {
            add(Step("Dismiss keyguard (wm)", "wm dismiss-keyguard"))
        }
    }

    /** Ensures Bluetooth stack is active and ready for immediate profile connection. */
    fun bluetoothNudgeSteps(@Suppress("UNUSED_PARAMETER") address: String): List<Step> = listOf(
        Step("Ensure BT manager enabled", "cmd bluetooth_manager enable"),
    )

    /** Root fallback command to start BudsOverlayService as a foreground service if background FGS restrictions block it. */
    fun startOverlayServiceCommand(packageName: String): String =
        "am start-foreground-service -n $packageName/.overlay.BudsOverlayService --ez USE_FOREGROUND true"

    /** Starts a target component from root when background activity launch is blocked while locked. */
    fun startActivityCommand(component: String, action: String? = null): String {
        val actionFlag = if (!action.isNullOrBlank()) "-a $action " else ""
        return "am start ${actionFlag}-n $component -f 0x10000000"
    }

    fun startPackageLaunchCommand(packageName: String): String =
        "monkey -p $packageName -c android.intent.category.LAUNCHER 1"

    /** Reads active media sessions to extract currently playing title/artist for the overlay media pill. */
    fun readNowPlayingCommand(): String = "dumpsys media_session"
}
