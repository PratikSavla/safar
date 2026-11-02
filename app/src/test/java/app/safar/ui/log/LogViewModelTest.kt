package app.safar.ui.log

import app.safar.core.LogKind
import app.safar.core.LogRecord
import app.safar.data.Settings
import app.safar.protocol.ChecksumMode
import app.safar.protocol.ClusterData
import app.safar.protocol.encodeCluster
import app.safar.protocol.toHex
import app.safar.ui.testing.FakeLog
import app.safar.ui.testing.FakeSettings
import app.safar.ui.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val hex = toHex(encodeCluster(ClusterData(42, 12_345, 1.5, 2.5, 3), ChecksumMode.SUM))
    private val records = listOf(
        LogRecord(1, 1_000, LogKind.TX, '1', hex, name = "Navigation"),
        LogRecord(2, 2_000, LogKind.RX, '7', hex, ok = false, name = "Cluster"),
        LogRecord(3, 3_000, LogKind.EVENT, name = "connected", detail = mapOf("mtu" to "65")),
        LogRecord(4, 4_000, LogKind.ERROR, name = "write failed"),
    )

    @Test
    fun filtersAreNewestFirst() {
        assertEquals(listOf(4L, 3L, 2L, 1L), filterRecords(records, LogFilter()).map { it.id })
        assertEquals(listOf(1L), filterRecords(records, LogFilter(dir = DirFilter.TX)).map { it.id })
        assertEquals(listOf(4L, 3L), filterRecords(records, LogFilter(dir = DirFilter.EVT)).map { it.id })
        assertEquals(listOf(4L, 2L), filterRecords(records, LogFilter(errorsOnly = true)).map { it.id })
        assertEquals(listOf(2L), filterRecords(records, LogFilter(type = '7')).map { it.id })
    }

    @Test
    fun rowsSummariseFramesAndEvents() {
        val frame = rowOf(records[1], ZoneOffset.UTC)
        assertEquals(LogBadge.ERR, frame.badge)
        assertEquals("00:00:02.000", frame.time)
        assertEquals("Cluster · type 7", frame.title)
        assertEquals("Speed 42 km/h · Odometer 12345 km · Trip A 1.5 km", frame.summary)
        assertEquals(42, frame.hexPreview?.length)
        assertEquals("mtu 65", rowOf(records[2]).summary)
        assertEquals("write failed", rowOf(records[3]).summary)
        assertEquals("Event" to " connected", detailTitle(records[2]))
        assertEquals("failed", detailFields(records[1]).toMap()["Validation"])
    }

    @Test
    fun previewKeepsFiveShortLines() {
        val lines = (1..8).map { if (it == 2) "x".repeat(130) else "line $it" }.asSequence()
        val p = previewOf("safar.jsonl", 2_048, lines)
        assertEquals(8, p.lineCount)
        assertEquals(5, p.firstLines.size)
        assertEquals(121, p.firstLines[1].length)
        assertEquals("8 lines · 2.0 KB", p.subtitle())
    }

    @Test
    fun exportUsesHideNamesByDefaultAndCanTurnItOff() = runTest {
        val log = FakeLog(records)
        val vm = LogViewModel(log, FakeSettings(Settings(hideNames = true)), main.dispatcher, main.dispatcher)
        backgroundScope.launch(main.dispatcher) { vm.state.collect {} }
        vm.openExport()
        advanceUntilIdle()
        val sheet = vm.state.value.export
        assertTrue(sheet?.hideNames == true)
        assertFalse(sheet?.busy == true)
        assertEquals(7, sheet?.preview?.lineCount)
        assertTrue(sheet?.preview?.firstLines?.first()?.contains("***") == true)
        vm.setHideNames(false)
        advanceUntilIdle()
        assertEquals(listOf(true, false), log.exports)
        assertTrue(vm.state.value.export?.preview?.firstLines?.first()?.contains("Alex") == true)
        vm.closeExport()
        assertNull(vm.state.value.export)
    }

    @Test
    fun sessionDetailAndSwitch() = runTest {
        val log = FakeLog(records)
        val settings = FakeSettings()
        val vm = LogViewModel(log, settings, main.dispatcher, main.dispatcher)
        backgroundScope.launch(main.dispatcher) { vm.state.collect {} }
        vm.toggleSession()
        assertTrue(vm.state.value.sessionActive)
        vm.toggleSession()
        assertFalse(log.sessionActive.value)
        vm.open(2)
        assertEquals(2L, vm.state.value.selected?.id)
        vm.closeDetail()
        assertNull(vm.state.value.selected)
        vm.setDebugLog(true)
        assertTrue(settings.settings.value.debugLog)
        assertTrue(vm.state.value.debugLogOn)
    }
}
