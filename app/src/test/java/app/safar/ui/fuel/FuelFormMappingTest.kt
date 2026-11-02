package app.safar.ui.fuel

import app.safar.core.RideState
import app.safar.data.FuelField
import app.safar.data.FuelFixtures
import app.safar.data.FuelUp
import app.safar.data.OdoSource
import app.safar.data.OdoSuggestion
import app.safar.data.OdoSuggestionSource
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FuelFormMappingTest {
    private val ups = FuelFixtures.FUEL_UPS
    private val live = OdoSuggestion(12_301, OdoSuggestionSource.BIKE, 1_000)
    private val last = OdoSuggestion(12_250, OdoSuggestionSource.BIKE_LAST, 1_000)
    private val none = OdoSuggestion(null, OdoSuggestionSource.NONE, null)

    @Test
    fun `parsing accepts a decimal comma and rejects junk`() {
        assertEquals(5.2, parseDecimal(" 5,2 "))
        assertEquals(712.0, parseDecimal("712"))
        assertNull(parseDecimal(""))
        assertNull(parseDecimal("abc"))
        assertEquals(12_301, parseOdo("12301"))
        assertNull(parseOdo("12301.5"))
        assertNull(parseOdo(" "))
    }

    @Test
    fun `a new form takes the bike odometer, read-only only while live`() {
        assertEquals(FuelForm(odo = "12301", editOdo = false), initialForm(null, live))
        assertEquals(FuelForm(odo = "12250", editOdo = true), initialForm(null, last))
        assertEquals(FuelForm(odo = "", editOdo = true), initialForm(null, none))
        assertEquals(FuelForm("6.7", "712", "12164", full = true, editOdo = true), initialForm(ups[5], none))
    }

    @Test
    fun `errors wait for typing, warnings show at once`() {
        val empty = FuelForm(odo = "12301")
        val v = validate(empty, ups, null, 12.0)
        assertFalse(v.ok)
        assertTrue(fieldMessages(empty, v).isEmpty())
        val big = FuelForm(litres = "14", total = "0", odo = "11000")
        val m = fieldMessages(big, validate(big, ups, null, 12.0))
        assertEquals(FieldMessage("More than the 12 L tank", error = false), m[FuelField.LITRES])
        assertEquals(FieldMessage("Enter the amount paid", error = true), m[FuelField.TOTAL])
        assertEquals(FieldMessage("Lower than the last fuel-up (12,164 km)", error = true), m[FuelField.ODO])
    }

    @Test
    fun `editing validates only against earlier fuel-ups`() {
        val entry = ups[3] // top-up at 11,894
        assertEquals(listOf(1L, 2L, 3L), validationBase(ups, entry).map { it.id })
        val form = initialForm(entry, none)
        assertTrue(validate(form, ups, entry, 12.0).ok)
    }

    @Test
    fun `odometer source follows the prototype`() {
        assertEquals(OdoSource.BIKE, odoSourceFor(FuelForm(odo = "12301"), null, live))
        assertEquals(OdoSource.EDITED, odoSourceFor(FuelForm(odo = "12305"), null, live))
        assertEquals(OdoSource.MANUAL, odoSourceFor(FuelForm(odo = "12305"), null, none))
        val bikeEntry = ups[5]
        assertEquals(OdoSource.BIKE, odoSourceFor(FuelForm(odo = "12164"), bikeEntry, none))
        assertEquals(OdoSource.EDITED, odoSourceFor(FuelForm(odo = "12170"), bikeEntry, none))
        assertEquals(OdoSource.MANUAL, odoSourceFor(FuelForm(odo = "11615"), ups[2], none))
    }

    @Test
    fun `odometer labels and the bike-said hint`() {
        assertEquals(OdoLabel("from bike · 3 s ago", OdoTone.MOSS), odoLabel(null, live, "3 s ago"))
        assertEquals(OdoLabel("last seen on bike · 2 h ago · check it", OdoTone.AMBER), odoLabel(null, last, "2 h ago"))
        assertEquals(OdoTone.RUST, odoLabel(null, none, null).tone)
        assertEquals(OdoLabel("saved as typed", OdoTone.INK), odoLabel(ups[2], none, null))
        assertEquals("bike said 12,301", bikeSaid(FuelForm(odo = "12310"), null, live))
        assertNull(bikeSaid(FuelForm(odo = "12301"), null, live))
        assertNull(bikeSaid(FuelForm(odo = "12310"), ups[5], live))
    }

    @Test
    fun `save builds a record only when valid`() {
        val form = FuelForm("6.5", "690", "12301", full = false, editOdo = false)
        val v = validate(form, ups, null, 12.0)
        assertEquals("₹106.15 per litre", perLitreLine(v.perLitre, "₹"))
        val saved = assertNotNull(toFuelUp(form, v, null, live, nowMillis = 5_000, barsNow = 2))
        assertEquals(FuelUp(0, 5_000, 12_301, 6.5, 690.0, false, OdoSource.BIKE, 2), saved)
        val bad = FuelForm("", "690", "12301")
        assertNull(toFuelUp(bad, validate(bad, ups, null, 12.0), null, live, 5_000, null))
        val edited = assertNotNull(toFuelUp(FuelForm("6.9", "712", "12164"), validate(FuelForm("6.9", "712", "12164"), ups, ups[5], 12.0), ups[5], none, 9, null))
        assertEquals(ups[5].copy(litres = 6.9), edited)
    }

    @Test
    fun `the form locks while moving and cannot save`() {
        val form = FuelForm("6.5", "690", "12301")
        val moving = fuelUpUiState(null, false, live, form, ups, 12.0, "₹", RideState(speedKmh = 42f, moving = true))
        assertEquals("Locked while moving (42 km/h). Stop to edit.", moving.locked)
        assertFalse(moving.canSave)
        val still = fuelUpUiState(null, false, live, form, ups, 12.0, "₹", RideState())
        assertTrue(still.canSave)
        assertEquals("price per litre appears here", perLitreLine(null, "₹"))
    }

    @Test
    fun `fuel notices are taken once and expire`() {
        FuelNotices.post("Saved · range updated", nowMillis = 1_000)
        assertEquals("Saved · range updated", FuelNotices.take(nowMillis = 2_000))
        assertNull(FuelNotices.take(nowMillis = 2_000))
        FuelNotices.post("Old", nowMillis = 1_000)
        assertNull(FuelNotices.take(nowMillis = 10_000))
    }
}
