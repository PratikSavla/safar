package app.safar.ui.service

import app.safar.data.ServiceIcon
import app.safar.data.ServiceItem
import app.safar.data.SpanUnit
import app.safar.data.TimeSpan
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServiceFormTest {
    private val today = LocalDate.of(2026, 11, 6)
    private val oil = ServiceItem(1, "Engine oil", 3000, 10_000, 1_000L, ServiceIcon.OIL)
    private val months = TimeSpan(6, SpanUnit.MONTHS)

    @Test
    fun `an item opens in the sheet as saved`() {
        assertEquals(ServiceForm("Engine oil", ServiceIcon.OIL, DueBy.DISTANCE, 3000), formFor(oil))
        val both = formFor(oil.copy(everyTime = months, firstAtKm = 11_000))
        assertEquals(DueBy.BOTH, both.dueBy)
        assertEquals(true, both.firstOn)
        assertEquals("11000", both.firstAtKmText)
        assertEquals(DueBy.TIME, formFor(oil.copy(everyKm = null, everyTime = months)).dueBy)
    }

    @Test
    fun `time steppers stay within each unit's range`() {
        assertEquals(TimeSpan(7, SpanUnit.MONTHS), stepCount(months, +1))
        assertEquals(TimeSpan(1, SpanUnit.DAYS), stepCount(TimeSpan(1, SpanUnit.DAYS), -1))
        assertEquals(TimeSpan(36, SpanUnit.MONTHS), stepCount(TimeSpan(36, SpanUnit.MONTHS), +1))
        assertEquals(TimeSpan(52, SpanUnit.WEEKS), withUnit(TimeSpan(60, SpanUnit.DAYS), SpanUnit.WEEKS))
        assertEquals(LocalDate.of(2027, 1, 6), stepFirstDate(LocalDate.of(2026, 12, 6), SpanUnit.MONTHS, +1, today))
        assertEquals(LocalDate.of(2026, 11, 7), stepFirstDate(LocalDate.of(2026, 11, 10), SpanUnit.WEEKS, -1, today), "never today or before")
    }

    @Test
    fun `turning the first service on suggests one interval from now`() {
        val on = withFirstOn(ServiceForm(dueBy = DueBy.BOTH, everyKm = 3000, everyTime = months), odoKm = 12_000, today = today)
        assertEquals("15000", on.firstAtKmText)
        assertEquals(LocalDate.of(2027, 5, 6), on.firstByDate)
        assertNull(formProblem(on, 12_000, today))
    }

    @Test
    fun `problems are spelled out`() {
        val first = ServiceForm(dueBy = DueBy.BOTH, firstOn = true)
        assertEquals("We need your odometer first. Connect your bike or log a fuel-up.", formProblem(ServiceForm(), null, today))
        assertNull(formProblem(ServiceForm(dueBy = DueBy.TIME), null, today), "time alone needs no odometer")
        assertNull(formProblem(ServiceForm(), null, today, counted = oil), "an item already counted by distance keeps its odometer")
        assertEquals("Set when the first service is due, or turn it off.", formProblem(first, 12_000, today))
        assertEquals(
            "The first service must be past your odometer now (12,000 km).",
            formProblem(first.copy(firstAtKmText = "11500"), 12_000, today),
        )
        assertEquals("The first service date must be after today.", formProblem(first.copy(firstByDate = today), 12_000, today))
        assertNull(formProblem(first.copy(firstByDate = today.plusDays(1)), 12_000, today))
    }

    @Test
    fun `a new item counts from the odometer and now`() {
        val form = ServiceForm("Coolant", ServiceIcon.COOLANT, DueBy.BOTH, 2000, months, firstOn = true, firstAtKmText = "13000", firstByDate = today.plusMonths(2))
        val item = formItem(form, null, 12_000, 5_000L)
        assertEquals(ServiceItem(0, "Coolant", 2000, 12_000, 5_000L, ServiceIcon.COOLANT, months, 13_000, today.plusMonths(2)), item)
        val timeOnly = formItem(form.copy(dueBy = DueBy.TIME), null, null, 5_000L)
        assertNull(timeOnly.everyKm)
        assertNull(timeOnly.firstAtKm, "a first odometer only counts with distance on")
    }

    @Test
    fun `an edit keeps the last service, and a new side starts counting now`() {
        val edited = formItem(formFor(oil).copy(icon = ServiceIcon.STAR, dueBy = DueBy.BOTH), oil, 12_000, 9_000L)
        assertEquals(oil.copy(icon = ServiceIcon.STAR, everyTime = months), edited, "time counts from the dated last service")
        val wasTime = oil.copy(everyKm = null, everyTime = months)
        val toKm = formItem(formFor(wasTime).copy(dueBy = DueBy.BOTH), wasTime, 12_000, 9_000L)
        assertEquals(12_000, toKm.lastDoneOdoKm, "distance starts from the odometer now")
        assertEquals(1000, toKm.everyKm, "the sheet offers 1,000 km for a side that was off")
        val off = formItem(formFor(oil.copy(firstAtKm = 11_000)).copy(firstOn = false), oil.copy(firstAtKm = 11_000), 12_000, 9_000L)
        assertNull(off.firstAtKm)
    }
}
