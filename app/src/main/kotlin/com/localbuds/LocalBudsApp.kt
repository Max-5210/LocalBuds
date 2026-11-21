package com.localbuds

import android.app.Application
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.Context
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import androidx.core.content.ContextCompat
import com.localbuds.bt.DeviceFinder
import com.localbuds.extras.ExtrasInitializer
import com.localbuds.root.RootShell
import com.localbuds.root.RootTweaks
import com.localbuds.service.BluetoothConnectionReceiver

class LocalBudsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        logNonSdkApiUseInDebugBuilds()
        RootShell.configure(this)
        container = AppContainer(this)
        com.localbuds.backup.BackupStaging.applyIfReady(this)
        startUnlocked()
    }

    private fun logNonSdkApiUseInDebugBuilds() {
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy())
                .detectNonSdkApiUsage()
                .penaltyLog()
                .build(),
        )
    }

    private val started = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Runs once per process: at start when unlocked. */
    private fun startUnlocked() {
        if (!started.compareAndSet(false, true)) return
        // Root-powered performance: whitelist from Doze, grant SYSTEM_ALERT_WINDOW, keep BT wake-ups instant
        RootTweaks.startup(this)
        // Register in-process dynamic Bluetooth receiver so ACL/A2DP/HFP/LE connect events fire in 0ms
        // without waiting on Android's background manifest broadcast queue.
        runCatching {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(DeviceFinder.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(
                this,
                BluetoothConnectionReceiver(),
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
        }
        // Automations, low-battery alert and widget/tile refresh
        ExtrasInitializer.start(this, container)
        // Call detection
        container.callAudio
        // Shortcuts remapper
        container.shortcuts
        // Headset EQ
        container.headsetEq.start()
        // Diagnostics timeline
        container.timeline.start()
        // Companion presence
        kotlin.concurrent.thread(name = "LocalBuds-presence", isDaemon = true) {
            com.localbuds.bt.CompanionPresence(this).observeAll()
        }
    }
}

/** `context.appContainer` from anywhere that has a Context. */
val Context.appContainer: AppContainer
    get() = (applicationContext as LocalBudsApp).container
