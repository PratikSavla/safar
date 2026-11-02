package app.safar.data

import app.safar.core.BikeModel
import app.safar.core.ClusterReading
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// Fuel maths, a port of ux/shared/fuel.js (see ux/FUEL.md). The bike reports only fuel bars and a
// whole-km odometer, so litres and price come from the rider's fuel-ups. Mileage uses the full-to-full
// method: km between two full fills / litres added after the first full, up to and including the next.

private const val DEFAULT_TANK_L = 12.0
private const val DEFAULT_BARS = 6
private const val RECENT_SEGMENTS = 3
private const val MONTH_MILLIS = 30L * 86_400_000L

/** JavaScript `Math.round(n * 10^d) / 10^d`: half rounds towards positive infinity. */
internal fun jsRound(n: Double, digits: Int = 1): Double {
    val f = Math.pow(10.0, digits.toDouble())
    return floor(n * f + 0.5) / f
}

/** JavaScript `Math.round(n)` as a whole number. */
internal fun jsRoundToLong(n: Double): Long = floor(n + 0.5).toLong()

private val byOdo = compareBy<FuelUp> { it.odometerKm }

/** Tank size in litres: the rider's override, else the model spec, else 12 L. */
fun tankLitres(settings: Settings, model: BikeModel?): Double =
    settings.tankLitres ?: model?.tankLitres ?: DEFAULT_TANK_L

/** Where the pre-filled odometer came from: live frame, last frame before disconnect, or nothing. */
enum class OdoSuggestionSource { BIKE, BIKE_LAST, NONE }

data class OdoSuggestion(val value: Int?, val source: OdoSuggestionSource, val atMillis: Long?)

/**
 * Odometer to pre-fill in the fuel-up form. [cluster] is the live reading, [lastCluster] the one kept after a
 * disconnect. The live reading counts as [OdoSuggestionSource.BIKE] only while [connected].
 */
fun odoSuggestion(cluster: ClusterReading?, lastCluster: ClusterReading?, connected: Boolean): OdoSuggestion {
    val reading = cluster ?: lastCluster ?: return OdoSuggestion(null, OdoSuggestionSource.NONE, null)
    val source = if (connected && cluster != null) OdoSuggestionSource.BIKE else OdoSuggestionSource.BIKE_LAST
    return OdoSuggestion(reading.odometerKm, source, reading.receivedAtMillis)
}

enum class FuelField { ODO, LITRES, TOTAL }

/** [errors] block saving, [warnings] allow it. [perLitre] is the live price per litre, 2 decimals. */
data class FuelUpValidation(
    val errors: Map<FuelField, String>,
    val warnings: Map<FuelField, String>,
    val perLitre: Double?,
) {
    val ok: Boolean get() = errors.isEmpty()
}

/**
 * Checks a form entry against the existing [fuelUps]. [odo] is null when the field is empty or not a whole
 * number; [litres] and [total] are null when empty.
 */
fun validateFuelUp(odo: Int?, litres: Double?, total: Double?, fuelUps: List<FuelUp>, tank: Double): FuelUpValidation {
    val errors = linkedMapOf<FuelField, String>()
    val warnings = linkedMapOf<FuelField, String>()
    if (litres == null || litres <= 0) {
        errors[FuelField.LITRES] = "Enter the litres from the pump"
    } else if (litres > tank * 1.05) {
        warnings[FuelField.LITRES] = "More than the ${formatNumber(tank)} L tank"
    }
    if (total == null || total <= 0) errors[FuelField.TOTAL] = "Enter the amount paid"
    if (odo == null || odo <= 0) {
        errors[FuelField.ODO] = "Enter the odometer (whole km)"
    } else {
        val prev = fuelUps.sortedWith(byOdo).lastOrNull()
        if (prev != null && odo < prev.odometerKm) {
            errors[FuelField.ODO] = "Lower than the last fuel-up (${groupThousands(prev.odometerKm)} km)"
        } else if (prev != null && odo - prev.odometerKm > tank * 60) {
            warnings[FuelField.ODO] = "${groupThousands(odo - prev.odometerKm)} km since the last fuel-up — a missed entry?"
        }
    }
    val perLitre = if (litres != null && litres > 0 && total != null && total > 0) jsRound(total / litres, 2) else null
    return FuelUpValidation(errors, warnings, perLitre)
}

/** One full-to-full segment. [atMillis] is the time of the closing full fill. */
data class MileageSegment(
    val fromOdo: Int,
    val toOdo: Int,
    val km: Int,
    val litres: Double,
    val kmpl: Double,
    val atMillis: Long,
)

/** Full-to-full segments, oldest first. */
fun mileageSegments(fuelUps: List<FuelUp>): List<MileageSegment> {
    val out = mutableListOf<MileageSegment>()
    var start: FuelUp? = null
    var litres = 0.0
    for (f in fuelUps.sortedWith(byOdo)) {
        if (start != null) litres += f.litres
        if (!f.full) continue
        if (start != null && f.odometerKm > start.odometerKm) {
            val km = f.odometerKm - start.odometerKm
            out += MileageSegment(start.odometerKm, f.odometerKm, km, jsRound(litres, 2), jsRound(km / litres), f.atMillis)
        }
        start = f
        litres = 0.0
    }
    return out
}

/** How [FuelStats.remainingL] was worked out. */
enum class FuelMethod { HISTORY, BARS, NONE }

data class MonthFuel(val count: Int, val litres: Double, val money: Long)

