package app.safar.ui.documents

import app.safar.core.RideState
import app.safar.data.BikeDocument
import app.safar.data.DocKind
import app.safar.data.DocumentFiles
import app.safar.data.DocumentRepository
import app.safar.data.ReminderStage
import app.safar.ui.testing.FakeRide
import app.safar.ui.testing.MainDispatcherRule
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val today = 20_000L

    private class Docs : DocumentRepository {
        override val documents = MutableStateFlow<List<BikeDocument>>(emptyList())
        private var next = 1L
        override suspend fun get(id: Long) = documents.value.firstOrNull { it.id == id }
        override suspend fun add(document: BikeDocument): Long = next++.also { documents.value += document.copy(id = it) }
        override suspend fun update(document: BikeDocument) { documents.value = documents.value.map { if (it.id == document.id) document else it } }
        override suspend fun setRemindedStage(id: Long, stage: Int) = Unit
        override suspend fun delete(id: Long) { documents.value = documents.value.filter { it.id != id } }
    }

    private val repo = Docs()
    private val ride = FakeRide()
    private val files by lazy { DocumentFiles(File(tmp.root, "documents")) }

    private fun vm(id: Long? = null) =
        DocumentViewModel(id, repo, files, ride.state, Dispatchers.Unconfined, CoroutineScope(Dispatchers.Unconfined), { today }, { 77 })

    private fun pdf() = PickedSource("application/pdf", "puc.pdf") { ByteArrayInputStream("%PDF".toByteArray()) }

    @Test
    fun aNewPaperIsSavedWithItsFileAndTheReminderAlreadyDue() = runTest {
        val vm = vm()
        vm.state.launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))
        assertFalse(vm.state.value.canSave)
        vm.pick(pdf())
        vm.setKind(DocKind.PUC)
        vm.setExpiry(today + 5)
        vm.save()
        val saved = repo.documents.value.single()
        assertEquals("PUC", saved.title, "an empty name takes the kind's")
        assertEquals(today + 5, saved.expiryEpochDay)
        assertEquals(ReminderStage.CLOSE, saved.remindedStage)
        assertEquals(77, saved.addedMillis)
        assertTrue(files.exists(saved.fileName))
        assertNotNull(vm.done.value)
    }

    @Test
    fun aRefusedFileShowsWhy() = runTest {
        val vm = vm()
        vm.state.launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))
        vm.pick(PickedSource("application/zip", "x.zip") { ByteArrayInputStream(byteArrayOf(1)) })
        assertEquals("Safar keeps PDFs and photos only.", vm.notices.current.value?.second)
        assertEquals(null, vm.state.value.file)
    }

    @Test
    fun replacingTheFileDeletesTheOldOneOnSave() = runTest {
        val old = files.save(ByteArrayInputStream(byteArrayOf(1)), "image/jpeg")
        repo.add(BikeDocument(0, "RC", DocKind.RC, old.fileName, "image/jpeg", 1, 5))
        val vm = vm(1)
        vm.state.launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))
        vm.pick(pdf())
        vm.save()
        val saved = repo.documents.value.single()
        assertTrue(saved.fileName.endsWith(".pdf"))
        assertFalse(files.exists(old.fileName))
        assertEquals(1, files.list().size)
    }

    @Test
    fun editingIsLockedWhileMoving() = runTest {
        ride.state.value = RideState(moving = true, speedKmh = 40f)
        val vm = vm()
        vm.state.launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))
        vm.pick(pdf())
        assertEquals(null, vm.state.value.file)
        assertNotNull(vm.state.value.locked)
    }

    @Test
    fun deletingRemovesTheRowAndTheFile() = runTest {
        val stored = files.save(ByteArrayInputStream(byteArrayOf(1)), "image/png")
        repo.add(BikeDocument(0, "Bill", DocKind.SERVICE_BILL, stored.fileName, "image/png", 1, 5))
        val vm = vm(1)
        vm.state.launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))
        vm.delete()
        assertTrue(repo.documents.value.isEmpty())
        assertFalse(files.exists(stored.fileName))
    }
}
