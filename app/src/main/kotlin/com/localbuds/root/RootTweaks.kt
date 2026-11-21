package com.localbuds.root

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Metadata parsed from `dumpsys media_session` for the overlay Now Playing pill. */
data class NowPlayingInfo(
    val title: String,
    val artist: String? = null,
    val packageName: String? = null,
)

/**
 * High-level orchestration for libsu root actions:
 * Requests superuser on startup when enabled, applies Doze whitelisting, sets active standby
 * bucket, auto-grants SYSTEM_ALERT_WINDOW / BT / notification permissions, wakes display on
 * case-open, and reads active media sessions.
 */
object RootTweaks {
    private const val TAG = "LocalBuds/Root"

    internal data class StepResult(val step: RootCommands.Step, val result: CommandResult)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Toggle Root Mode ON or OFF and persist the preference. */
    suspend fun setEnabled(context: Context, enabled: Boolean): RootState {
        val app = context.applicationContext
        RootShell.setEnabled(app, enabled)
        if (!enabled) {
            return RootShell.state.value
        }
        return enable(app)
    }

    /** Toggle the Now Playing media pill on the Frosted Glass overlay. */
    fun setShowMediaOnOverlay(context: Context, enabled: Boolean) {
        RootShell.setShowMediaOnOverlay(context.applicationContext, enabled)
    }

    /** Request superuser and apply all system optimizations (respects persisted Root Mode toggle). */
    suspend fun enable(context: Context): RootState {
        val app = context.applicationContext
        RootShell.setEnabled(app, true)
        val state = RootShell.request(app)
        if (state == RootState.GRANTED && RootShell.isActive()) {
            applyKeepAlive(app)
            grantOverlay(app)
        }
        return state
    }

    /** Startup trigger: restores root and applies tweaks only when Root Mode is enabled. */
    fun startup(context: Context) {
        val app = context.applicationContext
        scope.launch {
            if (!RootShell.isEnabled(app)) {
                Log.d(TAG, "Root mode is toggled off; skipping startup root tweaks")
                return@launch
            }
            val state = RootShell.restore(app)
            if (state == RootState.GRANTED) {
                applyKeepAlive(app)
                grantOverlay(app)
            } else if (state == RootState.UNKNOWN) {
                // Attempt first-run root request only if enabled
                enable(app)
            }
        }
    }

    /** Silent process restart: restores root and re-applies Doze whitelist & standby bucket if enabled. */
    fun restoreInBackground(context: Context) {
        val app = context.applicationContext
        scope.launch {
            if (!RootShell.isEnabled(app)) return@launch
            if (RootShell.restore(app) == RootState.GRANTED) {
                applyKeepAlive(app)
                grantOverlay(app)
            }
        }
    }

    internal suspend fun applyKeepAlive(context: Context): List<StepResult> {
        if (!RootShell.isActive()) return emptyList()
        return runSteps(context) { RootCommands.keepAliveSteps(it) }
    }

    internal suspend fun grantOverlay(context: Context): List<StepResult> {
        if (!RootShell.isActive()) return emptyList()
        return runSteps(context) { RootCommands.overlaySteps(it) }
    }

    /** Lights up display on lockscreen when earbuds connect (and optionally dismisses keyguard). */
    fun wakeForOverlay(dismissKeyguard: Boolean = false) {
        if (!RootShell.isActive()) return
        scope.launch {
            RootCommands.wakeForOverlaySteps(dismissKeyguard).forEach { step ->
                RootShell.exec(step.command)
            }
        }
    }

    /** Convenience alias kept for compatibility. */
    fun wakeScreen() = wakeForOverlay(dismissKeyguard = false)

    /** Ensures Bluetooth stack stays awake / active on case open when Root Mode is active. */
    fun nudgeBluetoothProfile(address: String) {
        if (!RootShell.isActive()) return
        if (!RootCommands.isSafeMacAddress(address)) return
        scope.launch {
            RootCommands.bluetoothNudgeSteps(address).forEach { step ->
                RootShell.exec(step.command)
            }
        }
    }

