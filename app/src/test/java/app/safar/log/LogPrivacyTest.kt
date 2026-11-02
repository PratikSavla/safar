package app.safar.log

import app.safar.protocol.CallStatus
import app.safar.protocol.ChecksumMode
import app.safar.protocol.LogDirection
import app.safar.protocol.LogEntry
import app.safar.protocol.MessageSource
import app.safar.protocol.encodeCall
import app.safar.protocol.encodeMessage
import app.safar.protocol.encodeMissedCall
import app.safar.protocol.encodeNavigation
import app.safar.protocol.encodeRider
import app.safar.protocol.frameEntry
import app.safar.protocol.logDetail
import app.safar.protocol.NavStatus
import app.safar.protocol.NavigationData
import app.safar.protocol.toHex
import app.safar.protocol.validateFrame
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LogPrivacyTest {
    private val at = Instant.parse("2026-01-02T03:04:05.678Z")

    private fun ascii(frame: ByteArray, range: IntRange) = range.map { (frame[it].toInt() and 0xFF).toChar() }.joinToString("")

    @Test
    fun callFrameNameBecomesStarsAndChecksumStaysValid() {
        val frame = encodeCall("Alex", CallStatus.RINGING, MessageSource.PHONE, ChecksumMode.SUM)
        val masked = LogPrivacy.maskFrame(frame)
        assertEquals("****" + "\u0000".repeat(16), ascii(masked, 2..21))
        assertEquals('N'.code, masked[22].toInt())
        assertEquals('1'.code, masked[23].toInt())
        assertTrue(validateFrame(masked, ChecksumMode.SUM).ok)
        // Same bytes the encoder makes for a caller named "****".
        assertContentEquals(encodeCall("****", CallStatus.RINGING, MessageSource.PHONE, ChecksumMode.SUM), masked)
    }

    @Test
    fun sessionStartedFirstConnectionFlagIsNotANameAndStaysReadable() {
        assertEquals("F", LogPrivacy.maskDetail("riderFrame", "F"))
        assertEquals("****", LogPrivacy.maskDetail("rider", "Alex"))
    }

    @Test
    fun invertedChecksumIsRecalculatedInInvertedMode() {
        val frame = encodeMessage("Rider", 3, MessageSource.WHATSAPP, ChecksumMode.INVERTED)
        val masked = LogPrivacy.maskFrame(frame)
        assertEquals("*****", ascii(masked, 5..9))
        assertEquals(3, masked[4].toInt())
        assertTrue(validateFrame(masked, ChecksumMode.INVERTED).ok)
        assertContentEquals(encodeMessage("*****", 3, MessageSource.WHATSAPP, ChecksumMode.INVERTED), masked)
    }

    @Test
    fun missedCallAndRiderFramesMaskOnlyTheNameField() {
        val missed = LogPrivacy.maskFrame(encodeMissedCall("Alex Rider", 2, MessageSource.PHONE, ChecksumMode.SUM))
        assertContentEquals(encodeMissedCall("**********", 2, MessageSource.PHONE, ChecksumMode.SUM), missed)
        val rider = LogPrivacy.maskFrame(encodeRider("RIDER", firstConnection = true, ChecksumMode.SUM))
        assertContentEquals(encodeRider("*****", firstConnection = true, ChecksumMode.SUM), rider)
        assertEquals('F'.code, rider[27].toInt())
    }

    @Test
    fun framesWithoutNamesAreUnchanged() {
        val nav = encodeNavigation(
            NavigationData(4, 300, LocalTime.of(13, 1), 41_600, NavStatus.NORMAL, active = true),
            ChecksumMode.SUM,
        )
        assertContentEquals(nav, LogPrivacy.maskFrame(nav))
    }

    @Test
    fun invalidFrameKeepsItsChecksumByte() {
        val frame = encodeCall("Alex", CallStatus.RINGING, MessageSource.PHONE, ChecksumMode.SUM)
        frame[28] = (frame[28] + 1).toByte()
        val masked = LogPrivacy.maskFrame(frame)
        assertEquals(frame[28], masked[28])
        assertEquals("****", ascii(masked, 2..5))
    }

    @Test
    fun frameEntryHexIsMasked() {
        val frame = encodeCall("Alex", CallStatus.ANSWERED, MessageSource.WHATSAPP, ChecksumMode.SUM)
        val entry = frameEntry(LogDirection.TX, frame, ok = true, at = at) as LogEntry.Frame
        val masked = LogPrivacy.maskEntry(entry) as LogEntry.Frame
        assertEquals(toHex(LogPrivacy.maskFrame(frame)), masked.hex)
        assertEquals(entry.at, masked.at)
        assertEquals('2', masked.type)
    }

    @Test
    fun eventDetailsMaskNamesBikeNamesAddressesAndSerials() {
        val entry = LogEntry.Event(
            at,
            "Connecting",
            logDetail(
                "caller" to "Alex Rider",
                "bike" to "SBM01A1B2C3D",
                "address" to "00:11:22:33:44:55",
                "serial" to "A1B2",
                "mtu" to 65,
                "road" to "Main Rd",
            ),
        )
        val detail = assertNotNull((LogPrivacy.maskEntry(entry) as LogEntry.Event).detail)
        assertEquals(JsonPrimitive("**** *****"), detail["caller"])
        assertEquals(JsonPrimitive("SBM01*******"), detail["bike"])
        assertEquals(JsonPrimitive("00:11:22:**:**:**"), detail["address"])
        assertEquals(JsonPrimitive("****"), detail["serial"])
        assertEquals(JsonPrimitive(65), detail["mtu"])
        assertEquals(JsonPrimitive("Main Rd"), detail["road"])
    }
}
