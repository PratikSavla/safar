package app.safar.ui.fuel

import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.core.SavedBike
import app.safar.data.FuelFixtures
import app.safar.data.FuelMethod
import app.safar.data.MileageSegment
import app.safar.data.OdoSource
import app.safar.data.Settings
import java.time.ZoneOffset
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FuelMappingTest {
    private val zone = ZoneOffset.UTC
    private val saved = SavedBike("SBM01A1B2C3D", "00:11:22:33:44:55", FuelFixtures.VSTROM)

    @Test
    fun `page state uses fuel stats, currency and the live bars`() {
        val bike = BikeState(link = LinkState.Connected, saved = saved, cluster = FuelFixtures.cluster(12_301, 2))
        val s = fuelUiState(FuelFixtures.FUEL_UPS, Settings(currencySymbol = "€", lowFuelAlert = false), bike, FuelFixtures.NOW, zone)
        val stats = requireNotNull(s.stats)
        assertEquals(FuelMethod.HISTORY, stats.method)
        assertEquals("€", s.currency)
        assertFalse(s.lowFuelAlert)
        assertEquals(BarsReading(2, 6, FuelFixtures.NOW, live = true), s.reading)
        assertTrue(methodLine(stats).startsWith("from your fuel-ups · avg ${jsNumber(requireNotNull(stats.avgKmpl))} km/L, last 3 fills"))
        assertEquals("calculated · fuel-ups", rangeSource(stats.method))
    }

    @Test
    fun `bars after a disconnect are the last known reading, not live`() {
        val last = FuelFixtures.cluster(12_250, 3, at = 1_000)
        val r = barsReading(BikeState(link = LinkState.Idle, saved = saved, lastCluster = last))
        assertEquals(BarsReading(3, 6, 1_000, live = false), r)
        assertEquals(BarsReading(null, 6, null, live = false), barsReading(BikeState()))
    }

    @Test
    fun `history is ordered by odometer with source badges`() {
        val rows = fuelRows(FuelFixtures.FUEL_UPS, zone)
        assertEquals(listOf(12164, 11952, 11894, 11610, 11262, 10880), rows.map { it.odometerKm })
        assertEquals(OdoBadge.BIKE, rows[0].badge)
        assertEquals(OdoBadge.EDITED, rows[1].badge)
        assertEquals(OdoBadge.TYPED, rows[3].badge)
        assertFalse(rows[2].full)
        assertEquals(712.0 / 6.7, rows[0].perLitre, 1e-9)
        assertEquals("31 Oct", rows[0].date)
        assertEquals("typed", odoBadge(OdoSource.MANUAL).label)
    }

    @Test
    fun `method lines match the prototype`() {
        val none = requireNotNull(fuelUiState(emptyList(), Settings(), BikeState(), FuelFixtures.NOW, zone).stats)
        assertEquals("no estimate yet · log a full tank to start", methodLine(none))
        assertEquals(0f, tankFraction(none))
        val bars = requireNotNull(
            fuelUiState(emptyList(), Settings(), BikeState(saved = saved, cluster = FuelFixtures.cluster(100, 3)), FuelFixtures.NOW, zone).stats,
        )
        assertEquals(FuelMethod.BARS, bars.method)
        assertEquals("rough · from fuel bars only, log a full tank to improve", methodLine(bars))
        assertEquals(0.5f, tankFraction(bars))
    }

    @Test
    fun `trend chart spans the box with two km per litre of headroom`() {
        val segs = listOf(seg(40.0), seg(44.0))
        val chart = requireNotNull(trendChart(segs, 42.0, 368f, 118f, 14f, 22f, 22f))
        assertEquals(listOf(14f, 354f), chart.xs)
        // lo = 38, hi = 46: 40 sits at 3/4 of the 74 px plot height, 44 at 1/4, the average in the middle.
        assertEquals(22f + 74f * 0.75f, chart.ys[0], 1e-3f)
        assertEquals(22f + 74f * 0.25f, chart.ys[1], 1e-3f)
        assertEquals(59f, requireNotNull(chart.avgY), 1e-3f)
        assertNull(trendChart(listOf(seg(40.0)), null, 368f, 118f, 14f, 22f, 22f))
    }

    @Test
    fun `numbers print like JavaScript`() {
        assertEquals("12", jsNumber(12.0))
        assertEquals("41.8", jsNumber(41.8))
    }

    private fun seg(kmpl: Double) = MileageSegment(0, 100, 100, 100 / kmpl, kmpl, 0)
}
