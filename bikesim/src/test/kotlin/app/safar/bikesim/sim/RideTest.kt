package app.safar.bikesim.sim

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import org.junit.Test

class RideTest {
    private fun state(speed: Int, fuel: Int = 4, since: Double = 0.0) = RideState(
        SimCluster(speedKmh = speed, odometerKm = 100.0, tripAKm = 10.0, tripBKm = 20.0, fuelBars = fuel),
        since,
    )

    @Test
    fun `one second at 36 km per h adds 10 m to odometer and both trips`() {
        val next = Ride.advance(state(36), 1.0, drainFuel = false)
        assertEquals(100.01, next.cluster.odometerKm, 1e-9)
        assertEquals(10.01, next.cluster.tripAKm, 1e-9)
        assertEquals(20.01, next.cluster.tripBKm, 1e-9)
        assertEquals(4, next.cluster.fuelBars)
    }

    @Test
    fun `an hour of seconds at 60 km per h adds 60 km`() {
        var s = state(60)
        repeat(3600) { s = Ride.advance(s, 1.0, drainFuel = false) }
        assertEquals(160.0, s.cluster.odometerKm, 1e-6)
        assertEquals(70.0, s.cluster.tripAKm, 1e-6)
    }

    @Test
    fun `standing still changes nothing`() {
        val start = state(0)
        assertSame(start, Ride.advance(start, 1.0, drainFuel = true))
    }

    @Test
    fun `fuel drops one bar per KM_PER_FUEL_BAR km and keeps the remainder`() {
        val next = Ride.advance(state(180, fuel = 4, since = 4.0), 30.0, drainFuel = true)
        // 180 km/h for 30 s = 1.5 km; 4.0 + 1.5 = 5.5 km -> one bar, 0.5 km carried.
        assertEquals(3, next.cluster.fuelBars)
        assertEquals(0.5, next.kmSinceFuelBar, 1e-9)
    }

    @Test
    fun `fuel stops at zero bars`() {
        val next = Ride.advance(state(180, fuel = 1), 3600.0, drainFuel = true)
        assertEquals(0, next.cluster.fuelBars)
        assertEquals(0.0, next.kmSinceFuelBar)
        assertEquals(280.0, next.cluster.odometerKm, 1e-6)
    }

    @Test
    fun `no drain keeps the fuel gauge`() {
        val next = Ride.advance(state(180, fuel = 2), 3600.0, drainFuel = false)
        assertEquals(2, next.cluster.fuelBars)
    }

    @Test
    fun `negative time is rejected`() {
        assertFailsWith<IllegalArgumentException> { Ride.advance(state(10), -1.0, drainFuel = false) }
    }
}
