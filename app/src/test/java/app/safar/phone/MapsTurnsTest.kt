package app.safar.phone

import app.safar.core.NavStatusCode
import app.safar.core.PhoneSnapshot
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapsTurnsTest {
    private val zone = ZoneOffset.UTC
    private val now = LocalDateTime.of(2026, 11, 2, 11, 30).toInstant(zone).toEpochMilli()
    private val phoneOk = PhoneSnapshot(signalBars = 3, hasInternet = true, gpsOn = true)

    // Synthetic fields in the confirmed Google Maps 26.44 layout (docs 11.2).
    private val depart = MapsFields(title = "Head southwest on Main Rd", shortCriticalText = "", subText = "Arrive 12:55 pm", progress = 0, progressMax = 23708)
    private val turn = MapsFields(title = "200 m · Turn right toward NH 48", shortCriticalText = "200 m", subText = "Arrive 11:56 am", progress = 338, progressMax = 1723)

    @Test
    fun departureMapsToSnapshot() {
        val snap = reduceMaps(null, depart, matchedIcon = null, nowMillis = now, zone = zone).toSnapshot(phoneOk, zone)
        assertEquals(17, snap.iconCode, "head southwest")
        assertFalse(snap.iconMatched)
        assertEquals(0, snap.distanceToTurnM)
        assertEquals(23708, snap.remainingM)
        assertEquals(LocalDateTime.of(2026, 11, 2, 12, 55).toInstant(zone).toEpochMilli(), snap.etaMillis)
        assertEquals(NavStatusCode.NORMAL, snap.status)
        assertEquals("Head southwest on Main Rd", snap.instruction)
        assertEquals(now, snap.receivedAtMillis)
    }

    @Test
    fun turnUsesShortCriticalTextAndProgress() {
        val snap = reduceMaps(null, turn, null, now, zone).toSnapshot(phoneOk, zone)
        assertEquals(4, snap.iconCode, "turn right")
        assertEquals(200, snap.distanceToTurnM)
        assertEquals(1723 - 338, snap.remainingM)
        assertEquals("Turn right toward NH 48", snap.instruction)
    }

    @Test
    fun matchedIconWinsOverText() {
        val turnState = reduceMaps(null, turn, matchedIcon = 6, nowMillis = now, zone = zone)
        assertEquals(6, turnState.iconCode)
        assertTrue(turnState.iconMatched)
    }

    @Test
    fun keepsPreviousValuesWhenFieldsFailToParse() {
        val first = reduceMaps(null, turn, null, now, zone)
        val odd = MapsFields(title = "", shortCriticalText = "soon", subText = "", progress = null, progressMax = null)
        val next = reduceMaps(first, odd, null, now + 1000, zone)
        val snap = next.toSnapshot(phoneOk, zone)
        assertEquals(200, snap.distanceToTurnM)
        assertEquals(1723 - 338, snap.remainingM)
        assertEquals(4, snap.iconCode)
        assertEquals("Turn right toward NH 48", snap.instruction)
        assertEquals(first.update.eta, next.update.eta)
        assertEquals(now + 1000, snap.receivedAtMillis)
    }

    @Test
    fun unknownManeuverKeepsLastIcon() {
        val first = reduceMaps(null, turn, null, now, zone)
        val next = reduceMaps(first, turn.copy(title = "150 m · Rua Principal", shortCriticalText = null), null, now + 1000, zone)
        assertEquals(4, next.iconCode)
        assertEquals(150, next.toSnapshot(phoneOk, zone).distanceToTurnM)
        val fresh = reduceMaps(null, MapsFields(title = "Rua Principal"), null, now, zone)
        assertEquals(DEFAULT_ICON_CODE, fresh.iconCode)
    }

    @Test
    fun phoneStateDecidesStatus() {
        val state = reduceMaps(null, turn, null, now, zone)
        // GPS off always suppresses the route, even when Maps is active.
        assertEquals(NavStatusCode.GPS_OFF, state.toSnapshot(phoneOk.copy(gpsOn = false), zone).status)
        // No internet / no signal: Maps is active (update.active = true), so the cluster keeps the arrow.
        assertEquals(NavStatusCode.NORMAL, state.toSnapshot(phoneOk.copy(hasInternet = false), zone).status)
        val noSignal = phoneOk.copy(signalBars = 0, hasInternet = false)
        assertEquals(NavStatusCode.NORMAL, state.toSnapshot(noSignal, zone).status)
        assertEquals(NavStatusCode.NORMAL, state.toSnapshot(phoneOk.copy(signalBars = 0), zone).status, "Wi-Fi only")
    }

    @Test
    fun reroutingAndArrival() {
        val first = reduceMaps(null, turn, null, now, zone)
        val rerouting = reduceMaps(first, MapsFields(title = "Rerouting…"), null, now + 1000, zone)
        assertEquals(NavStatusCode.RECALCULATING, rerouting.toSnapshot(phoneOk, zone).status)
        val arrived = reduceMaps(first, MapsFields(title = "You have arrived"), null, now + 2000, zone)
        assertEquals(NavStatusCode.ARRIVED, arrived.toSnapshot(phoneOk, zone).status)
    }

    @Test
    fun logDetailCarriesRawFields() {
        val detail = turn.toLogDetail(iconHash = "00000000000000ff", iconCode = null)
        assertEquals("200 m · Turn right toward NH 48", detail["title"])
        assertEquals("1723", detail["progressMax"])
        assertEquals("none", detail["iconCode"])
        assertFalse("text" in detail)
    }
}
