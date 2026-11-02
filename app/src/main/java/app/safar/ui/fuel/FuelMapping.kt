package app.safar.ui.fuel

import androidx.compose.runtime.Immutable
import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.data.FuelMethod
import app.safar.data.FuelStats
import app.safar.data.FuelUp
import app.safar.data.MileageSegment
import app.safar.data.OdoSource
import app.safar.data.Settings
import app.safar.data.fuelStats
import app.safar.ui.components.ValueOrigin
import app.safar.ui.format.Formatters
import app.safar.ui.format.ValueSource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

// Pure state mapping for the fuel page (ux/variant-b/js/screens/fuel.js). All maths come from data/FuelStats.

private const val DEFAULT_BARS = 6
private const val AVERAGE_TANKS = 3
private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

/**
 * The bike's fuel bars: live while connected, else the last reading kept after a disconnect. [connected] is the
 * link itself, so a connected bike with no reading yet says "Waiting for the bike".
 */
data class BarsReading(val bars: Int?, val max: Int, val atMillis: Long?, val live: Boolean, val connected: Boolean = live) {
    /** Where the bars came from, for [app.safar.ui.components.Freshness]. */
    val origin: ValueOrigin get() = ValueOrigin(ValueSource.BIKE, atMillis, connected = if (atMillis == null) connected else live)
}

/** Badge for where a fuel-up's odometer came from (fuel.js `ODO_BADGE`). */
enum class OdoBadge(val label: String, val spoken: String) {
    BIKE("from bike", "read from your bike"),
    EDITED("edited", "edited by you"),
    TYPED("typed", "typed by you"),
}

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

@Immutable
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

/** "last full tank", "last 3 full tanks": the tanks the average is taken over. */
fun averageOver(segments: Int): String = min(AVERAGE_TANKS, segments).let { if (it == 1) "last full tank" else "last $it full tanks" }

/** How the range was worked out, under the big number (fuel.js `methodLine`). */
fun methodLine(stats: FuelStats): String = when (stats.method) {
    FuelMethod.HISTORY -> "Worked out from your ${averageOver(stats.segments.size)}: ${stats.avgKmpl?.let(::jsNumber)} km/L."
    FuelMethod.BARS -> "A rough guess from the fuel bars. Log a full tank for a better one."
    FuelMethod.NONE -> "No estimate yet. Log a full tank to start."
}

/** Developer mode only: the technical source of the range figure. */
fun rangeSource(method: FuelMethod): String = when (method) {
    FuelMethod.HISTORY -> "calculated · fuel-ups"
    FuelMethod.BARS -> "rough · bars"
    FuelMethod.NONE -> "log a full tank"
}

/** What TalkBack reads for the tank bar: "About 5.2 of 12 litres left." */
fun tankDescription(stats: FuelStats): String = stats.remainingL?.let { "About ${jsNumber(it)} of ${jsNumber(stats.tank)} litres left." }
    ?: "${jsNumber(stats.tank)} litre tank. Fuel left not known yet."

/** The calm hint when the bars and the fuel-ups disagree. */
fun gaugeHint(stats: FuelStats): String =
    "Your bike shows ${stats.bars} of ${stats.barsMax} bars, but your fuel-ups say about ${stats.remainingL?.let(::jsNumber)} L is left. " +
        "Log your next fuel-up, or check the tank size in Settings."

/** "1 fuel-up", "3 fuel-ups". */
fun fuelUpsText(count: Int): String = if (count == 1) "1 fuel-up" else "$count fuel-ups"

/** "1 tank", "4 tanks". */
fun tanksText(count: Int): String = if (count == 1) "1 tank" else "$count tanks"

/** What TalkBack reads for a receipt: "31 Oct: 6.7 litres for ₹712, ₹106.27 a litre. 12,164 km, read from your bike. Full tank." */
fun fuelUpSpoken(row: FuelUpRowUi, currency: String): String =
    "${row.date}: ${jsNumber(row.litres)} litres for ${Formatters.money(row.total, currency)}, " +
        "${Formatters.money(row.perLitre, currency, 2)} a litre. ${Formatters.odometer(row.odometerKm)} km, ${row.badge.spoken}. " +
        if (row.full) "Full tank." else "Top-up."

/** Share of the tank still in it, 0..1, for the tank bar. */
fun tankFraction(stats: FuelStats): Float =
    if (stats.remainingL == null || stats.tank <= 0) 0f else (stats.remainingL / stats.tank).toFloat().coerceIn(0f, 1f)

fun barsReading(bike: BikeState): BarsReading {
    val max = bike.saved?.model?.fuelBars ?: DEFAULT_BARS
    val connected = bike.link == LinkState.Connected
    val live = connected && bike.cluster != null
    val reading = bike.cluster ?: bike.lastCluster
    return BarsReading(reading?.fuelBars, max, reading?.receivedAtMillis, live, connected)
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
