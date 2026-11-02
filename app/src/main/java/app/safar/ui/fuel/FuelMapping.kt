package app.safar.ui.fuel

import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.data.FuelMethod
import app.safar.data.FuelStats
import app.safar.data.FuelUp
import app.safar.data.MileageSegment
import app.safar.data.OdoSource
import app.safar.data.Settings
import app.safar.data.fuelStats
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

// Pure state mapping for the fuel page (ux/variant-b/js/screens/fuel.js). All maths come from data/FuelStats.

private const val DEFAULT_BARS = 6
private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

/** The cluster's fuel bars: live while connected, else the last reading kept after a disconnect. */
data class BarsReading(val bars: Int?, val max: Int, val atMillis: Long?, val live: Boolean)

/** Badge for where a fuel-up's odometer came from (fuel.js `ODO_BADGE`). */
enum class OdoBadge(val label: String) { BIKE("bike"), EDITED("edited"), TYPED("typed") }

data class FuelUpRowUi(
    val id: Long,
    val date: String,
    val litres: Double,
    val total: Double,
    val perLitre: Double,
    val odometerKm: Int,
    val badge: OdoBadge,
    val full: Boolean,
)

data class FuelUiState(
    val loading: Boolean = true,
    val stats: FuelStats? = null,
    val currency: String = "₹",
    val reading: BarsReading = BarsReading(null, DEFAULT_BARS, null, false),
    val rows: List<FuelUpRowUi> = emptyList(),
    val lowFuelAlert: Boolean = true,
)

fun odoBadge(source: OdoSource): OdoBadge = when (source) {
    OdoSource.BIKE -> OdoBadge.BIKE
    OdoSource.EDITED -> OdoBadge.EDITED
    OdoSource.MANUAL -> OdoBadge.TYPED
}

/** A number as JavaScript prints it: 40.0 -> "40", 41.8 -> "41.8". */
fun jsNumber(v: Double): String = if (v == Math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()

/** "12 Mar". */
fun fuelDate(atMillis: Long, zone: ZoneId): String = shortDate.format(Instant.ofEpochMilli(atMillis).atZone(zone))

/** How the range was worked out, under the big number (fuel.js `methodLine`). */
fun methodLine(stats: FuelStats): String = when (stats.method) {
    FuelMethod.HISTORY -> "from your fuel-ups · avg ${stats.avgKmpl?.let(::jsNumber)} km/L, last ${min(3, stats.segments.size)} fills"
    FuelMethod.BARS -> "rough · from fuel bars only, log a full tank to improve"
    FuelMethod.NONE -> "no estimate yet · log a full tank to start"
}

/** Source caption for the range figure. */
fun rangeSource(method: FuelMethod): String = when (method) {
    FuelMethod.HISTORY -> "calculated · fuel-ups"
    FuelMethod.BARS -> "rough · bars"
    FuelMethod.NONE -> "log a full tank"
}

/** Share of the tank still in it, 0..1, for the tank bar. */
fun tankFraction(stats: FuelStats): Float =
    if (stats.remainingL == null || stats.tank <= 0) 0f else (stats.remainingL / stats.tank).toFloat().coerceIn(0f, 1f)

fun barsReading(bike: BikeState): BarsReading {
    val max = bike.saved?.model?.fuelBars ?: DEFAULT_BARS
    val live = bike.link == LinkState.Connected && bike.cluster != null
    val reading = bike.cluster ?: bike.lastCluster
    return BarsReading(reading?.fuelBars, max, reading?.receivedAtMillis, live)
}

/** History rows, highest odometer first, then newest first (fuel.js `history`). */
fun fuelRows(fuelUps: List<FuelUp>, zone: ZoneId): List<FuelUpRowUi> =
    fuelUps.sortedWith(compareByDescending<FuelUp> { it.odometerKm }.thenByDescending { it.atMillis }).map {
        FuelUpRowUi(it.id, fuelDate(it.atMillis, zone), it.litres, it.totalPaid, it.totalPaid / it.litres, it.odometerKm, odoBadge(it.odoSource), it.full)
    }

fun fuelUiState(fuelUps: List<FuelUp>, settings: Settings, bike: BikeState, nowMillis: Long, zone: ZoneId): FuelUiState {
    val model = bike.saved?.model
    val stats = fuelStats(fuelUps, settings, model, bike.cluster ?: bike.lastCluster, nowMillis)
    return FuelUiState(
        loading = false,
        stats = stats,
        currency = settings.currencySymbol,
        reading = barsReading(bike),
        rows = fuelRows(fuelUps, zone),
        lowFuelAlert = settings.lowFuelAlert,
    )
}

/** Mileage trend in a [width] x [height] box (fuel.js `trend`): one point per full-to-full segment. */
data class TrendChart(val xs: List<Float>, val ys: List<Float>, val avgY: Float?)

fun trendChart(segs: List<MileageSegment>, avg: Double?, width: Float, height: Float, padX: Float, padTop: Float, padBottom: Float): TrendChart? {
    if (segs.size < 2) return null
    val ks = segs.map { it.kmpl }
    val lo = minOf(ks.min(), avg ?: Double.MAX_VALUE) - 2
    val hi = maxOf(ks.max(), avg ?: 0.0) + 2
    fun y(k: Double) = (padTop + (1 - (k - lo) / (hi - lo)) * (height - padTop - padBottom)).toFloat()
    val xs = segs.indices.map { i -> padX + i.toFloat() / (segs.size - 1) * (width - 2 * padX) }
    return TrendChart(xs, ks.map(::y), avg?.let(::y))
}
