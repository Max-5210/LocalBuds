package com.localbuds

import android.content.Context
import com.localbuds.audio.AudioTuningRepositoryImpl
import com.localbuds.audio.HeadsetEqMemory
import com.localbuds.bt.AudioLinkMonitor
import com.localbuds.bt.DeviceFamily
import com.localbuds.bt.DeviceFinder
import com.localbuds.call.CallAudioRepositoryImpl
import com.localbuds.call.HeadsetVolumeControl
import com.localbuds.codec.CodecRepositoryImpl
import com.localbuds.core.AudioTuningRepository
import com.localbuds.core.AutomationRepository
import com.localbuds.core.CallAudioRepository
import com.localbuds.core.CodecRepository
import com.localbuds.core.HeadphoneRepository
import com.localbuds.core.MicCheckRepository
import com.localbuds.extras.automation.AutomationRepositoryImpl
import com.localbuds.mic.MicCheckRepositoryImpl
import com.localbuds.service.HeadphoneProtocol
import com.localbuds.service.QcUltra2Protocol
import com.localbuds.service.RoutingHeadphoneRepository
import com.localbuds.service.ServiceHeadphoneRepository
import com.localbuds.service.VendorDriver
import com.localbuds.shortcut.ShortcutEngine
import com.localbuds.shortcut.ShortcutPrefs
import com.localbuds.sleep.SleepRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container. One instance per process, owned by [LocalBudsApp].
 * Streamlined specifically for real Bose QuietComfort & Ultra hardware connections.
 */
class AppContainer(private val appContext: Context) {
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The QC Ultra / QC Ultra Earbuds command layer (BMAP addresses, payloads and state mapping). */
    private val headphoneProtocol: HeadphoneProtocol by lazy { QcUltra2Protocol() }

    /**
     * Mirrors `AppPreferences.autoConnectOnBluetooth`; read on each Bluetooth event.
     */
    private val autoConnect: () -> Boolean = { runCatching { automation.preferences.value.autoConnectOnBluetooth }.getOrDefault(true) }

    private val boseHeadphones: ServiceHeadphoneRepository by lazy {
        ServiceHeadphoneRepository(
            appContext,
            headphoneProtocol,
            applicationScope,
            autoConnect = autoConnect,
            prefs = devicePrefs,
            finder = deviceFinder,
        )
    }

    /**
     * Shizuku shell for the few reads Android keeps from apps (dumpsys); optional.
     */
    val privilegedShell: com.localbuds.call.PrivilegedShell by lazy { com.localbuds.call.ShizukuPrivilegedShell(appContext) }

    /**
     * The saved headphones and what LocalBuds has learned about how to reach them.
     */
    val devicePrefs: com.localbuds.bt.DevicePrefs by lazy { com.localbuds.bt.DevicePrefs(appContext) }

    /** What the app talks to: exclusively drives Bose BMAP devices. */
    val headphones: HeadphoneRepository by lazy {
        RoutingHeadphoneRepository(
            drivers = listOf(
                VendorDriver(DeviceFamily.BOSE, boseHeadphones),
            ),
            prefs = devicePrefs,
            finder = deviceFinder,
            scope = applicationScope,
            audioLink = AudioLinkMonitor(appContext, deviceFinder, applicationScope),
        )
    }

    val tuning: AudioTuningRepository by lazy { AudioTuningRepositoryImpl(appContext, applicationScope) }

    /** Pro EQ preset per headset, applied when that headset connects. Started by [LocalBudsApp]. */
    val headsetEq: HeadsetEqMemory by lazy { HeadsetEqMemory(appContext, headphones, tuning, applicationScope) }
    val codec: CodecRepository by lazy {
        CodecRepositoryImpl(appContext, applicationScope, preferredAddress = { headphones.state.value.connection.deviceAddress })
    }
    val micCheck: MicCheckRepository by lazy { MicCheckRepositoryImpl(appContext, applicationScope) }
    val automation: AutomationRepository by lazy { AutomationRepositoryImpl(appContext, applicationScope) }

    /** Bonded-device lookup shared by connection code and UI. */
    val deviceFinder: DeviceFinder by lazy { DeviceFinder(appContext, devicePrefs) }

    /** Diagnostics timeline. */
    val timeline: com.localbuds.bt.TimelineRecorder by lazy {
        com.localbuds.bt.TimelineRecorder(
            appContext,
            com.localbuds.bt.TimelineStore(java.io.File(appContext.filesDir, "bt_timeline.tsv")),
            applicationScope,
            devicePrefs,
        )
    }

    /** Find my earbuds: a tone into the connected Bose earbuds. */
    val find: com.localbuds.find.FindPlayer by lazy { com.localbuds.find.FindPlayer(appContext, headphones, deviceFinder, applicationScope) }

    /** Shortcuts remapper. */
    val shortcuts: ShortcutEngine by lazy {
        ShortcutEngine(appContext, ShortcutPrefs(appContext), tuning, headphones, devicePrefs, applicationScope)
    }

    /** In-call volume boost and call-codec readout. */
    val callAudio: CallAudioRepository by lazy {
        CallAudioRepositoryImpl(
            appContext, applicationScope, headphones,
            headsetVolume = HeadsetVolumeControl { percent ->
                val state = headphones.state.value
                val max = state.volumeMax ?: return@HeadsetVolumeControl false
                val target = (max * percent / 100.0).toInt().coerceIn(0, max)
                if ((state.volume ?: 0) >= target) return@HeadsetVolumeControl true
                headphones.setVolume(target)
                true
            },
        )
    }

    /** Sleep sounds and alarm. */
    val sleepRuntime: com.localbuds.sleep.SleepRepositoryImpl by lazy {
        SleepRepositoryImpl(appContext, headphones, deviceFinder, com.localbuds.bt.CompanionRemote(appContext), applicationScope)
    }

    val sleep: com.localbuds.core.SleepRepository get() = sleepRuntime
}
