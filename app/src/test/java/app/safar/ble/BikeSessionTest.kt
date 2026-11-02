package app.safar.ble

import app.safar.core.AlertCounter
import app.safar.core.AlertSource
import app.safar.core.AlertsState
import app.safar.core.BikeCatalog
import app.safar.core.ClusterReading
import app.safar.core.DebugLog
import app.safar.core.LogKind
import app.safar.core.LogRecord
import app.safar.core.NavSnapshot
import app.safar.core.NavState
import app.safar.core.NavStatusCode
import app.safar.core.PhoneSnapshot
import app.safar.data.Settings
import app.safar.protocol.ChecksumMode
import app.safar.protocol.ClusterData
import app.safar.protocol.encodeCluster
import app.safar.protocol.messageType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.plus
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class FakeLog : DebugLog {
    val frames = mutableListOf<Triple<LogKind, ByteArray, Boolean>>()
    val notes = mutableListOf<String?>()
    val events = mutableListOf<String>()
    override val records: StateFlow<List<LogRecord>> = MutableStateFlow(emptyList())
    override val sessionActive: StateFlow<Boolean> = MutableStateFlow(false)
    override fun frame(kind: LogKind, bytes: ByteArray, ok: Boolean, note: String?) {
        frames += Triple(kind, bytes, ok)
        notes += note
    }
    override fun event(name: String, detail: Map<String, String>, error: Boolean) {
        events += name
    }
    override fun startSession() = Unit
    override fun stopSession() = Unit
    override fun clear() = Unit
    override suspend fun export(hideNames: Boolean): File = error("not used")
}

@OptIn(ExperimentalCoroutinesApi::class)
class BikeSessionTest {
    private val settings = MutableStateFlow(Settings(riderName = "Alex", speedAlertOn = true, speedAlertKmh = 100))
    private val phone = MutableStateFlow(PhoneSnapshot(80, false, 3, true, true))
    private val alerts = MutableStateFlow(AlertsState())
    private val navigation = MutableStateFlow(NavState())
    private val sent = mutableListOf<Pair<Long, ByteArray>>()
    private val readings = mutableListOf<ClusterReading>()
    private val log = FakeLog()

    private fun TestScope.session(first: Boolean = true): Pair<BikeSession, kotlinx.coroutines.CoroutineScope> {
        val scope = backgroundScope + SupervisorJob(backgroundScope.coroutineContext.job)
        val config = SessionConfig(ChecksumMode.SUM, BikeCatalog.forBleName("SBM01A1B2C3D"), first)
        val s = BikeSession(
            scope = scope,
            config = config,
            inputs = SessionInputs(settings, phone, alerts, navigation),
            send = { sent += testScheduler.currentTime to it },
            log = log,
            now = { testScheduler.currentTime },
            zone = ZoneOffset.UTC,
            onReading = { readings += it },
        )
        return s to scope
    }

    private fun types(from: Long = 0, until: Long = Long.MAX_VALUE) =
        sent.filter { it.first in from until until }.map { messageType(it.second) }

    @Test
    fun riderFrameRepeatsThenStatusEverySecond() = runTest {
        val (s, _) = session(first = true)
        s.start()
        advanceTimeBy(5_500)
        val riders = sent.filter { messageType(it.second) == '6' }
        assertEquals(listOf(0L, 1_000L, 2_000L), riders.map { it.first })
        assertEquals('F'.code, riders[0].second[27].toInt())
        assertEquals("ALEX", String(riders[0].second, 2, 4, Charsets.US_ASCII))
        val status = sent.filter { messageType(it.second) == '3' }.map { it.first }
        assertEquals(listOf(2_000L, 3_000L, 4_000L, 5_000L), status)
        assertTrue(types().none { it == '1' }, "no navigation frame before the first snapshot")
    }

    @Test
    fun reconnectSendsRAndEmptyNameSendsRider() = runTest {
        settings.value = Settings(riderName = "")
        val (s, _) = session(first = false)
        s.start()
        runCurrent()
        val rider = sent.single().second
        assertEquals('R'.code, rider[27].toInt())
        assertEquals("RIDER", String(rider, 2, 5, Charsets.US_ASCII))
    }

    @Test
    fun statusCarriesClearedFlagForFourTicks() = runTest {
        alerts.value = AlertsState(messages = AlertCounter(2, "Alex", AlertSource.WHATSAPP))
        val (s, _) = session()
        s.start()
        advanceTimeBy(2_500) // first status tick at 2 s: count 2
        alerts.value = AlertsState()
        advanceTimeBy(6_000)
        val byte14 = sent.filter { messageType(it.second) == '3' }.map { it.second[14].toInt().toChar() }
        assertEquals(listOf('N', 'Y', 'Y', 'Y', 'Y', 'N', 'N'), byte14)
    }

    private fun snapshot(at: Long) = NavSnapshot(4, true, 300, 0L, 41_600, NavStatusCode.NORMAL, "Turn right", at)

