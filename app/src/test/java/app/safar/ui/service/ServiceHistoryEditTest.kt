package app.safar.ui.service

import app.safar.data.ServiceIcon
import app.safar.data.ServiceItem
import app.safar.data.ServiceRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServiceHistoryEditTest {
    private val utc = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 23)
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    private val older = ServiceRecord(1, 1, "Engine oil", ServiceIcon.OIL, at("2026-02-06T12:00:00Z"), 6_874, 1380.0)
    private val last = ServiceRecord(2, 1, "Engine oil", ServiceIcon.OIL, at("2026-08-11T10:30:00Z"), 9_874, 1450.0, "Workshop, NH 48")
    private val chain = ServiceRecord(3, 2, "Chain clean & lube", ServiceIcon.CHAIN, at("2026-11-02T12:00:00Z"), 11_655)
    private val oil = ServiceItem(1, "Engine oil", 3000, 9_874, last.atMillis, ServiceIcon.OIL)
    private val chainItem = ServiceItem(2, "Chain clean & lube", 500, 11_655, chain.atMillis, ServiceIcon.CHAIN)
    private val items = listOf(oil, chainItem)
    private val records = listOf(chain, last, older)

    @Test
    fun `the sheet opens with the record's values`() {
        assertEquals(PastForm(1, LocalDate.of(2026, 8, 11).toEpochDay(), "9874", "1450", "Workshop, NH 48"), formForRecord(last, utc))
        assertEquals(PastForm(2, LocalDate.of(2026, 11, 2).toEpochDay(), "11655", "", ""), formForRecord(chain, utc))
    }

    @Test
    fun `an edit on the same day keeps the saved time, a new day moves to noon`() {
        val note = assertNotNull(editedRecord(formForRecord(last, utc).copy(note = "Dealer"), items, today, utc, last))
        assertEquals(last.copy(note = "Dealer"), note)
        val moved = assertNotNull(editedRecord(formForRecord(last, utc).copy(day = LocalDate.of(2026, 8, 20).toEpochDay()), items, today, utc, last))
        assertEquals(at("2026-08-20T12:00:00Z"), moved.atMillis)
        assertEquals(2L, moved.id)
    }

    @Test
    fun `fixing the odometer of the service an item counts from moves the item`() {
        val fixed = last.copy(odoKm = 9_900)
        assertEquals(listOf(oil.copy(lastDoneOdoKm = 9_900)), itemsAfterEdit(items, records, last, fixed))
    }

    @Test
    fun `moving that service before an older one counts from the older one`() {
        val earlier = last.copy(atMillis = at("2026-01-10T12:00:00Z"), odoKm = 6_000)
        assertEquals(listOf(oil.copy(lastDoneMillis = older.atMillis, lastDoneOdoKm = 6_874)), itemsAfterEdit(items, records, last, earlier))
    }

    @Test
    fun `editing an older service leaves the item alone unless it becomes the newest`() {
        assertTrue(itemsAfterEdit(items, records, older, older.copy(cost = 1400.0)).isEmpty())
        val newest = older.copy(atMillis = at("2026-11-20T12:00:00Z"), odoKm = 12_190)
        assertEquals(listOf(oil.copy(lastDoneMillis = newest.atMillis, lastDoneOdoKm = 12_190)), itemsAfterEdit(items, records, older, newest))
    }

    @Test
    fun `nothing changes for a note-only edit`() {
        assertTrue(itemsAfterEdit(items, records, last, last.copy(note = "Dealer")).isEmpty())
    }
}
