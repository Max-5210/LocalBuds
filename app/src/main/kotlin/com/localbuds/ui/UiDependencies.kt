package com.localbuds.ui

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.localbuds.appContainer
import com.localbuds.core.AudioTuningRepository
import com.localbuds.core.AutomationRepository
import com.localbuds.core.CallAudioRepository
import com.localbuds.core.CodecRepository
import com.localbuds.core.HeadphoneRepository
import com.localbuds.core.MicCheckRepository
import com.localbuds.core.SleepRepository
import com.localbuds.core.fake.FakeAudioTuningRepository
import com.localbuds.core.fake.FakeAutomationRepository
import com.localbuds.core.fake.FakeCallAudioRepository
import com.localbuds.core.fake.FakeCodecRepository
import com.localbuds.core.fake.FakeHeadphoneRepository
import com.localbuds.core.fake.FakeMicCheckRepository
import com.localbuds.core.fake.FakeSleepRepository
import com.localbuds.ui.common.UiMessenger

/**
 * Everything the UI layer needs, bundled so ViewModels can be built from one object. At runtime
 * it wraps [com.localbuds.AppContainer]; previews and tests use [fakeUiDependencies].
 */
class UiDependencies(
    val headphones: HeadphoneRepository,
    val tuning: AudioTuningRepository,
    val codec: CodecRepository,
    val micCheck: MicCheckRepository,
    val automation: AutomationRepository,
    val callAudio: CallAudioRepository,
    val messenger: UiMessenger = UiMessenger(),
    /** Per-headset Pro EQ memory; null in previews and tests. */
    val headsetEq: com.localbuds.audio.HeadsetEqMemory? = null,
    /** The Sleep tab; the in-memory fake in previews and tests. */
    val sleep: SleepRepository = FakeSleepRepository(),
) {
    companion object {
        fun from(context: Context): UiDependencies {
            val c = context.appContainer
            // Process-wide messenger: ViewModels keep the instance they were built with across
            // configuration changes, so the root must collect from that same one.
            return UiDependencies(c.headphones, c.tuning, c.codec, c.micCheck, c.automation, c.callAudio, UiMessenger.shared, c.headsetEq, c.sleep)
        }
    }
}

val LocalUiDependencies = staticCompositionLocalOf<UiDependencies> {
    error("No UiDependencies provided; wrap the tree in LocalBudsApp or EarshotPreview")
}

fun fakeUiDependencies(startConnected: Boolean = true): UiDependencies = UiDependencies(
    headphones = FakeHeadphoneRepository(startConnected = startConnected),
    tuning = FakeAudioTuningRepository(),
    codec = FakeCodecRepository(),
    micCheck = FakeMicCheckRepository(),
    automation = FakeAutomationRepository(),
    callAudio = FakeCallAudioRepository(),
)