    @Test
    fun navigationStartsWithFirstSnapshotAndSendsOneEndedFrame() = runTest {
        val (s, _) = session()
        s.start()
        navigation.value = NavState(active = true, waiting = true)
        advanceTimeBy(1_000)
        assertTrue(types().none { it == '1' })
        navigation.value = NavState(active = true, snapshot = snapshot(1))
        advanceTimeBy(1_000)
        val navFrames = sent.filter { messageType(it.second) == '1' }
        assertEquals(4, navFrames.size, "every 250 ms")
        assertTrue(navFrames.all { it.second[24] == '1'.code.toByte() })
        navigation.value = NavState(active = false, snapshot = snapshot(1))
        advanceTimeBy(2_000)
        val after = sent.filter { messageType(it.second) == '1' }.drop(4)
        assertEquals(1, after.size)
        assertEquals('0'.code.toByte(), after.single().second[24])
        assertTrue("Navigation ended frame sent" in log.events)
    }

    @Test
    fun ringingCallRepeatsEverySecondWhileRingingUpToTheCap() = runTest {
        val (s, _) = session()
        s.sendCall("Alex", AlertSource.PHONE, answered = false)
        advanceTimeBy(90_000)
        val calls = sent.filter { messageType(it.second) == '2' }
        assertEquals(BikeSession.CALL_RING_REPEAT, calls.size)
        assertEquals(59_000L, calls.last().first)
    }

    @Test
    fun stopRingingEndsTheRepeatsOfThatSourceOnly() = runTest {
        val (s, _) = session()
        s.sendCall("Rider", AlertSource.WHATSAPP, answered = false)
        advanceTimeBy(2_500) // 3 ringing frames
        s.stopRinging(AlertSource.PHONE) // another source: ignored
        advanceTimeBy(1_000)
        s.stopRinging(AlertSource.WHATSAPP) // declined
        advanceTimeBy(20_000)
        assertEquals(4, sent.count { messageType(it.second) == '2' })

        sent.clear()
        s.sendCall("Rider", AlertSource.WHATSAPP, answered = true)
        s.stopRinging(AlertSource.WHATSAPP) // answered frames are not ringing: they all go out
        advanceTimeBy(1_000)
        assertEquals(3, sent.count { messageType(it.second) == '2' })
    }

    @Test
    fun answeredCallReplacesRingingAndMissedStopsIt() = runTest {
        val (s, _) = session()
        s.sendCall("Alex", AlertSource.PHONE, answered = false)
        advanceTimeBy(2_500) // 3 ringing frames
        s.sendCall("Alex", AlertSource.PHONE, answered = true)
        advanceTimeBy(5_000)
        val calls = sent.filter { messageType(it.second) == '2' }
        assertEquals(listOf('1', '1', '1', '2', '2', '2'), calls.map { it.second[23].toInt().toChar() })
        assertEquals(listOf(2_500L, 2_700L, 2_900L), calls.drop(3).map { it.first })

        sent.clear()
        s.sendCall("Rider", AlertSource.WHATSAPP, answered = false)
        advanceTimeBy(1_500)
        s.sendMissedCalls("Rider", 1, AlertSource.WHATSAPP)
        advanceTimeBy(5_000)
        assertEquals(listOf('2', '2', '4', '4'), types())
        assertEquals(1, sent[2].second[3].toInt())
    }

    @Test
    fun messageIsSentThreeTimes200msApart() = runTest {
        val (s, _) = session()
        s.sendMessages("Alex", 3, AlertSource.WHATSAPP)
        advanceTimeBy(1_000)
        assertEquals(listOf(0L, 200L, 400L), sent.map { it.first })
        assertTrue(sent.all { messageType(it.second) == '5' && it.second[4].toInt() == 3 })
    }

    @Test
    fun nothingIsSentAfterTheScopeIsCancelled() = runTest {
        val (s, scope) = session()
        s.start()
        runCurrent()
        scope.cancel()
        sent.clear()
        s.sendMessages("Alex", 1, AlertSource.PHONE)
        s.sendIconTest(4)
        advanceTimeBy(5_000)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun rxDiscardsFirstFuelAndLogsInvalidFrames() = runTest {
        val (s, _) = session()
        val frame = encodeCluster(ClusterData(87, 12345, 123.4, 56.7, 5), ChecksumMode.SUM)
        s.onRx(frame)
        s.onRx(frame)
        val bad = frame.copyOf().also { it[28] = (it[28] + 1).toByte() }
        s.onRx(bad)
        assertEquals(2, readings.size)
        assertNull(readings[0].fuelBars)
        assertEquals(5, readings[1].fuelBars)
        assertEquals(12345, readings[1].odometerKm)
        assertEquals(listOf(true, true, false), log.frames.map { it.third })
        assertEquals("first frame: fuel ignored", log.notes[0])
        assertTrue(log.notes[2]!!.startsWith("checksum")) // non-null: invalid frames carry the reason
    }

    @Test
    fun iconTestSendsOneNavigationFrame() = runTest {
        val (s, _) = session()
        s.sendIconTest(21)
        runCurrent()
        val frame = sent.single().second
        assertEquals('1', messageType(frame))
        assertEquals(21, frame[2].toInt())
    }
}
