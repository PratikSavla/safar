package app.safar.ui.navigate

import app.safar.core.NavSnapshot
import app.safar.core.NavState
import app.safar.core.NavStatusCode
import app.safar.data.ParkedSpot
import app.safar.data.Place
import app.safar.data.PlaceKind
import app.safar.data.PlaceSource
import app.safar.data.latLngQuery
import app.safar.protocol.NavStatus
import app.safar.protocol.STATUS_ICON_CODE
import app.safar.protocol.formatEta
import app.safar.protocol.maneuverByCode
import app.safar.ui.art.TurnCodes
import app.safar.ui.format.Formatters
import app.safar.ui.format.ValueUnit
import java.time.Instant
import java.time.ZoneId

/** Icon of a destination row; PARKED is the last parked spot. */
enum class DestKind { HOME, WORK, FUEL, STAR, PARKED }

/**
 * One row of the Route list. [key] is "place:<id>" or "parked". [startLabel] is what the rider sees while
 * navigating ("En route to …"). [atMillis] is set for the parked spot so the row can show its age.
 */
data class Destination(
    val key: String,
    val placeId: Long?,
    val label: String,
    val detail: String,
    val query: String,
    val kind: DestKind,
    val via: String,
    val startLabel: String,
    val atMillis: Long? = null,
)

const val PARKED_KEY = "parked"

fun placeKey(id: Long): String = "place:$id"

fun PlaceKind.toDestKind(): DestKind = when (this) {
    PlaceKind.HOME -> DestKind.HOME
    PlaceKind.WORK -> DestKind.WORK
    PlaceKind.FUEL -> DestKind.FUEL
    PlaceKind.STAR -> DestKind.STAR
}

/** Short source word shown at the right of a row ("via Maps", "GPS", ...). */
fun PlaceSource.viaWord(): String = when (this) {
    PlaceSource.MAPS -> "via Maps"
    PlaceSource.HERE -> "GPS"
    PlaceSource.TYPED -> "address"
    PlaceSource.PARKED -> "parked"
    PlaceSource.TRIP -> "ride end"
}

/** Saved places in rider order, then the last parked spot if there is one. */
fun destinations(places: List<Place>, parked: ParkedSpot?): List<Destination> {
    val saved = places.sortedBy { it.sortOrder }.map { p ->
        Destination(placeKey(p.id), p.id, p.label, p.detail.ifBlank { p.query }, p.query, p.kind.toDestKind(), p.source.viaWord(), p.label)
    }
    if (parked == null) return saved
    val query = latLngQuery(parked.lat, parked.lng)
    val spot = Destination(
        key = PARKED_KEY, placeId = null, label = "Where I parked", detail = parked.label ?: query, query = query,
        kind = DestKind.PARKED, via = "parked", startLabel = "your parked bike", atMillis = parked.atMillis,
    )
    return saved + spot
}

/** The selected row, falling back to the first one when the selection disappeared (deleted place). */
fun resolveSelection(list: List<Destination>, selectedKey: String?): Destination? =
    list.firstOrNull { it.key == selectedKey } ?: list.firstOrNull()

/** The intent text Maps gets, shown readable under the Start button (`google.navigation:q=<query>&mode=l`). */
fun readableNavUri(query: String): String = "google.navigation:q=$query&mode=l"

/** Rider-facing name of a status byte (docs 4.3). */
fun NavStatusCode.label(): String = when (this) {
    NavStatusCode.NO_NETWORK -> "No cellular network"
    NavStatusCode.NORMAL -> "Normal"
    NavStatusCode.RECALCULATING -> "Recalculating"
    NavStatusCode.VIA_POINT -> "Via point reached"
    NavStatusCode.GPS_OFF -> "GPS is off"
    NavStatusCode.ARRIVED -> "Destination reached"
    NavStatusCode.NO_INTERNET -> "No internet"
}

/** Stamp colour of a status: fine (moss), via point (slate), trouble (amber). */
enum class StatusTone { FINE, INFO, WARN }

fun NavStatusCode.tone(): StatusTone = when (this) {
    NavStatusCode.NORMAL, NavStatusCode.ARRIVED -> StatusTone.FINE
    NavStatusCode.VIA_POINT -> StatusTone.INFO
    else -> StatusTone.WARN
}

/** What the cluster receives for one navigation snapshot, ready to show (the "mirror" card). */
data class NavMirror(
    /** Code in byte 2: the turn icon, or 46 when the status hides it (docs 4.4). */
    val clusterIcon: Int,
    val iconCode: Int,
    val iconName: String,
    val iconMatched: Boolean,
    val toTurn: ValueUnit,
    val toTurnMeters: Int,
    val remaining: ValueUnit,
    val remainingMeters: Int,
    /** ETA as sent, "0412PM". */
    val etaSent: String,
    /** ETA for people, "04:12 PM". */
    val etaShown: String,
    val status: NavStatusCode,
    val statusLabel: String,
    val tone: StatusTone,
    val instruction: String,
    val receivedAtMillis: Long,
)

fun mirrorOf(s: NavSnapshot, zone: ZoneId = ZoneId.systemDefault()): NavMirror {
    val sent = formatEta(Instant.ofEpochMilli(s.etaMillis).atZone(zone).toLocalTime())
    val showsIcon = NavStatus.fromCode(s.status.code)?.showsIcon ?: true
    val name = maneuverByCode(s.iconCode)?.name ?: TurnCodes.spec(s.iconCode).name
    return NavMirror(
        clusterIcon = if (showsIcon) s.iconCode else STATUS_ICON_CODE,
        iconCode = s.iconCode,
        iconName = name,
        iconMatched = s.iconMatched,
        toTurn = Formatters.navDistance(s.distanceToTurnM),
        toTurnMeters = s.distanceToTurnM,
        remaining = Formatters.navDistance(s.remainingM),
        remainingMeters = s.remainingM,
        etaSent = sent,
        etaShown = Formatters.clusterEta(sent),
        status = s.status,
        statusLabel = s.status.label(),
        tone = s.status.tone(),
        instruction = s.instruction,
        receivedAtMillis = s.receivedAtMillis,
    )
}

/** Which body the Route page shows. */
enum class RoutePhase { PICK, AWAITING_MAPS, WAITING_FIRST_UPDATE, ACTIVE }

fun routePhase(nav: NavState, awaitingMaps: Boolean): RoutePhase = when {
    nav.active && nav.snapshot != null -> RoutePhase.ACTIVE
    nav.active || nav.waiting -> RoutePhase.WAITING_FIRST_UPDATE
    awaitingMaps -> RoutePhase.AWAITING_MAPS
    else -> RoutePhase.PICK
}
