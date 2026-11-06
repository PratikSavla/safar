package app.safar.ui.service

import app.safar.core.BikeState
import app.safar.core.ClusterReading
import app.safar.core.LinkState
import app.safar.core.RideState
import app.safar.data.FuelUp
import app.safar.data.OdoSource
import app.safar.data.ServiceIcon
import app.safar.data.ServiceItem
import app.safar.data.ServiceState
import app.safar.data.SpanUnit
import app.safar.data.TimeSpan
import app.safar.data.serviceStatus
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServiceMappingTest {
    private companion object {
        val UTC: ZoneOffset = ZoneOffset.UTC
        val TODAY: LocalDate = LocalDate.of(2026, 11, 6)
        val NOW = TODAY.atStartOfDay(UTC).toInstant().toEpochMilli()
    }

    private fun reading(odo: Int, at: Long = 5_000) = ClusterReading(0, odo, 0.0, 0.0, 4, 'N', 0, 0, at)

    private fun fill(odo: Int, at: Long) = FuelUp(0, at, odo, 10.0, 1_000.0, true, OdoSource.MANUAL)

    private val oil = ServiceItem(1, "Engine oil", 3000, 9_200, null, ServiceIcon.OIL)
    private val chain = ServiceItem(2, "Chain clean & lube", 500, 11_820, null, ServiceIcon.CHAIN)
    private val air = ServiceItem(3, "Air filter", 6000, 9_200, null, ServiceIcon.AIR)
    private val items = listOf(air, oil, chain)

    @Test
    fun `odometer comes from the live bike, the last reading, or the last service`() {
        assertEquals(OdoNowUi(12_301, 5_000, true, OdoFrom.BIKE), odoNow(BikeState(link = LinkState.Connected, cluster = reading(12_301)), items))
        assertEquals(OdoNowUi(12_250, 5_000, false, OdoFrom.LAST_RIDE), odoNow(BikeState(lastCluster = reading(12_250)), items))
        assertEquals(OdoNowUi(11_820, null, false, OdoFrom.SERVICE), odoNow(BikeState(), items))
        assertNull(odoNow(BikeState(), emptyList()).km)
        val timeOnly = ServiceItem(9, "Battery", null, 0, null, ServiceIcon.BATTERY, everyTime = TimeSpan(6, SpanUnit.MONTHS))
        assertNull(odoNow(BikeState(), listOf(timeOnly)).km, "a time-only item added without an odometer is not a reading")
        assertNull(odoNote(OdoNowUi(12_301, 5_000, true, OdoFrom.BIKE)))
        assertNull(odoNote(OdoNowUi(12_250, 5_000, false, OdoFrom.LAST_RIDE)))
        assertEquals("From your last service", odoNote(OdoNowUi(11_820, null, false, OdoFrom.SERVICE)))
        assertEquals("From your last fuel-up", odoNote(OdoNowUi(12_300, 2_000, false, OdoFrom.FUEL_UP)))
        assertEquals("Connect your bike or log a fuel-up to see it.", odoNote(odoNow(BikeState(), emptyList())))
    }

    @Test
    fun `without a bike reading the typed fuel-up odometer counts, so Home and the logbook agree`() {
        val fills = listOf(fill(12_000, 1_000), fill(12_300, 2_000))
        val coolant = ServiceItem(4, "Coolant", 1_100, 12_300, null, ServiceIcon.GENERIC)
        assertEquals(OdoNowUi(12_300, 2_000, false, OdoFrom.FUEL_UP), odoNow(BikeState(), emptyList(), fills))
        // A later service odometer beats an older fuel-up; a live bike beats both.
        assertEquals(12_300, odoNow(BikeState(), listOf(coolant), listOf(fill(12_000, 1_000))).km)
        assertEquals(12_400, odoNow(BikeState(link = LinkState.Connected, cluster = reading(12_400)), listOf(coolant), fills).km)
        val s = serviceUiState(listOf(coolant), BikeState(), RideState(), reminders = true, fuelUps = fills, nowMillis = NOW, zone = UTC)
        assertEquals("Due in 1,100 km, at 13,400 km", s.rows.single().line)
        assertEquals(0, s.needYou)
    }

    @Test
    fun `a new item needs a known odometer`() {
        assertEquals("Counted from 12,300 km, your odometer now.", addFromLine(12_300))
        assertEquals("Counted from today.", addFromLine(null, DueBy.TIME))
        assertEquals("Counted from 12,300 km, your odometer now, and from today.", addFromLine(12_300, DueBy.BOTH))
        assertEquals("We need your odometer first. Connect your bike or log a fuel-up.", addFromLine(null))
    }

    @Test
    fun `rows are most urgent first with words and due lines`() {
        val s = serviceUiState(items, BikeState(link = LinkState.Connected, cluster = reading(12_301)), RideState(), reminders = true, nowMillis = NOW, zone = UTC)
        assertEquals(listOf("Engine oil", "Chain clean & lube", "Air filter"), s.rows.map { it.status.item.label })
        assertEquals(listOf("overdue", "due soon", "on track"), s.rows.map { it.word })
        assertEquals("Overdue by 101 km", s.rows[0].line)
        assertEquals("Due in 19 km, at 12,320 km", s.rows[1].line)
        assertEquals(1f, s.rows[0].drawn)
        assertEquals(2, s.needYou)
        assertEquals(", 2 need you.", titleTail(s.needYou))
        assertEquals(", 1 needs you.", titleTail(1))
        assertEquals(", all good.", titleTail(0))
    }

    @Test
    fun `due line for an item on track`() {
        val st = serviceStatus(air, 10_000, NOW, UTC)
        assertEquals(ServiceState.OK, st.state)
        assertEquals("Due in 5,200 km, at 15,200 km", dueLine(st))
        assertEquals(800f / 6000f, serviceRow(st).drawn, 1e-6f)
    }

    @Test
    fun `interval steps by 500 from 2,000 km, else by 50, never below one step`() {
        assertEquals(3500, stepInterval(3000, +1))
        assertEquals(2500, stepInterval(3000, -1))
        assertEquals(1500, stepInterval(2000, -1))
        assertEquals(350, stepInterval(300, +1))
        assertEquals(50, stepInterval(50, -1))
        assertEquals(500, intervalStep(2000))
        assertEquals(50, intervalStep(1999))
    }

    @Test
    fun `mark done uses the current odometer, else the item's last service`() {
        assertEquals(12_301, markDoneOdo(OdoNowUi(12_301, 1, true, OdoFrom.BIKE), oil))
        assertEquals(9_200, markDoneOdo(OdoNowUi(null, null, false, OdoFrom.SERVICE), oil))
    }

    @Test
    fun `typing is locked while moving and labels are cleaned`() {
        val s = serviceUiState(items, BikeState(), RideState(speedKmh = 30f, moving = true), reminders = false, nowMillis = NOW, zone = UTC)
        assertEquals("Locked while moving (30 km/h). Stop to edit.", s.lockReason)
        assertEquals(false, s.reminders)
        assertEquals("Coolant check", cleanLabel("  Coolant check "))
        assertNull(cleanLabel("   "))
        assertEquals(32, cleanLabel("x".repeat(50))?.length)
    }
}
