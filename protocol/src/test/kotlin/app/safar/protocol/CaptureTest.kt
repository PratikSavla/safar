package app.safar.protocol

import org.junit.Test
import kotlin.test.assertEquals

/**
 * Port of docs/helper/tests/capture.test.ts: frames captured from a real V-Strom SX and the official
 * app v7.6 (HCI snoop log of 2026-11-02).
 */
class CaptureTest {
    private val bike = "SBM01A1B2C3D" // synthetic name, same model code as the test bike
    private val mode = checksumModeFor(bike)

    private val cluster = "a5 37 30 30 30 30 30 30 34 36 32 30 30 32 33 36 32 30 30 34 36 32 33 4e 33 1d ff ff eb 7f"
    private val navigation = "a5 31 04 ff 30 30 33 30 4d 30 31 30 31 50 4d ff ff ff 34 31 2e 36 4b 31 31 ff ff ff 13 7f"
    private val status = "a5 33 31 4e ff ff ff 32 31 31 32 31 31 32 4e 4e ff ff ff ff ff 01 00 01 ff ff ff ff 9e 7f"

    @Test
    fun `the test bike is a V-Strom SX with checksum mode sum`() {
        assertEquals("V-STROM SX", identifyVehicle(bike)?.name)
        assertEquals(ChecksumMode.SUM, mode)
    }

    @Test
    fun `real cluster frame decodes`() {
        val data = decodeCluster(fromHex(cluster), mode)
        assertEquals(
            listOf<Number>(0, 462, 236.2, 462.3, 3),
            listOf<Number>(data.speedKmh, data.odometerKm, data.tripAKm, data.tripBKm, data.fuelBars),
        )
        assertEquals('N', data.unknown.b23.toChar())
        assertEquals(0x1D, data.unknown.b25)
    }

    @Test
    fun `real navigation frame agrees with the documented layout`() {
        val frame = fromHex(navigation)
        assertEquals(true, validateFrame(frame, mode).ok)
        assertEquals(4, frame.u8(2)) // turn right
        assertEquals("0030M", ascii(frame, 4, 9))
        assertEquals("0101PM", ascii(frame, 9, 15))
        assertEquals("41.6K11", ascii(frame, 18, 25))
        assertEquals(Distance("41.6", DistanceUnit.KILOMETRES), formatDistance(41_600))
    }

    @Test
    fun `real status frame from app v7_6 is valid and has the documented clock field`() {
        val frame = fromHex(status)
        assertEquals(true, validateFrame(frame, mode).ok)
        assertEquals("1N", ascii(frame, 2, 4)) // battery 25-49 %, not charging
        assertEquals(listOf(0xFF, 0xFF, 0xFF), listOf(frame.u8(4), frame.u8(5), frame.u8(6))) // alert off
        assertEquals("2112112NN", ascii(frame, 7, 16)) // signal 2, 11:21:12
    }

    // Captured call frames with the contact name replaced by "Alex". The checksums were recalculated
    // with a separate script. All other bytes are as captured.
    @Test
    fun `encoder gives the same missed call frame as the official app`() {
        val captured = "a5 34 ff 01 41 6c 65 78 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 4e ff ff ff 09 7f"
        assertEquals(captured, toHex(encodeMissedCall("Alex", 1, MessageSource.PHONE, mode)))
    }

    @Test
    fun `encoder gives the same call frames as the official app`() {
        val ringing = "a5 32 41 6c 65 78 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 4e 31 ff ff ff ff 37 7f"
        val answered = "a5 32 41 6c 65 78 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 4e 32 ff ff ff ff 38 7f"
        assertEquals(ringing, toHex(encodeCall("Alex", CallStatus.RINGING, MessageSource.PHONE, mode)))
        assertEquals(answered, toHex(encodeCall("Alex", CallStatus.ANSWERED, MessageSource.PHONE, mode)))
    }
}
