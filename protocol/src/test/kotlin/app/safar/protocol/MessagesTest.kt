package app.safar.protocol

import org.junit.Test
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Call, message, rider and status edge cases. Expected bytes come from the TypeScript helper. */
class MessagesTest {
    private val inv = ChecksumMode.INVERTED

    @Test
    fun `any name length gives exactly 30 bytes with a NUL-terminated name field`() {
        val names = (0..40).map { "A".repeat(it) } + listOf(
            "B".repeat(29),
            "🏍️".repeat(29),
            "Alex Rider Alex Rider Alex Rider",
            "é".repeat(100),
        )
        for (name in names) {
            val frames = listOf(
                encodeCall(name, CallStatus.RINGING, MessageSource.PHONE, ChecksumMode.SUM),
                encodeMissedCall(name, 2, MessageSource.WHATSAPP, inv),
                encodeMessage(name, 3, MessageSource.WHATSAPP, ChecksumMode.SUM),
                encodeRider(name, firstConnection = false, mode = inv),
            )
            for (frame in frames) {
                assertEquals(FRAME_LENGTH, frame.size, "name length ${name.length}")
                assertEquals(END_BYTE, frame.u8(29))
            }
            assertEquals(0, frames[0].u8(21), "last name byte of the call frame stays NUL")
            assertTrue(validateFrame(frames[1], inv).ok)
        }
    }

    @Test
    fun `sanitizeName trims, collapses and replaces non-ASCII code points`() {
        assertEquals("Alex Rider", sanitizeName("  Alex   Rider!! "))
        assertEquals("Zo Caf", sanitizeName("Zoë 🏍️ Café"))
        assertEquals("0012345", sanitizeName("+00 12345"))
        assertEquals("ABCDEFGHIJKLMNOPQRS", sanitizeName("ABCDEFGHIJKLMNOPQRS"))
        assertEquals("ABCDEFGHIJKLMNOPQRS", sanitizeName("ABCDEFGHIJKLMNOPQRST"))
        assertEquals("", sanitizeName("😀"))
        assertEquals("Main Rd NH 48", sanitizeName("Main Rd, NH 48"))
    }

    @Test
    fun `long WhatsApp call name in inverted mode`() {
        assertEquals(
            "a5 32 41 42 43 44 45 46 47 48 49 4a 4b 4c 4d 4e 4f 50 51 52 53 00 57 31 ff ff ff ff cb 7f",
            toHex(encodeCall("ABCDEFGHIJKLMNOPQRSTUVWXYZ", CallStatus.RINGING, MessageSource.WHATSAPP, inv)),
        )
    }

    @Test
    fun `message, cleared missed call and rider frames in inverted mode`() {
        assertEquals(
            "a5 35 ff 59 03 41 6c 65 78 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 57 ff ff 90 7f",
            toHex(encodeMessage("Alex", 3, MessageSource.WHATSAPP, inv)),
        )
        assertEquals(
            "a5 34 ff 00 52 69 64 65 72 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 57 ff ff ff 82 7f",
            toHex(encodeMissedCall("Rider", 0, MessageSource.WHATSAPP, inv)),
        )
        assertEquals(
            "a5 36 52 69 64 65 72 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 ff ff ff ff ff 52 86 7f",
            toHex(encodeRider("Rider", firstConnection = false, mode = inv)),
        )
    }

    @Test
    fun `counts outside 0 to 254 are rejected`() {
        assertEquals(0xFE, encodeMessage("Alex", 254, MessageSource.PHONE, inv).u8(4))
        assertFailsWith<FrameException> { encodeMessage("Alex", 255, MessageSource.PHONE, inv) }
        assertFailsWith<FrameException> { encodeMissedCall("Alex", -1, MessageSource.PHONE, inv) }
    }

    @Test
    fun `status frame with speed alert at 12 PM`() {
        val status = PhoneStatus(80, false, 80, 3, LocalTime.of(12, 5, 9), true, false)
        assertEquals(
            "a5 33 33 4e 30 38 30 33 31 32 30 35 30 39 59 4e ff ff ff ff ff ff ff ff ff ff ff ff 4b 7f",
            toHex(encodePhoneStatus(status, ChecksumMode.SUM)),
        )
        val midnight = encodePhoneStatus(status.copy(time = LocalTime.MIDNIGHT), ChecksumMode.SUM)
        assertEquals("120000", ascii(midnight, 8, 14))
    }

    @Test
    fun `status frame rejects out-of-range values`() {
        val status = PhoneStatus(80, false, 0, 3, LocalTime.NOON, false, false)
        assertFailsWith<FrameException> { encodePhoneStatus(status.copy(signalBars = 4), inv) }
        assertFailsWith<FrameException> { encodePhoneStatus(status.copy(speedAlertKmh = 1000), inv) }
        assertTrue(validateFrame(encodePhoneStatus(status, inv), inv).ok)
    }

    @Test
    fun `battery code boundaries`() {
        assertEquals(
            listOf('0', '1', '1', '2', '2', '3', '3'),
            listOf(24, 25, 49, 50, 74, 75, 100).map(::batteryCode),
        )
    }
}
