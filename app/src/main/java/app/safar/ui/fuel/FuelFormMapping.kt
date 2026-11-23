package app.safar.ui.fuel

import app.safar.data.FuelField
import app.safar.data.FuelUp
import app.safar.data.FuelUpValidation
import app.safar.data.OdoSource
import app.safar.data.OdoSuggestion
import app.safar.data.OdoSuggestionSource
import app.safar.data.validateFuelUp
import app.safar.ui.copy.Phrases
import app.safar.ui.format.Formatters
import app.safar.ui.format.FreshnessText
import java.time.ZoneId

// Pure logic of the fuel-up form (ux/variant-b/js/screens/fuel-form.js). Validation is data/validateFuelUp.

/**
 * What the rider has typed. [editOdo] = the odometer is an open field rather than the bike's reading. [day] is
 * the fill's date chosen by the rider (days since 1970-01-01); null keeps the default: today for a new receipt,
 * the saved time for an edit. [byPrice] = the rider types the price per litre ([price]) and Safar works out
 * the litres from the amount paid.
 */
data class FuelForm(
    val litres: String = "",
    val total: String = "",
    val odo: String = "",
    val full: Boolean = true,
    val editOdo: Boolean = true,
    val day: Long? = null,
    val byPrice: Boolean = false,
    val price: String = "",
)

/** A line under a field: an error blocks saving, a warning does not. */
data class FieldMessage(val text: String, val error: Boolean)

/** Colour of the odometer source line: moss = live bike, amber = check it, rust = type it, ink = saved entry. */
enum class OdoTone { MOSS, AMBER, RUST, INK }

data class OdoLabel(val text: String, val tone: OdoTone)

/** "5,2" and "5.2" both read as 5.2; empty or junk is null. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

/** Whole km only, as on the bike's display. Null when empty or not a whole number. */
fun parseOdo(text: String): Int? = text.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()

/** First form state: the saved entry, or empty with the bike's odometer (read-only while it is live). */
fun initialForm(entry: FuelUp?, sugg: OdoSuggestion): FuelForm {
    if (entry != null) {
        return FuelForm(jsNumber(entry.litres), jsNumber(entry.totalPaid), entry.odometerKm.toString(), entry.full, editOdo = true)
    }
    return FuelForm(odo = sugg.value?.toString().orEmpty(), editOdo = sugg.source != OdoSuggestionSource.BIKE)
}

/** Fuel-ups logged at or before [atMillis], without the entry being edited. */
fun validationBase(fuelUps: List<FuelUp>, entry: FuelUp?, atMillis: Long = entry?.atMillis ?: Long.MAX_VALUE): List<FuelUp> =
    fuelUps.filter { it.id != entry?.id && it.atMillis <= atMillis }

/** Fuel-ups logged after [atMillis], without the entry being edited: the new odometer may not pass theirs. */
fun laterFuelUps(fuelUps: List<FuelUp>, entry: FuelUp?, atMillis: Long): List<FuelUp> =
    fuelUps.filter { it.id != entry?.id && it.atMillis > atMillis }

/** [atMillis] is when the entry will be saved as filled ([recordMillis]); by default the entry's own time, or after every fuel-up. */
fun validate(form: FuelForm, fuelUps: List<FuelUp>, entry: FuelUp?, tank: Double, atMillis: Long = entry?.atMillis ?: Long.MAX_VALUE): FuelUpValidation =
    validateFuelUp(
        parseOdo(form.odo), formLitres(form), parseDecimal(form.total),
        validationBase(fuelUps, entry, atMillis), tank, laterFuelUps(fuelUps, entry, atMillis),
    )

/** Errors only once the field has text (no red on an empty form); warnings always. */
fun fieldMessages(form: FuelForm, v: FuelUpValidation): Map<FuelField, FieldMessage> {
    val typed = mapOf(FuelField.LITRES to form.litres, FuelField.TOTAL to form.total, FuelField.ODO to form.odo)
    if (form.byPrice) return priceMessages(form, v, fieldMessages(form.copy(byPrice = false, litres = form.price), v))
    return FuelField.entries.mapNotNull { f ->
        val err = v.errors[f]?.takeIf { typed[f].orEmpty().isNotBlank() }
        when {
            err != null -> f to FieldMessage(err, error = true)
            v.errors[f] == null && v.warnings[f] != null -> f to FieldMessage(v.warnings.getValue(f), error = false)
            else -> null
        }
    }.toMap()
}

