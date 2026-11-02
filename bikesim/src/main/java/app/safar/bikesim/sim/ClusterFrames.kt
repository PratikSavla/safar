package app.safar.bikesim.sim

import app.safar.protocol.CHECKSUM_INDEX
import app.safar.protocol.CLUSTER_TYPE
import app.safar.protocol.ChecksumMode
import app.safar.protocol.FILLER_BYTE
import app.safar.protocol.FRAME_LENGTH
import app.safar.protocol.buildFrame
import app.safar.protocol.computeChecksum
import app.safar.protocol.digits
import kotlin.math.floor

/**
 * Cluster frames ('7') as the bike sends them (docs 3.1). Unlike `encodeCluster` in :protocol, byte 23 carries
 * the gear, byte 25 a settable raw value and bytes 26-27 the 0xFF filler of a real V-Strom SX capture.
 */
object ClusterFrames {
    private const val RAW25_INDEX = 25
    private const val SPEED_START = 2
    private const val ODOMETER_END = 11
    private const val BAD_DIGIT = '?'

    fun build(cluster: SimCluster, mode: ChecksumMode): ByteArray =
        frame(cluster, mode, digits(cluster.speedKmh, 3), digits(floor(cluster.odometerKm).toLong(), 6))

    private fun frame(cluster: SimCluster, mode: ChecksumMode, speed: String, odometer: String): ByteArray {
        val body = CLUSTER_TYPE + speed + odometer +
            digits(Math.round(cluster.tripAKm * 10), 6) +
            digits(Math.round(cluster.tripBKm * 10), 6) +
            cluster.gear.code +
            cluster.fuelBars.toString() +
            "\u0000\u0000\u0000" // bytes 25..27, set below
        val overrides = mapOf(RAW25_INDEX to cluster.raw25, 26 to FILLER_BYTE, 27 to FILLER_BYTE)
        return buildFrame(body, mode, overrides)
    }

    /**
     * A correct frame with a checksum byte that matches neither checksum mode, so a receiver rejects it
     * whatever mode it uses.
     */
    fun wrongChecksum(cluster: SimCluster, mode: ChecksumMode): ByteArray {
        val frame = build(cluster, mode)
        val sum = computeChecksum(frame, ChecksumMode.SUM)
        val inverted = computeChecksum(frame, ChecksumMode.INVERTED)
        var bad = (sum + 1) and 0xFF
        while (bad == sum || bad == inverted) bad = (bad + 1) and 0xFF
        frame[CHECKSUM_INDEX] = bad.toByte()
        return frame
    }

    /**
     * A 30-byte cluster frame with a broken start byte, broken end byte and non-digit speed and odometer fields.
     * The checksum byte is left as computed for the original frame. A receiver rejects it at the framing check,
     * before it reads any field.
     */
    fun badFraming(cluster: SimCluster, mode: ChecksumMode): ByteArray {
        val frame = build(cluster, mode)
        frame[0] = 0x00
        frame[FRAME_LENGTH - 1] = 0x00
        for (i in SPEED_START until ODOMETER_END) frame[i] = BAD_DIGIT.code.toByte()
        return frame
    }

    /**
     * A cluster frame with valid start byte, end byte and checksum whose speed and odometer fields are not
     * digits, so it passes the framing check and fails in the field decoder.
     */
    fun badFields(cluster: SimCluster, mode: ChecksumMode): ByteArray =
        frame(cluster, mode, BAD_DIGIT.toString().repeat(3), BAD_DIGIT.toString().repeat(6))
}
