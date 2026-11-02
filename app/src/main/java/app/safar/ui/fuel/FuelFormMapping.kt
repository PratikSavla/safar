package app.safar.ui.fuel

import app.safar.data.FuelField
import app.safar.data.FuelUp
import app.safar.data.FuelUpValidation
import app.safar.data.OdoSource
import app.safar.data.OdoSuggestion
import app.safar.data.OdoSuggestionSource
import app.safar.data.validateFuelUp
import app.safar.ui.format.Formatters

// Pure logic of the fuel-up form (ux/variant-b/js/screens/fuel-form.js). Validation is data/validateFuelUp.

/** What the rider has typed. [editOdo] = the odometer is an open field rather than the bike's reading. */
data class FuelForm(
    val litres: String = "",
    val total: String = "",
    val odo: String = "",
    val full: Boolean = true,
    val editOdo: Boolean = true,
)

/** A line under a field: an error blocks saving, a warning does not. */
data class FieldMessage(val text: String, val error: Boolean)

/** Colour of the odometer source line: moss = live bike, amber = check it, rust = type it, ink = saved entry. */
enum class OdoTone { MOSS, AMBER, RUST, INK }

data class OdoLabel(val text: String, val tone: OdoTone)

/** "5,2" and "5.2" both read as 5.2; empty or junk is null. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

/** Whole km only, as on the cluster. Null when empty or not a whole number. */
fun parseOdo(text: String): Int? = text.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()

/** First form state: the saved entry, or empty with the bike's odometer (read-only while it is live). */
fun initialForm(entry: FuelUp?, sugg: OdoSuggestion): FuelForm {
    if (entry != null) {
        return FuelForm(jsNumber(entry.litres), jsNumber(entry.totalPaid), entry.odometerKm.toString(), entry.full, editOdo = true)
    }
    return FuelForm(odo = sugg.value?.toString().orEmpty(), editOdo = sugg.source != OdoSuggestionSource.BIKE)
}

/** Fuel-ups to validate against: when editing, only those before the entry, without the entry itself. */
fun validationBase(fuelUps: List<FuelUp>, entry: FuelUp?): List<FuelUp> =
    if (entry == null) fuelUps else fuelUps.filter { it.id != entry.id && it.odometerKm <= entry.odometerKm }

fun validate(form: FuelForm, fuelUps: List<FuelUp>, entry: FuelUp?, tank: Double): FuelUpValidation =
    validateFuelUp(parseOdo(form.odo), parseDecimal(form.litres), parseDecimal(form.total), validationBase(fuelUps, entry), tank)

/** Errors only once the field has text (no red on an empty form); warnings always. */
fun fieldMessages(form: FuelForm, v: FuelUpValidation): Map<FuelField, FieldMessage> {
    val typed = mapOf(FuelField.LITRES to form.litres, FuelField.TOTAL to form.total, FuelField.ODO to form.odo)
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

/** The odometer's source line (fuel-form.js `odoBlock`). [age] is "3 s ago" for the suggestion's time. */
fun odoLabel(entry: FuelUp?, sugg: OdoSuggestion, age: String?): OdoLabel {
    if (entry != null) return OdoLabel("saved as ${odoBadge(entry.odoSource).label}", OdoTone.INK)
    return when (sugg.source) {
        OdoSuggestionSource.BIKE -> OdoLabel("from bike · ${age ?: "now"}", OdoTone.MOSS)
        OdoSuggestionSource.BIKE_LAST -> OdoLabel("last seen on bike · ${age ?: "earlier"} · check it", OdoTone.AMBER)
        OdoSuggestionSource.NONE -> OdoLabel("Bike not connected. Type the odometer from the cluster.", OdoTone.RUST)
    }
}

/** "bike said 12,164" once the rider changed the bike's number on a new entry. */
fun bikeSaid(form: FuelForm, entry: FuelUp?, sugg: OdoSuggestion): String? {
    val said = sugg.value ?: return null
    if (entry != null || parseOdo(form.odo) == said) return null
    return "bike said ${Formatters.odometer(said)}"
}

/** "₹106.27 per litre", or the placeholder until litres and amount are both in. */
fun perLitreLine(perLitre: Double?, currency: String): String =
    if (perLitre == null) "price per litre appears here" else "${Formatters.money(perLitre, currency, 2)} per litre"

/** The record to save, or null while the form has errors. [barsNow] = live fuel bars, if connected. */
fun toFuelUp(form: FuelForm, v: FuelUpValidation, entry: FuelUp?, sugg: OdoSuggestion, nowMillis: Long, barsNow: Int?): FuelUp? {
    if (!v.ok) return null
    val odo = parseOdo(form.odo) ?: return null
    val litres = parseDecimal(form.litres) ?: return null
    val total = parseDecimal(form.total) ?: return null
    val source = odoSourceFor(form, entry, sugg)
    return entry?.copy(odometerKm = odo, litres = litres, totalPaid = total, full = form.full, odoSource = source)
        ?: FuelUp(atMillis = nowMillis, odometerKm = odo, litres = litres, totalPaid = total, full = form.full, odoSource = source, barsBefore = barsNow)
}
