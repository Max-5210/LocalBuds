package com.localbuds.overlay

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.localbuds.R
import com.localbuds.appContainer
import com.localbuds.root.RootShell
import com.localbuds.root.RootState
import com.localbuds.root.RootTweaks
import com.localbuds.service.ServiceNotification
import com.localbuds.ui.common.BoseUltraEarbudGraphic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Clean minimalistic frosted-glass bottom sheet pop-up overlay that appears immediately on
 * case-open / connection (including over the lock screen when Root Mode is enabled) for 4 seconds.
 *
 * Pure visual card: shows only the product title (Bose QC Ultra Earbuds), the split Left and Right
 * earbud PNG icons ([R.drawable.earbud_left] and [R.drawable.earbud_right]), and their clean visual
 * battery level bars (no mode badges, no status text readouts, and no numerical percentage text).
 */
class BudsOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val visibleState = mutableStateOf(false)
    private var dismissing = false
    private var dismissRunnable: Runnable? = null
    private var attachJob: Job? = null
    private var inForeground = false

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_USE_FOREGROUND, false) == true) {
            enterForegroundNow()
        }

        // Single-session guard: never flash, tear down, or restart while already attached or attaching.
        if (overlayView != null || attachJob?.isActive == true) {
            return START_NOT_STICKY
        }

        val initialLeft = intent?.getIntExtra(EXTRA_LEFT_BAT, -1) ?: -1
        val initialRight = intent?.getIntExtra(EXTRA_RIGHT_BAT, -1) ?: -1
        val initialDevice = intent?.getStringExtra(EXTRA_DEVICE_NAME) ?: "Bose QC Ultra Earbuds"
        val initialAddress = intent?.getStringExtra(EXTRA_DEVICE_ADDRESS)

        attachJob = serviceScope.launch {
            if (!Settings.canDrawOverlays(this@BudsOverlayService)) {
                if (RootShell.isEnabled(this@BudsOverlayService)) {
                    withContext(Dispatchers.IO) {
                        if (RootShell.state.value == RootState.UNKNOWN || RootShell.state.value == RootState.UNAVAILABLE) {
                            RootShell.restore(this@BudsOverlayService)
                        }
                        if (RootShell.isActive()) {
                            RootTweaks.grantOverlay(this@BudsOverlayService)
                        }
                    }
                    repeat(12) {
                        if (Settings.canDrawOverlays(this@BudsOverlayService)) return@repeat
                        delay(50L)
                    }
                }
            }
            if (!Settings.canDrawOverlays(this@BudsOverlayService)) {
                Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted; cannot display popup overlay")
                lastLaunchAttemptMs = 0L
                stopSelfCleanly()
                return@launch
            }
            showPopup(initialLeft, initialRight, initialDevice, initialAddress)
        }
        return START_NOT_STICKY
    }

    private fun enterForegroundNow() {
        if (inForeground) return
        val hasConnect = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        runCatching {
            ServiceNotification(this).ensureChannel()
            val notif = NotificationCompat.Builder(this, ServiceNotification.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tile_mode)
                .setContentTitle("Bose QC Ultra Earbuds")
                .setContentText("Bose QC Ultra Earbuds")
                .setSilent(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasConnect) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else 0
            ServiceCompat.startForeground(this, OVERLAY_NOTIFICATION_ID, notif, type)
            inForeground = true
        }.onFailure { e ->
            Log.w(TAG, "enterForegroundNow failed: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun buildLayoutParams(withBlur: Boolean = true): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

        if (withBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            windowAnimations = 0
            if (withBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurBehindRadius = 40
                dimAmount = 0f
            }
        }
    }

    private fun scheduleAutoDismiss(delayMs: Long = VISIBLE_DURATION_MS) {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable { beginDismiss() }
        dismissRunnable = r
        handler.postDelayed(r, delayMs)
    }

    private fun showPopup(
        initialLeft: Int,
        initialRight: Int,
        initialDevice: String,
        initialAddress: String?,
    ) {
        dismissing = false
        visibleState.value = true
        val params = buildLayoutParams(withBlur = true)

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@BudsOverlayService)
            setViewTreeViewModelStoreOwner(this@BudsOverlayService)
            setViewTreeSavedStateRegistryOwner(this@BudsOverlayService)

            setContent {
                val liveState by appContainer.headphones.state.collectAsState()

                val deviceName = liveState.info?.customName?.takeIf { it.isNotBlank() }
                    ?: liveState.info?.productName?.takeIf { it.isNotBlank() }
                    ?: liveState.connection.deviceName?.removePrefix("LE-")?.trim()?.takeIf { it.isNotBlank() }
                    ?: initialDevice

                val leftBattery = liveState.battery?.left?.percent
                    ?: liveState.batteryPercent
                    ?: initialLeft.takeIf { it >= 0 }

                val rightBattery = liveState.battery?.right?.percent
                    ?: liveState.batteryPercent
                    ?: initialRight.takeIf { it >= 0 }

                val visible by visibleState

                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        initialOffsetY = { it },
                    ) + fadeIn(),
                    exit = slideOutVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        targetOffsetY = { it },
                    ) + fadeOut(),
                ) {
                    val cardShape = RoundedCornerShape(30.dp)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 22.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    if (dragAmount > 14f) {
                                        beginDismiss()
                                    }
                                }
                            },
                        shape = cardShape,
                        color = Color.Transparent,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .clip(cardShape)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xE0131924), Color(0xF0090C12))
                                    )
                                )
                                .border(
                                    width = 1.2.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color(0x66FFFFFF),
                                            Color(0x22FFFFFF),
                                            Color(0x14CBD5E1),
                                        )
                                    ),
                                    shape = cardShape,
                                )
                                .padding(horizontal = 22.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Subtle frosted glass drag pill
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.28f)),
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Product Title only (no mode badges, no status text readouts)
                            Text(
                                text = deviceName,
                                color = Color(0xFFF8FAFC),
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // Pure visual Left & Right earbud PNG icons + clean battery level bars
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                EarbudVisualTile(
                                    percentage = leftBattery,
                                    isRight = false,
                                    modifier = Modifier.weight(1f),
                                )
                                EarbudVisualTile(
                                    percentage = rightBattery,
                                    isRight = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }

        try {
            try {
                windowManager.addView(overlayView, params)
            } catch (blurErr: Exception) {
                Log.w(TAG, "addView with blur failed (${blurErr.message}), retrying without FLAG_BLUR_BEHIND")
                windowManager.addView(overlayView, buildLayoutParams(withBlur = false))
            }
            isShowing = true
            lastShownElapsedMs = SystemClock.elapsedRealtime()
            lastShownKey = initialAddress?.uppercase() ?: initialDevice
            visibleState.value = true
            scheduleAutoDismiss(VISIBLE_DURATION_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add BudsOverlayService window: ${e.message}", e)
            overlayView = null
            isShowing = false
            lastLaunchAttemptMs = 0L
            stopSelfCleanly()
        }
    }

    @Composable
    private fun EarbudVisualTile(
        percentage: Int?,
        isRight: Boolean,
        modifier: Modifier = Modifier,
    ) {
        val pct = percentage ?: -1
        val barColor = when {
            pct < 0 -> Color(0xFF94A3B8)
            pct <= 20 -> Color(0xFFF87171)
            pct <= 40 -> Color(0xFFFBBF24)
            else -> Color(0xFF4ADE80)
        }

        val animatedFraction by animateFloatAsState(
            targetValue = if (pct >= 0) (pct.coerceIn(0, 100) / 100f).coerceAtLeast(0.08f) else 0.35f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "battery_bar_fill",
        )

        val tileShape = RoundedCornerShape(22.dp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .clip(tileShape)
                .background(Color(0x881A2230))
                .border(1.dp, Color(0x2EFFFFFF), tileShape)
                .padding(vertical = 16.dp, horizontal = 14.dp),
        ) {
            BoseUltraEarbudGraphic(
                isRight = isRight,
                modifier = Modifier.size(72.dp),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Clean visual battery level bar (no numerical percentage text)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFraction)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(barColor),
                )
            }
        }
    }

    private fun beginDismiss() {
        if (dismissing) return
        dismissing = true
        handler.removeCallbacksAndMessages(null)
        visibleState.value = false
        handler.postDelayed({ dismissOverlay() }, EXIT_ANIM_MS)
    }

    private fun detachOverlayView() {
        handler.removeCallbacksAndMessages(null)
        overlayView?.let {
            runCatching { windowManager.removeView(it) }
            overlayView = null
        }
        isShowing = false
        lastShownElapsedMs = SystemClock.elapsedRealtime()
    }

    private fun dismissOverlay() {
        detachOverlayView()
        stopSelfCleanly()
    }

    private fun stopSelfCleanly() {
        if (inForeground) {
            runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
            inForeground = false
        }
        stopSelf()
    }

    override fun onDestroy() {
        attachJob?.cancel()
        serviceScope.cancel()
        detachOverlayView()
        if (inForeground) {
            runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
            inForeground = false
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LocalBuds/Overlay"
        private const val OVERLAY_NOTIFICATION_ID = 1002
        const val EXTRA_LEFT_BAT = "LEFT_BAT"
        const val EXTRA_RIGHT_BAT = "RIGHT_BAT"
        const val EXTRA_CASE_BAT = "CASE_BAT"
        const val EXTRA_ANC_MODE = "ANC_MODE"
        const val EXTRA_DEVICE_NAME = "DEVICE_NAME"
        const val EXTRA_DEVICE_ADDRESS = "DEVICE_ADDRESS"
        const val EXTRA_USE_FOREGROUND = "USE_FOREGROUND"

        private const val VISIBLE_DURATION_MS = 4_000L
        private const val EXIT_ANIM_MS = 280L
        private const val COOLDOWN_MS = 12_000L
        private const val LAUNCH_IN_FLIGHT_MS = 2_000L

        @Volatile
        var isShowing: Boolean = false
            private set

        @Volatile
        private var lastShownElapsedMs = 0L

        @Volatile
        private var lastLaunchAttemptMs = 0L

        @Volatile
        private var lastShownKey: String? = null

        private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Resets the single-session cooldown when earbuds disconnect so reopening the case pops cleanly. */
        fun notifyDisconnected(address: String?) {
            val key = address?.uppercase()
            if (key == null || lastShownKey == null || key == lastShownKey) {
                lastShownElapsedMs = 0L
                lastLaunchAttemptMs = 0L
                lastShownKey = null
            }
        }

        /**
         * Launches the minimalistic visual overlay immediately from either a foreground or background/receiver context,
         * waking the lock screen and falling back cleanly to Root Mode privileges on Android 15/16 if needed.
         */
        fun launch(
            context: Context,
            deviceName: String? = null,
            leftBat: Int = -1,
            rightBat: Int = -1,
            caseBat: Int = -1,
            ancMode: String? = null,
            address: String? = null,
        ) {
            val app = context.applicationContext
            val now = SystemClock.elapsedRealtime()
            // Strict single-trigger guard: prevent any double-trigger or flash while showing,
            // within the session cooldown window, or while a launch is already in flight.
            if (isShowing ||
                (lastShownElapsedMs > 0L && now - lastShownElapsedMs < COOLDOWN_MS) ||
                (lastLaunchAttemptMs > 0L && now - lastLaunchAttemptMs < LAUNCH_IN_FLIGHT_MS)
            ) {
                return
            }
            lastLaunchAttemptMs = now
            lastShownKey = address?.uppercase() ?: deviceName ?: "default"

            // Wake display immediately (lock-screen illumination)
            RootTweaks.wakeForOverlay(dismissKeyguard = false)
            wakeDisplayFallback(app)

            val baseIntent = Intent(app, BudsOverlayService::class.java).apply {
                putExtra(EXTRA_LEFT_BAT, leftBat)
                putExtra(EXTRA_RIGHT_BAT, rightBat)
                putExtra(EXTRA_CASE_BAT, caseBat)
                putExtra(EXTRA_ANC_MODE, ancMode ?: "Quiet")
                putExtra(EXTRA_DEVICE_NAME, deviceName ?: "Bose QC Ultra Earbuds")
                putExtra(EXTRA_DEVICE_ADDRESS, address)
            }

            if (Settings.canDrawOverlays(app)) {
                try {
                    app.startService(Intent(baseIntent).putExtra(EXTRA_USE_FOREGROUND, false))
                    return
                } catch (e: Exception) {
                    Log.d(TAG, "plain startService with overlay grant failed, trying FGS: ${e.message}")
                }
            }

            try {
                ContextCompat.startForegroundService(app, Intent(baseIntent).putExtra(EXTRA_USE_FOREGROUND, true))
                return
            } catch (e: Exception) {
                Log.d(TAG, "startForegroundService failed, trying startService: ${e.message}")
            }

            try {
                app.startService(Intent(baseIntent).putExtra(EXTRA_USE_FOREGROUND, false))
                return
            } catch (e: Exception) {
                Log.d(TAG, "startService failed, trying root fallback: ${e.message}")
            }

            if (RootShell.isEnabled(app)) {
                bgScope.launch {
                    if (RootShell.state.value != RootState.GRANTED) {
                        RootShell.restore(app)
                    }
                    RootTweaks.grantOverlay(app)
                    RootTweaks.startOverlayService(app)
                }
            } else {
                lastLaunchAttemptMs = 0L
            }
        }

        @Suppress("DEPRECATION")
        @SuppressLint("WakelockTimeout")
        private fun wakeDisplayFallback(context: Context) {
            runCatching {
                val pm = context.getSystemService(PowerManager::class.java) ?: return
                if (!pm.isInteractive) {
                    pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "LocalBuds:overlayWake",
                    ).apply {
                        setReferenceCounted(false)
                        acquire(4_500L)
                    }
                }
            }
        }
    }
}
