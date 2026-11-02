package app.safar.ui.settings

import app.safar.core.BikeModel
import app.safar.core.VehicleFamily
import app.safar.protocol.MAX_NAME_LENGTH
import app.safar.protocol.sanitizeName
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** Speed alert range when no bike is saved (motorcycle range, docs 7.2). */
val DEFAULT_ALERT_RANGE = 10..299

const val TANK_MAX_LITRES = 40.0

/** Longest name the field takes; the cluster shows the first [MAX_NAME_LENGTH] cleaned characters. */
const val RIDER_NAME_FIELD_MAX = 40

/** How the cluster shows the rider name: upper case, printable ASCII, max 19 characters (docs 6.2). */
fun clusterName(raw: String): String = sanitizeName(raw.uppercase(Locale.ROOT))

/** Counter next to the name field: cleaned length of 19. */
fun nameCounter(raw: String): String = "${clusterName(raw).length}/$MAX_NAME_LENGTH"

/** Result of reading the tank size field. [litres] null = use the model spec. */
sealed interface TankInput {
    data class Valid(val litres: Double?) : TankInput
    data class Invalid(val reason: String) : TankInput
}

/**
 * Parses the tank field ("12", "12.5", "12,5"). Rounds to 0.1 L. The model spec value, or an empty field when a
 * spec is known, means "no override".
 */
fun parseTank(text: String, specLitres: Double?): TankInput {
    val clean = text.trim().replace(',', '.')
    if (clean.isEmpty()) {
        return if (specLitres != null) TankInput.Valid(null) else TankInput.Invalid("Enter the tank size in litres")
    }
    val n = clean.toDoubleOrNull() ?: return TankInput.Invalid("Use a number, like 12.5")
    if (n <= 0.0 || n > TANK_MAX_LITRES) return TankInput.Invalid("Tank size must be between 1 and 40 L")
    val rounded = BigDecimal(n).setScale(1, RoundingMode.HALF_UP).toDouble()
    return TankInput.Valid(if (specLitres != null && rounded == specLitres) null else rounded)
}

/** "12", "5.3": litres without a trailing ".0". */
fun litresText(litres: Double): String {
    val r = BigDecimal(litres).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros()
    return r.toPlainString()
}

fun alertRangeOf(model: BikeModel?): IntRange = model?.alertRange ?: DEFAULT_ALERT_RANGE

fun familyWord(model: BikeModel?): String = when (model?.family) {
    VehicleFamily.SCOOTER -> "scooter"
    VehicleFamily.MOTORCYCLE -> "motorcycle"
    null -> "motorcycle"
}

/** Tank row subtitle: the model spec and, when set, the rider's own value. */
fun tankSubtitle(model: BikeModel?, own: Double?): String {
    val spec = if (model != null) "Model spec ${litresText(model.tankLitres)} L (${model.name})" else "No bike paired · no model spec"
    return if (own != null) "$spec · using yours: ${litresText(own)} L" else spec
}
