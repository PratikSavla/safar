package app.safar.protocol

import org.junit.Test
import java.time.LocalTime
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
    fun `call frames match bytes decoded from the original app`() {
        assertEquals(
            "a5 32 4a 4f 48 4e 20 44 4f 45 00 00 00 00 00 00 00 00 00 00 00 00 4e 31 ff ff ff ff d4 7f",
            toHex(encodeCall("JOHN DOE", CallStatus.RINGING, MessageSource.PHONE, ChecksumMode.SUM)),
        )
        assertEquals(0x32, encodeCall("JOHN DOE", CallStatus.ANSWERED, MessageSource.PHONE, ChecksumMode.SUM).u8(23))
        assertEquals(
            "a5 34 ff 01 4a 4f 48 4e 20 44 4f 45 00 00 00 00 00 00 00 00 00 00 00 00 4e ff ff ff a6 7f",
            toHex(encodeMissedCall("JOHN DOE", 1, MessageSource.PHONE, ChecksumMode.SUM)),
        )
        assertEquals(
            "a5 35 ff 59 01 4a 4f 48 4e 20 44 4f 45 00 00 00 00 00 00 00 00 00 00 00 00 57 ff ff 0a 7f",
            toHex(encodeMessage("JOHN DOE", 1, MessageSource.WHATSAPP, ChecksumMode.SUM)),
        )
    }

    @Test
    fun `names are cleaned and cut to 19 characters`() {
        assertEquals("Alex Work", sanitizeName("Alex \uD83D\uDE00 (Work)"))
        assertEquals("001234567890", sanitizeName("+00 12345 67890"))
        assertEquals("ABCDEFGHIJKLMNOPQRS", sanitizeName("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
        val frame = encodeCall("ABCDEFGHIJKLMNOPQRSTUVWXYZ", CallStatus.RINGING, MessageSource.PHONE, ChecksumMode.SUM)
        assertEquals(30, frame.size)
    }

    @Test
    fun `distance and ETA use the cluster text format`() {
        assertEquals(Distance("0250", DistanceUnit.METRES), formatDistance(248))
        assertEquals(Distance("01.2", DistanceUnit.KILOMETRES), formatDistance(1234))
        assertEquals(Distance("12.5", DistanceUnit.KILOMETRES), formatDistance(12_480))
        assertEquals(Distance("0123", DistanceUnit.KILOMETRES), formatDistance(123_400))
        assertEquals("0530PM", formatEta(LocalTime.of(17, 30)))
        assertEquals("1205AM", formatEta(LocalTime.of(0, 5)))
    }

    @Test
    fun `navigation frame puts each field at the documented byte`() {
        val frame = encodeNavigation(
            NavigationData(
                iconCode = 1, // turn left
                distanceToTurnM = 250,
                eta = LocalTime.of(17, 30),
                remainingM = 12_480,
                status = NavStatus.NORMAL,
                active = true,
            ),
            ChecksumMode.SUM,
        )
        assertEquals(0x31, frame.u8(1))
        assertEquals(1, frame.u8(2))
        assertEquals(0xFF, frame.u8(3))
        assertEquals("0250M", ascii(frame, 4, 9))
        assertEquals("0530PM", ascii(frame, 9, 15))
        assertEquals(listOf(0xFF, 0xFF, 0xFF), listOf(frame.u8(15), frame.u8(16), frame.u8(17)))
        assertEquals("12.5K11", ascii(frame, 18, 25))
        assertEquals(true, validateFrame(frame, ChecksumMode.SUM).ok)
    }

    @Test
    fun `navigation frame sends icon 46 when the status is not normal`() {
        val frame = encodeNavigation(
            NavigationData(4, 0, LocalTime.of(9, 0), 0, NavStatus.GPS_OFF, active = true),
            ChecksumMode.SUM,
        )
        assertEquals(46, frame.u8(2))
        assertEquals('4', frame.u8(23).toChar())
    }

    @Test
    fun `phone status frame layout`() {
        val frame = encodePhoneStatus(
            PhoneStatus(
                batteryPercent = 60,
                charging = true,
                speedAlertKmh = 0,
                signalBars = 2,
                time = LocalTime.of(23, 18, 37),
                messagesCleared = false,
                missedCallsCleared = true,
            ),
            ChecksumMode.SUM,
        )
        assertEquals("32Y", ascii(frame, 1, 4))
        assertEquals(listOf(0xFF, 0xFF, 0xFF), listOf(frame.u8(4), frame.u8(5), frame.u8(6)))
        assertEquals("2111837NY", ascii(frame, 7, 16))
        assertEquals(true, validateFrame(frame, ChecksumMode.SUM).ok)
    }

    @Test
    fun `rider frame layout`() {
        val frame = encodeRider("Rider", true, ChecksumMode.SUM)
        assertEquals("6Rider", ascii(frame, 1, 7))
        assertEquals(0xFF, frame.u8(22))
        assertEquals('F', frame.u8(27).toChar())
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
