package app.safar.protocol

import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Google Maps parser cases beyond the TypeScript tests. */
class GoogleMapsEdgeTest {
    private val now = LocalDateTime.of(2026, 11, 2, 11, 30)
    private val phoneOk = PhoneConditions(gpsOn = true, cellularOk = true, internetOk = true)

    @Test
    fun `non-breaking spaces between number and unit are accepted like JavaScript`() {
        assertEquals(TitleParts("200 m", "Turn right"), splitTitle("200 m · Turn right"))
        assertEquals(TitleParts("1.2 km", "Keep left"), splitTitle("1.2 km • Keep left"))
        assertEquals(1200, parseDistance("1.2 km"))
    }

    @Test
    fun `units must end at a word boundary`() {
        assertNull(parseDistance("12 min"))
        assertNull(parseDistance("3 miles"))
        assertEquals(483, parseDistance("0.3 mi"))
        assertEquals(1500, parseDistance("in 1.5 KM"))
    }

    @Test
    fun `clock edge cases`() {
        assertEquals(LocalDateTime.of(2026, 11, 2, 12, 5), parseClock("12:05 pm", now))
        assertEquals(LocalDateTime.of(2026, 11, 2, 11, 29), parseClock("11:29", now)) // within 60 s grace
        assertEquals(LocalDateTime.of(2026, 11, 3, 11, 28), parseClock("11:28", now))
        assertNull(parseClock("soon", now))
    }

    @Test
    fun `progress past the maximum clamps to 0 and missing fields default to zero`() {
        val update = updateFromNotification(
            null,
            MapsNotification(title = "Continue on NH 48", progress = 500, progressMax = 400),
            now,
        )
        assertEquals(0, update.remainingM)
        assertEquals(0, update.distanceToTurnM)
        assertEquals(8, update.iconCode)
        assertEquals(now, update.eta)
    }

    @Test
    fun `unknown maneuver uses the previous icon code`() {
        val update = updateFromNotification(null, MapsNotification(title = "Something new"), now)
        assertNull(update.iconCode)
        assertEquals(12, toNavigationData(update, 12, phoneOk).iconCode)
    }

    @Test
    fun `phone conditions take priority over route status`() {
        val arrived = updateFromNotification(null, MapsNotification(title = "You have arrived"), now)
        assertEquals(NavStatus.DESTINATION_REACHED, toNavigationData(arrived, 8, phoneOk).status)
        assertEquals(
            NavStatus.NO_NETWORK_SIGNAL,
            toNavigationData(arrived, 8, phoneOk.copy(cellularOk = false)).status,
        )
        assertEquals(NavStatus.NO_INTERNET, toNavigationData(arrived, 8, phoneOk.copy(internetOk = false)).status)
    }

    @Test
    fun `more instruction rules`() {
        assertEquals(9, codeFromInstruction("Your destination is on the left"))
        assertEquals(43, codeFromInstruction("Take the ferry"))
        assertEquals(21, codeFromInstruction("At the roundabout, take the first exit"))
        assertEquals(45, codeFromInstruction("Enter the rotary"))
        assertEquals(41, codeFromInstruction("Head northeast"))
        assertEquals(40, codeFromInstruction("Head north on Main Rd"))
        assertEquals(27, codeFromInstruction("Take the exit on the left"))
    }
}
