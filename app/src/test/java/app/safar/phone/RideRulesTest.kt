package app.safar.phone

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RideRulesTest {
    @Test
    fun movingStartsAboveTenAndClearsAfterTenSecondsBelowSix() {
        val m = MotionDetector()
        m.onSpeed(9.9f, 0)
        assertFalse(m.moving)
        m.onSpeed(10.5f, 1_000)
        assertTrue(m.moving)
        m.onSpeed(7f, 2_000) // between thresholds: stays moving
        assertTrue(m.moving)
        m.onSpeed(5f, 3_000)
        m.onSpeed(4f, 12_000)
        assertTrue(m.moving, "9 s below 6 km/h")
        m.onSpeed(4f, 13_000)
        assertFalse(m.moving, "10 s below 6 km/h")
    }

    @Test
    fun slowPhaseInterruptedRestartsTimer() {
        val m = MotionDetector()
        m.onSpeed(20f, 0)
        m.onSpeed(3f, 1_000)
        m.onSpeed(8f, 9_000) // back above 6: timer resets
        m.onSpeed(3f, 10_000)
        m.onSpeed(3f, 19_000)
        assertTrue(m.moving)
        m.onSpeed(3f, 20_000)
        assertFalse(m.moving)
    }

    @Test
    fun speedGoesStaleAfterFiveSeconds() {
        val m = MotionDetector()
        m.onSpeed(30f, 0)
        assertEquals(30f, m.speedAt(5_000))
        assertNull(m.speedAt(5_001))
        assertTrue(m.evaluate(6_000))
        assertTrue(m.evaluate(15_000))
        assertFalse(m.evaluate(16_000), "no fix counts as 0 km/h for 10 s")
    }

    @Test
    fun bikeConnectionStartsAndUserDisconnectEnds() {
        val rules = TripRules()
        assertEquals(TripDecision.None, rules.evaluate(0, BikeLink.OFF, null))
        assertEquals(TripDecision.Start(viaBike = true), rules.evaluate(1_000, BikeLink.CONNECTED, null))
        assertEquals(TripDecision.None, rules.evaluate(2_000, BikeLink.CONNECTED, 40f))
        assertEquals(TripDecision.End, rules.evaluate(3_000, BikeLink.OFF, 0f))
        assertFalse(rules.recording)
    }

    @Test
    fun lostLinkEndsAfterGraceUnlessItReconnects() {
        val rules = TripRules(disconnectGraceMs = 90_000)
        rules.evaluate(0, BikeLink.CONNECTED, null)
        assertEquals(TripDecision.None, rules.evaluate(10_000, BikeLink.LOST, 30f))
        assertEquals(TripDecision.None, rules.evaluate(60_000, BikeLink.CONNECTED, 30f), "drop-out")
        assertEquals(TripDecision.None, rules.evaluate(70_000, BikeLink.LOST, 0f))
        assertEquals(TripDecision.None, rules.evaluate(159_999, BikeLink.LOST, 0f))
        assertEquals(TripDecision.End, rules.evaluate(160_000, BikeLink.LOST, 0f))
        assertEquals(TripDecision.None, rules.evaluate(170_000, BikeLink.LOST, 0f), "no restart while lost")
    }

    @Test
    fun movementForTwentySecondsStartsTrip() {
        val rules = TripRules()
        assertEquals(TripDecision.None, rules.evaluate(0, BikeLink.OFF, 15f))
        assertEquals(TripDecision.None, rules.evaluate(10_000, BikeLink.OFF, 15f))
        assertEquals(TripDecision.None, rules.evaluate(15_000, BikeLink.OFF, 8f), "dip resets")
        assertEquals(TripDecision.None, rules.evaluate(16_000, BikeLink.OFF, 15f))
        assertEquals(TripDecision.None, rules.evaluate(35_000, BikeLink.OFF, 15f))
        assertEquals(TripDecision.Start(viaBike = false), rules.evaluate(36_000, BikeLink.OFF, 15f))
    }

    @Test
    fun movementTripEndsAfterIdleUnlessBikeJoins() {
        val rules = TripRules(startMovingMs = 0, idleEndMs = 300_000)
        rules.evaluate(0, BikeLink.OFF, 20f)
        assertEquals(TripDecision.None, rules.evaluate(299_000, BikeLink.OFF, null))
        assertEquals(TripDecision.End, rules.evaluate(300_000, BikeLink.OFF, null))

        rules.evaluate(400_000, BikeLink.OFF, 20f)
        rules.evaluate(401_000, BikeLink.CONNECTED, 20f) // bike joins the trip
        assertEquals(TripDecision.None, rules.evaluate(800_000, BikeLink.LOST, null))
        assertEquals(TripDecision.End, rules.evaluate(890_000, BikeLink.LOST, null))
    }

    @Test
    fun shortTripsAreDiscarded() {
        assertFalse(keepTrip(0.29))
        assertTrue(keepTrip(0.3))
        assertEquals(1.2, tripDistanceKm(1.2, 100, 105))
        assertEquals(5.0, tripDistanceKm(0.0, 100, 105))
        assertEquals(0.0, tripDistanceKm(0.0, null, 105))
    }

    @Test
    fun manualStopPreventsImmediateRestartUntilMovement() {
        val rules = TripRules()
        assertEquals(TripDecision.Start(viaBike = true), rules.evaluate(1_000, BikeLink.CONNECTED, 0f))
        assertTrue(rules.recording)

        rules.stop()
        assertFalse(rules.recording)

        // Stationary while connected does not immediately restart
        assertEquals(TripDecision.None, rules.evaluate(2_000, BikeLink.CONNECTED, 0f))
        assertFalse(rules.recording)

        // As soon as bike moves above 10 km/h, new ride starts easily!
        assertEquals(TripDecision.Start(viaBike = true), rules.evaluate(3_000, BikeLink.CONNECTED, 15f))
        assertTrue(rules.recording)
    }
}
