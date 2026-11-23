package app.safar.ui.fuel

import app.safar.data.FuelField
import app.safar.data.FuelFixtures
import app.safar.data.OdoSuggestion
import app.safar.data.OdoSuggestionSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FuelFormInputsTest {
    private val ups = FuelFixtures.FUEL_UPS
    private val now = FuelFixtures.NOW
    private val utc = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 2).toEpochDay()
    private val none = OdoSuggestion(null, OdoSuggestionSource.NONE, null)

    @Test
    fun `the fill date defaults to today for a new receipt and the saved day for an edit`() {
        assertEquals(today, fillDay(FuelForm(), null, now, utc))
        assertEquals(today - 2, fillDay(FuelForm(), ups[5], now, utc))
        assertEquals(today - 9, fillDay(FuelForm(day = today - 9), ups[5], now, utc))
    }

    @Test
    fun `a back-dated fill is saved at noon, today at now, an unchanged edit at its own time`() {
        assertEquals(now, recordMillis(FuelForm(), null, now, utc))
        assertEquals(now, recordMillis(FuelForm(day = today), null, now, utc))
        assertEquals(now, recordMillis(FuelForm(day = today + 3), null, now, utc))
        assertEquals(Instant.parse("2026-10-29T12:00:00Z").toEpochMilli(), recordMillis(FuelForm(day = today - 4), null, now, utc))
        assertEquals(ups[5].atMillis, recordMillis(FuelForm(day = today - 2), ups[5], now, utc))
        assertEquals(ups[5].atMillis, recordMillis(FuelForm(), ups[5], now, utc))
    }

    @Test
    fun `day words`() {
        assertEquals("today", dayWord(today, today))
        assertEquals("yesterday", dayWord(today - 1, today))
        assertEquals("Thu, 29 Oct", dayWord(today - 4, today))
        assertEquals("2 Nov 2025", dayWord(today - 365, today))
    }

    @Test
    fun `a back-dated fill is checked against the fill-ups on both sides of it`() {
        // 2026-10-27 sits between fuel-up 4 (11,894 km, 25 Oct) and fuel-up 5 (11,952 km, 29 Oct).
        val at = recordMillis(FuelForm(day = today - 6), null, now, utc)
        assertEquals(listOf(1L, 2L, 3L, 4L), validationBase(ups, null, at).map { it.id })
        assertEquals(listOf(5L, 6L), laterFuelUps(ups, null, at).map { it.id })
        assertTrue(validate(FuelForm("3", "300", "11920"), ups, null, 12.0, at).ok)
        val high = FuelForm("3", "300", "12000")
        assertEquals(
            FieldMessage("Higher than a later fuel-up (11,952 km)", error = true),
            fieldMessages(high, validate(high, ups, null, 12.0, at))[FuelField.ODO],
        )
        val low = FuelForm("3", "300", "11800")
        assertEquals(
            FieldMessage("Lower than the last fuel-up (11,894 km)", error = true),
            fieldMessages(low, validate(low, ups, null, 12.0, at))[FuelField.ODO],
        )
    }

    @Test
    fun `a back-dated receipt keeps its date and drops today's fuel bars`() {
        val form = FuelForm("3", "300", "11920", day = today - 6)
        val at = recordMillis(form, null, now, utc)
        val saved = assertNotNull(toFuelUp(form, validate(form, ups, null, 12.0, at), null, none, now, barsNow = 4, zone = utc))
        assertEquals(at, saved.atMillis)
        assertNull(saved.barsBefore)
    }

    @Test
    fun `litres come from the amount and the price per litre`() {
        val form = FuelForm(total = "500", price = "106.15", odo = "12301", byPrice = true)
        assertEquals(4.71, formLitres(form))
        assertNull(formLitres(form.copy(price = "")))
        assertNull(formLitres(form.copy(total = "0")))
        val v = validate(form, ups, null, 12.0)
        assertTrue(v.ok)
        assertEquals("4.71 L at this price", amountLine(form, v, "₹"))
        assertEquals("Litres show here", amountLine(form.copy(price = ""), v, "₹"))
        val saved = assertNotNull(toFuelUp(form, v, null, none, now, null, utc))
        assertEquals(4.71, saved.litres)
        assertEquals(500.0, saved.totalPaid)
    }

    @Test
    fun `a missing price is asked for under the price, a missing amount only under the amount`() {
        val noPrice = FuelForm(total = "500", price = "x", odo = "12301", byPrice = true)
        assertEquals(
            FieldMessage("Enter the price per litre", error = true),
            fieldMessages(noPrice, validate(noPrice, ups, null, 12.0))[FuelField.LITRES],
        )
        val noTotal = FuelForm(total = "", price = "106", odo = "12301", byPrice = true)
        val m = fieldMessages(noTotal, validate(noTotal, ups, null, 12.0))
        assertNull(m[FuelField.LITRES])
        assertNull(m[FuelField.TOTAL])
        val big = FuelForm(total = "1500", price = "100", odo = "12301", byPrice = true)
        assertEquals(FieldMessage("More than the 12 L tank", error = false), fieldMessages(big, validate(big, ups, null, 12.0))[FuelField.LITRES])
    }

    @Test
    fun `switching fills the opened field from the other`() {
        val litres = FuelForm(litres = "6.5", total = "690")
        assertEquals("106.15", switchByPrice(litres, true).price)
        val price = FuelForm(total = "500", price = "106.15", byPrice = true)
        assertEquals("4.71", switchByPrice(price, false).litres)
        assertEquals("9", switchByPrice(price.copy(litres = "9"), false).litres)
        assertEquals(price, switchByPrice(price, true))
    }
}
