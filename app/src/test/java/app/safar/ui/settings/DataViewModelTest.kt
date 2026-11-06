package app.safar.ui.settings

import app.safar.core.RideState
import app.safar.data.backup.BackupCounts
import app.safar.data.backup.BackupData
import app.safar.data.backup.BackupManifest
import app.safar.data.backup.BackupRejected
import app.safar.data.backup.StagedBackup
import app.safar.data.backup.TripDto
import app.safar.data.backup.UserData
import app.safar.ui.testing.FakeRide
import app.safar.ui.testing.MainDispatcherRule
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class DataViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakeUserData : UserData {
        var exports = 0
        var wipes = 0
        var restored: StagedBackup? = null
        var discarded: StagedBackup? = null
        var refuse: String? = null
        val staged = StagedBackup(BackupManifest("safar-backup", 1), BackupData(trips = listOf(TripDto(id = 1))), File("unused"), 0)

        override suspend fun export(out: OutputStream, appVersion: String, nowMillis: Long): BackupCounts {
            exports++
            return BackupCounts(trips = 2)
        }

        override suspend fun stage(input: InputStream): StagedBackup {
            refuse?.let { throw BackupRejected(it) }
            return staged
        }

        override suspend fun restore(staged: StagedBackup, todayEpochDay: Long) { restored = staged }
        override suspend fun discard(staged: StagedBackup) { discarded = staged }
        override suspend fun wipe() { wipes++ }
    }

    private val data = FakeUserData()
    private val ride = FakeRide()
    private var forgot = 0
    private var cleared = 0
    private val vm by lazy { DataViewModel(data, ride.state, "1.0", { forgot++ }, { cleared++ }, today = { 0 }) }

    @Test
    fun deleteEverythingForgetsTheBikeClearsTheRestAndReportsDone() = runTest {
        vm.state.launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))
        vm.wipe()
        assertEquals(1, forgot)
        assertEquals(1, data.wipes)
        assertEquals(1, cleared)
        assertTrue(vm.wiped.value)
    }

    @Test
    fun nothingRunsWhileARideIsRecording() = runTest {
        ride.state.value = RideState(recordingTripId = 3)
        vm.state.launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))
        vm.wipe()
        vm.export { ByteArrayOutputStream() }
        assertEquals(0, data.wipes)
        assertEquals(0, data.exports)
        assertNotNull(vm.state.value.locked)
    }

    @Test
    fun exportTellsWhatWasWritten() = runTest {
        vm.state.launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))
        vm.export { ByteArrayOutputStream() }
        assertEquals("Exported 2 rides", vm.notices.current.value?.second)
        assertEquals(DataWork.NONE, vm.state.value.work)
    }

    @Test
    fun anImportWaitsForTheRiderThenReplaces() = runTest {
        vm.state.launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))
        vm.read { ByteArrayInputStream(byteArrayOf()) }
        assertEquals(1, vm.state.value.offer?.counts?.trips)
        assertNull(data.restored)
        vm.confirmImport()
        assertEquals(data.staged, data.restored)
        assertNull(vm.state.value.offer)
    }

    @Test
    fun aCancelledImportIsThrownAway() = runTest {
        vm.state.launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))
        vm.read { ByteArrayInputStream(byteArrayOf()) }
        vm.cancelImport()
        assertEquals(data.staged, data.discarded)
        assertNull(data.restored)
    }

    @Test
    fun aRefusedFileShowsItsReason() = runTest {
        data.refuse = "This file isn’t a Safar export."
        vm.state.launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))
        vm.read { ByteArrayInputStream(byteArrayOf()) }
        assertEquals("This file isn’t a Safar export.", vm.notices.current.value?.second)
        assertNull(vm.state.value.offer)
        assertFalse(vm.wiped.value)
    }
}
