package app.safar.ui.ride

import app.safar.core.ActiveCall
import app.safar.core.AlertSource
import app.safar.core.AlertsState
import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.core.NavSnapshot
import app.safar.core.NavState
import app.safar.core.NavStatusCode
import app.safar.core.RideState
import app.safar.data.Settings
import app.safar.ui.components.ValueOrigin
import app.safar.ui.format.ValueSource
import app.safar.ui.testReading
import app.safar.ui.testSaved
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RideStateTest {
    private val bike = BikeState(link = LinkState.Connected, saved = testSaved, cluster = testReading(bars = 3, tempC = 84))

    private fun snap(status: NavStatusCode, icon: Int = 2, m: Int = 850) = NavSnapshot(icon, true, m, 0, 11_200, status, "Main Rd", 1)

    @Test
    fun speedAlertTurnsTheNumberRust() {
        val s = Settings(speedAlertOn = true, speedAlertKmh = 60)
        assertTrue(rideUi(bike, RideState(speedKmh = 64.6f), NavState(), AlertsState(), s).overLimit)
        assertFalse(rideUi(bike, RideState(speedKmh = 59.4f), NavState(), AlertsState(), s).overLimit)
        assertFalse(rideUi(bike, RideState(speedKmh = 90f), NavState(), AlertsState(), Settings()).overLimit)
    }

    @Test
    fun bikeValuesComeFromTheCluster() {
        val ui = rideUi(bike, RideState(), NavState(), AlertsState(), Settings())
        assertEquals("84", ui.engineTemp)
        assertEquals(3, ui.fuelBars)
        assertEquals(6, ui.fuelMax)
        assertNull(ui.linkNote)
        assertNull(ui.speedKmh)
        assertEquals("waiting for your bike", rideUi(bike.copy(link = LinkState.Reconnecting(1, 0)), RideState(), NavState(), AlertsState(), Settings()).linkNote)
        assertEquals("bike not connected", rideUi(BikeState(), RideState(), NavState(), AlertsState(), Settings()).linkNote)
        assertEquals("bluetooth off", linkNote(LinkState.BluetoothOff))
        assertEquals("looking for your bike", linkNote(LinkState.Searching))
    }

    @Test
    fun bikeValuesSayWhenTheyAreLastKnown() {
        val live = rideUi(bike, RideState(), NavState(), AlertsState(), Settings())
        assertTrue(live.bikeLive)
        assertEquals(ValueOrigin(ValueSource.BIKE, 1_000L, connected = true), live.bikeOrigin)
        assertEquals(1_000L, live.readingAt)
        val dropped = BikeState(link = LinkState.Reconnecting(1, 0), saved = testSaved, lastCluster = testReading(at = 500L, tempC = 61))
        val stale = rideUi(dropped, RideState(), NavState(), AlertsState(), Settings())
        assertFalse(stale.bikeLive)
        assertEquals(ValueOrigin(ValueSource.BIKE, 500L, connected = false), stale.bikeOrigin)
        assertEquals("61", stale.engineTemp)
        assertEquals(500L, stale.readingAt)
        val none = rideUi(BikeState(), RideState(), NavState(), AlertsState(), Settings())
        assertNull(none.readingAt)
        assertEquals(ValueOrigin(ValueSource.BIKE, null, connected = false), none.bikeOrigin)
        // Connected with no frame yet: "Waiting for the bike".
        val first = rideUi(BikeState(link = LinkState.Connected, saved = testSaved), RideState(), NavState(), AlertsState(), Settings())
        assertEquals(ValueOrigin(ValueSource.BIKE, null, connected = true), first.bikeOrigin)
    }

    @Test
    fun turnBlockFollowsTheNavigationState() {
        assertNull(turnUi(NavState()))
        assertEquals(TurnUi.Waiting, turnUi(NavState(active = true, waiting = true)))
        assertEquals(TurnUi.Turn(2, "850", "m"), turnUi(NavState(active = true, snapshot = snap(NavStatusCode.NORMAL))))
        assertEquals(TurnUi.Turn(2, "1.2", "km"), turnUi(NavState(active = true, snapshot = snap(NavStatusCode.VIA_POINT, m = 1_234))))
        assertEquals(TurnUi.Status("Arrived", 9, true), turnUi(NavState(active = true, snapshot = snap(NavStatusCode.ARRIVED))))
        assertEquals(TurnUi.Status("GPS off", 46, false), turnUi(NavState(active = true, snapshot = snap(NavStatusCode.GPS_OFF))))
        assertEquals(TurnUi.Turn(40, "850", "m"), turnUi(NavState(active = true, snapshot = snap(NavStatusCode.NORMAL, icon = 99))))
    }

    @Test
    fun callLabelSaysWhereTheCallShows() {
        val ringing = AlertsState(call = ActiveCall("Alex", AlertSource.PHONE, false, 0))
        assertEquals("ringing · on your bike", rideUi(bike, RideState(), NavState(), ringing, Settings()).call?.label)
        assertEquals("ringing", rideUi(bike, RideState(), NavState(), ringing, Settings(alertCalls = false)).call?.label)
        val answered = AlertsState(call = ActiveCall("Rider", AlertSource.WHATSAPP, true, 0))
        val call = rideUi(bike, RideState(), NavState(), answered, Settings()).call
        assertEquals("on call", call?.label)
        assertEquals(true, call?.answered)
    }
}
