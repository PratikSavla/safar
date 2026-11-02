package app.safar.e2e

import app.safar.ble.TxLogger
import app.safar.ble.WriteQueue
import app.safar.core.AlertSource
import app.safar.core.LinkState
import app.safar.protocol.CallStatus
import app.safar.protocol.ChecksumMode
import app.safar.protocol.MessageSource
import app.safar.protocol.encodeCall
import app.safar.sim.BikeRig
import app.safar.sim.CLOCK_BASE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A loaded phone or bike acknowledges writes late or refuses them as busy; that must not end a healthy link
 * or lose a call frame. A bike that stops acknowledging altogether still ends the link.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SlowAckTest {
    private val ringing = encodeCall("Alex", CallStatus.RINGING, MessageSource.PHONE, ChecksumMode.SUM)

    private fun TestScope.connectedRig(): BikeRig {
        val rig = BikeRig(this)
        rig.pairFromScan()
        // Half-way between two status frames, so a call frame is written at once.
        rig.advance(3_500)
        return rig
    }

    @Test
    fun aThreeSecondAckDuringACallKeepsTheLinkAndTheCallFrame() = runTest {
        val rig = connectedRig()
        rig.bike.ackDelayMs = 3_000
        val at = rig.time
        rig.alerts.callRinging("Alex", AlertSource.PHONE)
        rig.advance(1)
        rig.bike.ackDelayMs = 20
        rig.advance(10_000)

        assertEquals(LinkState.Connected, rig.link)
        assertEquals(emptyList(), rig.recording.named("Write queue stalled"))
        val first = rig.writes('2').first()
        assertContentEquals(ringing, first.bytes)
        assertEquals(at, first.atMillis)
        assertEquals(at + 3_000, first.ackedAtMillis, "the slow ack was waited for")
        assertEquals(1, rig.bike.maxWritesInFlight, "nothing was written over the outstanding write")
        val tx = rig.txAfter(at - 1)
        assertTrue(tx.all { it.ok }, "every write succeeded: ${tx.map { it.note }}")
        assertEquals("slow ack 3000 ms", tx.first().note)
        assertTrue(rig.writes('2').size >= 5, "the ringing repeats went on: ${rig.writes('2').size}")
    }

    @Test
    fun aBusyRefusalIsRetriedAndTheCallFrameGetsThrough() = runTest {
        val rig = connectedRig()
        val link = rig.radio.links.last()
        link.refuseWrites = 2
        val at = rig.time
        rig.alerts.callRinging("Alex", AlertSource.PHONE)
        rig.advance(1_000)

        assertEquals(2, link.busyRefusals)
        val first = rig.writes('2').first()
        assertContentEquals(ringing, first.bytes)
        assertEquals(at + 2 * WriteQueue.BUSY_BACKOFF_MS, first.atMillis)
        val logged = rig.txAfter(at - 1).first()
        assertEquals(true, logged.ok)
        assertEquals("ok after 2 busy retries", logged.note)
        assertEquals(LinkState.Connected, rig.link)
    }

    @Test
    fun aBurstOfBusyRefusalsDoesNotEndTheLink() = runTest {
        val rig = connectedRig()
        val link = rig.radio.links.last()
        link.refuseWrites = 4 * (WriteQueue.BUSY_RETRIES + 1)
        val at = rig.time
        rig.alerts.callRinging("Alex", AlertSource.PHONE)
        rig.advance(5_000)

        assertEquals(LinkState.Connected, rig.link)
        assertEquals(emptyList(), rig.recording.named("Write queue stalled"))
        val busy = rig.txAfter(at - 1).filter { !it.ok }
        assertTrue(busy.isNotEmpty() && busy.all { it.note == "write busy: request busy, 3 retries" }, "${busy.map { it.note }}")
        assertTrue(rig.writes('2').isNotEmpty(), "a call frame got through once the stack was free")
    }

    @Test
    fun aBikeThatStopsAcknowledgingEndsTheLink() = runTest {
        val rig = connectedRig()
        rig.bike.ackDelayMs = 3_600_000
        val at = rig.time
        val limit = TxLogger.STALL_LIMIT * WriteQueue.WRITE_HARD_LIMIT_MS + 1_000
        while (rig.recording.named("Link ended").isEmpty()) {
            check(rig.time - at <= limit) { "the link did not end within $limit ms" }
            rig.advance(100)
        }

        val stalled = rig.recording.named("Write queue stalled").single()
        assertEquals("${TxLogger.STALL_LIMIT}", stalled.detail["failures"])
        val timeout = "write timeout: no ack in ${WriteQueue.WRITE_HARD_LIMIT_MS} ms"
        val failed = rig.txAfter(at - 1).filter { !it.ok }
        assertEquals(List(TxLogger.STALL_LIMIT) { timeout }, failed.take(TxLogger.STALL_LIMIT).map { it.note })
        assertTrue(failed.drop(TxLogger.STALL_LIMIT).all { it.note == "write failed: not connected" }, "after the end")
        val endedAt = rig.recording.named("Link ended").single().atMillis - CLOCK_BASE
        assertTrue(endedAt - at >= TxLogger.STALL_TIME_MS, "not before ${TxLogger.STALL_TIME_MS} ms without an ack")
    }
}
