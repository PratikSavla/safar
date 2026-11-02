package app.safar.ui.widget

import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.core.NavState
import app.safar.core.NavStatusCode
import app.safar.data.FuelMethod
import app.safar.data.FuelUp
import app.safar.data.Settings
import app.safar.data.fuelStats
import app.safar.ui.art.TurnCodes
import app.safar.ui.format.Formatters
import java.time.ZoneId

// Pure mapping for the home-screen widget (ux/variant-b/js/screens/widget.js `safarWidget`). No Android imports.

private const val DEFAULT_BARS = 6

/** Ink of the link word and dot. */
enum class WidgetTone { MOSS, AMBER, SLATE, RUST }

/** Next turn while Google Maps navigates: what, and how far. */
data class WidgetTurn(val code: Int, val text: String, val distance: String?)

/** Everything the 4x2 widget prints. Times are clock times so the text never goes stale between updates. */
data class WidgetSnapshot(
    val model: String,
    val link: String,
    val tone: WidgetTone,
    val odometer: String,
    val tripA: String,
    val bars: Int?,
    val barsMax: Int,
    val range: String,
    val rangeSource: String,
    val footer: String,
    val turn: WidgetTurn?,
) {
    companion object {
        /** Before the app has any data (or before its parts are wired). */
        val Empty = WidgetSnapshot("no bike", "not linked", WidgetTone.RUST, "—", "—", null, DEFAULT_BARS, "—", "log a full tank", "waiting for the bike", null)
    }
}

/** The link in one word with its tone (widget.js `connWord` and tones). */
fun linkWord(link: LinkState): Pair<String, WidgetTone> = when (link) {
    LinkState.Connected -> "linked" to WidgetTone.MOSS
    is LinkState.Reconnecting -> "reconnecting" to WidgetTone.AMBER
    is LinkState.Connecting -> "connecting" to WidgetTone.SLATE
    LinkState.Searching -> "searching" to WidgetTone.SLATE
    LinkState.BluetoothOff -> "bluetooth off" to WidgetTone.RUST
    LinkState.Idle -> "not linked" to WidgetTone.RUST
}

/** The turn line, or null when Maps is not navigating. */
fun widgetTurn(nav: NavState): WidgetTurn? {
    if (!nav.active) return null
    val snap = nav.snapshot ?: return if (nav.waiting) WidgetTurn(0, "Waiting for Google Maps…", null) else null
    if (snap.status == NavStatusCode.ARRIVED) return WidgetTurn(snap.iconCode, "Arrived", null)
    return WidgetTurn(snap.iconCode, TurnCodes.spec(snap.iconCode).name, Formatters.navDistance(snap.distanceToTurnM).toString())
}

private fun rangeSource(method: FuelMethod): String = when (method) {
    FuelMethod.HISTORY -> "fuel-ups"
    FuelMethod.BARS -> "rough · bars"
    FuelMethod.NONE -> "log a full tank"
}

fun widgetSnapshot(bike: BikeState, nav: NavState, fuelUps: List<FuelUp>, settings: Settings, nowMillis: Long, zone: ZoneId): WidgetSnapshot {
    val reading = bike.cluster ?: bike.lastCluster
    val live = bike.link == LinkState.Connected && bike.cluster != null
    val model = bike.saved?.model
    val stats = fuelStats(fuelUps, settings, model, reading, nowMillis)
    val (word, tone) = linkWord(bike.link)
    val footer = when {
        reading == null -> "waiting for the bike"
        live -> "bike · ${Formatters.clock(reading.receivedAtMillis, zone)}"
        else -> "last seen on bike · ${Formatters.clock(reading.receivedAtMillis, zone)}"
    }
    return WidgetSnapshot(
        model = model?.name ?: "no bike",
        link = word,
        tone = tone,
        odometer = reading?.let { Formatters.odometer(it.odometerKm) } ?: "—",
        tripA = reading?.let { Formatters.km1(it.tripAKm) } ?: "—",
        bars = reading?.fuelBars,
        barsMax = model?.fuelBars ?: DEFAULT_BARS,
        range = Formatters.approx(stats.rangeKm?.toDouble()),
        rangeSource = rangeSource(stats.method),
        footer = footer,
        turn = widgetTurn(nav),
    )
}

/** Fuel bar heights in dp for the widget gauge: a rising ruler like the app's FuelBars. */
fun widgetBarHeights(max: Int, height: Float): List<Float> = List(max.coerceAtLeast(1)) { i -> height * (0.45f + 0.55f * (i + 1) / max.coerceAtLeast(1)) }
