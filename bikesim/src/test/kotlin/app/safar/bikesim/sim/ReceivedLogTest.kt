package app.safar.bikesim.sim

import app.safar.protocol.ChecksumMode
import app.safar.protocol.encodeRider
import app.safar.protocol.toHex
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ReceivedLogTest {
    private val frame = FrameDescriber.describe(encodeRider("Alex", true, ChecksumMode.SUM), ChecksumMode.SUM)

    private fun entry(id: Long, second: Long) = ReceivedEntry(id, Instant.ofEpochSecond(1_700_000_000 + second), frame)

    @Test
    fun `keeps the newest entries first and drops past capacity`() {
        var log = ReceivedLog(capacity = 3)
        for (i in 1L..5L) log = log.add(entry(i, i))
        assertEquals(listOf(5L, 4L, 3L), log.entries.map { it.id })
    }

    @Test
    fun `default capacity is 200`() {
        var log = ReceivedLog()
        for (i in 1L..250L) log = log.add(entry(i, i))
        assertEquals(200, log.entries.size)
        assertEquals(250L, log.entries.first().id)
    }

    @Test
    fun `hex export lists frames oldest first with a time`() {
        val log = ReceivedLog().add(entry(1, 0)).add(entry(2, 1))
        val lines = log.asHex(ZoneOffset.UTC).lines()
        assertEquals(2, lines.size)
        assertEquals("22:13:20.000  ${toHex(encodeRider("Alex", true, ChecksumMode.SUM))}", lines[0])
        assertTrue(lines[1].startsWith("22:13:21.000  a5 36 "))
    }

    @Test
    fun `clear empties the log`() {
        val log = ReceivedLog().add(entry(1, 0)).clear()
        assertFalse(log.entries.isNotEmpty())
        assertEquals("", log.asHex(ZoneOffset.UTC))
    }

    @Test
    fun `name presets match the Safar filter and pick the checksum mode`() {
        for (name in NAME_PRESETS) assertTrue(nameInfo(name).matchesFilter, name)
        assertEquals(ChecksumMode.SUM, nameInfo(DEFAULT_SIM_NAME).mode)
        assertEquals("V-STROM SX", nameInfo(DEFAULT_SIM_NAME).vehicle)
        assertEquals(ChecksumMode.INVERTED, nameInfo("SAS01A1B2C3D").mode)
        assertFalse(nameInfo("SIMBIKE").matchesFilter)
    }
}
