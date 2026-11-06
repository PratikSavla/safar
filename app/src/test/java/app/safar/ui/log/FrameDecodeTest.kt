package app.safar.ui.log

import app.safar.protocol.CallStatus
import app.safar.protocol.ChecksumMode
import app.safar.protocol.ClusterData
import app.safar.protocol.MessageSource
import app.safar.protocol.NavStatus
import app.safar.protocol.NavigationData
import app.safar.protocol.PhoneStatus
import app.safar.protocol.encodeCall
import app.safar.protocol.encodeCluster
import app.safar.protocol.encodeMessage
import app.safar.protocol.encodeMissedCall
import app.safar.protocol.encodeNavigation
import app.safar.protocol.encodePhoneStatus
import app.safar.protocol.encodeRider
import app.safar.protocol.toHex
import org.junit.Test
import java.time.LocalTime
import kotlin.test.assertEquals

class FrameDecodeTest {
    private fun fields(bytes: ByteArray) = decodeFrame(toHex(bytes)).fields.toMap()

    @Test
    fun clusterFrameUsesTheProtocolDecoder() {
        val d = decodeFrame(toHex(encodeCluster(ClusterData(42, 12_345, 142.6, 8.1, 4), ChecksumMode.INVERTED)))
        val f = d.fields.toMap()
        assertEquals("Cluster", d.typeName)
        assertEquals("42 km/h", f["Speed"])
        assertEquals("12345 km", f["Odometer"])
        assertEquals("142.6 km", f["Trip A"])
        assertEquals("4", f["Fuel bars"])
        assertEquals(true, f["Checksum"]?.endsWith("inverted ok"))
        assertEquals(ByteRole.START, d.bytes[0].role)
        assertEquals(ByteRole.TYPE, d.bytes[1].role)
        assertEquals("48 °C · byte 25 0x30", f["Engine temp"], "the encoder writes '0' (0x30) at byte 25")
        assertEquals(ByteRole.UNKNOWN, d.bytes[23].role)
        assertEquals(ByteRole.DATA, d.bytes[25].role)
        assertEquals(ByteRole.CHECKSUM, d.bytes[28].role)
        assertEquals(ByteRole.END, d.bytes[29].role)
        assertEquals("A5", d.bytes[0].text)
    }

    @Test
    fun navigationFrameFields() {
        val nav = NavigationData(4, 850, LocalTime.of(16, 12), 11_200, NavStatus.NORMAL, active = true)
        val f = fields(encodeNavigation(nav, ChecksumMode.SUM))
        assertEquals("4 · Turn right", f["Icon"])
        assertEquals("0850 M", f["To next turn"])
        assertEquals("0412PM", f["ETA"])
        assertEquals("11.2 K", f["Remaining"])
        assertEquals("1 · normal", f["Status"])
        assertEquals("active", f["Navigation"])
        assertEquals(true, f["Checksum"]?.endsWith("sum ok"))
    }

    @Test
    fun gpsOffSendsTheStatusIcon() {
        val nav = NavigationData(4, 0, LocalTime.NOON, 0, NavStatus.GPS_OFF, active = false)
        val f = fields(encodeNavigation(nav, ChecksumMode.SUM))
        assertEquals("46 · status", f["Icon"])
        assertEquals("4 · gps off", f["Status"])
        assertEquals("ended", f["Navigation"])
    }

    @Test
    fun alertFramesShowNamesCountsAndSources() {
        assertEquals("Alex", fields(encodeCall("Alex", CallStatus.ANSWERED, MessageSource.WHATSAPP, ChecksumMode.SUM))["Name"])
        assertEquals("answered", fields(encodeCall("Alex", CallStatus.ANSWERED, MessageSource.PHONE, ChecksumMode.SUM))["Call"])
        val missed = fields(encodeMissedCall("Rider", 3, MessageSource.PHONE, ChecksumMode.SUM))
        assertEquals("3", missed["Count"])
        assertEquals("Rider", missed["Name"])
        assertEquals("N · phone", missed["Source"])
        val msg = fields(encodeMessage("Alex", 12, MessageSource.WHATSAPP, ChecksumMode.SUM))
        assertEquals("12", msg["Count"])
        assertEquals("W · WhatsApp", msg["Source"])
        val rider = fields(encodeRider("Alex", firstConnection = true, ChecksumMode.SUM))
        assertEquals("Alex", rider["Rider name"])
        assertEquals("F · first with this cluster", rider["Connection"])
    }

    @Test
    fun statusFrameFields() {
        val s = PhoneStatus(80, true, 0, 2, LocalTime.of(14, 5, 9), messagesCleared = false, missedCallsCleared = true)
        val f = fields(encodePhoneStatus(s, ChecksumMode.SUM))
        assertEquals("75 % or more", f["Battery"])
        assertEquals("yes", f["Charging"])
        assertEquals("off", f["Speed alert"])
        assertEquals("2 of 3 bars", f["Signal"])
        assertEquals("02:05:09", f["Clock"])
        assertEquals("yes", f["Missed calls cleared"])
        assertEquals("100 km/h", fields(encodePhoneStatus(s.copy(speedAlertKmh = 100), ChecksumMode.SUM))["Speed alert"])
    }

    @Test
    fun brokenFramesNeverThrow() {
        assertEquals("3 bytes, expected 30", decodeFrame("a5 37 7f").fields.toMap()["Problem"])
        assertEquals("hex string has an odd number of digits", decodeFrame("a5 3").fields.toMap()["Problem"])
        val bytes = encodeCluster(ClusterData(1, 2, 3.0, 4.0, 5), ChecksumMode.SUM)
        bytes[28] = (bytes[28] + 1).toByte()
        assertEquals(true, decodeFrame(toHex(bytes)).fields.toMap()["Checksum"]?.startsWith("checksum 0x"))
    }
}
