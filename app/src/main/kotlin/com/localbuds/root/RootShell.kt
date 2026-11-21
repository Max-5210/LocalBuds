package com.localbuds.root

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The app's one door to root, over libsu's "main shell" (one long-lived `su` process per app
 * process, which libsu creates on first use and caches). This is the only file that imports libsu.
 */
object RootShell {
    private const val TAG = "LocalBuds/Root"

    /** Seconds libsu waits for `su` to answer. Long enough for a person to read the grant prompt and tap. */
    private const val SHELL_TIMEOUT_S = 30L

    private const val PREFS = "localbuds_root"
    private const val KEY_GRANTED_BEFORE = "granted_before"
    private const val KEY_ROOT_MODE_ENABLED = "root_mode_enabled"
    private const val KEY_OVERLAY_NOW_PLAYING = "overlay_now_playing"

    private val _state = MutableStateFlow(RootState.UNKNOWN)

    /** Where root stands now. Collect it from the UI. */
    val state: StateFlow<RootState> = _state.asStateFlow()

    private val _enabled = MutableStateFlow(true)
    val enabledFlow: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _showMediaOnOverlay = MutableStateFlow(true)
    val showMediaOnOverlayFlow: StateFlow<Boolean> = _showMediaOnOverlay.asStateFlow()

    /** True when superuser is granted AND the user has Root Mode enabled in preferences. */
    fun isActive(): Boolean = _state.value == RootState.GRANTED && _enabled.value

    fun showMediaOnOverlay(): Boolean = _showMediaOnOverlay.value

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Mutex()

    /**
     * Sets libsu up and hydrates persisted toggles. Call once in `Application.onCreate`.
     */
    fun configure(context: Context) {
        val app = context.applicationContext
        val debuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        runCatching {
            Shell.enableVerboseLogging = debuggable
            Shell.setDefaultBuilder(Shell.Builder.create().setTimeout(SHELL_TIMEOUT_S))
        }.onFailure { Log.w(TAG, "libsu was already in use; keeping its configuration (${it.javaClass.simpleName})") }
        runCatching {
            val p = prefs(app)
            _enabled.value = p.getBoolean(KEY_ROOT_MODE_ENABLED, true)
            _showMediaOnOverlay.value = p.getBoolean(KEY_OVERLAY_NOW_PLAYING, true)
        }
    }

    fun isEnabled(context: Context): Boolean {
        val value = prefs(context.applicationContext).getBoolean(KEY_ROOT_MODE_ENABLED, true)
        _enabled.value = value
        return value
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context.applicationContext).edit().putBoolean(KEY_ROOT_MODE_ENABLED, enabled).apply()
        _enabled.value = enabled
        if (!enabled) {
            Log.i(TAG, "root mode toggled OFF by user")
        }
    }

    fun setShowMediaOnOverlay(context: Context, enabled: Boolean) {
        prefs(context.applicationContext).edit().putBoolean(KEY_OVERLAY_NOW_PLAYING, enabled).apply()
        _showMediaOnOverlay.value = enabled
    }

    /** Opens the root shell, asking the root manager if it has not been asked. Returns the outcome. */
    suspend fun request(context: Context): RootState {
        val app = context.applicationContext
        return scope.async { gate.withLock { openMainShell(app) } }.await()
    }

    /**
     * Process-start path: reopens root only if the user granted it on an earlier run.
     */
    suspend fun restore(context: Context): RootState {
        val app = context.applicationContext
        val grantedBefore = withContext(Dispatchers.IO) { prefs(app).getBoolean(KEY_GRANTED_BEFORE, false) }
        return if (grantedBefore) request(app) else _state.value
    }

    /**
     * Runs [command] in the root shell if Root Mode is active & granted.
     */
    suspend fun exec(command: String): CommandResult {
        if (!isActive()) return CommandResult.NOT_RUN
        return withContext(Dispatchers.IO) {
            if (!Shell.getShell().isRoot) {
                _state.value = RootState.UNAVAILABLE
                return@withContext CommandResult.NOT_RUN
            }
            val result = Shell.cmd(command).exec()
            CommandResult(
                isSuccess = result.isSuccess,
                exitCode = result.code,
                stdout = result.out,
                stderr = result.err,
            )
        }
    }

    private fun openMainShell(app: Context): RootState {
        _state.value = RootState.CHECKING
        val granted = runCatching {
            val cached = Shell.getCachedShell()
            if (cached != null && !cached.isRoot) cached.close()
            Shell.getShell().isRoot
        }.getOrElse {
            Log.w(TAG, "could not open a shell: ${it.javaClass.simpleName}")
            false
        }

        val result = if (granted) RootState.GRANTED else RootState.UNAVAILABLE
        prefs(app).edit().putBoolean(KEY_GRANTED_BEFORE, granted).apply()
        _state.value = result
        Log.i(TAG, "root: $result")
        return result
    }

    private fun prefs(app: Context) = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
