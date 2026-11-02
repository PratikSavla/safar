package app.safar.ui.format

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test
import kotlin.test.assertEquals

class FormattersTest {
    private val zone: ZoneId = ZoneOffset.ofHoursMinutes(5, 30)

    /** 2026-03-12 18:30 local time. */
    private val now = LocalDateTime.of(2026, 3, 12, 18, 30).atZone(zone).toInstant().toEpochMilli()

    private fun ago(seconds: Long) = Formatters.age(now - seconds * 1000, now, zone)

    @Test
    fun `age covers now, seconds, minutes and hours`() {
        assertEquals("now", ago(0))
        assertEquals("now", Formatters.age(now + 5_000, now, zone))
        assertEquals("1 s ago", ago(1))
        assertEquals("3 s ago", ago(3))
        assertEquals("59 s ago", ago(59))
        assertEquals("1 min ago", ago(60))
        assertEquals("2 min ago", ago(150))
        assertEquals("59 min ago", ago(3599))
        assertEquals("1 h ago", ago(3600))
        assertEquals("23 h ago", ago(23 * 3600 + 59))
    }

    @Test
    fun `age switches to days by calendar date`() {
        assertEquals("yesterday", ago(24 * 3600))
        assertEquals("2 days ago", ago(2 * 24 * 3600))
        assertEquals("6 days ago", ago(6 * 24 * 3600))
        assertEquals("5 Mar", ago(7 * 24 * 3600))
    }

    @Test
    fun `odometer groups thousands`() {
        assertEquals("0", Formatters.odometer(0))
        assertEquals("999", Formatters.odometer(999))
        assertEquals("1,000", Formatters.odometer(1000))
        assertEquals("12,345", Formatters.odometer(12345))
        assertEquals("1,234,567", Formatters.odometer(1_234_567))
    }

    @Test
    fun `km1 keeps one decimal`() {
        assertEquals("0.0", Formatters.km1(0.0))
        assertEquals("12.3", Formatters.km1(12.34))
        assertEquals("12.4", Formatters.km1(12.35))
        assertEquals("1,234.6", Formatters.km1(1234.56))
        assertEquals("-2.5", Formatters.km1(-2.5))
    }

    @Test
    fun `navDistance uses metres then km`() {
        assertEquals(ValueUnit("0", "m"), Formatters.navDistance(0))
        assertEquals(ValueUnit("850", "m"), Formatters.navDistance(848))
        assertEquals(ValueUnit("990", "m"), Formatters.navDistance(994))
        assertEquals(ValueUnit("1.0", "km"), Formatters.navDistance(996))
        assertEquals(ValueUnit("1.2", "km"), Formatters.navDistance(1240))
        assertEquals(ValueUnit("99.9", "km"), Formatters.navDistance(99_900))
        assertEquals(ValueUnit("123", "km"), Formatters.navDistance(123_400))
        assertEquals("850 m", Formatters.navDistance(850).toString())
    }

    @Test
    fun `clock formats 24 h and 12 h`() {
        val t = LocalDateTime.of(2026, 3, 12, 14, 5).atZone(zone).toInstant().toEpochMilli()
        assertEquals("14:05", Formatters.clock(t, zone))
        assertEquals("2:05 PM", Formatters.clock(t, zone, is24h = false))
        val midnight = LocalDateTime.of(2026, 3, 12, 0, 7).atZone(zone).toInstant().toEpochMilli()
        assertEquals("00:07", Formatters.clock(midnight, zone))
        assertEquals("12:07 AM", Formatters.clock(midnight, zone, is24h = false))
    }

    @Test
    fun `clusterEta adds a colon and space`() {
        assertEquals("04:12 PM", Formatters.clusterEta("0412PM"))
        assertEquals("…", Formatters.clusterEta(null))
        assertEquals("12", Formatters.clusterEta("12"))
    }

    @Test
    fun `money uses Indian grouping for rupees`() {
        assertEquals("₹0", Formatters.money(0.0))
        assertEquals("₹999", Formatters.money(999.0))
        assertEquals("₹1,23,457", Formatters.money(123_456.6))
        assertEquals("₹12,34,567.50", Formatters.money(1_234_567.5, decimals = 2))
        assertEquals("€1,234,567.5", Formatters.money(1_234_567.5, symbol = "€", decimals = 1))
        assertEquals("-₹105", Formatters.money(-105.0))
    }

    @Test
    fun `groupDigits handles both systems`() {
        assertEquals("1,00,000", Formatters.groupDigits(100_000, indian = true))
        assertEquals("100,000", Formatters.groupDigits(100_000, indian = false))
        assertEquals("-1,234", Formatters.groupDigits(-1234, indian = false))
    }

    @Test
    fun `approx rounds or shows a dash`() {
        assertEquals("≈312", Formatters.approx(311.6))
        assertEquals("—", Formatters.approx(null))
    }

    @Test
    fun `cluster distance matches the LCD encoding`() {
        assertEquals(ValueUnit("0250", "M"), ClusterText.distance(250))
        assertEquals(ValueUnit("0850", "M"), ClusterText.distance(846))
        assertEquals(ValueUnit("01.2", "K"), ClusterText.distance(1200))
        assertEquals(ValueUnit("12.5", "K"), ClusterText.distance(12_500))
        assertEquals(ValueUnit("0123", "K"), ClusterText.distance(123_000))
        assertEquals("01.2", ClusterText.distance(1234).value)
        assertEquals("12.5", ClusterText.distance(12_480).value)
        assertEquals("0100", ClusterText.distance(99_950).value)
    }

    @Test
    fun `cluster distance rounds km ties like JS toFixed`() {
        assertEquals(ValueUnit("01.1", "K"), ClusterText.distance(1150))
        assertEquals(ValueUnit("02.0", "K"), ClusterText.distance(2050))
        assertEquals(ValueUnit("09.9", "K"), ClusterText.distance(9950))
        assertEquals(ValueUnit("12.3", "K"), ClusterText.distance(12_350))
    }

    @Test
    fun `cluster status words replace the distance`() {
        assertEquals("GPS OFF", ClusterText.statusWord('4'))
        assertEquals(null, ClusterText.statusWord('1'))
    }
}
