package app.safar.ui.widget

import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.core.NavSnapshot
import app.safar.core.NavState
import app.safar.core.NavStatusCode
import app.safar.core.SavedBike
import app.safar.data.FuelFixtures
import app.safar.data.Settings
import java.time.ZoneOffset
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WidgetMappingTest {
    private val zone = ZoneOffset.UTC
    private val saved = SavedBike("SBM01A1B2C3D", "00:11:22:33:44:55", FuelFixtures.VSTROM)

    private fun nav(code: Int, metres: Int, status: NavStatusCode = NavStatusCode.NORMAL) = NavState(
        active = true, destinationLabel = "Main Rd",
        snapshot = NavSnapshot(code, true, metres, 0, 5_000, status, "Turn left onto Main Rd", 0),
    )

    @Test
    fun `connected bike fills odometer, trip, bars, range and a clock-time footer`() {
        val bike = BikeState(link = LinkState.Connected, saved = saved, cluster = FuelFixtures.cluster(12_301, 2))
        val s = widgetSnapshot(bike, NavState(), FuelFixtures.FUEL_UPS, Settings(), FuelFixtures.NOW, zone)
        assertEquals("V-Strom SX", s.model)
        assertEquals("linked" to WidgetTone.MOSS, s.link to s.tone)
        assertEquals("12,301", s.odometer)
        assertEquals("84.6", s.tripA)
        assertEquals(2, s.bars)
        assertEquals(6, s.barsMax)
        assertEquals("fuel-ups", s.rangeSource)
        assertEquals("bike · 12:00", s.footer)
        assertNull(s.turn)
    }

    @Test
    fun `no bike shows placeholders`() {
        val s = widgetSnapshot(BikeState(), NavState(), emptyList(), Settings(), FuelFixtures.NOW, zone)
        assertEquals(WidgetSnapshot.Empty, s)
    }

    @Test
    fun `a disconnected bike reads as last seen`() {
        val bike = BikeState(link = LinkState.Reconnecting(2, 0), saved = saved, lastCluster = FuelFixtures.cluster(12_250, 3))
        val s = widgetSnapshot(bike, NavState(), emptyList(), Settings(), FuelFixtures.NOW, zone)
        assertEquals("reconnecting" to WidgetTone.AMBER, s.link to s.tone)
        assertEquals("last seen on bike · 12:00", s.footer)
        assertEquals("rough · bars", s.rangeSource)
    }

    @Test
    fun `next turn while navigating`() {
        assertEquals(WidgetTurn(1, "Turn left", "850 m"), widgetTurn(nav(1, 846)))
        assertEquals("Arrived", widgetTurn(nav(45, 0, NavStatusCode.ARRIVED))?.text)
        assertEquals("Waiting for Google Maps…", widgetTurn(NavState(active = true, waiting = true))?.text)
        assertNull(widgetTurn(NavState()))
    }

    @Test
    fun `link words and bar heights`() {
        assertEquals("bluetooth off" to WidgetTone.RUST, linkWord(LinkState.BluetoothOff))
        assertEquals("connecting" to WidgetTone.SLATE, linkWord(LinkState.Connecting("SBM01A1B2C3D")))
        val h = widgetBarHeights(5, 30f)
        assertEquals(5, h.size)
        assertEquals(30f, h.last(), 1e-4f)
        assertEquals(30f * (0.45f + 0.55f / 5), h.first(), 1e-4f)
    }
}
