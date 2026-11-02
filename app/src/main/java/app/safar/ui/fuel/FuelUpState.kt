package app.safar.ui.fuel

import app.safar.core.RideState
import app.safar.data.FuelField
import app.safar.data.FuelUp
import app.safar.data.FuelUpValidation
import app.safar.data.OdoSuggestion
import app.safar.ui.format.LockText

/** Everything the fuel-up form shows. [locked] is the UX rule 3 reason while the phone moves. */
data class FuelUpUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val entry: FuelUp? = null,
    val sugg: OdoSuggestion? = null,
    val form: FuelForm = FuelForm(),
    val validation: FuelUpValidation? = null,
    val messages: Map<FuelField, FieldMessage> = emptyMap(),
    val perLitre: String = perLitreLine(null, "₹"),
    val bikeSaid: String? = null,
    val locked: String? = null,
    val speedKmh: Float? = null,
    val currency: String = "₹",
    /** A save or delete is being written; Save stays disabled so a double tap cannot log the receipt twice. */
    val saving: Boolean = false,
) {
    val canSave: Boolean get() = validation?.ok == true && locked == null && !missing && !saving
}

fun fuelUpUiState(
    entry: FuelUp?,
    missing: Boolean,
    sugg: OdoSuggestion,
    form: FuelForm,
    fuelUps: List<FuelUp>,
    tank: Double,
    currency: String,
    ride: RideState,
): FuelUpUiState {
    val v = validate(form, fuelUps, entry, tank)
    return FuelUpUiState(
        loading = false,
        missing = missing,
        entry = entry,
        sugg = sugg,
        form = form,
        validation = v,
        messages = fieldMessages(form, v),
        perLitre = perLitreLine(v.perLitre, currency),
        bikeSaid = bikeSaid(form, entry, sugg),
        locked = LockText.whileMoving(ride.moving, ride.speedKmh),
        speedKmh = ride.speedKmh,
        currency = currency,
    )
}
