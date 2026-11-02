package app.safar.e2e

import app.safar.ble.BikeSession
import app.safar.ble.ClearedFlagTracker
import app.safar.core.AlertSource
import app.safar.protocol.CallStatus
import app.safar.protocol.ChecksumMode
import app.safar.protocol.FRAME_LENGTH
import app.safar.protocol.MessageSource
import app.safar.protocol.Timing
import app.safar.protocol.encodeCall
import app.safar.protocol.encodeMessage
import app.safar.protocol.encodeMissedCall
import app.safar.protocol.sanitizeName
import app.safar.sim.BikeRig
import app.safar.sim.TxFrames
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Call, missed call and message frames from the alert center through the session (docs 5.2-5.6, 6.3). */
@OptIn(ExperimentalCoroutinesApi::class)
class CallsTest {
    private val sum = ChecksumMode.SUM

    private fun TestScope.callRig(): BikeRig {
        val rig = BikeRig(this)
        rig.pairFromScan()
        rig.advance(3_000)
        return rig
    }

    @Test
    fun ringingRepeatsEverySecondUntilAnswered() = runTest {
        val rig = callRig()
        val at = rig.time
        rig.alerts.callRinging("Alex", AlertSource.PHONE)
        rig.advance(2_500)
        rig.alerts.callAnswered("Alex", AlertSource.PHONE)
        rig.advance(20_000)

        val calls = rig.writes('2')
        val ringing = encodeCall("Alex", CallStatus.RINGING, MessageSource.PHONE, sum)
        val answered = encodeCall("Alex", CallStatus.ANSWERED, MessageSource.PHONE, sum)
        val gap = Timing.EVENT_GAP_MS
        val answeredAt = at + 2_500
        // A status frame went out at the same moment; the first ring waits for its ack.
        assertEquals(at, rig.writes('3').last { it.atMillis <= at }.atMillis)
        val firstRing = at + rig.bike.ackDelayMs
        val times = listOf(firstRing, at + 1_000, at + 2_000, answeredAt, answeredAt + gap, answeredAt + 2 * gap)
        assertEquals(times, calls.map { it.atMillis })
        calls.take(3).forEach { assertContentEquals(ringing, it.bytes) }
        calls.drop(3).forEach { assertContentEquals(answered, it.bytes) }
        assertEquals(TxFrames.Call("Alex", 'N', '1'), rig.frames.call(calls.first().bytes))
        assertEquals(TxFrames.Call("Alex", 'N', '2'), rig.frames.call(calls.last().bytes))
    }

    @Test
    fun unansweredRingingStopsAfterTheRepeatLimit() = runTest {
        val rig = callRig()
        val at = rig.time
        rig.alerts.callRinging("Rider", AlertSource.WHATSAPP)
        rig.advance((BikeSession.CALL_RING_REPEAT + 10) * Timing.CALL_REPEAT_PERIOD_MS)
        val calls = rig.writes('2')
        val offered = List(BikeSession.CALL_RING_REPEAT) { at + it * Timing.CALL_REPEAT_PERIOD_MS }
        // The first ring waits for the ack of the status frame written at the same moment.
        assertEquals(listOf(at + rig.bike.ackDelayMs) + offered.drop(1), calls.map { it.atMillis })
        assertEquals(TxFrames.Call("Rider", 'W', '1'), rig.frames.call(calls.first().bytes), "WhatsApp call source")
    }

    @Test
    fun ringingStopsWhenTheCallEndsWithoutAnotherFrame() = runTest {
        val rig = callRig()
        rig.alerts.callRinging("Alex", AlertSource.PHONE)
        rig.advance(3_500)
        rig.alerts.callEnded(AlertSource.PHONE)
        rig.advance(10_000)
        val calls = rig.writes('2')
        assertEquals(4, calls.size, "rings at 0, 1, 2 and 3 s, nothing after the call ended")
        assertTrue(calls.all { rig.frames.call(it.bytes).status == '1' })
        assertEquals(emptyList(), rig.writes('4'), "an ended call is not a missed call")
    }

