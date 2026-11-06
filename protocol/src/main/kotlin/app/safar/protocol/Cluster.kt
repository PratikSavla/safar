package app.safar.protocol

/*
 * Bike -> app cluster data (message type '7', 0x37). Port of docs/helper/src/cluster.ts.
 * The bike sends this frame as a notification on the RX characteristic.
 */

const val CLUSTER_TYPE = '7'

/** Position of the engine temperature byte in the cluster frame. */
const val ENGINE_TEMP_INDEX = 25

/** Engine temperature byte value that means "no reading" (the 0xFF filler). */
const val NO_ENGINE_TEMP = 0xFF

/**
 * Engine temperature in °C from cluster byte 25: one raw binary byte, whole degrees (a V-Strom SX sent 29, then
 * 30, from a cold engine). Null for [NO_ENGINE_TEMP] or a value outside 0..254.
 */
fun engineTempC(raw: Int): Int? = raw.takeIf { it in 0 until NO_ENGINE_TEMP }

/**
 * Raw bytes the original app does not use (positions 23, 25, 26, 27). [b25] is the engine temperature
 * (see [engineTempC]); the others are kept for research.
 */
data class ClusterRawBytes(val b23: Int = 0, val b25: Int = 0, val b26: Int = 0, val b27: Int = 0)

data class ClusterData(
    /** Vehicle speed in km/h. The original app keeps the highest value as "top speed". */
    val speedKmh: Int,
    /** Odometer in km (whole km). */
    val odometerKm: Int,
    /** Trip A in km, one decimal place. */
    val tripAKm: Double,
    /** Trip B in km, one decimal place. */
    val tripBKm: Double,
    /** Fuel gauge bars, 0..6. 0 means empty or unknown. */
    val fuelBars: Int,
    /** Bytes 23, 25, 26, 27. [encodeCluster] ignores this field. */
    val unknown: ClusterRawBytes = ClusterRawBytes(),
) {
    /** Engine temperature in °C from byte 25, null when the cluster sends no reading. */
    val engineTempC: Int? get() = engineTempC(unknown.b25)
}

private val ASCII_DIGITS = Regex("^[0-9]+$")

fun decodeCluster(frame: ByteArray, mode: ChecksumMode): ClusterData {
    val check = validateFrame(frame, mode)
    if (!check.ok) throw FrameException("invalid cluster frame: ${check.reason}")
    if (messageType(frame) != CLUSTER_TYPE) {
        throw FrameException("not a cluster frame: type \"${messageType(frame)}\"")
    }
    val fuelChar = frame.u8(24).toChar()
    return ClusterData(
        speedKmh = parseDigits(frame, 2, 5, "speed"),
        odometerKm = parseDigits(frame, 5, 11, "odometer"),
        tripAKm = parseDigits(frame, 11, 17, "trip A") / 10.0,
        tripBKm = parseDigits(frame, 17, 23, "trip B") / 10.0,
        fuelBars = if (fuelChar in '1'..'6') fuelChar - '0' else 0,
        unknown = ClusterRawBytes(frame.u8(23), frame.u8(25), frame.u8(26), frame.u8(27)),
    )
}

/** Build a cluster frame. Use it to simulate the bike in tests or in a mock peripheral. */
fun encodeCluster(data: ClusterData, mode: ChecksumMode): ByteArray {
    if (data.fuelBars !in 0..6) throw FrameException("fuelBars must be 0..6, got ${data.fuelBars}")
    val body = CLUSTER_TYPE +
        digits(data.speedKmh, 3) +
        digits(data.odometerKm, 6) +
        digits(Math.round(data.tripAKm * 10), 6) +
        digits(Math.round(data.tripBKm * 10), 6) +
        "0" + // position 23, unused
        data.fuelBars.toString() +
        "000" // positions 25..27, unused
    return buildFrame(body, mode)
}

private fun parseDigits(frame: ByteArray, start: Int, end: Int, field: String): Int {
    val text = ascii(frame, start, end)
    if (!ASCII_DIGITS.matches(text)) throw FrameException("$field is not ASCII digits: \"$text\"")
    return text.toInt()
}
