package app.safar.data

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Expected values come from running the places.js functions with node. */
class PlacesTest {
    @Test
    fun parsesNameAddressAndLink() {
        val p = parseMapsShare("Lake View Point\nLake Rd, Sample Town\nhttps://maps.app.goo.gl/AbCdEf123")
        assertEquals(
            SharedPlace(
                label = "Lake View Point",
                detail = "Lake Rd, Sample Town",
                query = "Lake View Point, Lake Rd, Sample Town",
                link = "https://maps.app.goo.gl/AbCdEf123",
                source = PlaceSource.MAPS,
            ),
            p,
        )
    }

    @Test
    fun acceptsOnlyPlausiblySizedShares() {
        assertEquals("Main Rd", acceptShare("Main Rd"))
        assertNull(acceptShare("   "))
        assertNull(acceptShare(null))
        assertEquals(SHARE_MAX_CHARS, acceptShare("x".repeat(SHARE_MAX_CHARS))?.length)
        assertNull(acceptShare("x".repeat(SHARE_MAX_CHARS + 1)))
    }

    @Test
    fun joinsMultipleAddressLinesAndTrimsCrLf() {
        val p = parseMapsShare("  Fuel stop \r\nMain Rd\r\n\r\nNH 48\r\n")
        assertEquals("Fuel stop", p?.label)
        assertEquals("Main Rd, NH 48", p?.detail)
        assertEquals("Fuel stop, Main Rd, NH 48", p?.query)
        assertNull(p?.link)
    }

    @Test
    fun linkOnlyGetsDefaultLabel() {
        val p = parseMapsShare("https://maps.app.goo.gl/abc")
        assertEquals("Shared place", p?.label)
        assertEquals("", p?.detail)
        assertEquals("Shared place", p?.query)
        assertEquals("https://maps.app.goo.gl/abc", p?.link)
    }

    @Test
    fun blankTextIsNull() {
        assertNull(parseMapsShare("  \n "))
        assertNull(parseMapsShare(null))
    }

    @Test
    fun encodesLikeEncodeUriComponent() {
        assertEquals(
            "Caf%C3%A9%20Rider%2C%20Main%20Rd%20(NH%2048)%20%26%2010.5%2C70.25%20~*'!",
            encodeUriComponent("Café Rider, Main Rd (NH 48) & 10.5,70.25 ~*'!"),
        )
    }

    @Test
    fun navUriUsesTwoWheelerMode() {
        assertEquals("google.navigation:q=Main%20Rd%2C%20NH%2048&mode=l", navUri("Main Rd, NH 48"))
        assertEquals("google.navigation:q=10.5%2C70.25&mode=l", navUri("10.5,70.25"))
    }

    @Test
    fun parsesLatLng() {
        assertEquals(LatLng(10.5, 70.25), parseLatLng("10.5,70.25"))
        assertEquals(LatLng(-33.5, -70.25), parseLatLng(" -33.5 , -70.25 "))
        assertEquals(LatLng(12.0, 77.0), parseLatLng("12,77"))
        assertNull(parseLatLng("91,10"))
        assertNull(parseLatLng("10,181"))
        assertNull(parseLatLng("Main Rd"))
        assertNull(parseLatLng("10.5"))
        assertNull(parseLatLng(null))
    }

    @Test
    fun latLngQueryHasSixDecimals() {
        assertEquals("10.500000,70.250000", latLngQuery(10.5, 70.25))
    }
}