/** Where the saved odometer came from (fuel-form.js `odoSource`). */
fun odoSourceFor(form: FuelForm, entry: FuelUp?, sugg: OdoSuggestion): OdoSource {
    val odo = parseOdo(form.odo)
    if (entry != null) {
        return when {
            odo == entry.odometerKm -> entry.odoSource
            entry.odoSource == OdoSource.MANUAL -> OdoSource.MANUAL
            else -> OdoSource.EDITED
        }
    }
    if (sugg.value == null) return OdoSource.MANUAL
    return if (odo == sugg.value) OdoSource.BIKE else OdoSource.EDITED
}

/**
 * Where the odometer came from, in the rider's words (fuel-form.js `odoBlock`). No age while the bike is live;
 * a reading kept from the last ride says which day it is from. Exact ages are Developer mode only.
 */
fun odoLabel(
    entry: FuelUp?,
    sugg: OdoSuggestion,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    backDated: Boolean = false,
): OdoLabel {
    if (entry != null) return OdoLabel(savedAs(entry.odoSource), OdoTone.INK)
    val now = sugg.value
    // The bike's reading is today's: a fill on an earlier day had a lower odometer.
    if (backDated && now != null) return OdoLabel("Your bike shows ${Formatters.odometer(now)} km now. Type the odometer on that day.", OdoTone.AMBER)
    return when (sugg.source) {
        OdoSuggestionSource.BIKE -> OdoLabel("From your bike", OdoTone.MOSS)
        OdoSuggestionSource.BIKE_LAST -> {
            val from = sugg.atMillis?.let { FreshnessText.lastRide(it, nowMillis, zone) } ?: "From your last ride"
            OdoLabel("$from. Check it on your bike.", OdoTone.AMBER)
        }
        OdoSuggestionSource.NONE -> OdoLabel("Bike not connected. Type the odometer from ${Phrases.YOUR_BIKES_DISPLAY}.", OdoTone.RUST)
    }
}

private fun savedAs(source: OdoSource): String = when (source) {
    OdoSource.BIKE -> "Saved from your bike"
    OdoSource.EDITED -> "Saved with your correction"
    OdoSource.MANUAL -> "Saved as you typed it"
}

/** "Your bike showed 12,164 km" once the rider changed the bike's number on a new entry. */
fun bikeSaid(form: FuelForm, entry: FuelUp?, sugg: OdoSuggestion): String? {
    val said = sugg.value ?: return null
    if (entry != null || parseOdo(form.odo) == said) return null
    return "Your bike showed ${Formatters.odometer(said)} km"
}

/** "₹106.27 per litre", or the placeholder until litres and amount are both in. */
fun perLitreLine(perLitre: Double?, currency: String): String =
    if (perLitre == null) "Price per litre shows here" else "${Formatters.money(perLitre, currency, 2)} per litre"

/**
 * The record to save, or null while the form has errors. [barsNow] = live fuel bars, if connected; kept only for
 * a fill logged today, since a back-dated fill was not made with the bars on the cluster now.
 */
fun toFuelUp(
    form: FuelForm,
    v: FuelUpValidation,
    entry: FuelUp?,
    sugg: OdoSuggestion,
    nowMillis: Long,
    barsNow: Int?,
    zone: ZoneId = ZoneId.systemDefault(),
): FuelUp? {
    if (!v.ok) return null
    val odo = parseOdo(form.odo) ?: return null
    val litres = formLitres(form) ?: return null
    val total = parseDecimal(form.total) ?: return null
    val source = odoSourceFor(form, entry, sugg)
    val at = recordMillis(form, entry, nowMillis, zone)
    return entry?.copy(atMillis = at, odometerKm = odo, litres = litres, totalPaid = total, full = form.full, odoSource = source)
        ?: FuelUp(
            atMillis = at, odometerKm = odo, litres = litres, totalPaid = total, full = form.full, odoSource = source,
            barsBefore = barsNow.takeIf { at == nowMillis },
        )
}
