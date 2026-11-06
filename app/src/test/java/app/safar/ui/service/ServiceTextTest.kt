package app.safar.ui.service

import app.safar.data.ServiceIcon
import app.safar.data.ServiceItem
import app.safar.data.SpanUnit
import app.safar.data.TimeSpan
import app.safar.data.serviceStatus
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals

class ServiceTextTest {
    private val utc = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 6)
    private val now = millis(today)

    private fun millis(date: LocalDate): Long = date.atStartOfDay(utc).toInstant().toEpochMilli()

    private val oil = ServiceItem(1, "Engine oil", 3000, 10_000, millis(LocalDate.of(2026, 6, 6)), ServiceIcon.OIL)
    private val sixMonths = TimeSpan(6, SpanUnit.MONTHS)

    private fun row(item: ServiceItem, odo: Int) = serviceRow(serviceStatus(item, odo, now, utc))

    @Test
    fun `spans and days read naturally`() {
        assertEquals("1 day", spanText(TimeSpan(1, SpanUnit.DAYS)))
        assertEquals("2 weeks", spanText(TimeSpan(2, SpanUnit.WEEKS)))
        assertEquals("6 months", spanText(sixMonths))
        assertEquals("12 days", daysText(12))
        assertEquals("Sun 6 Dec 2026", longDate(LocalDate.of(2026, 12, 6)))
    }

    @Test
    fun `every line names the interval and a pending first service`() {
        assertEquals("every 3,000 km", everyLine(oil))
        assertEquals("every 3,000 km or 6 months", everyLine(oil.copy(everyTime = sixMonths)))
        assertEquals("every 2 weeks", everyLine(oil.copy(everyKm = null, everyTime = TimeSpan(2, SpanUnit.WEEKS))))
        val first = oil.copy(everyTime = sixMonths, firstAtKm = 11_000, firstByDate = LocalDate.of(2026, 12, 6))
        assertEquals("first at 11,000 km or by 6 Dec, then every 3,000 km or 6 months", everyLine(first))
    }

    @Test
    fun `due line by time, both sides and overdue`() {
        val timeOnly = oil.copy(everyKm = null, everyTime = sixMonths)
        assertEquals("Due in 30 days, on 6 Dec", row(timeOnly, 10_100).line)
        assertEquals("6 Jun" to "6 Dec", row(timeOnly, 10_100).let { it.fromLabel to it.toLabel })
        val both = oil.copy(everyTime = sixMonths)
        assertEquals("Due in 30 days, on 6 Dec, or in 2,900 km, at 13,000 km, whichever comes first", row(both, 10_100).line)
        assertEquals("Due in 100 km, at 13,000 km, or in 30 days, on 6 Dec, whichever comes first", row(both, 12_900).line)
        assertEquals("10,000 km" to "13,000 km", row(both, 12_900).let { it.fromLabel to it.toLabel })
        assertEquals("Overdue by 200 km", row(both, 13_200).line)
        val weeks = oil.copy(everyKm = null, everyTime = TimeSpan(2, SpanUnit.WEEKS), lastDoneMillis = millis(LocalDate.of(2026, 10, 20)))
        assertEquals("Overdue by 3 days", row(weeks, 0).line)
        val today = oil.copy(everyKm = null, everyTime = TimeSpan(1, SpanUnit.WEEKS), lastDoneMillis = millis(LocalDate.of(2026, 10, 30)))
        assertEquals("Due today", row(today, 0).line)
    }

    @Test
    fun `the road speaks its side`() {
        assertEquals("84 percent of the way from the last service to the next, by time", roadSpoken(row(oil.copy(everyTime = sixMonths), 10_100)))
        assertEquals("3 percent of the way from the last service to the next, by distance", roadSpoken(row(oil, 10_100)))
    }
}
