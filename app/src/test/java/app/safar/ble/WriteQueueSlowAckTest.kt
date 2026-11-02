package app.safar.ble

import app.safar.protocol.CallStatus
import app.safar.protocol.ChecksumMode
import app.safar.protocol.MessageSource
import app.safar.protocol.PhoneStatus
import app.safar.protocol.encodeCall
import app.safar.protocol.encodePhoneStatus
import app.safar.protocol.messageType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalTime
import kotlin.test.assertEquals

/** Slow acknowledgements and busy refusals: no write overlaps an outstanding one, a busy write is retried. */
@OptIn(ExperimentalCoroutinesApi::class)
class WriteQueueSlowAckTest {
    private val mode = ChecksumMode.SUM

    private fun status(second: Int) = encodePhoneStatus(
        PhoneStatus(80, false, 0, 3, LocalTime.of(10, 0, second), messagesCleared = false, missedCallsCleared = false),
        mode,
    )

    private fun call(name: String) = encodeCall(name, CallStatus.RINGING, MessageSource.PHONE, mode)

    private class Recorder : WriteListener {
        val written = mutableListOf<Triple<Char, WriteOutcome, WriteInfo>>()
        val dropped = mutableListOf<Pair<Char, String>>()
        override fun onWritten(frame: ByteArray, outcome: WriteOutcome, info: WriteInfo) {
            written += Triple(messageType(frame), outcome, info)
        }
        override fun onDropped(frame: ByteArray, reason: String) {
            dropped += messageType(frame) to reason
        }
    }

    /** Scripted GATT: each write takes the next latency and outcome; [starts] logs (time, type) of each call. */
    private class ScriptedWriter(private val test: TestScope, private val script: MutableList<Pair<Long, WriteOutcome>>) :
        FrameWriter {
        val starts = mutableListOf<Pair<Long, Char>>()
        override suspend fun write(frame: ByteArray): WriteOutcome {
            starts += test.testScheduler.currentTime to messageType(frame)
            val (latency, outcome) = if (script.isEmpty()) 20L to WriteOutcome.Ok else script.removeAt(0)
            delay(latency)
            return outcome
        }
    }

    private fun TestScope.queue(writer: FrameWriter, rec: Recorder) =
        WriteQueue(writer, rec, clock = { testScheduler.currentTime })

    @Test
    fun aThreeSecondAckIsWaitedForAndNothingElseStartsMeanwhile() = runTest {
        val writer = ScriptedWriter(this, mutableListOf(3_000L to WriteOutcome.Ok))
        val rec = Recorder()
        val q = queue(writer, rec)
        val job = launch { q.run() }
        q.offer(status(1))
        runCurrent()
        q.offer(call("Alex"))
        advanceTimeBy(2_999)
        assertEquals(listOf(0L to '3'), writer.starts, "no write starts while the first waits for its ack")
        advanceTimeBy(100)
        assertEquals(listOf(0L to '3', 3_000L to '2'), writer.starts)
        assertEquals(listOf(WriteOutcome.Ok, WriteOutcome.Ok), rec.written.map { it.second })
        assertEquals(WriteInfo(3_000, 0, slowAck = true), rec.written[0].third)
        assertEquals(WriteInfo(20, 0, slowAck = false), rec.written[1].third)
        q.close()
        job.join()
    }

    @Test
    fun aBusyRefusalIsRetriedAfterABackOff() = runTest {
        val busy = WriteOutcome.Busy("request busy")
        val writer = ScriptedWriter(this, mutableListOf(0L to busy, 0L to busy))
        val rec = Recorder()
        val q = queue(writer, rec)
        val job = launch { q.run() }
        q.offer(call("Alex"))
        advanceTimeBy(1_000)
        val gap = WriteQueue.BUSY_BACKOFF_MS
        assertEquals(listOf(0L to '2', gap to '2', 2 * gap to '2'), writer.starts)
        assertEquals(WriteOutcome.Ok, rec.written.single().second, "the call frame is not lost")
        assertEquals(2, rec.written.single().third.busyRetries)
        q.close()
        job.join()
    }

    @Test
    fun aWriteBusyAfterEveryRetryIsReportedAsBusy() = runTest {
        val busy = WriteOutcome.Busy("request busy")
        val writer = ScriptedWriter(this, MutableList(WriteQueue.BUSY_RETRIES + 1) { 0L to busy })
        val rec = Recorder()
        val q = queue(writer, rec)
        val job = launch { q.run() }
        q.offer(call("Alex"))
        advanceTimeBy(1_000)
        assertEquals(WriteQueue.BUSY_RETRIES + 1, writer.starts.size)
        assertEquals(busy, rec.written.single().second)
        assertEquals(WriteQueue.BUSY_RETRIES, rec.written.single().third.busyRetries)
        q.close()
        job.join()
    }

    @Test
    fun aBusyStatusFrameIsDroppedWhenANewerOneWaits() = runTest {
        val writer = ScriptedWriter(this, mutableListOf(0L to WriteOutcome.Busy("request busy")))
        val rec = Recorder()
        val q = queue(writer, rec)
        val job = launch { q.run() }
        q.offer(status(1))
        runCurrent()
        val newer = status(2)
        q.offer(newer)
        advanceTimeBy(1_000)
        assertEquals(listOf('3' to "superseded"), rec.dropped)
        assertEquals(listOf('3'), rec.written.map { it.first })
        assertEquals(WriteOutcome.Ok, rec.written.single().second)
        assertEquals(listOf(0L to '3', WriteQueue.BUSY_BACKOFF_MS to '3'), writer.starts)
        q.close()
        job.join()
    }
}
