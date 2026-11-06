package app.safar.bikesim.sim

import app.safar.protocol.FrameException

/** Byte 23 of the cluster frame. A V-Strom SX sends 'N' in neutral (docs 3.1). */
enum class Gear(val code: Char) {
    NEUTRAL('N'),
    FIRST('1'),
    SECOND('2'),
    THIRD('3'),
    FOURTH('4'),
    FIFTH('5'),
    SIXTH('6'),
    ;

    val label: String get() = code.toString()
}

/** Highest speed the controls offer. The frame itself carries 3 digits. */
const val MAX_SIM_SPEED_KMH = 180

/** Highest fuel gauge value (docs 3.1, byte 24). */
const val MAX_FUEL_BARS = 6

/**
 * What the simulated cluster shows. Distances are doubles so the ride integration can add fractions of a km
 * every second; the frame carries whole km for the odometer and tenths for the trips.
 */
data class SimCluster(
    val speedKmh: Int = 0,
    val odometerKm: Double = 12_261.0,
    val tripAKm: Double = 84.6,
    val tripBKm: Double = 462.3,
    val fuelBars: Int = 4,
    val gear: Gear = Gear.NEUTRAL,
    /** Byte 25: engine temperature, whole °C as one raw byte; 0xFF = no reading. A cold V-Strom SX sent 29, 30. */
    val engineTempC: Int = 29,
) {
    init {
        if (speedKmh !in 0..999) throw FrameException("speedKmh must be 0..999, got $speedKmh")
        if (fuelBars !in 0..MAX_FUEL_BARS) throw FrameException("fuelBars must be 0..6, got $fuelBars")
        if (engineTempC !in 0..0xFF) throw FrameException("engineTempC must be 0..255, got $engineTempC")
        if (odometerKm < 0 || tripAKm < 0 || tripBKm < 0) throw FrameException("distances must be >= 0")
    }
}
