package app.safar.data

import app.safar.data.FuelFixtures.FUEL_UPS
import app.safar.data.FuelFixtures.cluster
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Expected messages come from running ux/shared/fuel.js `validateFuelUp` with node. */
class FuelValidationTest {
    @Test
    fun validEntry() {
        val v = validateFuelUp(12300, 8.2, 870.0, FUEL_UPS, 12.0)
        assertTrue(v.ok)
        assertTrue(v.warnings.isEmpty())
        assertEquals(106.1, v.perLitre)
    }

    @Test
    fun missingAmountsAndLowOdometer() {
        val v = validateFuelUp(12000, 0.0, 0.0, FUEL_UPS, 12.0)
        assertFalse(v.ok)
        assertEquals("Enter the litres from the pump", v.errors[FuelField.LITRES])
        assertEquals("Enter the amount paid", v.errors[FuelField.TOTAL])
        assertEquals("Lower than the last fuel-up (12,164 km)", v.errors[FuelField.ODO])
        assertNull(v.perLitre)
    }

    @Test
    fun warningsAllowSave() {
        val v = validateFuelUp(13000, 12.7, 1300.0, FUEL_UPS, 12.0)
        assertTrue(v.ok)
        assertEquals("More than the 12 L tank", v.warnings[FuelField.LITRES])
        assertEquals("836 km since the last fuel-up — a missed entry?", v.warnings[FuelField.ODO])
        assertEquals(102.36, v.perLitre)
    }

    @Test
    fun sameOdometerAsLastIsAllowed() {
        val v = validateFuelUp(12164, 12.6, 1300.0, FUEL_UPS, 12.0)
        assertTrue(v.ok)
        assertTrue(v.warnings.isEmpty())
        assertEquals(103.17, v.perLitre)
    }

    @Test
    fun missingOdometerIsAnError() {
        val v = validateFuelUp(null, 3.0, 300.0, FUEL_UPS, 12.0)
        assertEquals("Enter the odometer (whole km)", v.errors[FuelField.ODO])
        assertEquals(100.0, v.perLitre)
        assertEquals("Enter the odometer (whole km)", validateFuelUp(0, 3.0, 300.0, FUEL_UPS, 12.0).errors[FuelField.ODO])
    }

    @Test
    fun scooterTankFormatsLikeJs() {
        assertEquals("More than the 5.2 L tank", validateFuelUp(12, 5.5, 500.0, emptyList(), 5.2).warnings[FuelField.LITRES])
        assertEquals("More than the 5 L tank", validateFuelUp(12, 5.5, 500.0, emptyList(), 5.0).warnings[FuelField.LITRES])
        assertEquals(90.91, validateFuelUp(12, 5.5, 500.0, emptyList(), 5.0).perLitre)
    }

    @Test
    fun odoSuggestionSources() {
        val live = cluster(12261, 4, at = 1_000)
        val last = cluster(12250, 4, at = 500)
        assertEquals(OdoSuggestion(12261, OdoSuggestionSource.BIKE, 1_000), odoSuggestion(live, last, connected = true))
        assertEquals(OdoSuggestion(12261, OdoSuggestionSource.BIKE_LAST, 1_000), odoSuggestion(live, last, connected = false))
        assertEquals(OdoSuggestion(12250, OdoSuggestionSource.BIKE_LAST, 500), odoSuggestion(null, last, connected = true))
        assertEquals(OdoSuggestion(null, OdoSuggestionSource.NONE, null), odoSuggestion(null, null, connected = false))
    }
}
