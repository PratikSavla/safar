package app.safar.sim

import app.safar.protocol.CHECKSUM_INDEX
import app.safar.protocol.ChecksumMode
import app.safar.protocol.ClusterData
import app.safar.protocol.computeChecksum
import app.safar.protocol.encodeCluster

/**
 * What the simulated cluster shows, as sent in frame '7' (docs 3.1). [byte23] is the gear character the bike
 * puts at position 23 and [byte25] the raw byte at 25; the protocol encoder writes '0' there, so they are
 * patched in and the checksum is calculated again.
 */
data class ClusterSpec(
    val speedKmh: Int = 0,
    val odometerKm: Int = 12_261,
    val tripAKm: Double = 84.6,
    val tripBKm: Double = 462.3,
    val fuelBars: Int = 4,
    val byte23: Int = 'N'.code,
    val byte25: Int = 0x1D,
) {
    fun encode(mode: ChecksumMode): ByteArray {
        val frame = encodeCluster(ClusterData(speedKmh, odometerKm, tripAKm, tripBKm, fuelBars), mode)
        frame[GEAR_INDEX] = byte23.toByte()
        frame[RAW25_INDEX] = byte25.toByte()
        frame[CHECKSUM_INDEX] = computeChecksum(frame, mode).toByte()
        return frame
    }

    companion object {
        const val GEAR_INDEX = 23
        const val RAW25_INDEX = 25
    }
}

/** Corrupt variants of a valid frame, as a noisy link could deliver them. */
object BadFrames {
    /** Checksum byte off by one. */
    fun badChecksum(frame: ByteArray): ByteArray =
        frame.copyOf().also { it[CHECKSUM_INDEX] = (it[CHECKSUM_INDEX] + 1).toByte() }

    /** Only the first 20 bytes. */
    fun truncated(frame: ByteArray): ByteArray = frame.copyOf(20)

    /** 31 bytes: a stray byte after the end byte. */
    fun tooLong(frame: ByteArray): ByteArray = frame + byteArrayOf(0x00)

    /** Start byte destroyed, checksum still matching the payload. */
    fun badStart(frame: ByteArray): ByteArray = frame.copyOf().also { it[0] = 0x00 }

    /** A letter in the speed digits, with a checksum that matches it. */
    fun nonDigitSpeed(frame: ByteArray, mode: ChecksumMode): ByteArray = frame.copyOf().also {
        it[2] = 'X'.code.toByte()
        it[CHECKSUM_INDEX] = computeChecksum(it, mode).toByte()
    }
}
