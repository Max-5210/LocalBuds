package com.localbuds.bt

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Buffer
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The per-family link hints: BOSE on the key it always had (`rfcomm_channel`), stored only for
 * the saved device and forgotten when another device is saved. A real protobuf-backed DataStore.
 */
class DevicePrefsHintTest {

    @get:Rule val folder = TemporaryFolder()

    private lateinit var store: DataStore<Preferences>

    private class FilePrefsStore(private val file: File) : DataStore<Preferences> {
        private val lock = Mutex()
        private val state = MutableStateFlow(read())

        private fun read(): Preferences =
            if (!file.isFile) emptyPreferences()
            else runBlocking { PreferencesSerializer.readFrom(Buffer().write(file.readBytes())) }

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = lock.withLock {
            val next = transform(read())
            val buf = Buffer()
            PreferencesSerializer.writeTo(next, buf)
            file.writeBytes(buf.readByteArray())
            state.value = read()
            state.value
        }
    }

    private fun prefs(): DevicePrefs {
        store = FilePrefsStore(File(folder.root, "bt_device.preferences_pb"))
        return DevicePrefs(store)
    }

    private fun raw(): Preferences = runBlocking { store.data.first() }

    @Test fun `Bose hints stay on their legacy keys`() = runBlocking<Unit> {
        val p = prefs()
        p.save("aa:bb:cc:dd:ee:01", "Bose QC Ultra Headphones")
        p.saveCandidate("AA:BB:CC:DD:EE:01", BmapCandidate.Channel(8))
        assertEquals(8, raw()[intPreferencesKey("rfcomm_channel")], "the 0.75 key, as an Int")
        assertEquals(BmapCandidate.Channel(8), p.candidateFor("AA:BB:CC:DD:EE:01"))
        assertEquals("8", p.linkHint(DeviceFamily.BOSE, "aa:bb:cc:dd:ee:01"))

        p.saveLinkHint(DeviceFamily.BOSE, "AA:BB:CC:DD:EE:01", BmapCandidate.SdpSpp.prefValue.toString())
        assertEquals(BmapCandidate.SdpSpp, p.candidateFor("AA:BB:CC:DD:EE:01"), "SDP winners keep their negative codes")
        assertFailsWith<IllegalArgumentException> { p.saveLinkHint(DeviceFamily.BOSE, "AA:BB:CC:DD:EE:01", "spp") }
    }

    @Test fun `a value written by 0_75 is read back unchanged`() = runBlocking<Unit> {
        val p = prefs()
        p.save("AA:BB:CC:DD:EE:02", "LE-Bose QC Ultra")
        // What 0.75 wrote for an LE CoC winner, straight into the store.
        store.edit { it[intPreferencesKey("rfcomm_channel")] = BmapCandidate.LeCoc.prefValue }
        assertEquals(BmapCandidate.LeCoc, p.candidateFor("AA:BB:CC:DD:EE:02"))
        assertEquals(DeviceFamily.BOSE, p.knownFamily("AA:BB:CC:DD:EE:02"), "the pre-0.6 fallback still reads the legacy key")
    }

    @Test fun `hints belong to the saved device and go when another one is saved`() = runBlocking<Unit> {
        val p = prefs()
        p.save("AA:BB:CC:DD:EE:04", "Bose QC Ultra Earbuds")
        p.saveLinkHint(DeviceFamily.BOSE, "AA:BB:CC:DD:EE:99", "2")
        assertNull(p.linkHint(DeviceFamily.BOSE, "AA:BB:CC:DD:EE:99"), "not the saved device: nothing stored")
        assertNull(raw()[intPreferencesKey("rfcomm_channel")])

        p.saveCandidate("AA:BB:CC:DD:EE:04", BmapCandidate.Channel(2))
        p.save("aa:bb:cc:dd:ee:04", "QC Ultra Earbuds") // same device, renamed: everything stays
        assertEquals("2", p.linkHint(DeviceFamily.BOSE, "AA:BB:CC:DD:EE:04"))
        assertEquals(BmapCandidate.Channel(2), p.candidateFor("AA:BB:CC:DD:EE:04"))

        p.save("AA:BB:CC:DD:EE:05", "Other")
        assertNull(raw()[intPreferencesKey("rfcomm_channel")])
        assertNull(p.linkHint(DeviceFamily.BOSE, "AA:BB:CC:DD:EE:04"))
        assertEquals("AA:BB:CC:DD:EE:05", p.savedDevice()?.address)
    }
}
