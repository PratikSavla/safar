package app.safar.data

import app.safar.core.BikeCatalog
import app.safar.core.ClusterReading
import java.time.Instant

/** Synthetic fixtures mirroring `FUEL_UPS` in ux/shared/data.js. */
object FuelFixtures {
    /** `day(d)` in data.js: 2026-11-02 06:30 UTC minus [d] days. */
    fun day(d: Long): Long = Instant.parse("2026-11-02T06:30:00Z").minusSeconds(d * 86_400).toEpochMilli()

    val NOW: Long = Instant.parse("2026-11-02T12:00:00Z").toEpochMilli()

    /** V-Strom SX from the synthetic BLE name used across the tests. */
    val VSTROM = BikeCatalog.forBleName("SBM01A1B2C3D")

    val FUEL_UPS = listOf(
        FuelUp(1, day(30), 10880, 11.2, 1176.0, true, OdoSource.BIKE, 0),
        FuelUp(2, day(22), 11262, 11.4, 1197.0, true, OdoSource.BIKE, 1),
        FuelUp(3, day(12), 11610, 10.6, 1118.0, true, OdoSource.MANUAL, null),
        FuelUp(4, day(8), 11894, 4.0, 424.0, false, OdoSource.BIKE, 3),
        FuelUp(5, day(4), 11952, 6.4, 678.0, true, OdoSource.EDITED, 2),
        FuelUp(6, day(2), 12164, 6.7, 712.0, true, OdoSource.BIKE, 1),
    )

    fun cluster(odo: Int, bars: Int?, at: Long = NOW) = ClusterReading(
        speedKmh = 0, odometerKm = odo, tripAKm = 84.6, tripBKm = 462.3, fuelBars = bars,
        gear = 'N', raw23 = 0x4E, raw25 = 29, receivedAtMillis = at,
    )
}
