package app.safar.ui.service

import app.safar.data.ServiceIcon
import app.safar.data.ServiceItem
import app.safar.data.ServiceRecord
import app.safar.data.SpanUnit
import app.safar.data.TimeSpan
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ServiceHistoryTest {
    private val utc = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 2)
    private val oilDone = Instant.parse("2026-06-10T09:00:00Z").toEpochMilli()
    private val oil = ServiceItem(1, "Engine oil", 3000, 9_200, oilDone, ServiceIcon.OIL)
    private val battery = ServiceItem(2, "Battery check", null, 9_200, null, ServiceIcon.BATTERY, everyTime = TimeSpan(6, SpanUnit.MONTHS))
    private val items = listOf(oil, battery)

    @Test
    fun `history lines show date, odometer and cost when known`() {
        val records = listOf(
            ServiceRecord(4, 1, "Engine oil", ServiceIcon.OIL, Instant.parse("2026-03-12T12:00:00Z").toEpochMilli(), 11_820, 450.0, "Main Rd garage"),
            ServiceRecord(3, 2, "Battery check", ServiceIcon.BATTERY, Instant.parse("2025-12-01T12:00:00Z").toEpochMilli(), null),
        )
        val rows = historyRows(records, "₹", utc)
        assertEquals(HistoryRowUi(4, "Engine oil", ServiceIcon.OIL, "12 Mar 2026", "11,820 km · ₹450", "Main Rd garage"), rows[0])
        assertEquals(HistoryRowUi(3, "Battery check", ServiceIcon.BATTERY, "1 Dec 2025", "", null), rows[1])
    }

    @Test
    fun `the sheet asks for an item, a past day and the odometer of distance items`() {
        val day = today.toEpochDay() - 30
        assertEquals("Choose what was done.", pastProblem(PastForm(day = day), items, today))
        assertEquals("Pick a day up to today.", pastProblem(PastForm(1, today.toEpochDay() + 1, "9000"), items, today))
        assertEquals("Enter the odometer at that service.", pastProblem(PastForm(1, day), items, today))
        assertEquals("The odometer is whole km, as on your bike.", pastProblem(PastForm(1, day, "0"), items, today))
        assertEquals("The cost is a number, or leave it empty.", pastProblem(PastForm(1, day, "9000", cost = "abc"), items, today))
        assertNull(pastProblem(PastForm(2, day), items, today))
        assertNull(pastProblem(PastForm(1, day, "9000", cost = "450,50"), items, today))
    }

    @Test
    fun `a past record is saved at noon with the item's name and icon`() {
        val day = LocalDate.of(2026, 9, 1).toEpochDay()
        val r = assertNotNull(pastRecord(PastForm(1, day, "10400", " 450,5 ", "  Dealer  "), items, today, utc))
        assertEquals(ServiceRecord(0, 1, "Engine oil", ServiceIcon.OIL, Instant.parse("2026-09-01T12:00:00Z").toEpochMilli(), 10_400, 450.5, "Dealer"), r)
        assertNull(pastRecord(PastForm(1, day), items, today, utc))
    }

    @Test
    fun `a newer past service moves the item's last service, an older one does not`() {
        val newer = ServiceRecord(0, 1, "Engine oil", ServiceIcon.OIL, Instant.parse("2026-09-01T12:00:00Z").toEpochMilli(), 10_400)
        assertEquals(oil.copy(lastDoneMillis = newer.atMillis, lastDoneOdoKm = 10_400), itemAfterPast(oil, newer))
        assertNull(itemAfterPast(oil, newer.copy(atMillis = Instant.parse("2026-01-01T12:00:00Z").toEpochMilli())))
        assertNull(itemAfterPast(oil, newer.copy(odoKm = 9_000)))
        assertNull(itemAfterPast(oil, newer.copy(odoKm = null)))
        val pending = battery.copy(firstByDate = LocalDate.of(2026, 12, 1))
        val check = ServiceRecord(0, 2, "Battery check", ServiceIcon.BATTERY, Instant.parse("2026-10-01T12:00:00Z").toEpochMilli(), null)
        assertEquals(battery.copy(lastDoneMillis = check.atMillis), itemAfterPast(pending, check))
    }

    @Test
    fun `the toast says whether the reminder counts from it`() {
        val r = ServiceRecord(0, 1, "Engine oil", ServiceIcon.OIL, Instant.parse("2026-09-01T12:00:00Z").toEpochMilli(), 10_400)
        assertEquals("Engine oil on 1 Sep 2026 logged. Next one counted from it.", pastLoggedText(r, true, utc))
        assertEquals("Engine oil on 1 Sep 2026 added to the history", pastLoggedText(r, false, utc))
    }
}
