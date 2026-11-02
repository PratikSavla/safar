package app.safar.protocol

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Cluster edge cases beyond the TypeScript tests. */
class ClusterTest {
    private val clusterHex = "a5 37 30 38 37 30 31 32 33 34 35 30 30 31 32 33 34 30 30 30 35 36 37 30 35 30 30 30 56 7f"

    @Test
    fun `encoder gives the documented example bytes`() {
        val frame = encodeCluster(ClusterData(87, 12345, 123.4, 56.7, 5), ChecksumMode.SUM)
        assertContentEquals(fromHex(clusterHex), frame)
    }

    @Test
    fun `raw bytes 23, 25, 26 and 27 are kept`() {
        val data = decodeCluster(fromHex(clusterHex), ChecksumMode.SUM)
        assertEquals(ClusterRawBytes(0x30, 0x30, 0x30, 0x30), data.unknown)
    }

    @Test
    fun `other message types are rejected`() {
        val frame = buildFrame("3" + "0".repeat(26), ChecksumMode.SUM)
        val error = assertFailsWith<FrameException> { decodeCluster(frame, ChecksumMode.SUM) }
        assertEquals("not a cluster frame: type \"3\"", error.message)
    }

    @Test
    fun `non-digit fields are rejected`() {
        val frame = buildFrame("7" + "0A0" + "0".repeat(23), ChecksumMode.SUM)
        val error = assertFailsWith<FrameException> { decodeCluster(frame, ChecksumMode.SUM) }
        assertEquals("speed is not ASCII digits: \"0A0\"", error.message)
    }

    @Test
    fun `fuel bars outside 0 to 6 cannot be encoded`() {
        assertFailsWith<FrameException> { encodeCluster(ClusterData(0, 0, 0.0, 0.0, 7), ChecksumMode.SUM) }
    }
}
