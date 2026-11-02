package app.safar.log

import app.safar.core.LogKind
import app.safar.protocol.CallStatus
import app.safar.protocol.ChecksumMode
import app.safar.protocol.LogEntry
import app.safar.protocol.LogHeaderInfo
import app.safar.protocol.MessageSource
import app.safar.protocol.encodeCall
import app.safar.protocol.encodeCluster
import app.safar.protocol.ClusterData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppLogTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val header = LogHeaderInfo("Safar", "0.1.0", "Test Phone", "16 (API 36)", "SBM01A1B2C3D")

    private fun TestScope.newLog(debug: MutableStateFlow<Boolean>): Pair<AppLog, File> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val logs = tmp.newFolder("logs")
        val files = LogFileStore(logs, tmp.newFolder("exports"), { header }, dispatcher)
        val log = AppLog(backgroundScope, debug, files, now = { testScheduler.currentTime })
        return log to logs
    }

    private val callFrame = encodeCall("Alex", CallStatus.RINGING, MessageSource.PHONE, ChecksumMode.SUM)

    @Test
    fun keepsLast500WhenDebugLogIsOff() = runTest {
        val (log, _) = newLog(MutableStateFlow(false))
        runCurrent()
        repeat(600) { log.event("e$it") }
        advanceTimeBy(300)
        val records = log.records.value
        assertEquals(500, records.size)
        assertEquals("e599", records.first().name, "newest first")
        assertEquals("e100", records.last().name)
    }

    @Test
    fun debugLogKeeps20000AndShrinksBackKeepingNewest() = runTest {
        val debug = MutableStateFlow(true)
        val (log, _) = newLog(debug)
        runCurrent()
        assertEquals(20_000, log.currentCapacity)
        repeat(1200) { log.event("e$it") }
        advanceTimeBy(300)
        assertEquals(1200, log.records.value.size)
        debug.value = false
        advanceTimeBy(300)
        assertEquals(500, log.records.value.size)
        assertEquals("e700", log.records.value.last().name)
    }

    @Test
    fun recordsArePublishedAtMostFourTimesPerSecond() = runTest {
        val (log, _) = newLog(MutableStateFlow(false))
        runCurrent()
        var emissions = 0
        backgroundScope.launch { log.records.collect { emissions++ } }
        runCurrent()
        val before = emissions
        repeat(40) {
            log.event("tick$it")
            advanceTimeBy(25) // 40 events over 1 s
        }
        advanceTimeBy(300)
        assertTrue(emissions - before in 4..5, "got ${emissions - before} publishes")
        assertEquals("tick39", log.records.value.first().name)
    }

    @Test
    fun frameRowsCarryTypeHexAndNote() = runTest {
        val (log, _) = newLog(MutableStateFlow(false))
        runCurrent()
        log.frame(LogKind.TX, callFrame, ok = false, note = "write timeout")
        advanceTimeBy(300)
        val row = log.records.value.single()
        assertEquals(LogKind.TX, row.kind)
        assertEquals('2', row.type)
        assertEquals("Call", row.name)
        assertFalse(row.ok)
        assertEquals("write timeout", row.detail["note"])
        assertTrue(row.hex!!.startsWith("a5 32 41 6c 65 78")) // non-null: frame rows always carry hex
    }

    @Test
    fun exportWritesHeaderAndMasksNames() = runTest {
        val (log, _) = newLog(MutableStateFlow(false))
        runCurrent()
        log.frame(LogKind.TX, callFrame, ok = true)
        log.frame(LogKind.RX, encodeCluster(ClusterData(0, 12345, 1.0, 2.0, 4), ChecksumMode.SUM), ok = true)
        log.event("Call", mapOf("caller" to "Alex"))
        val lines = log.export(hideNames = true).readLines()
        assertEquals(4, lines.size)
        assertTrue(lines[0].contains("\"kind\":\"header\"") && lines[0].contains("\"bikeName\":\"SBM01*******\""))
        assertTrue(lines[1].contains("a5 32 2a 2a 2a 2a 00"), lines[1])
        assertTrue(lines[2].contains("\"type\":\"7\""))
        assertTrue(lines[3].contains("\"caller\":\"****\""), lines[3])
        val plain = log.export(hideNames = false).readLines()
        assertTrue(plain[1].contains("a5 32 41 6c 65 78"))
        assertTrue(plain[0].contains("SBM01A1B2C3D"))
    }

    @Test
    fun sessionFileIsWrittenOnlyWithDebugLogOn() = runTest {
        val debug = MutableStateFlow(false)
        val (log, logsDir) = newLog(debug)
        runCurrent()
        log.startSession()
        log.event("before debug on")
        advanceTimeBy(100)
        assertTrue(log.sessionActive.value)
        assertEquals(0, logsDir.listFiles()!!.size) // non-null: the folder exists
        debug.value = true
        advanceTimeBy(100)
        log.event("Connected", mapOf("mtu" to "65"))
        advanceTimeBy(100)
        log.stopSession()
        advanceTimeBy(100)
        val file = logsDir.listFiles()!!.single() // non-null: the folder exists
        val lines = file.readLines()
        assertTrue(lines[0].contains("\"kind\":\"header\""))
        assertTrue(lines.any { it.contains("\"name\":\"Connected\"") })
        assertTrue(lines.last().contains("Log session stopped"))
        assertFalse(log.sessionActive.value)
    }

    @Test
    fun errorEventKeepsItsMessageNextToTheErrorFlagInTheFile() {
        val line = eventLine(1, 0, "Connection error", mapOf("error" to "GATT 133"), error = true)
        val detail = (line.entry as LogEntry.Event).detail.orEmpty()
        assertEquals(JsonPrimitive(true), detail["error"])
        assertEquals(JsonPrimitive("GATT 133"), detail["message"])
        assertEquals("GATT 133", line.record.detail["error"])
        val plain = eventLine(2, 0, "Note", mapOf("error" to "none"), error = false)
        assertEquals(JsonPrimitive("none"), (plain.entry as LogEntry.Event).detail.orEmpty()["error"])
    }
}
