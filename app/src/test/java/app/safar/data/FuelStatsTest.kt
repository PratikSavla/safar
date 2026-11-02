package app.safar.data

import app.safar.data.FuelFixtures.FUEL_UPS
import app.safar.data.FuelFixtures.NOW
import app.safar.data.FuelFixtures.VSTROM
import app.safar.data.FuelFixtures.cluster
import app.safar.data.FuelFixtures.day
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Expected values come from running ux/shared/fuel.js with node on the same seed. */
class FuelStatsTest {
    private val settings = Settings()

    @Test
    fun segmentsMatchJs() {
        val segs = mileageSegments(FUEL_UPS)
        assertEquals(listOf(10880 to 11262, 11262 to 11610, 11610 to 11952, 11952 to 12164), segs.map { it.fromOdo to it.toOdo })
        assertEquals(listOf(382, 348, 342, 212), segs.map { it.km })
        assertEquals(listOf(11.4, 10.6, 10.4, 6.7), segs.map { it.litres })
        assertEquals(listOf(33.5, 32.8, 32.9, 31.6), segs.map { it.kmpl })
        assertEquals(day(2), segs.last().atMillis)
    }

    @Test
    fun connectedWithFourBars() {
        val s = fuelStats(FUEL_UPS, settings, VSTROM, cluster(12261, 4), NOW)
        assertEquals(12.0, s.tank)
        assertEquals(12261, s.odoNow)
        assertEquals(32.6, s.avgKmpl)
        assertEquals(31.6, s.lastKmpl)
        assertEquals(32.8, s.lifetimeKmpl)
        assertEquals(FuelMethod.HISTORY, s.method)
        assertEquals(9.0, s.remainingL)
        assertEquals(293L, s.rangeKm)
        assertEquals(97, s.sinceFullKm)
        assertEquals(6L, s.lastFull?.id)
        assertEquals(4, s.bars)
        assertEquals(6, s.barsMax)
        assertEquals(8.0, s.barsLitres)
        assertFalse(s.gaugeDisagrees)
        assertEquals(3.58, s.costPerKm)
        assertEquals(50.3, s.spentL)
        assertEquals(5305L, s.spentMoney)
        assertEquals(MonthFuel(5, 39.1, 4129), s.month)
        assertEquals(106.27, s.lastPerLitre)
    }

    @Test
    fun noClusterUsesLastFuelUpOdometer() {
        val s = fuelStats(FUEL_UPS, settings, VSTROM, null, NOW)
        assertEquals(12164, s.odoNow)
        assertEquals(12.0, s.remainingL)
        assertEquals(391L, s.rangeKm)
        assertEquals(0, s.sinceFullKm)
        assertNull(s.bars)
        assertNull(s.barsLitres)
        assertFalse(s.gaugeDisagrees)
    }

    @Test
    fun gaugeDisagreesWhenBarsAreFarOff() {
        val s = fuelStats(FUEL_UPS, settings, VSTROM, cluster(12261, 1), NOW)
        assertEquals(2.0, s.barsLitres)
        assertEquals(9.0, s.remainingL)
        assertTrue(s.gaugeDisagrees)
    }

    @Test
    fun singleFullFillFallsBackToBars() {
        val s = fuelStats(FUEL_UPS.take(1), Settings(tankLitres = 15.0), VSTROM, cluster(11000, 3), NOW)
        assertEquals(15.0, s.tank)
        assertEquals(FuelMethod.BARS, s.method)
        assertEquals(7.5, s.remainingL)
        assertNull(s.rangeKm)
        assertNull(s.sinceFullKm)
        assertNull(s.costPerKm)
        assertEquals(11.2, s.spentL)
        assertEquals(1176L, s.spentMoney)
        assertEquals(MonthFuel(0, 0.0, 0), s.month)
        assertEquals(105.0, s.lastPerLitre)
    }

    @Test
    fun emptyHistoryHasNothing() {
        val s = fuelStats(emptyList(), settings, null, null, NOW)
        assertEquals(12.0, s.tank)
        assertNull(s.odoNow)
        assertEquals(FuelMethod.NONE, s.method)
        assertNull(s.remainingL)
        assertNull(s.rangeKm)
        assertNull(s.lastFull)
        assertEquals(6, s.barsMax)
        assertEquals(0.0, s.spentL)
        assertEquals(0L, s.spentMoney)
        assertNull(s.lastPerLitre)
    }

    @Test
    fun topUpAfterLastFullAddsLitres() {
        val topUp = FuelUp(7, day(1), 12300, 2.5, 265.0, false, OdoSource.BIKE)
        val s = fuelStats(FUEL_UPS + topUp, settings, VSTROM, cluster(12400, 3), NOW)
        assertEquals(7.3, s.remainingL)
        assertEquals(238L, s.rangeKm)
        assertEquals(236, s.sinceFullKm)
        assertEquals(6.0, s.barsLitres)
        assertEquals(3.74, s.costPerKm)
        assertEquals(52.8, s.spentL)
        assertEquals(5570L, s.spentMoney)
        assertEquals(MonthFuel(6, 41.6, 4394), s.month)
        assertEquals(106.0, s.lastPerLitre)
    }

    @Test
    fun tankPrefersOverrideThenModel() {
        assertEquals(9.5, tankLitres(Settings(tankLitres = 9.5), VSTROM))
        assertEquals(12.0, tankLitres(settings, VSTROM))
        assertEquals(12.0, tankLitres(settings, null))
    }
}
