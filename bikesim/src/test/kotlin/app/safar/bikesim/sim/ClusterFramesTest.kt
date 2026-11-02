package app.safar.bikesim.sim

import app.safar.protocol.ChecksumMode
import app.safar.protocol.FrameException
import app.safar.protocol.decodeCluster
import app.safar.protocol.toHex
import app.safar.protocol.validateFrame
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ClusterFramesTest {
    private val cluster = SimCluster(
        speedKmh = 87,
        odometerKm = 12345.9,
        tripAKm = 123.4,
        tripBKm = 56.7,
        fuelBars = 5,
        gear = Gear.THIRD,
        raw25 = 0x1E,
    )

    @Test
    fun `frame matches the docs layout with gear, raw byte 25 and FF filler`() {
        val frame = ClusterFrames.build(cluster, ChecksumMode.SUM)
        assertEquals(
            "a5 37 30 38 37 30 31 32 33 34 35 30 30 31 32 33 34 30 30 30 35 36 37 33 35 1e ff ff e5 7f",
            toHex(frame),
        )
    }

    @Test
    fun `protocol decoder reads every field back`() {
        val decoded = decodeCluster(ClusterFrames.build(cluster, ChecksumMode.SUM), ChecksumMode.SUM)
        assertEquals(87, decoded.speedKmh)
        assertEquals(12345, decoded.odometerKm)
        assertEquals(123.4, decoded.tripAKm)
        assertEquals(56.7, decoded.tripBKm)
        assertEquals(5, decoded.fuelBars)
        assertEquals('3'.code, decoded.unknown.b23)
        assertEquals(0x1E, decoded.unknown.b25)
        assertEquals(0xFF, decoded.unknown.b26)
    }

    @Test
    fun `neutral gear sends N`() {
        val frame = ClusterFrames.build(SimCluster(gear = Gear.NEUTRAL), ChecksumMode.SUM)
        assertEquals('N'.code, frame[23].toInt())
    }

    @Test
    fun `inverted mode frame validates only in inverted mode`() {
        val frame = ClusterFrames.build(cluster, ChecksumMode.INVERTED)
        assertTrue(validateFrame(frame, ChecksumMode.INVERTED).ok)
        assertFalse(validateFrame(frame, ChecksumMode.SUM).ok)
        assertEquals(0x1A, frame[28].toInt() and 0xFF)
    }

    @Test
    fun `wrong checksum frame fails both modes and keeps the payload`() {
        for (mode in ChecksumMode.entries) {
            val good = ClusterFrames.build(cluster, mode)
            val bad = ClusterFrames.wrongChecksum(cluster, mode)
            assertFalse(validateFrame(bad, ChecksumMode.SUM).ok)
            assertFalse(validateFrame(bad, ChecksumMode.INVERTED).ok)
            assertEquals(toHex(good.copyOfRange(0, 28)), toHex(bad.copyOfRange(0, 28)))
        }
    }

    @Test
    fun `corrupt frame breaks start, end and digits`() {
        val frame = ClusterFrames.corrupt(cluster, ChecksumMode.SUM)
        assertEquals(30, frame.size)
        assertEquals(0, frame[0].toInt())
        assertEquals(0, frame[29].toInt())
        assertEquals('?'.code, frame[2].toInt())
        assertFailsWith<FrameException> { decodeCluster(frame, ChecksumMode.SUM) }
    }

    @Test
    fun `odometer sends whole km and trips wrap at six digits`() {
        val frame = ClusterFrames.build(SimCluster(odometerKm = 999.99, tripAKm = 100000.2), ChecksumMode.SUM)
        val decoded = decodeCluster(frame, ChecksumMode.SUM)
        assertEquals(999, decoded.odometerKm)
        assertEquals(0.2, decoded.tripAKm)
    }

    @Test
    fun `out of range values are rejected`() {
        assertFailsWith<FrameException> { SimCluster(fuelBars = 7) }
        assertFailsWith<FrameException> { SimCluster(raw25 = 256) }
        assertFailsWith<FrameException> { SimCluster(speedKmh = -1) }
    }
}
