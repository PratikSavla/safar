package app.safar.ui.parked

import app.safar.data.GpsFix
import app.safar.data.ParkedSpot
import java.time.ZoneOffset
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParkedMappingTest {
    private val spot = ParkedSpot(18.5000, 73.8000, 8f, 1_000, "Main Rd")

    @Test
    fun `bearing points along the compass`() {
        assertEquals(0.0, bearingDeg(18.0, 73.0, 18.1, 73.0), 1e-6)
        assertEquals(180.0, bearingDeg(18.1, 73.0, 18.0, 73.0), 1e-6)
        assertEquals(90.0, bearingDeg(0.0, 73.0, 0.0, 73.1), 1e-6)
        assertEquals(270.0, bearingDeg(0.0, 73.1, 0.0, 73.0), 1e-6)
    }

    @Test
    fun `direction words and needle angle`() {
        assertEquals("north", dirWord(0.0))
        assertEquals("north-east", dirWord(44.0))
        assertEquals("south-west", dirWord(225.0))
        assertEquals("north", dirWord(350.0))
        assertEquals(30f, pointerAngle(60.0, 30f))
        assertEquals(330f, pointerAngle(10.0, 40f))
        assertEquals(60f, pointerAngle(60.0, null))
    }

    @Test
    fun `distance reads in metres to 5 m, then km`() {
        assertEquals("85 m", distanceText(84))
        assertEquals("0 m", distanceText(2))
        assertEquals("1.2 km", distanceText(1_234))
    }

    @Test
    fun `state without a phone fix keeps the spot but no distance`() {
        val s = parkedUiState(spot, null, heading = null, locationAllowed = false)
        assertEquals("Main Rd", s.place)
        assertNull(s.distanceM)
        assertNull(s.pointerDeg)
        assertFalse(s.hasCompass)
        assertFalse(s.locationAllowed)
    }

    @Test
    fun `state with a fix south of the bike points north, minus the heading`() {
        // About 111 m south of the spot.
        val s = parkedUiState(spot, GpsFix(18.4990, 73.8000, 2_000, 5f), heading = 90f, locationAllowed = true)
        assertEquals(111, s.distanceM)
        assertEquals(0, s.bearingDeg)
        assertEquals(270f, s.pointerDeg)
        assertEquals(-90f, s.roseDeg)
        assertTrue(s.hasCompass)
        assertFalse(s.here)
        val there = parkedUiState(spot, GpsFix(18.50003, 73.8000, 2_000, 5f), heading = null, locationAllowed = true)
        assertTrue(there.here)
    }

    @Test
    fun `declination turns the magnetic heading into true north before the needle is worked out`() {
        // Magnetic 90, declination 10 degrees west: the top of the phone points 80 degrees from true north.
        val s = parkedUiState(spot, GpsFix(18.4990, 73.8000, 2_000, 5f), heading = 90f, locationAllowed = true, declination = -10f)
        assertEquals(0, s.bearingDeg)
        assertEquals(280f, s.pointerDeg)
        assertEquals(-80f, s.roseDeg)
        val east = parkedUiState(spot, GpsFix(18.4990, 73.8000, 2_000, 5f), heading = 355f, locationAllowed = true, declination = 12f)
        assertEquals(353f, east.pointerDeg)
        assertEquals(-7f, east.roseDeg)
    }

    @Test
    fun `place falls back to plain words and share text carries a maps link`() {
        val unnamed = spot.copy(label = null)
        assertEquals("Your parked spot", placeName(unnamed))
        assertEquals(
            "My bike is parked here: Main Rd\nhttps://www.google.com/maps/search/?api=1&query=18.500000,73.800000",
            shareText(spot),
        )
        assertEquals(
            "My bike is parked here\nhttps://www.google.com/maps/search/?api=1&query=18.500000,73.800000",
            shareText(unnamed),
        )
        assertNull(parkedUiState(null, null, null, true).spot)
    }

    @Test
    fun `parked page words never show seconds or raw bearings`() {
        assertEquals("Parked under a minute ago", parkedAgo(1_000, 31_000, ZoneOffset.UTC))
        assertEquals("Parked 12 min ago", parkedAgo(1_000, 1_000 + 12 * 60_000L, ZoneOffset.UTC))
        assertEquals("to the north-east", directionText(44))
        assertNull(phoneFixNote(1_000, 5_000, ZoneOffset.UTC), "nothing while the phone's fix is fresh")
        assertEquals("Your location: last seen 12 min ago", phoneFixNote(1_000, 1_000 + 12 * 60_000L, ZoneOffset.UTC))
        assertEquals("Compass. Waiting for your phone’s location.", compassDescription(parkedUiState(spot, null, null, true)))
        val near = parkedUiState(spot, GpsFix(18.5000, 73.8000, 2_000, 5f), null, true)
        assertEquals("You’re at your bike.", compassDescription(near))
        val south = parkedUiState(spot, GpsFix(18.4990, 73.8000, 2_000, 5f), null, true)
        assertEquals("Your bike is 110 m away, to the north.", compassDescription(south))
        assertEquals(
            "The spot is accurate to about 8 m. Hold your phone flat and the arrow points to your bike.",
            accuracyText(8f, hasCompass = true),
        )
        assertTrue(accuracyText(8f, hasCompass = false).contains("no compass"))
    }

    @Test
    fun `rotation takes the short way round`() {
        assertEquals(370f, shortestRotation(350f, 10f))
        assertEquals(-10f, shortestRotation(10f, 350f))
        assertEquals(90f, shortestRotation(0f, 90f))
    }

    @Test
    fun `park here keeps the fix's own time and refuses fixes over ten minutes old`() {
        val fix = GpsFix(18.5, 73.8, 60_000, 5f)
        assertEquals(ParkedSpot(18.5, 73.8, 5f, 60_000, null), parkHereSpot(fix, 60_000 + PARK_HERE_MAX_AGE_MS))
        assertNull(parkHereSpot(fix, 60_001 + PARK_HERE_MAX_AGE_MS))
        assertNull(parkHereSpot(null, 0))
        assertEquals(0f, parkHereSpot(fix.copy(accuracyM = null), 60_000)?.accuracyM)
    }
}
