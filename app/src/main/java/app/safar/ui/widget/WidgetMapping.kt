package app.safar.ui.widget

import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.core.NavState
import app.safar.core.NavStatusCode
import app.safar.data.FuelStats
import app.safar.data.FuelUp
import app.safar.data.Settings
import app.safar.data.fuelStats
import app.safar.ui.art.TurnCodes
import app.safar.ui.copy.LinkCopy
import app.safar.ui.copy.Phrases
import app.safar.ui.format.Formatters
import app.safar.ui.format.FreshnessText
import app.safar.ui.fuel.rangeNote
import java.time.ZoneId

// Pure mapping for the home-screen widget (ux/variant-b/js/screens/widget.js `safarWidget`). No Android imports.

private const val DEFAULT_BARS = 6
private const val NONE = "—"
private const val YOUR_BIKE = "your bike"
private const val LOG_FULL_TANK = "log a full tank"
private const val PAIR_IN_APP = "Open Safar to pair your bike"

/** Ink of the link word and dot. */
enum class WidgetTone { MOSS, AMBER, SLATE, RUST }

/** Next turn while Google Maps navigates: what, and how far. */
data class WidgetTurn(val code: Int, val text: String, val distance: String?)

/**
 * Everything the 4x2 widget prints. The footer never counts seconds or minutes, so the text does not go stale
 * between updates. [rangeNote] is null when the range needs no explanation. [description] is what TalkBack reads.
 */
data class WidgetSnapshot(
    val model: String,
    val link: String,
    val tone: WidgetTone,
    val odometer: String,
    val tripA: String,
    val bars: Int?,
    val barsMax: Int,
    val range: String,
    val rangeNote: String?,
    val footer: String,
    val turn: WidgetTurn?,
    val description: String,
) {
    companion object {
        /** Before the app has any data (or before its parts are wired): the same as a phone with no bike paired. */
        val Empty = WidgetSnapshot(
            YOUR_BIKE, LinkCopy.stamp(LinkState.Idle, hasSavedBike = false), WidgetTone.RUST, "—", "—", null, DEFAULT_BARS, "—",
            LOG_FULL_TANK, PAIR_IN_APP, null,
            widgetDescription(YOUR_BIKE, LinkCopy.headline(LinkState.Idle, null, hasSavedBike = false), "—", "—", "—", null),
        )
    }
}

/** The link in one word with its tone (widget.js `connWord` and tones), in the same words as the app. */
fun linkWord(link: LinkState, hasSavedBike: Boolean = true): Pair<String, WidgetTone> = LinkCopy.stamp(link, hasSavedBike) to when (link) {
    LinkState.Connected -> WidgetTone.MOSS
    is LinkState.Reconnecting -> WidgetTone.AMBER
    is LinkState.Connecting, LinkState.Searching -> WidgetTone.SLATE
    LinkState.BluetoothOff, LinkState.Idle -> WidgetTone.RUST
}

/** One spoken summary: "Safar, V-Strom SX: Connected. Odometer 12,301 km, Trip A 84.6 km. Range about 180 km." */
fun widgetDescription(model: String, headline: String, odometer: String, tripA: String, range: String, turn: WidgetTurn?): String {
    val parts = mutableListOf("Safar, $model: $headline.")
    if (odometer != NONE) parts += "Odometer $odometer km" + if (tripA != NONE) ", Trip A $tripA km." else "."
    if (range != NONE) parts += "Range ${range.replace("≈", "about ")} km."
    turn?.let { parts += "Next: ${it.text}" + (it.distance?.let { d -> " in $d." } ?: ".") }
    return parts.joinToString(" ")
}

/** The line along the bottom when Maps is not navigating. Developer mode keeps the source and clock time. */
fun widgetFooter(bike: BikeState, nowMillis: Long, zone: ZoneId, developer: Boolean): String {
    val reading = bike.cluster ?: bike.lastCluster
    val live = bike.link == LinkState.Connected && bike.cluster != null
    if (developer && reading != null) {
        val clock = Formatters.clock(reading.receivedAtMillis, zone)
        return if (live) "bike · $clock" else "last seen on bike · $clock"
    }
    return when {
        bike.saved == null && reading == null -> PAIR_IN_APP
        reading == null -> Phrases.WAITING_FOR_BIKE
        live -> "Ready to ride"
        else -> FreshnessText.lastRide(reading.receivedAtMillis, nowMillis, zone)
    }
}

/** The turn line, or null when Maps is not navigating. */
fun widgetTurn(nav: NavState): WidgetTurn? {
    if (!nav.active) return null
    val snap = nav.snapshot ?: return if (nav.waiting) WidgetTurn(0, Phrases.WAITING_FOR_MAPS, null) else null
    if (snap.status == NavStatusCode.ARRIVED) return WidgetTurn(snap.iconCode, "Arrived", null)
    return WidgetTurn(snap.iconCode, TurnCodes.spec(snap.iconCode).name, Formatters.navDistance(snap.distanceToTurnM).toString())
}

/** Under the range: the app's own note ([app.safar.ui.fuel.rangeNote]), in the widget's lower case. */
fun widgetRangeNote(stats: FuelStats): String? = rangeNote(stats)?.replaceFirstChar { it.lowercase() }

fun widgetSnapshot(bike: BikeState, nav: NavState, fuelUps: List<FuelUp>, settings: Settings, nowMillis: Long, zone: ZoneId): WidgetSnapshot {
    val reading = bike.cluster ?: bike.lastCluster
    val model = bike.saved?.model
    val stats = fuelStats(fuelUps, settings, model, reading, nowMillis)
    val (word, tone) = linkWord(bike.link, bike.saved != null)
    val name = LinkCopy.modelName(bike) ?: YOUR_BIKE
    val odometer = reading?.let { Formatters.odometer(it.odometerKm) } ?: NONE
    val tripA = reading?.let { Formatters.km1(it.tripAKm) } ?: NONE
    val range = Formatters.approx(stats.rangeKm?.toDouble())
    val turn = widgetTurn(nav)
    return WidgetSnapshot(
        model = name,
        link = word,
        tone = tone,
        odometer = odometer,
        tripA = tripA,
        bars = reading?.fuelBars,
        barsMax = model?.fuelBars ?: DEFAULT_BARS,
        range = range,
        rangeNote = widgetRangeNote(stats),
        footer = widgetFooter(bike, nowMillis, zone, settings.developerMode),
        turn = turn,
        description = widgetDescription(name, LinkCopy.headline(bike), odometer, tripA, range, turn),
    )
}

/** Fuel bar heights in dp for the widget gauge: a rising ruler like the app's FuelBars. */
fun widgetBarHeights(max: Int, height: Float): List<Float> = List(max.coerceAtLeast(1)) { i -> height * (0.45f + 0.55f * (i + 1) / max.coerceAtLeast(1)) }
