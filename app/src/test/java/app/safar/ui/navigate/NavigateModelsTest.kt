package app.safar.ui.navigate

import app.safar.core.NavSnapshot
import app.safar.core.NavState
import app.safar.core.NavStatusCode
import app.safar.data.ParkedSpot
import app.safar.data.Place
import app.safar.data.PlaceKind
import app.safar.data.PlaceSource
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NavigateModelsTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val home = Place(1, "Home", "Main Rd", "Main Rd, Sample Town", PlaceKind.HOME, PlaceSource.MAPS, 1)
    private val fuel = Place(2, "Fuel", "", "NH 48", PlaceKind.FUEL, PlaceSource.TYPED, 0)

    @Test
    fun destinationsFollowSortOrderAndEndWithParked() {
        val list = destinations(listOf(home, fuel), ParkedSpot(12.5, 77.25, 8f, 1_000L, null))
        assertEquals(listOf("place:2", "place:1", PARKED_KEY), list.map { it.key })
        assertEquals("NH 48", list[0].detail) // blank detail falls back to the query
        assertEquals("address", list[0].via)
        assertEquals("12.500000,77.250000", list[2].query)
        assertEquals("Saved spot", list[2].detail) // no raw coordinates in the row
        assertEquals("your parked bike", list[2].startLabel)
        assertEquals(1_000L, list[2].atMillis)
    }

    @Test
    fun selectionFallsBackToFirstRow() {
        val list = destinations(listOf(home, fuel), null)
        assertEquals("place:1", resolveSelection(list, "place:1")?.key)
        assertEquals("place:2", resolveSelection(list, "place:99")?.key)
        assertNull(resolveSelection(emptyList(), null))
    }

    @Test
    fun mirrorShowsWhatTheClusterGets() {
        val eta = LocalDateTime.of(2026, 1, 5, 16, 12).toInstant(ZoneOffset.UTC).toEpochMilli()
        val m = mirrorOf(NavSnapshot(4, false, 1_234, eta, 11_200, NavStatusCode.NORMAL, "Turn right", 5L), zone)
        assertEquals(4, m.clusterIcon)
        assertEquals("Turn right", m.iconName)
        assertEquals("0412PM", m.etaSent)
        assertEquals("04:12 PM", m.etaShown)
        assertEquals("1.2 km", m.toTurn.toString())
        assertEquals("11.2 km", m.remaining.toString())
        assertEquals(StatusTone.FINE, m.tone)
    }

    @Test
    fun mirrorDistancesMatchTheFrameRounding() {
        // Same digits as encodeNavigation: 10 m first, then toFixed(1) on the binary value.
        val km = listOf(1_049, 1_150, 2_050).map { mirrorOf(NavSnapshot(1, true, it, 0L, it, NavStatusCode.NORMAL, "", 0L), zone) }
        assertEquals(listOf("1.1 km", "1.1 km", "2.0 km"), km.map { it.toTurn.toString() })
        assertEquals(listOf("1.1 km", "1.1 km", "2.0 km"), km.map { it.remaining.toString() })
        assertEquals("100 km", mirrorOf(NavSnapshot(1, true, 99_960, 0L, 0, NavStatusCode.NORMAL, "", 0L), zone).toTurn.toString())
        assertEquals("0 m", mirrorOf(NavSnapshot(1, true, 4, 0L, 0, NavStatusCode.NORMAL, "", 0L), zone).remaining.toString())
    }

    @Test
    fun statusThatHidesTheIconSends46() {
        val m = mirrorOf(NavSnapshot(1, true, 850, 0L, 900, NavStatusCode.GPS_OFF, "", 0L), zone)
        assertEquals(46, m.clusterIcon)
        assertEquals("GPS is off", m.statusLabel)
        assertEquals(StatusTone.WARN, m.tone)
        assertEquals("850 m", m.toTurn.toString())
    }

    @Test
    fun recalculatingAndArrivedAreLabelled() {
        assertEquals("Recalculating", NavStatusCode.RECALCULATING.label())
        assertEquals("No internet", NavStatusCode.NO_INTERNET.label())
        assertEquals("On route", NavStatusCode.NORMAL.label())
        assertEquals("You have arrived", NavStatusCode.ARRIVED.label())
        assertEquals(StatusTone.FINE, NavStatusCode.ARRIVED.tone())
        assertEquals(StatusTone.INFO, NavStatusCode.VIA_POINT.tone())
    }

    @Test
    fun phaseFollowsTheBridge() {
        val snap = NavSnapshot(1, true, 10, 0, 10, NavStatusCode.NORMAL, "", 0)
        assertEquals(RoutePhase.PICK, routePhase(NavState(), awaitingMaps = false))
        assertEquals(RoutePhase.AWAITING_MAPS, routePhase(NavState(), awaitingMaps = true))
        assertEquals(RoutePhase.WAITING_FIRST_UPDATE, routePhase(NavState(waiting = true), awaitingMaps = false))
        assertEquals(RoutePhase.WAITING_FIRST_UPDATE, routePhase(NavState(active = true), awaitingMaps = true))
        assertEquals(RoutePhase.ACTIVE, routePhase(NavState(active = true, snapshot = snap), awaitingMaps = false))
    }

    @Test
    fun readableUriKeepsTheQueryAsTyped() {
        assertEquals("google.navigation:q=Main Rd, Sample Town&mode=l", readableNavUri("Main Rd, Sample Town"))
    }
}
