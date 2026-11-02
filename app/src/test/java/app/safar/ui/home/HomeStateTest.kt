package app.safar.ui.home

import app.safar.core.AlertsState
import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.core.NavSnapshot
import app.safar.core.NavState
import app.safar.core.NavStatusCode
import app.safar.core.RideState
import app.safar.core.VehicleFamily
import app.safar.data.FuelMethod
import app.safar.data.ServiceIcon
import app.safar.data.ServiceItem
import app.safar.data.Settings
import app.safar.data.Trip
import app.safar.data.fuelStats
import app.safar.ui.TEST_BIKE
import app.safar.ui.components.StampTone
import app.safar.ui.components.ValueOrigin
import app.safar.ui.format.ValueSource
import app.safar.ui.testReading
import app.safar.ui.testSaved
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeStateTest {
    @Test
    fun connectionCardWordsFollowTheLink() {
        val saved = BikeState(saved = testSaved)
        assertEquals("Not connected" to "Turn the ignition on, then tap Connect.", connUi(saved).let { it.title to it.subtitle })
        assertEquals("offline", connUi(saved).stamp)
        assertEquals("Not set up yet" to "new", connUi(BikeState()).let { it.title to it.stamp })
        val linked = connUi(saved.copy(link = LinkState.Connected, mtu = 65))
        assertEquals(ConnKind.CONNECTED, linked.kind)
        assertEquals("Connected" to "connected", linked.title to linked.stamp)
        assertEquals("Ready to ride. Turns, calls and the time go to the display.", linked.subtitle)
        assertEquals(StampTone.MOSS, linked.tone)
        assertEquals(65, linked.mtu)
        val connecting = connUi(BikeState(link = LinkState.Connecting("SBS21A1B2C3D")))
        assertEquals("Connecting to your Avenis", connecting.title)
        assertEquals("This takes a few seconds.", connecting.subtitle)
        assertEquals(VehicleFamily.SCOOTER, connecting.family)
        val retry = connUi(saved.copy(link = LinkState.Reconnecting(3, 9_000)))
        assertEquals("Bike is off or out of range" to "waiting", retry.title to retry.stamp)
        assertEquals("We’ll reconnect as soon as it’s back.", retry.subtitle)
        assertEquals(3, retry.attempt)
        assertEquals(9_000L, retry.nextAttemptAt)
        assertEquals(ConnKind.BLUETOOTH_OFF, connUi(saved.copy(link = LinkState.BluetoothOff)).kind)
    }

    private fun stats(bike: BikeState) = fuelStats(emptyList(), Settings(), bike.saved?.model, bike.cluster ?: bike.lastCluster, 0)

    @Test
    fun ledgerIsLiveOnlyWhileConnected() {
        val live = BikeState(link = LinkState.Connected, saved = testSaved, cluster = testReading(at = 5_000))
        val l = ledgerUi(live, stats(live))!! // test: a reading is present
        assertTrue(l.live)
        assertEquals(ValueOrigin(ValueSource.BIKE, 5_000, connected = true), l.bikeOrigin)
        assertEquals(12_345, l.odometerKm)
        assertEquals("N", l.gear)
        val gone = BikeState(saved = testSaved, lastCluster = testReading(at = 2_000))
        val g = ledgerUi(gone, stats(gone))!! // test: a last-known reading is present
        assertFalse(g.live)
        assertEquals(ValueOrigin(ValueSource.BIKE, 2_000, connected = false), g.bikeOrigin)
        assertEquals(2_000L, g.readingAt)
        assertNull(ledgerUi(BikeState(), stats(BikeState())))
    }

    @Test
    fun lowFuelWarns() {
        val one = BikeState(link = LinkState.Connected, saved = testSaved, cluster = testReading(bars = 1))
        assertEquals(FuelWarning.ONE_BAR, ledgerUi(one, stats(one))?.fuelWarning)
        val zero = one.copy(cluster = testReading(bars = 0))
        assertEquals("Tank reads empty. Refuel now.", ledgerUi(zero, stats(zero))?.warningText)
        val first = one.copy(cluster = testReading(bars = null))
        assertNull(ledgerUi(first, stats(first))?.fuelWarning)
    }

    @Test
    fun speedIsLiveOnlyWhileMoving() {
        assertEquals(SpeedUi(42, true, null), speedUi(RideState(speedKmh = 41.6f, moving = true, lastFixAtMillis = 9)))
        assertEquals(SpeedUi(0, false, 9L), speedUi(RideState(speedKmh = 0.2f, moving = false, lastFixAtMillis = 9)))
        assertEquals(SpeedUi(null, false, null), speedUi(RideState()))
    }

    @Test
    fun navStripShowsWaitingStatusOrDistance() {
        assertNull(navStripUi(NavState()))
        assertTrue(navStripUi(NavState(active = true, waiting = true))!!.waiting) // test: active nav
        val snap = NavSnapshot(2, true, 850, 0, 11_200, NavStatusCode.RECALCULATING, "Main Rd", 7)
        val s = navStripUi(NavState(active = true, destinationLabel = "NH 48", snapshot = snap))!! // test: active nav
        assertEquals("Re-routing", s.statusWord)
        assertEquals(850, s.distanceM)
        assertEquals("NH 48", s.destination)
    }

    @Test
    fun backoffScheduleMarksPastAndCurrentSteps() {
        val steps = backoffSteps(3)
        assertEquals(listOf("2 s", "5 s", "10 s", "30 s"), steps.map { it.label })
        assertEquals(listOf(true, true, false, false), steps.map { it.past })
        assertEquals(2, steps.indexOfFirst { it.now })
        assertEquals(3, backoffSteps(9).indexOfFirst { it.now })
        assertEquals(5, secondsLeft(10_000, 5_200))
        assertEquals(0, secondsLeft(1_000, 5_000))
        assertEquals(0.5f, waitFraction(1, 2_000, 1_000))
    }

    @Test
    fun garageHintsCarryLiveStatus() {
        val bike = BikeState(link = LinkState.Connected, saved = testSaved, cluster = testReading())
        val oil = ServiceItem(1, "Engine oil", 3000, 9_000, null, ServiceIcon.OIL)
        val chain = ServiceItem(2, "Chain clean & lube", 500, 12_000, null, ServiceIcon.CHAIN)
        val inputs = GarageInputs(stats(bike), 12_345, listOf(oil, chain), null, emptyList(), 2, null, 3, LinkState.Connected, false, 40)
        val tiles = garageTiles(inputs.copy(developerMode = true)).associateBy { it.key }
        assertEquals("1 overdue", tiles.getValue("service").hint)
        assertTrue(tiles.getValue("service").warn)
        assertEquals("nothing saved", tiles.getValue("parked").hint)
        assertEquals("no rides yet", tiles.getValue("trips").hint)
        assertEquals("2 places", tiles.getValue("navigate").hint)
        assertEquals("3 new", tiles.getValue("alerts").hint)
        assertEquals("40 in memory", tiles.getValue("log").hint)
        assertEquals("firmware, serial", tiles.getValue("bike").hint)
        assertEquals(9, tiles.size)
        assertEquals(null, garageTiles(inputs).firstOrNull { it.key == "log" }, "Debug log is a Developer mode tile")
        assertEquals("next in 155 km", garageTiles(inputs.copy(services = listOf(chain))).first { it.key == "service" }.hint)
    }

    @Test
    fun rangeSaysSomethingOnlyWhenItIsAGuessOrMissing() {
        assertNull(rangeNote(FuelMethod.HISTORY))
        assertEquals("Rough guess", rangeNote(FuelMethod.BARS))
        assertEquals("Log a full tank", rangeNote(FuelMethod.NONE))
        assertEquals("calculated · fuel-ups", rangeSource(FuelMethod.HISTORY))
        assertEquals("1 ride" to "12 rides", ridesLabel(1) to ridesLabel(12))
    }

    @Test
    fun tripsTileSkipsTheTripStillBeingRecorded() {
        val done = Trip(1, "Ride", 0, 60_000, 42.3, 88f, 40f, null, null, null, null)
        val open = Trip(2, "Ride", 120_000, null, 0.0, 0f, 0f, null, null, null, null)
        fun hint(trips: List<Trip>) = garageTiles(
            GarageInputs(stats(BikeState()), null, emptyList(), null, trips, 0, null, 0, LinkState.Idle, false, 0),
        ).first { it.key == "trips" }.hint
        assertEquals("last 42.3 km", hint(listOf(open, done)))
        assertEquals("recording", hint(listOf(open)))
    }

    @Test
    fun homeUiCountsRidesAndNamesTheModel() {
        val ui = homeUi(
            HomeLive(BikeState(saved = testSaved), NavState(), AlertsState(), Settings(riderName = " Alex ")),
            HomeStored(emptyList(), emptyList(), null, emptyList(), 0),
            logCount = 0, nowMillis = 0,
        )
        assertEquals("Alex", ui.riderName)
        assertEquals("V-Strom SX", ui.modelName)
        assertEquals(0, ui.rideCount)
        assertEquals(TEST_BIKE, ui.conn.bikeName)
    }
}
