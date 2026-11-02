package app.safar.protocol

import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Port of docs/helper/tests/google-maps.test.ts (the log test is in LogTest). */
class GoogleMapsTest {
    private val now = LocalDateTime.of(2026, 11, 2, 11, 30)
    private val phoneOk = PhoneConditions(gpsOn = true, cellularOk = true, internetOk = true)

    // Real notifications from Google Maps 26.44.03, Android 17, 2026-11-02. Place names are replaced.
    private val realFirst = MapsNotification(
        title = "Turn right at Main Chowk onto NH 48",
        subText = "Arrive 11:56 am",
        progress = 162,
        progressMax = 1607,
    )
    private val realNext = MapsNotification(
        title = "200 m · Turn right toward NH 48",
        shortCriticalText = "200 m",
        subText = "Arrive 11:56 am",
        progress = 338,
        progressMax = 1723,
    )

    // Departure step. The Maps screen showed "58 min · 24 km · 12:55 pm" for this route.
    private val realDepart = MapsNotification(
        title = "Head southwest on Main Rd",
        shortCriticalText = "",
        subText = "Arrive 12:55 pm",
        progress = 0,
        progressMax = 23708,
    )

    @Test
    fun `distances in metric and imperial units`() {
        assertEquals(300, parseDistance("300 m"))
        assertEquals(1200, parseDistance("1.2 km"))
        assertEquals(1200, parseDistance("1,2 km"))
        assertEquals(152, parseDistance("500 ft"))
        assertNull(parseDistance("Turn right"))
    }

    @Test
    fun `clock times in 12-hour and 24-hour format`() {
        assertEquals(11, parseClock("11:42 AM", now)?.hour)
        assertEquals(13, parseClock("1:05 pm", now)?.hour)
        assertEquals(23, parseClock("23:42", now)?.hour)
        assertEquals(3, parseClock("12:10 AM", now)?.dayOfMonth) // after midnight -> next day
    }

    @Test
    fun `sub-text gives remaining distance and ETA in any order`() {
        val a = parseSubText("12 min · 4.3 km · 11:42 AM", now)
        assertEquals(4300, a.remainingM)
        assertEquals(42, a.eta?.minute)
        val b = parseSubText("11:42 AM · 4.3 km", now)
        assertEquals(4300, b.remainingM)
    }

    @Test
    fun `instruction text gives a cluster icon`() {
        assertEquals(4, codeFromInstruction("Turn right onto MG Road"))
        assertEquals(7, codeFromInstruction("Make a U-turn"))
        assertEquals(23, codeFromInstruction("At the roundabout, take the 2nd exit"))
        assertEquals(3, codeFromInstruction("Slight left toward NH 48"))
        assertEquals(32, codeFromInstruction("Keep right at the fork"))
        assertNull(codeFromInstruction("Something new"))
    }

    @Test
    fun `real departure notification gives start heading, remaining distance and ETA`() {
        val update = updateFromNotification(null, realDepart, now)
        assertEquals(17, update.iconCode) // head south-west
        assertEquals(23708, update.remainingM)
        assertEquals(12, update.eta.hour)
        assertEquals(55, update.eta.minute)
        val frame = encodeNavigation(toNavigationData(update, 8, phoneOk), ChecksumMode.SUM)
        assertEquals("23.7K", ascii(frame, 18, 23))
        assertEquals("1255PM", ascii(frame, 9, 15))
    }

    @Test
    fun `real notification title is split into distance and instruction`() {
        assertEquals(TitleParts("200 m", "Turn right toward NH 48"), splitTitle(realNext.title))
        assertEquals(TitleParts(null, "Turn right at Main Chowk onto NH 48"), splitTitle(realFirst.title))
    }

    @Test
    fun `real notifications become valid cluster frames`() {
        val first = updateFromNotification(null, realFirst, now)
        assertEquals(4, first.iconCode)
        assertEquals(0, first.distanceToTurnM) // first update has no distance
        assertEquals(1445, first.remainingM)
        assertEquals(11, first.eta.hour)
        assertEquals(56, first.eta.minute)

        val next = updateFromNotification(first, realNext, now)
        assertEquals(200, next.distanceToTurnM)
        assertEquals(1385, next.remainingM)
        val frame = encodeNavigation(toNavigationData(next, 8, phoneOk), ChecksumMode.SUM)
        assertEquals(4, frame.u8(2))
        assertEquals(true, validateFrame(frame, ChecksumMode.SUM).ok)
    }

    @Test
    fun `icon bitmap match wins over the text, and bad fields keep old values`() {
        val first = updateFromNotification(null, realNext, now)
        val next = updateFromNotification(first, MapsNotification(title = "Odd text", subText = "", iconCode = 31), now)
        assertEquals(31, next.iconCode)
        assertEquals(200, next.distanceToTurnM)
        assertEquals(56, next.eta.minute)
    }

    @Test
    fun `older layout with android_text still works`() {
        val update = updateFromNotification(
            null,
            MapsNotification(title = "300 m", text = "Turn left onto Station Road", subText = "9 min · 2.4 km · 11:39 AM"),
            now,
        )
        assertEquals(1, update.iconCode)
        assertEquals(0, update.distanceToTurnM) // "300 m" alone is not the "<distance> · <instruction>" layout
        assertEquals(2400, update.remainingM)
    }

    @Test
    fun `status priority and end of navigation`() {
        val update = updateFromNotification(null, MapsNotification(title = "Rerouting…", subText = ""), now)
        assertEquals(NavStatus.REROUTING, toNavigationData(update, 8, phoneOk).status)
        assertEquals(NavStatus.GPS_OFF, toNavigationData(update, 8, phoneOk.copy(gpsOn = false)).status)
        assertEquals(false, toNavigationData(endedUpdate(update), 8, phoneOk).active)
    }
}
