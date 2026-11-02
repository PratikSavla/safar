package app.safar.protocol

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Edge cases of the frame envelope that the TypeScript tests do not cover. */
class FrameTest {
    private val cluster = "a5 37 30 38 37 30 31 32 33 34 35 30 30 31 32 33 34 30 30 30 35 36 37 30 35 30 30 30 56 7f"

    @Test
    fun `valid frame gives Valid with no reason`() {
        val result = validateFrame(fromHex(cluster), ChecksumMode.SUM)
        assertEquals(ValidationResult.Valid, result)
        assertTrue(result.ok)
        assertNull(result.reason)
    }

    @Test
    fun `bad checksum is rejected with expected and actual bytes`() {
        val frame = fromHex(cluster)
        frame[CHECKSUM_INDEX] = 0x57
        val result = validateFrame(frame, ChecksumMode.SUM)
        assertIs<ValidationResult.Invalid>(result)
        assertEquals("checksum 0x57, expected 0x56", result.reason)
    }

    @Test
    fun `sum frame is rejected in inverted mode`() {
        val result = validateFrame(fromHex(cluster), ChecksumMode.INVERTED)
        assertEquals("checksum 0x56, expected 0xa9", result.reason)
    }

    @Test
    fun `wrong length, start and end bytes are rejected`() {
        assertEquals("length 29, expected 30", validateFrame(ByteArray(29), ChecksumMode.SUM).reason)
        val badStart = fromHex(cluster).also { it[0] = 0x00 }
        assertEquals("start byte 0x00, expected 0xa5", validateFrame(badStart, ChecksumMode.SUM).reason)
        val badEnd = fromHex(cluster).also { it[29] = 0x7E }
        assertEquals("end byte 0x7e, expected 0x7f", validateFrame(badEnd, ChecksumMode.SUM).reason)
    }

    @Test
    fun `inverted mode builds a frame that validates only in inverted mode`() {
        val body = "7" + "0".repeat(26)
        val frame = buildFrame(body, ChecksumMode.INVERTED)
        val sum = (0x37 + 26 * 0x30) and 0xFF
        assertEquals(0xFF - sum, frame[CHECKSUM_INDEX].toInt() and 0xFF)
        assertTrue(validateFrame(frame, ChecksumMode.INVERTED).ok)
        assertEquals(false, validateFrame(frame, ChecksumMode.SUM).ok)
    }

    @Test
    fun `buildFrame applies overrides and rejects bad input`() {
        val frame = buildFrame("1" + "\u0000".repeat(26), ChecksumMode.SUM, mapOf(3 to 0x1FF))
        assertEquals(0xFF, frame[3].toInt() and 0xFF)
        assertEquals(30, frame.size)
        assertFailsWith<FrameException> { buildFrame("1", ChecksumMode.SUM) }
        assertFailsWith<FrameException> { buildFrame("é" + "0".repeat(26), ChecksumMode.SUM) }
        assertFailsWith<FrameException> { buildFrame("1" + "0".repeat(26), ChecksumMode.SUM, mapOf(28 to 1)) }
        assertFailsWith<FrameException> { buildFrame("1" + "0".repeat(26), ChecksumMode.SUM, mapOf(0 to 1)) }
    }

    @Test
    fun `computeChecksum needs a full frame`() {
        assertFailsWith<FrameException> { computeChecksum(ByteArray(10), ChecksumMode.SUM) }
    }

    @Test
    fun `hex helpers round-trip and ignore separators`() {
        assertContentEquals(byteArrayOf(0xA5.toByte(), 0x7F), fromHex("A5:7f"))
        assertEquals(cluster, toHex(fromHex(cluster)))
        assertEquals("0f", hex(15))
        assertFailsWith<FrameException> { fromHex("abc") }
    }

    @Test
    fun `digits pads and keeps the last digits`() {
        assertEquals("007", digits(7, 3))
        assertEquals("234", digits(1234, 3))
        assertEquals("000000", digits(0L, 6))
        assertFailsWith<FrameException> { digits(-1, 2) }
    }

    @Test
    fun `message type and ascii read raw bytes`() {
        val frame = fromHex(cluster)
        assertEquals('7', messageType(frame))
        assertEquals('\u0000', messageType(ByteArray(1)))
        assertEquals("087", ascii(frame, 2, 5))
    }

    @Test
    fun `model code of short names does not throw`() {
        assertEquals("", modelCode("S"))
        assertEquals("BM", modelCode("SBM"))
        assertNull(identifyVehicle("SB"))
        assertEquals(ChecksumMode.SUM, checksumModeFor(""))
        assertEquals(false, isSuzukiCluster(null))
    }
}