    /** Root fallback to launch BudsOverlayService if background restrictions blocked standard start. */
    suspend fun startOverlayService(context: Context): Boolean {
        if (!RootShell.isActive()) return false
        val pkg = context.applicationContext.packageName
        if (!RootCommands.isSafePackageName(pkg)) return false
        val res = RootShell.exec(RootCommands.startOverlayServiceCommand(pkg))
        if (!res.isSuccess) {
            Log.w(TAG, "Root startOverlayService failed: ${res.stderr.firstOrNull().orEmpty()}")
        }
        return res.isSuccess
    }

    /** Launches an activity via root shell (e.g., when triggered from background while locked). */
    suspend fun startActivityViaRoot(intent: Intent): Boolean {
        if (!RootShell.isActive()) return false
        val comp = intent.component?.flattenToShortString()
        if (comp != null && RootCommands.isSafeComponent(comp)) {
            val action = intent.action?.takeIf { Regex("^[A-Za-z0-9_.]+$").matches(it) }
            return RootShell.exec(RootCommands.startActivityCommand(comp, action)).isSuccess
        }
        val pkg = intent.`package`
        if (pkg != null && RootCommands.isSafePackageName(pkg)) {
            return RootShell.exec(RootCommands.startPackageLaunchCommand(pkg)).isSuccess
        }
        return false
    }

    /** Queries active media sessions via root `dumpsys media_session` for the overlay Now Playing pill. */
    suspend fun readNowPlaying(): NowPlayingInfo? {
        if (!RootShell.isActive() || !RootShell.showMediaOnOverlay()) return null
        val res = RootShell.exec(RootCommands.readNowPlayingCommand())
        if (!res.isSuccess || res.stdout.isEmpty()) return null
        return parseNowPlayingFromMediaSession(res.stdout)
    }

    internal fun parseNowPlayingFromMediaSession(lines: List<String>): NowPlayingInfo? {
        // Scan session blocks looking for an active PLAYING session and its metadata description line.
        var currentPkg: String? = null
        var isPlaying = false
        var fallbackInfo: NowPlayingInfo? = null

        val pkgRegex = Regex("""package=([A-Za-z0-9_.]+)""")
        val descRegex = Regex("""description=([^,\n]+)(?:,\s*([^,\n]+))?""")

        for (raw in lines) {
            val line = raw.trim()
            if (line.contains("Sessions Stack") || line.startsWith("User Records")) {
                currentPkg = null
                isPlaying = false
            }
            pkgRegex.find(line)?.let { match ->
                currentPkg = match.groupValues[1]
                isPlaying = false
            }
            if (line.contains("PlaybackState") && (line.contains("state=PLAYING(3)") || line.contains("state=3"))) {
                isPlaying = true
            }
            if (line.contains("metadata:") && line.contains("description=")) {
                val m = descRegex.find(line)
                if (m != null) {
                    val title = m.groupValues[1].trim().takeIf { it.isNotEmpty() && it != "null" }
                    val artist = m.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
                    if (title != null) {
                        val info = NowPlayingInfo(title = title, artist = artist, packageName = currentPkg)
                        if (isPlaying) return info
                        if (fallbackInfo == null) fallbackInfo = info
                    }
                }
            }
        }
        return fallbackInfo
    }

    private suspend fun runSteps(context: Context, steps: (String) -> List<RootCommands.Step>): List<StepResult> {
        val pkg = context.packageName
        if (!RootCommands.isSafePackageName(pkg)) {
            Log.w(TAG, "package name $pkg is not safe; skipping root commands")
            return emptyList()
        }
        return steps(pkg).map { step ->
            val result = RootShell.exec(step.command)
            Log.i(
                TAG,
                "${step.label}: " + if (result.isSuccess) "ok" else "failed (code ${result.exitCode}) ${result.stderr.firstOrNull().orEmpty()}",
            )
            StepResult(step, result)
        }
    }
}
