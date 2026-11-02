package app.safar.protocol

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Port of docs/helper/tests/protocol.test.ts with the same expected bytes. */
class ProtocolTest {
    private val clusterHex = "a5 37 30 38 37 30 31 32 33 34 35 30 30 31 32 33 34 30 30 30 35 36 37 30 35 30 30 30 56 7f"

    @Test
    fun `cluster frame decodes to the expected values`() {
        val data = decodeCluster(fromHex(clusterHex), ChecksumMode.SUM)
        assertEquals(87, data.speedKmh)
        assertEquals(12345, data.odometerKm)
        assertEquals(123.4, data.tripAKm)
        assertEquals(56.7, data.tripBKm)
        assertEquals(5, data.fuelBars)
    }

    @Test
    fun `cluster encode and decode give the same values`() {
        val input = ClusterData(speedKmh = 112, odometerKm = 4021, tripAKm = 0.3, tripBKm = 999.9, fuelBars = 1)
        val frame = encodeCluster(input, ChecksumMode.INVERTED)
        val out = decodeCluster(frame, ChecksumMode.INVERTED)
        assertEquals(input, out.copy(unknown = input.unknown))
    }

    @Test
    fun `cluster frame with a bad checksum is rejected`() {
        val frame = fromHex(clusterHex)
        frame[28] = (frame[28].toInt() xor 0x01).toByte()
        assertEquals(false, validateFrame(frame, ChecksumMode.SUM).ok)
        val error = assertFailsWith<FrameException> { decodeCluster(frame, ChecksumMode.SUM) }
        assertEquals(true, error.message?.contains("checksum"))
    }

    @Test
    fun `inverted checksum is 0xFF minus the plain sum`() {
        val frame = fromHex(clusterHex)
        assertEquals(0xFF - computeChecksum(frame, ChecksumMode.SUM), computeChecksum(frame, ChecksumMode.INVERTED))
    }

    @Test
    fun `fuel byte outside 1 to 6 gives 0 bars`() {
        val frame = encodeCluster(ClusterData(0, 0, 0.0, 0.0, 0), ChecksumMode.SUM)
        assertEquals(0, decodeCluster(frame, ChecksumMode.SUM).fuelBars)
    }

    @Test
    fun `vehicle detection from the BLE name`() {
        assertEquals(true, isSuzukiCluster("SBM01A1B2C3D"))
        assertEquals(false, isSuzukiCluster("SBM01"))
        assertEquals(false, isSuzukiCluster("MyHeadphones"))
        assertEquals("V-STROM SX", identifyVehicle("SBM01A1B2C3D")?.name)
        assertEquals(ChecksumMode.SUM, checksumModeFor("SBM01A1B2C3D"))
        assertEquals(ChecksumMode.INVERTED, checksumModeFor("SAS01A1B2C3D"))
        assertNull(identifyVehicle("SBX99A1B2C3D"))
    }
}
