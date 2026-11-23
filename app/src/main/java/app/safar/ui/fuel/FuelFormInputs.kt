package app.safar.ui.fuel

import app.safar.data.FuelField
import app.safar.data.FuelUp
import app.safar.data.FuelUpValidation
import app.safar.data.jsRound
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// The fuel-up form's date and price-per-litre inputs. Pure, unit-tested.

private val DAY_THIS_YEAR = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.UK)
private val DAY_OTHER_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)

/** Local date of [atMillis] as days since 1970-01-01. */
fun localDay(atMillis: Long, zone: ZoneId): Long = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate().toEpochDay()

/** The day the form shows: the rider's choice, else the saved entry's day, else today. */
fun fillDay(form: FuelForm, entry: FuelUp?, nowMillis: Long, zone: ZoneId): Long =
    form.day ?: localDay(entry?.atMillis ?: nowMillis, zone)

/**
 * When the fuel-up is saved as filled. No date chosen, or the entry's own day: its saved time (now for a new
 * one). Today: now. An earlier day: noon that day, so it sorts between that day's morning and evening fills.
 */
fun recordMillis(form: FuelForm, entry: FuelUp?, nowMillis: Long, zone: ZoneId): Long {
    val day = form.day ?: return entry?.atMillis ?: nowMillis
    if (entry != null && localDay(entry.atMillis, zone) == day) return entry.atMillis
    if (day >= localDay(nowMillis, zone)) return nowMillis
    return LocalDate.ofEpochDay(day).atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
}

/** "today", "yesterday", "Sat, 21 Nov", or "21 Nov 2025" for another year. */
fun dayWord(day: Long, today: Long): String {
    val date = LocalDate.ofEpochDay(day)
    return when {
        day == today -> "today"
        day == today - 1 -> "yesterday"
        date.year == LocalDate.ofEpochDay(today).year -> DAY_THIS_YEAR.format(date)
        else -> DAY_OTHER_YEAR.format(date)
    }
}

/** Litres to save: typed, or worked out from the amount paid and the price per litre (2 decimals). */
fun formLitres(form: FuelForm): Double? {
    if (!form.byPrice) return parseDecimal(form.litres)
    val price = parseDecimal(form.price)?.takeIf { it > 0 } ?: return null
    val total = parseDecimal(form.total)?.takeIf { it > 0 } ?: return null
    return jsRound(total / price, 2)
}

/**
 * Messages for the price-per-litre layout: the litres line moves under the price field. A missing price says
 * so; a missing amount is said once, under the amount.
 */
internal fun priceMessages(form: FuelForm, v: FuelUpValidation, base: Map<FuelField, FieldMessage>): Map<FuelField, FieldMessage> {
    val litres = base[FuelField.LITRES] ?: return base
    if (!litres.error) return base
    val priceOk = (parseDecimal(form.price) ?: 0.0) > 0
    return if (priceOk || v.errors[FuelField.LITRES] == null) base - FuelField.LITRES
    else base + (FuelField.LITRES to FieldMessage("Enter the price per litre", error = true))
}

/** The line under the amounts: price per litre from litres, or litres from the price. */
fun amountLine(form: FuelForm, v: FuelUpValidation, currency: String): String {
    if (!form.byPrice) return perLitreLine(v.perLitre, currency)
    val litres = formLitres(form) ?: return "Litres show here"
    return "${jsNumber(litres)} L at this price"
}

/**
 * Switches between typing litres and typing the price per litre. The field being opened is filled from the
 * other when it is empty, so the rider does not type the receipt twice.
 */
fun switchByPrice(form: FuelForm, byPrice: Boolean): FuelForm {
    if (form.byPrice == byPrice) return form
    if (byPrice) {
        val litres = parseDecimal(form.litres)?.takeIf { it > 0 }
        val total = parseDecimal(form.total)?.takeIf { it > 0 }
        val price = if (form.price.isBlank() && litres != null && total != null) jsNumber(jsRound(total / litres, 2)) else form.price
        return form.copy(byPrice = true, price = price)
    }
    val litres = if (form.litres.isBlank()) formLitres(form)?.let(::jsNumber).orEmpty() else form.litres
    return form.copy(byPrice = false, litres = litres)
}
