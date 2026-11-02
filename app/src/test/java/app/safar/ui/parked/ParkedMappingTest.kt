package app.safar.ui.parked

import app.safar.data.GpsFix
import app.safar.data.ParkedSpot
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
    fun `place falls back to coordinates and share text carries a maps link`() {
        val unnamed = spot.copy(label = null)
        assertEquals("18.50000, 73.80000", placeName(unnamed))
        assertEquals(
            "My bike is parked here: Main Rd\nhttps://www.google.com/maps/search/?api=1&query=18.500000,73.800000",
            shareText(spot),
        )
        assertNull(parkedUiState(null, null, null, true).spot)
    }

    @Test
    fun `rotation takes the short way round`() {
        assertEquals(370f, shortestRotation(350f, 10f))
        assertEquals(-10f, shortestRotation(10f, 350f))
        assertEquals(90f, shortestRotation(0f, 90f))
    }
}
