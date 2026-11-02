package app.safar.ble

import app.safar.protocol.CallStatus
import app.safar.protocol.ChecksumMode
import app.safar.protocol.MessageSource
import app.safar.protocol.NavStatus
import app.safar.protocol.NavigationData
import app.safar.protocol.PhoneStatus
import app.safar.protocol.encodeCall
import app.safar.protocol.encodeNavigation
import app.safar.protocol.encodePhoneStatus
import app.safar.protocol.messageType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalTime
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WriteQueueTest {
    private val mode = ChecksumMode.SUM

    private fun status(second: Int) = encodePhoneStatus(
        PhoneStatus(80, false, 0, 3, LocalTime.of(10, 0, second), messagesCleared = false, missedCallsCleared = false),
        mode,
    )

    private fun nav(metres: Int) =
        encodeNavigation(NavigationData(4, metres, LocalTime.of(13, 1), 41_600, NavStatus.NORMAL, true), mode)

    private fun call(name: String) = encodeCall(name, CallStatus.RINGING, MessageSource.PHONE, mode)

    private class Recorder : WriteListener {
        val written = mutableListOf<Pair<ByteArray, WriteOutcome>>()
        val dropped = mutableListOf<Pair<ByteArray, String>>()
        override fun onWritten(frame: ByteArray, outcome: WriteOutcome) {
            written += frame to outcome
        }
        override fun onDropped(frame: ByteArray, reason: String) {
            dropped += frame to reason
        }
    }

    /** Fake GATT: each write completes after [latencyMs]; tracks how many writes overlap. */
    private class FakeWriter(val latencyMs: Long = 20, val outcome: () -> WriteOutcome = { WriteOutcome.Ok }) : FrameWriter {
        var inFlight = 0
        var maxInFlight = 0
        val order = mutableListOf<Char>()
        override suspend fun write(frame: ByteArray): WriteOutcome {
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            order += messageType(frame)
            delay(latencyMs)
            inFlight--
            return outcome()
        }
    }

    @Test
    fun writesOneFrameAtATimeInPriorityOrder() = runTest {
        val writer = FakeWriter()
        val rec = Recorder()
        val queue = WriteQueue(writer, rec)
        queue.offer(status(1))
        queue.offer(nav(300))
        queue.offer(call("Alex"))
        val job = launch { queue.run() }
        advanceTimeBy(1_000)
        assertEquals(listOf('2', '1', '3'), writer.order)
        assertEquals(1, writer.maxInFlight)
        assertTrue(rec.written.all { it.second == WriteOutcome.Ok })
        queue.close()
        job.join()
    }

    @Test
    fun newerStatusAndNavigationReplacePendingOnes() = runTest {
        val rec = Recorder()
        val queue = WriteQueue(FakeWriter(), rec)
        queue.offer(status(1))
        queue.offer(status(2))
        queue.offer(nav(300))
        queue.offer(nav(250))
        assertEquals(2, queue.size)
        assertEquals(listOf("superseded", "superseded"), rec.dropped.map { it.second })
        assertContentEquals(status(1), rec.dropped[0].first)
        val job = launch { queue.run() }
        advanceTimeBy(1_000)
        assertContentEquals(nav(250), rec.written[0].first)
        assertContentEquals(status(2), rec.written[1].first)
        queue.close()
        job.join()
    }

    @Test
    fun fullQueueDropsStatusFirstThenNavigationThenOldestEvent() = runTest {
        val rec = Recorder()
        val queue = WriteQueue(FakeWriter(), rec, capacity = 3)
        queue.offer(status(1))
        queue.offer(nav(300))
        queue.offer(call("Alex"))
        assertTrue(queue.offer(call("Rider")))
        assertEquals('3', messageType(rec.dropped.single().first))
        assertTrue(queue.offer(call("Alex B")))
        assertEquals('1', messageType(rec.dropped[1].first))
        assertTrue(queue.offer(call("Rider B")))
        assertContentEquals(call("Alex"), rec.dropped[2].first)
        assertEquals(3, queue.size)
        // Only events left: a status frame cannot push an event out.
        assertFalse(queue.offer(status(5)))
        assertEquals("queue full", rec.dropped.last().second)
        assertEquals(3, queue.size)
    }

    @Test
    fun writeTimeoutIsReportedAndTheQueueMovesOn() = runTest {
        val stuck = CompletableDeferred<WriteOutcome>()
        var calls = 0
        val writer = FrameWriter { if (calls++ == 0) stuck.await() else WriteOutcome.Ok }
        val rec = Recorder()
        val queue = WriteQueue(writer, rec, timeoutMs = 1_500)
        val job = launch { queue.run() }
        queue.offer(call("Alex"))
        queue.offer(status(1))
        advanceTimeBy(1_499)
        assertTrue(rec.written.isEmpty())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(WriteOutcome.TimedOut, rec.written[0].second)
        assertEquals(WriteOutcome.Ok, rec.written[1].second)
        queue.close()
        job.join()
    }

    @Test
    fun failedWriteAndThrowingWriterAreReported() = runTest {
        var n = 0
        val writer = FrameWriter {
            when (n++) {
                0 -> WriteOutcome.Failed("status 133")
                1 -> throw IllegalStateException("gatt closed")
                else -> WriteOutcome.Ok
            }
        }
        val rec = Recorder()
        val queue = WriteQueue(writer, rec)
        val job = launch { queue.run() }
        queue.offer(call("Alex"))
        runCurrent()
        queue.offer(call("Rider"))
        runCurrent()
        queue.offer(call("Alex"))
        runCurrent()
        assertEquals(WriteOutcome.Failed("status 133"), rec.written[0].second)
        assertEquals(WriteOutcome.Failed("gatt closed"), rec.written[1].second)
        assertEquals(WriteOutcome.Ok, rec.written[2].second)
        queue.close()
        job.join()
    }

    @Test
    fun closeEndsRunAndRejectsNewFrames() = runTest {
        val queue = WriteQueue(FakeWriter(), Recorder())
        val job = launch { queue.run() }
        runCurrent()
        queue.offer(status(1))
        queue.close()
        runCurrent()
        assertTrue(job.isCompleted)
        assertFalse(queue.offer(call("Alex")))
        assertEquals(0, queue.size)
    }

    @Test
    fun writeCutOffByCancellationIsStillReported() = runTest {
        val rec = Recorder()
        val queue = WriteQueue(FakeWriter(latencyMs = 100), rec)
        val job = launch { queue.run() }
        queue.offer(call("Alex"))
        advanceTimeBy(50)
        job.cancel()
        runCurrent()
        assertEquals(1, rec.written.size)
        assertContentEquals(call("Alex"), rec.written.single().first)
        assertEquals(WriteOutcome.Failed(WriteQueue.CANCELLED), rec.written.single().second)
    }
}
