package app.safar.bikesim.sim

/** Distance the simulated bike rides on one fuel bar when auto drain is on. Short, so a test ride shows it. */
const val KM_PER_FUEL_BAR = 5.0

/** The cluster while riding, plus the distance ridden since the last fuel bar dropped. */
data class RideState(val cluster: SimCluster, val kmSinceFuelBar: Double = 0.0)

/** Odometer, trip and fuel integration for the "ride" toggle. Pure, so the JVM tests cover it. */
object Ride {
    /**
     * Rides [seconds] at the current speed: odometer, trip A and trip B grow by the same distance. With
     * [drainFuel], one bar drops every [KM_PER_FUEL_BAR] km until the gauge reads 0.
     */
    fun advance(state: RideState, seconds: Double, drainFuel: Boolean): RideState {
        require(seconds >= 0) { "seconds must be >= 0, got $seconds" }
        val c = state.cluster
        val km = c.speedKmh * seconds / SECONDS_PER_HOUR
        if (km == 0.0) return state
        val moved = c.copy(odometerKm = c.odometerKm + km, tripAKm = c.tripAKm + km, tripBKm = c.tripBKm + km)
        if (!drainFuel) return RideState(moved, state.kmSinceFuelBar)
        var bars = moved.fuelBars
        var since = state.kmSinceFuelBar + km
        while (since >= KM_PER_FUEL_BAR && bars > 0) {
            bars -= 1
            since -= KM_PER_FUEL_BAR
        }
        if (bars == 0) since = 0.0
        return RideState(moved.copy(fuelBars = bars), since)
    }

    private const val SECONDS_PER_HOUR = 3600.0
}
