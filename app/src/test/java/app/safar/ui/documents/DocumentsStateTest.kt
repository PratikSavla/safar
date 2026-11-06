package app.safar.ui.documents

import app.safar.data.BikeDocument
import app.safar.data.DocKind
import app.safar.ui.components.StampTone
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DocumentsStateTest {
    private val today = LocalDate.of(2026, 11, 6).toEpochDay()

    private fun doc(kind: DocKind, title: String, expiry: LocalDate?, mime: String = "application/pdf") =
        BikeDocument(1, title, kind, "a.pdf", mime, 1, 0, expiry?.toEpochDay())

    @Test
    fun aPaperExpiringSoonGetsAnAmberStampWithDaysLeft() {
        val row = paperRow(doc(DocKind.PUC, "PUC", LocalDate.of(2026, 11, 12)), today)
        assertEquals("6 days left", row.stamp)
        assertEquals(StampTone.AMBER, row.tone)
        assertEquals("valid till 12 Nov 2026 · PDF", row.subtitle)
    }

    @Test
    fun anExpiredPaperIsStampedInRust() {
        val row = paperRow(doc(DocKind.INSURANCE, "Bike insurance", LocalDate.of(2026, 11, 1)), today)
        assertEquals("expired", row.stamp)
        assertEquals(StampTone.RUST, row.tone)
        assertEquals("Insurance · expired on 1 Nov 2026 · PDF", row.subtitle)
    }

    @Test
    fun aPaperWithoutExpiryHasNoStamp() {
        val row = paperRow(doc(DocKind.RC, "RC", null, "image/jpeg"), today)
        assertNull(row.stamp)
        assertEquals("Photo", row.subtitle)
    }

    @Test
    fun sizesReadNaturally() {
        assertEquals("640 KB", sizeText(640 * 1024))
        assertEquals("2.5 MB", sizeText(5L * 1024 * 1024 / 2))
    }
}
