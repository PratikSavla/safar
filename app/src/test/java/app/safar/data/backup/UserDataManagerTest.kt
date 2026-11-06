package app.safar.data.backup

import app.safar.data.DocumentFiles
import app.safar.data.SavedBikeRecord
import app.safar.data.Settings
import app.safar.ui.testing.FakeSavedBike
import app.safar.ui.testing.FakeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserDataManagerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun manager(dao: FakeBackupDao, settings: FakeSettings, bike: FakeSavedBike, docs: File): UserDataManager =
        UserDataManager(dao, settings, bike, DocumentFiles(docs), tmp.root, Dispatchers.Unconfined)

    @Test
    fun exportThenImportOnAnotherPhoneRestoresEverything() = runTest {
        val oldDocs = tmp.newFolder("old-docs").also {
            File(it, PDF_NAME).writeBytes("%PDF".toByteArray())
            File(it, JPG_NAME).writeBytes(byteArrayOf(1, 2, 3))
        }
        val oldSettings = FakeSettings(Settings(onboarded = true, riderName = "Alex", currencySymbol = "$"))
        val old = manager(FakeBackupDao(sampleRows()), oldSettings, FakeSavedBike(SavedBikeRecord("SUZUKI-TEST", "00:11")), oldDocs)
        val out = ByteArrayOutputStream()
        val counts = old.export(out, "1.0", 5_000)
        assertEquals(2, counts.documents)

        val newDao = FakeBackupDao()
        val newSettings = FakeSettings(Settings(onboarded = true, dismissedPermissions = setOf("phone")))
        val newBike = FakeSavedBike(SavedBikeRecord("SUZUKI-NEW", "AA:BB"))
        val newDocs = File(tmp.root, "documents")
        val fresh = manager(newDao, newSettings, newBike, newDocs)
        val staged = fresh.stage(ByteArrayInputStream(out.toByteArray()))
        fresh.restore(staged, todayEpochDay = 0)

        assertEquals(sampleRows().trips, newDao.trips)
        assertEquals(3, newDao.points.size)
        assertEquals(sampleRows().places, newDao.placeRows)
        assertEquals(2, newDao.docs.size)
        assertContentEquals(byteArrayOf(1, 2, 3), File(newDocs, JPG_NAME).readBytes())
        assertFalse(staged.stagingDir.exists())
        val s = newSettings.settings.value
        assertEquals("Alex", s.riderName)
        assertEquals("$", s.currencySymbol)
        assertTrue(s.onboarded)
        assertEquals(setOf("phone"), s.dismissedPermissions)
        assertEquals("SUZUKI-NEW", newBike.saved.value?.name, "the phone's own pairing stays")
    }

    @Test
    fun importReplacesWhatWasThereInsteadOfAdding() = runTest {
        val docs = tmp.newFolder("docs").also { File(it, PDF_NAME).writeBytes(byteArrayOf(9)); File(it, JPG_NAME).writeBytes(byteArrayOf(8)) }
        val dao = FakeBackupDao(sampleRows())
        val m = manager(dao, FakeSettings(), FakeSavedBike(), docs)
        val out = ByteArrayOutputStream()
        m.export(out, "1.0", 1)
        m.restore(m.stage(ByteArrayInputStream(out.toByteArray())), 0)
        m.restore(m.stage(ByteArrayInputStream(out.toByteArray())), 0)
        assertEquals(2, dao.trips.size)
        assertEquals(1, dao.fuel.size)
        assertEquals(setOf(PDF_NAME, JPG_NAME), docs.list()!!.toSet())
    }

    @Test
    fun aPaperWhoseFileIsMissingIsLeftOutOfTheExport() = runTest {
        val docs = tmp.newFolder("docs").also { File(it, JPG_NAME).writeBytes(byteArrayOf(8)) }
        val counts = manager(FakeBackupDao(sampleRows()), FakeSettings(), FakeSavedBike(), docs).export(ByteArrayOutputStream(), "1.0", 1)
        assertEquals(1, counts.documents)
    }

    @Test
    fun wipeLeavesNothingBehind() = runTest {
        val docs = tmp.newFolder("docs").also { File(it, PDF_NAME).writeBytes(byteArrayOf(9)) }
        val dao = FakeBackupDao(sampleRows())
        val settings = FakeSettings(Settings(onboarded = true, riderName = "Alex", developerMode = true))
        val bike = FakeSavedBike(SavedBikeRecord("SUZUKI-TEST", "00:11"))
        manager(dao, settings, bike, docs).wipe()
        assertTrue(dao.trips.isEmpty() && dao.points.isEmpty() && dao.fuel.isEmpty() && dao.service.isEmpty())
        assertTrue(dao.placeRows.isEmpty() && dao.docs.isEmpty())
        assertNull(dao.parked)
        assertFalse(docs.exists())
        assertEquals(Settings(), settings.settings.value)
        assertNull(bike.saved.value)
    }

    @Test
    fun aCancelledImportLeavesNoStagedFiles() = runTest {
        val docs = tmp.newFolder("docs").also { File(it, PDF_NAME).writeBytes(byteArrayOf(9)); File(it, JPG_NAME).writeBytes(byteArrayOf(8)) }
        val m = manager(FakeBackupDao(sampleRows()), FakeSettings(), FakeSavedBike(), docs)
        val out = ByteArrayOutputStream()
        m.export(out, "1.0", 1)
        val staged = m.stage(ByteArrayInputStream(out.toByteArray()))
        assertTrue(staged.stagingDir.exists())
        m.discard(staged)
        assertFalse(staged.stagingDir.exists())
    }
}
