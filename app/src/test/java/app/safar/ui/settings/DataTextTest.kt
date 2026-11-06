package app.safar.ui.settings

import app.safar.core.RideState
import app.safar.data.backup.BackupCounts
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataTextTest {
    @Test
    fun summariesNameOnlyWhatTheFileHolds() {
        assertEquals("12 rides, 1 fuel-up and 2 papers", exportSummary(BackupCounts(trips = 12, fuelUps = 1, documents = 2)))
        assertEquals("1 place", exportSummary(BackupCounts(places = 1)))
        assertEquals("your settings", exportSummary(BackupCounts()))
    }

    @Test
    fun theImportSheetSaysWhatHappens() {
        val at = LocalDate.of(2026, 11, 6).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val text = importText(ImportOffer(BackupCounts(trips = 3), at, 0), ZoneOffset.UTC)
        assertEquals(
            "This export from 6 Nov 2026 holds 3 rides. It replaces everything on this phone now. Your paired bike stays as it is.",
            text,
        )
    }

    @Test
    fun deletingNeedsTheWord() {
        assertTrue(wipeConfirmed("DELETE"))
        assertTrue(wipeConfirmed(" delete "))
        assertFalse(wipeConfirmed("DELET"))
        assertFalse(wipeConfirmed(""))
    }

    @Test
    fun aRideInProgressLocksTheDataTools() {
        assertNull(lockReason(RideState()))
        assertNotNull(lockReason(RideState(moving = true)))
        assertNotNull(lockReason(RideState(recordingTripId = 4)))
    }
}
