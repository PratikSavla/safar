package app.safar.bikesim.sim

import app.safar.protocol.CallStatus
import app.safar.protocol.ChecksumMode
import app.safar.protocol.MessageSource
import app.safar.protocol.NavStatus
import app.safar.protocol.NavigationData
import app.safar.protocol.PhoneStatus
import app.safar.protocol.encodeCall
import app.safar.protocol.encodeMessage
import app.safar.protocol.encodeMissedCall
import app.safar.protocol.encodeNavigation
import app.safar.protocol.encodePhoneStatus
import app.safar.protocol.encodeRider
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class FrameDescriberTest {
    private val sum = ChecksumMode.SUM

    @Test
    fun `rider frame shows name and first connection flag`() {
        val d = FrameDescriber.describe(encodeRider("Alex", firstConnection = true, sum), sum)
        assertEquals('6', d.type)
        assertEquals("Rider", d.typeName)
        assertEquals("name \"Alex\" · F first connection", d.text)
        assertTrue(d.checksumOk)
    }

    @Test
    fun `status frame shows battery, charging, alert, signal, clock and flags`() {
        val status = PhoneStatus(80, true, 100, 2, LocalTime.of(22, 5, 9), messagesCleared = false, missedCallsCleared = true)
        val d = FrameDescriber.describe(encodePhoneStatus(status, sum), sum)
        assertEquals(
            "battery 75 % or more · charging yes · alert 100 km/h · signal 2/3 · clock 10:05:09 · " +
                "messages cleared no · missed calls cleared yes",
            d.text,
        )
    }

    @Test
    fun `status frame with the alert off`() {
        val status = PhoneStatus(10, false, 0, 0, LocalTime.of(9, 0), messagesCleared = false, missedCallsCleared = false)
        val text = FrameDescriber.describe(encodePhoneStatus(status, sum), sum).text
        assertTrue(text.startsWith("battery under 25 % · charging no · alert off · signal 0/3 · clock 09:00:00"), text)
    }

    @Test
    fun `navigation frame shows icon name, distances, ETA, status and active`() {
        val nav = NavigationData(4, 250, LocalTime.of(14, 55), 12_500, NavStatus.NORMAL, active = true)
        val d = FrameDescriber.describe(encodeNavigation(nav, sum), sum)
        assertEquals(
            "icon 4 Turn right · 250 m to turn · ETA 02:55 PM · 12.5 km left · status 1 normal · active",
            d.text,
        )
    }

    @Test
    fun `navigation frame with GPS off shows the status icon and ended`() {
        val nav = NavigationData(4, 1200, LocalTime.of(9, 5), 800, NavStatus.GPS_OFF, active = false)
        val text = FrameDescriber.describe(encodeNavigation(nav, sum), sum).text
        assertEquals("icon 46 status icon · 1.2 km to turn · ETA 09:05 AM · 800 m left · status 4 gps off · ended", text)
    }

    @Test
    fun `call frames show ringing and answered`() {
        val ringing = FrameDescriber.describe(encodeCall("Rider", CallStatus.RINGING, MessageSource.PHONE, sum), sum)
        assertEquals("name \"Rider\" · source N phone · ringing", ringing.text)
        val answered = FrameDescriber.describe(encodeCall("Alex", CallStatus.ANSWERED, MessageSource.WHATSAPP, sum), sum)
        assertEquals("name \"Alex\" · source W WhatsApp · answered", answered.text)
    }

    @Test
    fun `missed call and message frames show count, name and source`() {
        val missed = FrameDescriber.describe(encodeMissedCall("Alex", 2, MessageSource.PHONE, sum), sum)
        assertEquals("Missed call", missed.typeName)
        assertEquals("count 2 · name \"Alex\" · source N phone", missed.text)
        val message = FrameDescriber.describe(encodeMessage("Rider", 3, MessageSource.WHATSAPP, sum), sum)
        assertEquals("count 3 · sender \"Rider\" · source W WhatsApp", message.text)
    }

    @Test
    fun `checksum of the other mode is reported invalid with a hint`() {
        val d = FrameDescriber.describe(encodeRider("Alex", firstConnection = false, ChecksumMode.INVERTED), sum)
        assertFalse(d.checksumOk)
        assertTrue(d.checksumText.endsWith("(passes the other checksum mode)"), d.checksumText)
        assertEquals("name \"Alex\" · R reconnect", d.text)
    }

    @Test
    fun `inverted mode accepts an inverted frame`() {
        val inv = ChecksumMode.INVERTED
        val d = FrameDescriber.describe(encodeRider("Alex", firstConnection = true, inv), inv)
        assertTrue(d.checksumOk)
        assertTrue(d.checksumText.endsWith("inverted ok"), d.checksumText)
    }

    @Test
    fun `short and unknown frames do not throw`() {
        val short = FrameDescriber.describe(byteArrayOf(0xA5.toByte(), '6'.code.toByte(), 0x00), sum)
        assertEquals("3 bytes, expected 30", short.text)
        assertFalse(short.checksumOk)
        val unknown = ClusterFrames.build(SimCluster(), sum).also { it[1] = '9'.code.toByte() }
        assertEquals("type 0x39 is not in the protocol guide", FrameDescriber.describe(unknown, sum).text)
        assertEquals("Unknown", FrameDescriber.describe(byteArrayOf(), sum).typeName)
    }
}
