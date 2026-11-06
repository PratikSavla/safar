package app.safar.data.backup

import app.safar.data.SavedBikeRecord
import app.safar.data.Settings
import app.safar.data.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupRoundTripTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val settings = Settings(onboarded = true, riderName = "Alex", theme = ThemeMode.DARK, tankLitres = 12.5, documentReminders = false)
    private val bike = SavedBikeRecord("SUZUKI-TEST", "00:11:22:33:44:55")

    private fun docsDir(): File = tmp.newFolder("documents").also {
        File(it, PDF_NAME).writeBytes("%PDF-1.4\n".toByteArray())
        File(it, JPG_NAME).writeBytes(byteArrayOf(-1, -40, -1, 0, 1))
    }

    private fun export(dir: File): ByteArray {
        val data = UserSnapshot(sampleRows(), settings, bike).toBackupData()
        val manifest = BackupManifest(BACKUP_FORMAT, BACKUP_FORMAT_VERSION, "1.2.3", 9_000, data.counts())
        return ByteArrayOutputStream().also { out -> writeBackup(out, manifest, data) { File(dir, it) } }.toByteArray()
    }

    @Test
    fun anExportReadsBackToTheSameRowsSettingsAndFiles() {
        val dir = docsDir()
        val staged = readBackup(ByteArrayInputStream(export(dir)), tmp.root.resolve("staging"))

        assertEquals("1.2.3", staged.manifest.appVersion)
        assertEquals(BackupCounts(trips = 2, trackPoints = 3, fuelUps = 1, serviceItems = 2, places = 1, parkedSpot = true, documents = 2), staged.manifest.counts)
        assertEquals(0, staged.missingDocuments)
        val rows = staged.data.toRows(todayEpochDay = 0)
        val original = sampleRows()
        assertEquals(original.trips, rows.trips)
        assertEquals(original.trackPoints.map { it.copy(id = 0) }, rows.trackPoints)
        assertEquals(original.fuelUps, rows.fuelUps)
        assertEquals(original.serviceItems, rows.serviceItems)
        assertEquals(original.places, rows.places)
        assertEquals(original.parkedSpot, rows.parkedSpot)
        assertEquals(original.documents.map { it.copy(remindedStage = 0) }, rows.documents)
        assertEquals(settings, staged.data.settings!!.applyTo(Settings(onboarded = true)))
        assertEquals(SavedBikeDto(bike.name, bike.address), staged.data.savedBike)
        for (name in listOf(PDF_NAME, JPG_NAME)) assertContentEquals(File(dir, name).readBytes(), File(staged.stagingDir, name).readBytes())
    }

    @Test
    fun theManifestComesFirstAndTheDataIsReadableJson() {
        val zip = ZipInputStream(ByteArrayInputStream(export(docsDir())))
        val names = generateSequence { zip.nextEntry?.name }.toList()
        assertEquals(listOf(MANIFEST_ENTRY, DATA_ENTRY, "documents/$PDF_NAME", "documents/$JPG_NAME"), names)
    }

    @Test
    fun theReadableExpiryDateIsWrittenNextToTheDayNumber() {
        val data = UserSnapshot(sampleRows(), settings, null).toBackupData()
        assertEquals("2025-11-08", data.documents.first().expiryDate)
        assertTrue(BackupJson.encodeToString(BackupData.serializer(), data).contains("\"expiryDate\": \"2025-11-08\""))
    }

    @Test
    fun importedRemindersAlreadyDueCountAsSent() {
        val data = UserSnapshot(sampleRows(), settings, null).toBackupData()
        // The PUC expires on day 20 400; 5 days before is inside the 7-day window.
        assertEquals(2, data.toRows(todayEpochDay = 20_395).documents.first().remindedStage)
        assertEquals(0, data.toRows(todayEpochDay = 20_000).documents.first().remindedStage)
    }
}
