package com.localbuds.virtual

import com.localbuds.bt.BmapConnection
import com.localbuds.bt.BtLog
import com.localbuds.bt.ByteTransport
import com.localbuds.core.LabReading
import com.localbuds.protocol.BmapFrameBuffer
import com.localbuds.protocol.BmapOperator
import com.localbuds.service.ProtocolLog
import com.localbuds.service.lab.BmapLab
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Lab scanner of Bose BMAP against its virtual device, through the real connection class:
 * a Deep scan completes and returns readings, and a spy on the transport proves that nothing but
 * GET reads left the phone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LabScanEndToEndTest {

    @Before
    fun quiet() {
        BtLog.sink = { _, _, _ -> }
        ProtocolLog.sink = { _, _, _ -> }
    }

    /** The virtual device's own transport, recording every byte the phone writes. */
    private class SpyTransport(private val inner: ByteTransport) : ByteTransport {
        private val written = mutableListOf<ByteArray>()
        override val isConnected: Boolean get() = inner.isConnected
        override val incoming = inner.incoming
        override suspend fun write(bytes: ByteArray) {
            synchronized(written) { written += bytes.copyOf() }
            inner.write(bytes)
        }
        override fun close() = inner.close()
        fun clear() = synchronized(written) { written.clear() }
        fun all(): ByteArray = synchronized(written) { written.fold(ByteArray(0)) { a, b -> a + b } }
    }

    private suspend fun Flow<LabReading>.collectAll(): List<LabReading> = toList()

    // ---- Bose (BMAP) ----------------------------------------------------------------------

    @Test fun `Bose deep scan sweeps 64 x 64 GETs and sends nothing else`() = runTest {
        val spy = SpyTransport(VirtualTransport(VirtualQcUltra2(), replyDelayMs = 1L..1L, tickIntervalMs = 3_600_000L, random = Random(1)))
        val conn = BmapConnection(spy, backgroundScope, minWriteGapMs = 0)
        testScheduler.runCurrent()

        val readings = BmapLab.scan(conn, emptyList(), deep = true).collectAll()
        assertEquals(64 * 64, readings.size)
        assertTrue(readings.first { it.block == 2 && it.function == 2 }.frames.single().operatorName == "STATUS", "battery answered")
        assertEquals("Settings", readings.first { it.block == 1 }.groupName)

        val sent = BmapFrameBuffer().feed(spy.all())
        assertEquals(64 * 64, sent.size)
        assertTrue(sent.all { it.operator == BmapOperator.GET }, "GET only: ${sent.filter { it.operator != BmapOperator.GET }}")
        assertEquals((0..63).flatMap { b -> (0..63).map { b to it } }, sent.map { it.block to it.function })
        conn.close()
    }

    @Test fun `Bose scan reads functions 0 to 40 of the selected blocks`() = runTest {
        val spy = SpyTransport(VirtualTransport(VirtualQcUltra2(), replyDelayMs = 1L..1L, tickIntervalMs = 3_600_000L, random = Random(1)))
        val conn = BmapConnection(spy, backgroundScope, minWriteGapMs = 0)
        testScheduler.runCurrent()
        val profile = BmapLab.profile()
        assertTrue(profile.printConsole)
        assertEquals(setOf(1, 2, 31), profile.defaultGroups)
        assertEquals(4096, profile.deepReadCount)

        val readings = BmapLab.scan(conn, listOf(1, 31), deep = false).collectAll()
        assertEquals(2 * 41, readings.size)
        assertEquals(setOf(1, 31), readings.map { it.block }.toSet())
        assertTrue(BmapFrameBuffer().feed(spy.all()).all { it.operator == BmapOperator.GET })
        conn.close()
    }
}