    @Test
    fun missedCallsCountUpThenClearWithTheStatusFlag() = runTest {
        val rig = callRig()
        rig.alerts.callRinging("Alex", AlertSource.PHONE)
        rig.advance(1_500)
        val ringsBeforeMissed = rig.writes('2').size
        rig.alerts.callMissed("Alex", AlertSource.PHONE)
        rig.advance(3_000)
        assertEquals(ringsBeforeMissed, rig.writes('2').size, "a missed call stops the ringing repeats")
        rig.alerts.callMissed("Rider", AlertSource.PHONE)
        rig.advance(3_000)
        val clearedAt = rig.time
        rig.alerts.clearMissedCalls()
        rig.advance(8_000)

        val missed = rig.writes('4')
        val expected = listOf(
            encodeMissedCall("Alex", 1, MessageSource.PHONE, sum),
            encodeMissedCall("Rider", 2, MessageSource.PHONE, sum),
            encodeMissedCall("", 0, MessageSource.PHONE, sum),
        ).flatMap { frame -> List(Timing.MISSED_CALL_REPEAT) { frame } }
        assertEquals(expected.size, missed.size)
        expected.zip(missed).forEach { (e, w) -> assertContentEquals(e, w.bytes) }
        assertEquals(TxFrames.Count("Rider", 2, 'N'), rig.frames.missed(missed[2].bytes))
        assertEquals(Timing.MISSED_CALL_GAP_MS, missed[1].atMillis - missed[0].atMillis)

        val flags = rig.writes('3').map { it.atMillis to rig.frames.status(it.bytes).missedCleared }
        assertTrue(flags.filter { it.first < clearedAt }.all { it.second == 'N' })
        val after = flags.filter { it.first > clearedAt }.map { it.second }
        assertEquals(List(ClearedFlagTracker.HOLD_TICKS) { 'Y' } + List(after.size - ClearedFlagTracker.HOLD_TICKS) { 'N' }, after)
        assertTrue(rig.writes('3').all { rig.frames.status(it.bytes).messagesCleared == 'N' })
    }

    @Test
    fun whatsAppMissedCallUsesSourceW() = runTest {
        val rig = callRig()
        rig.alerts.callMissed("Alex", AlertSource.WHATSAPP)
        rig.advance(1_000)
        assertEquals(TxFrames.Count("Alex", 1, 'W'), rig.frames.missed(rig.writes('4').first().bytes))
    }

    @Test
    fun messagesCountUpThenClearWithTheStatusFlag() = runTest {
        val rig = callRig()
        rig.alerts.messageReceived("Alex")
        rig.advance(1_000)
        rig.alerts.messageReceived("Rider")
        rig.advance(3_000)
        val clearedAt = rig.time
        rig.alerts.clearMessages()
        rig.advance(8_000)

        val messages = rig.writes('5')
        val expected = listOf(
            encodeMessage("Alex", 1, MessageSource.WHATSAPP, sum),
            encodeMessage("Rider", 2, MessageSource.WHATSAPP, sum),
            encodeMessage("", 0, MessageSource.WHATSAPP, sum),
        ).flatMap { frame -> List(Timing.EVENT_REPEAT) { frame } }
        assertEquals(expected.size, messages.size)
        expected.zip(messages).forEach { (e, w) -> assertContentEquals(e, w.bytes) }
        assertEquals(TxFrames.Count("Rider", 2, 'W'), rig.frames.message(messages[3].bytes))
        assertEquals(TxFrames.Count("", 0, 'W'), rig.frames.message(messages.last().bytes))

        val after = rig.writes('3').filter { it.atMillis > clearedAt }.map { rig.frames.status(it.bytes).messagesCleared }
        assertEquals(List(ClearedFlagTracker.HOLD_TICKS) { 'Y' } + List(after.size - ClearedFlagTracker.HOLD_TICKS) { 'N' }, after)
    }

    @Test
    fun nothingIsSentWhileNotConnected() = runTest {
        val rig = BikeRig(this)
        rig.start()
        rig.alerts.callRinging("Alex", AlertSource.PHONE)
        rig.alerts.callMissed("Alex", AlertSource.PHONE)
        rig.alerts.messageReceived("Rider")
        rig.advance(20_000)
        assertEquals(emptyList(), rig.bike.writes)
        assertEquals(1, rig.alerts.state.value.missed.count, "the alert screen still counts it")

        rig.pairFromScan()
        rig.manager.disconnect()
        rig.advance(10)
        val writes = rig.bike.writes.size
        rig.alerts.callRinging("Alex", AlertSource.PHONE)
        rig.alerts.messageReceived("Rider")
        rig.advance(10_000)
        assertEquals(writes, rig.bike.writes.size, "nothing after a user disconnect")
    }

    @Test
    fun longNamesAndEmojiStillGiveThirtyByteFrames() = runTest {
        val rig = callRig()
        val long = "Alexander Rider of the Main Rd"
        val emoji = "Rider 🏍️ NH 48"
        rig.alerts.callRinging(long, AlertSource.PHONE)
        rig.advance(100)
        rig.alerts.callMissed(emoji, AlertSource.PHONE)
        rig.alerts.messageReceived("😀 Alex 😀 Rider Main Rd NH 48 Alex")
        rig.advance(2_000)

        val events = rig.bike.writes.filter { it.type in "245" }
        assertTrue(events.isNotEmpty())
        assertTrue(events.all { it.bytes.size == FRAME_LENGTH })
        assertEquals("Alexander Rider of ", rig.frames.call(rig.writes('2').first().bytes).name)
        assertEquals(19, sanitizeName(long).length)
        assertEquals("Rider NH 48", rig.frames.missed(rig.writes('4').first().bytes).name)
        val sender = rig.frames.message(rig.writes('5').first().bytes).name
        assertEquals("Alex Rider Main Rd ", sender)
    }
}
