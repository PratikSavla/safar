package app.safar.protocol

import org.junit.Test
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Navigation and maneuver edge cases. Expected values come from the TypeScript helper. */
class NavigationTest {
    private fun m(text: String) = Distance(text, DistanceUnit.METRES)
    private fun k(text: String) = Distance(text, DistanceUnit.KILOMETRES)

    @Test
    fun `metre distances round to 10 m`() {
        assertEquals(m("0000"), formatDistance(0))
        assertEquals(m("0000"), formatDistance(4))
        assertEquals(m("0010"), formatDistance(5))
        assertEquals(m("0990"), formatDistance(994))
    }

    @Test
    fun `999 m and 1000 m switch to kilometres`() {
        assertEquals(k("01.0"), formatDistance(995))
        assertEquals(k("01.0"), formatDistance(999))
        assertEquals(k("01.0"), formatDistance(1000))
    }

    @Test
    fun `kilometre decimal follows JavaScript toFixed`() {
        assertEquals(k("12.4"), formatDistance(12_450)) // 12.45 is 12.4499... in binary
        assertEquals(k("12.5"), formatDistance(12_480))
        assertEquals(k("01.3"), formatDistance(1250)) // exact half rounds up
    }

    @Test
    fun `99_9 km and 100 km boundaries`() {
        assertEquals(k("99.9"), formatDistance(99_900))
        assertEquals(k("99.9"), formatDistance(99_940))
        assertEquals(k("0100"), formatDistance(99_950)) // the TS helper gives "100.0" here
        assertEquals(k("0100"), formatDistance(99_990))
        assertEquals(k("0100"), formatDistance(100_000))
    }

    @Test
    fun `very long distances keep the last 4 digits`() {
        assertEquals(k("0000"), formatDistance(9_999_999))
        assertEquals(k("2346"), formatDistance(12_345_678))
    }

    @Test
    fun `negative or infinite distances are rejected`() {
        assertFailsWith<FrameException> { formatDistance(-1) }
        assertFailsWith<FrameException> { formatDistance(Double.POSITIVE_INFINITY) }
    }

    @Test
    fun `ETA at 12 AM and 12 PM`() {
        assertEquals("1200AM", formatEta(0, 0))
        assertEquals("1200PM", formatEta(12, 0))
        assertEquals("1159AM", formatEta(11, 59))
        assertEquals("1159PM", formatEta(LocalTime.of(23, 59, 59)))
        assertFailsWith<FrameException> { formatEta(24, 0) }
    }

    @Test
    fun `ended navigation frame in inverted mode matches the helper bytes`() {
        val data = NavigationData(9, 999, LocalTime.of(0, 7), 100_000, NavStatus.DESTINATION_REACHED, active = false)
        assertEquals(
            "a5 31 09 ff 30 31 2e 30 4b 31 32 30 37 41 4d ff ff ff 30 31 30 30 4b 35 30 ff ff ff f9 7f",
            toHex(encodeNavigation(data, ChecksumMode.INVERTED)),
        )
    }

    @Test
    fun `99_950 m no longer breaks the navigation frame`() {
        val data = NavigationData(8, 99_950, LocalTime.NOON, 99_950, NavStatus.NORMAL, active = true)
        val frame = encodeNavigation(data, ChecksumMode.SUM)
        assertEquals("0100K", ascii(frame, 4, 9))
    }

    @Test
    fun `icon code outside 1 to 45 is rejected`() {
        val data = NavigationData(46, 0, LocalTime.NOON, 0, NavStatus.NORMAL, active = true)
        assertFailsWith<FrameException> { encodeNavigation(data, ChecksumMode.SUM) }
        assertFailsWith<FrameException> { encodeNavigation(data.copy(iconCode = 0), ChecksumMode.SUM) }
    }

    @Test
    fun `status codes and icon visibility`() {
        assertEquals(NavStatus.GPS_OFF, NavStatus.fromCode('4'))
        assertNull(NavStatus.fromCode('9'))
        assertEquals(listOf('0', '1', '2', '3', '4', '5', '6'), NavStatus.entries.map { it.code })
        assertEquals(
            setOf(NavStatus.NORMAL, NavStatus.VIA_POINT_REACHED, NavStatus.DESTINATION_REACHED),
            NavStatus.entries.filter { it.showsIcon }.toSet(),
        )
    }

    @Test
    fun `maneuver table lookups`() {
        assertEquals(45, MANEUVERS.size)
        assertEquals((1..45).toList(), MANEUVERS.map { it.code })
        assertEquals(10, codeFromMapplsId(75))
        assertEquals(1, codeFromMapplsId(0))
        assertNull(codeFromMapplsId(9))
        assertEquals("Take ferry", maneuverByCode(43)?.name)
        assertNull(maneuverByCode(STATUS_ICON_CODE))
    }
}