/** Everything a fuel screen shows. Field meanings match ux/shared/fuel.js `fuelStats`. */
data class FuelStats(
    val tank: Double,
    val odoNow: Int?,
    val segments: List<MileageSegment>,
    val avgKmpl: Double?,
    val lastKmpl: Double?,
    val lifetimeKmpl: Double?,
    val method: FuelMethod,
    val remainingL: Double?,
    val rangeKm: Long?,
    val sinceFullKm: Int?,
    val lastFull: FuelUp?,
    val bars: Int?,
    val barsMax: Int,
    val barsLitres: Double?,
    val gaugeDisagrees: Boolean,
    val costPerKm: Double?,
    val spentL: Double,
    val spentMoney: Long,
    val month: MonthFuel,
    val lastPerLitre: Double?,
)

private data class Mileage(val avg: Double?, val last: Double?, val lifetime: Double?, val allLitres: Double)

private fun mileage(segs: List<MileageSegment>): Mileage {
    val recent = segs.takeLast(RECENT_SEGMENTS)
    val avg = if (recent.isEmpty()) null else jsRound(recent.sumOf { it.km }.toDouble() / recent.sumOf { it.litres })
    val allKm = segs.sumOf { it.km }.toDouble()
    val allL = segs.sumOf { it.litres }
    val lifetime = if (allL != 0.0) jsRound(allKm / allL) else null
    return Mileage(avg, segs.lastOrNull()?.kmpl, lifetime, allL)
}

private data class Estimate(val method: FuelMethod, val remainingL: Double?, val sinceFullKm: Int?)

private fun estimate(sorted: List<FuelUp>, avgKmpl: Double?, lastFull: FuelUp?, odoNow: Int?, tank: Double, barsLitres: Double?): Estimate {
    if (avgKmpl != null && avgKmpl != 0.0 && lastFull != null && odoNow != null) {
        val sinceFull = max(0, odoNow - lastFull.odometerKm)
        val topUps = sorted.filter { !it.full && it.odometerKm > lastFull.odometerKm }.sumOf { it.litres }
        val remaining = jsRound(min(tank, max(0.0, tank - sinceFull / avgKmpl + topUps)))
        return Estimate(FuelMethod.HISTORY, remaining, sinceFull)
    }
    if (barsLitres != null) return Estimate(FuelMethod.BARS, barsLitres, null)
    return Estimate(FuelMethod.NONE, null, null)
}

private fun costPerKm(sorted: List<FuelUp>, allLitres: Double, spentMoney: Double): Double? {
    val span = if (sorted.size > 1) sorted.last().odometerKm - sorted.first().odometerKm else 0
    if (span == 0 || allLitres == 0.0) return null
    return jsRound((spentMoney - sorted.last().totalPaid) / span, 2)
}

/**
 * Fuel screen numbers. [cluster] is the live or last-known cluster reading (its odometer and bars), [model] the
 * paired bike, [nowMillis] the clock for the 30-day "this month" window.
 */
fun fuelStats(
    fuelUps: List<FuelUp>,
    settings: Settings,
    model: BikeModel?,
    cluster: ClusterReading?,
    nowMillis: Long,
): FuelStats {
    val tank = tankLitres(settings, model)
    val sorted = fuelUps.sortedWith(byOdo)
    val odoNow = cluster?.odometerKm ?: sorted.lastOrNull()?.odometerKm
    val segs = mileageSegments(fuelUps)
    val m = mileage(segs)
    val lastFull = sorted.lastOrNull { it.full }
    val spentMoney = sorted.sumOf { it.totalPaid }
    val month = sorted.filter { it.atMillis >= nowMillis - MONTH_MILLIS }
    val bars = cluster?.fuelBars
    val barsMax = model?.fuelBars ?: DEFAULT_BARS
    val barsLitres = bars?.let { jsRound(it.toDouble() / barsMax * tank) }
    val est = estimate(sorted, m.avg, lastFull, odoNow, tank, barsLitres)
    val kmpl = m.avg ?: m.lifetime
    val rangeKm = if (est.remainingL != null && kmpl != null && kmpl != 0.0) jsRoundToLong(est.remainingL * kmpl) else null
    // The gauge disagrees when the estimate is more than 1.5 bars away from what the bars imply.
    val disagrees = est.method == FuelMethod.HISTORY && barsLitres != null && est.remainingL != null &&
        abs(est.remainingL - barsLitres) > 1.5 * (tank / barsMax)
    return FuelStats(
        tank = tank, odoNow = odoNow, segments = segs, avgKmpl = m.avg, lastKmpl = m.last, lifetimeKmpl = m.lifetime,
        method = est.method, remainingL = est.remainingL, rangeKm = rangeKm, sinceFullKm = est.sinceFullKm,
        lastFull = lastFull, bars = bars, barsMax = barsMax, barsLitres = barsLitres, gaugeDisagrees = disagrees,
        costPerKm = costPerKm(sorted, m.allLitres, spentMoney),
        spentL = jsRound(sorted.sumOf { it.litres }), spentMoney = jsRoundToLong(spentMoney),
        month = MonthFuel(month.size, jsRound(month.sumOf { it.litres }), jsRoundToLong(month.sumOf { it.totalPaid })),
        lastPerLitre = sorted.lastOrNull()?.let { jsRound(it.totalPaid / it.litres, 2) },
    )
}

/** `12.0` -> "12", `5.2` -> "5.2", like JavaScript number to string. */
internal fun formatNumber(n: Double): String =
    if (n == floor(n) && abs(n) < 1e15) n.toLong().toString() else n.toString()

/** 12164 -> "12,164", like `toLocaleString()` in an English locale. */
internal fun groupThousands(n: Int): String = String.format(java.util.Locale.US, "%,d", n)
