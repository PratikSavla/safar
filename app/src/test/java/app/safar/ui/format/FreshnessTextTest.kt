package app.safar.ui.format

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FreshnessTextTest {
    private val zone: ZoneId = ZoneOffset.ofHoursMinutes(5, 30)

    /** 2026-03-12 18:30 local time. */
    private val now = at(2026, 3, 12, 18, 30)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private fun note(source: ValueSource, atMillis: Long?, connected: Boolean = true) =
        FreshnessText.note(source, atMillis, now, connected, zone = zone)

    @Test
    fun `thresholds follow how often each source reports`() {
        assertEquals(30_000L, BIKE_STALE_AFTER_MS)
        assertEquals(30_000L, ValueSource.BIKE.staleAfterMillis)
        assertEquals(10_000L, ValueSource.PHONE_GPS.staleAfterMillis)
        assertEquals(NEVER_STALE, ValueSource.CALCULATED.staleAfterMillis)
        assertEquals(NEVER_STALE, ValueSource.GOOGLE_MAPS.staleAfterMillis)
        assertEquals(NEVER_STALE, ValueSource.RIDER.staleAfterMillis)
    }

    @Test
    fun `a fresh value says nothing`() {
        assertNull(note(ValueSource.BIKE, now - 5_000))
        assertNull(note(ValueSource.BIKE, now - 30_000))
        assertNull(note(ValueSource.PHONE_GPS, now - 10_000))
        assertNull(note(ValueSource.CALCULATED, now - 400L * 24 * 3600 * 1000))
    }

    @Test
    fun `a stale value says when it was last seen, never in seconds`() {
        assertEquals("Last seen under a minute ago", note(ValueSource.BIKE, now - 31_000))
        assertEquals("Last seen under a minute ago", note(ValueSource.PHONE_GPS, now - 11_000))
        assertEquals("Last seen 12 min ago", note(ValueSource.BIKE, now - 12 * 60_000))
        assertEquals("Last seen today, 14:05", note(ValueSource.BIKE, at(2026, 3, 12, 14, 5)))
        assertEquals("Last seen yesterday, 18:40", note(ValueSource.BIKE, at(2026, 3, 11, 18, 40)))
        assertEquals("Last seen 3 days ago", note(ValueSource.PHONE_GPS, at(2026, 3, 9, 8, 0)))
        assertEquals("Last seen 1 Mar", note(ValueSource.BIKE, at(2026, 3, 1, 8, 0)))
    }

    @Test
    fun `a bike value after the link dropped comes from the last ride`() {
        assertEquals("From your last ride", note(ValueSource.BIKE, now - 5_000, connected = false))
        assertEquals("From your last ride", note(ValueSource.BIKE, at(2026, 3, 12, 0, 10), connected = false))
        assertEquals("From your last ride · yesterday", note(ValueSource.BIKE, at(2026, 3, 11, 23, 50), connected = false))
        assertEquals("From your last ride · 4 days ago", note(ValueSource.BIKE, at(2026, 3, 8, 9, 0), connected = false))
        assertEquals("From your last ride · 20 Feb", note(ValueSource.BIKE, at(2026, 2, 20, 9, 0), connected = false))
    }

    @Test
    fun `no reading yet waits while connected and stays quiet otherwise`() {
        assertEquals("Waiting for the bike", note(ValueSource.BIKE, null))
        assertEquals("Waiting for GPS", note(ValueSource.PHONE_GPS, null))
        assertEquals("Waiting for Google Maps", note(ValueSource.GOOGLE_MAPS, null))
        assertNull(note(ValueSource.CALCULATED, null))
        assertNull(note(ValueSource.BIKE, null, connected = false))
    }

    @Test
    fun `a custom threshold overrides the source default`() {
        assertNull(FreshnessText.note(ValueSource.BIKE, now - 3_600_000, now, staleAfterMillis = NEVER_STALE, zone = zone))
        assertEquals("Last seen 2 min ago", FreshnessText.note(ValueSource.CALCULATED, now - 120_000, now, staleAfterMillis = 60_000, zone = zone))
    }

    @Test
    fun `isStale is strict and never for NEVER_STALE`() {
        assertFalse(FreshnessText.isStale(now - 30_000, now, BIKE_STALE_AFTER_MS))
        assertTrue(FreshnessText.isStale(now - 30_001, now, BIKE_STALE_AFTER_MS))
        assertFalse(FreshnessText.isStale(0, now, NEVER_STALE))
    }

    @Test
    fun `calm age treats the future as just now and supports 12 hour clocks`() {
        assertEquals("under a minute ago", FreshnessText.calmAge(now + 9_000, now, zone))
        assertEquals("yesterday, 6:40 PM", FreshnessText.calmAge(at(2026, 3, 11, 18, 40), now, zone, is24h = false))
    }

    @Test
    fun `developer line keeps the exact source and age`() {
        assertEquals("↳ bike · 3 s ago", FreshnessText.developer(ValueSource.BIKE, now - 3_000, now, zone = zone))
        assertEquals("↳ bike · waiting", FreshnessText.developer(ValueSource.BIKE, null, now, zone = zone))
        assertEquals(
            "↳ bike · Device Information · last known · 2 min ago",
            FreshnessText.developer(ValueSource.BIKE, now - 120_000, now, connected = false, detail = "Device Information", zone = zone),
        )
        assertEquals("↳ phone location · 12 s ago", FreshnessText.developer("phone location", now - 12_000, now, zone = zone))
    }
}
