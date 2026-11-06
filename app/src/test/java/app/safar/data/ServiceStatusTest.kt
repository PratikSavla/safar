package app.safar.data

import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServiceStatusTest {
    private val utc = ZoneOffset.UTC
    private val now = millis(LocalDate.of(2026, 11, 6))

    private fun millis(date: LocalDate): Long = date.atStartOfDay(utc).toInstant().toEpochMilli()

    private fun item(every: Int, last: Int) =
        ServiceItem(id = 1, label = "Engine oil", everyKm = every, lastDoneOdoKm = last, lastDoneMillis = null, icon = ServiceIcon.OIL)

    private fun timed(span: TimeSpan, doneOn: LocalDate, everyKm: Int? = null) =
        ServiceItem(1, "Battery", everyKm, 10_000, millis(doneOn), ServiceIcon.BATTERY, everyTime = span)

    private fun status(item: ServiceItem, odo: Int, at: Long = now) = serviceStatus(item, odo, at, utc)

    @Test
    fun okWhenFarFromDue() {
        val s = status(item(3000, 9800), 11000)
        val km = s.km!!
        assertEquals(1200, km.sinceKm)
        assertEquals(1800, km.leftKm)
        assertEquals(12800, km.dueAtKm)
        assertEquals(0.4, s.progress, 1e-9)
        assertEquals(ServiceState.OK, s.state)
        assertNull(s.time, "an item with no time interval has no time side")
    }

    @Test
    fun dueSoonWithinTenPercent() {
        assertEquals(ServiceState.OK, status(item(3000, 9800), 12499).state)
        assertEquals(ServiceState.DUE_SOON, status(item(3000, 9800), 12500).state)
        assertEquals(ServiceState.DUE_SOON, status(item(300, 12080), 12350).state)
        assertEquals(ServiceState.DUE_SOON, status(item(3000, 9800), 12800).state)
    }

    @Test
    fun overdueOncePastInterval() {
        val s = status(item(3000, 9800), 13261)
        assertEquals(-461, s.km!!.leftKm)
        assertEquals(ServiceState.OVERDUE, s.state)
        assertEquals(3461.0 / 3000, s.progress, 1e-9)
    }

    @Test
    fun odometerBelowLastServiceClampsToZero() {
        val s = status(item(500, 11890), 11800)
        assertEquals(0, s.km!!.sinceKm)
        assertEquals(500, s.km!!.leftKm)
        assertEquals(0.0, s.progress)
    }

    @Test
    fun sortsMostUrgentFirst() {
        val items = listOf(item(6000, 6100).copy(id = 3), item(500, 11890).copy(id = 2), item(3000, 9800).copy(id = 1))
        // Air filter overdue by 161 km; then the further along: oil 82 % (539 km left), chain 74 % (129 km left).
        val sorted = serviceStatuses(items, 12261, now, utc)
        assertEquals(listOf(3L, 1L, 2L), sorted.map { it.item.id })
        assertEquals(listOf(-161, 539, 129), sorted.map { it.km!!.leftKm })
    }

    @Test
    fun timeIntervalCountsCalendarDaysFromTheLastService() {
        val s = status(timed(TimeSpan(6, SpanUnit.MONTHS), LocalDate.of(2026, 6, 6)), 12_000)
        val time = s.time!!
        assertEquals(LocalDate.of(2026, 12, 6), time.dueOn)
        assertEquals(30L, time.leftDays)
        assertEquals(153L, time.sinceDays)
        assertEquals(ServiceState.OK, s.state)
        assertEquals(ServiceLead.TIME, s.lead)
        assertNull(s.km)
    }

    @Test
    fun timeIntervalDueSoonAndOverdue() {
        val months = timed(TimeSpan(6, SpanUnit.MONTHS), LocalDate.of(2026, 5, 20))
        assertEquals(ServiceState.DUE_SOON, status(months, 0).state, "14 of 184 days left")
        val weeks = timed(TimeSpan(2, SpanUnit.WEEKS), LocalDate.of(2026, 10, 20))
        assertEquals(-3L, status(weeks, 0).time!!.leftDays)
        assertEquals(ServiceState.OVERDUE, status(weeks, 0).state)
        val days = timed(TimeSpan(10, SpanUnit.DAYS), LocalDate.of(2026, 10, 28))
        assertEquals(ServiceState.DUE_SOON, status(days, 0).state, "a short interval is due soon from 1 day left")
    }

    @Test
    fun monthsAreCalendarMonths() {
        val s = status(timed(TimeSpan(1, SpanUnit.MONTHS), LocalDate.of(2027, 1, 31)), 0, millis(LocalDate.of(2027, 2, 1)))
        assertEquals(LocalDate.of(2027, 2, 28), s.time!!.dueOn)
    }

    @Test
    fun bothSidesWhicheverComesFirst() {
        // 6 months from 6 Jun (30 days left of 183) and 3,000 km from 10,000 km (2,900 km left at 10,100 km).
        val both = timed(TimeSpan(6, SpanUnit.MONTHS), LocalDate.of(2026, 6, 6), everyKm = 3000)
        val early = status(both, 10_100)
        assertEquals(ServiceLead.TIME, early.lead)
        assertEquals(early.time!!.progress, early.progress, 1e-9)
        val far = status(both, 13_100)
        assertEquals(ServiceLead.KM, far.lead)
        assertEquals(ServiceState.OVERDUE, far.state, "the worse side wins")
    }

    @Test
    fun pendingFirstServiceReplacesTheInterval() {
        val first = item(3000, 0).copy(firstAtKm = 1000)
        assertEquals(1000, status(first, 600).km!!.dueAtKm)
        assertEquals(400, status(first, 600).km!!.leftKm)
        val firstDate = timed(TimeSpan(6, SpanUnit.MONTHS), LocalDate.of(2026, 11, 1)).copy(firstByDate = LocalDate.of(2026, 12, 1))
        assertEquals(LocalDate.of(2026, 12, 1), status(firstDate, 0).time!!.dueOn)
        assertEquals(LocalDate.of(2027, 5, 1), status(firstDate.copy(firstByDate = null), 0).time!!.dueOn)
    }

    @Test
    fun undatedLastServiceHasNoTimeSide() {
        val s = status(timed(TimeSpan(1, SpanUnit.MONTHS), LocalDate.of(2026, 1, 1)).copy(lastDoneMillis = null), 0)
        assertNull(s.time)
        assertEquals(ServiceState.OK, s.state)
    }

    @Test
    fun defaultsStartAtCurrentOdometer() {
        val items = DefaultServiceItems.create(odometerKm = 12261, atMillis = 1_000)
        assertEquals(listOf(3000, 500, 6000, 5000, 300), items.map { it.everyKm })
        assertEquals(
            listOf(ServiceIcon.OIL, ServiceIcon.CHAIN, ServiceIcon.AIR, ServiceIcon.BRAKE, ServiceIcon.TYRE),
            items.map { it.icon },
        )
        items.forEach {
            assertEquals(12261, it.lastDoneOdoKm)
            assertEquals(1_000L, it.lastDoneMillis)
            assertEquals(0L, it.id)
            assertNull(it.everyTime)
            assertEquals(false, it.firstPending)
            assertEquals(ServiceState.OK, status(it, 12261).state)
        }
    }
}
