package app.safar.protocol

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Port of docs/helper/tests/protocol.test.ts with the same expected bytes. */
class ProtocolTest {
    private val clusterHex = "a5 37 30 38 37 30 31 32 33 34 35 30 30 31 32 33 34 30 30 30 35 36 37 30 35 30 30 30 56 7f"

    @Test
    fun `inverted checksum is 0xFF minus the plain sum`() {
        val frame = fromHex(clusterHex)
        assertEquals(0xFF - computeChecksum(frame, ChecksumMode.SUM), computeChecksum(frame, ChecksumMode.INVERTED))
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
