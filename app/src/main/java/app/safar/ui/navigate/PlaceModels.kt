package app.safar.ui.navigate

import app.safar.data.Place
import app.safar.data.PlaceKind
import app.safar.data.PlaceSource
import app.safar.data.latLngQuery
import app.safar.data.parseMapsShare
import kotlin.math.roundToInt

/** Longest name in the destination list. */
const val PLACE_LABEL_MAX = 32

/**
 * A place being saved or edited. Safar cannot search places (no map SDK, no API key): it comes shared from
 * Google Maps, from the phone's last GPS fix, or typed. [query] is what Maps gets.
 */
data class PlaceDraft(
    val source: PlaceSource,
    val label: String,
    val detail: String,
    val query: String,
    val kind: PlaceKind,
    val link: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

fun draftFromShare(text: String?): PlaceDraft? = parseMapsShare(text)?.let {
    PlaceDraft(PlaceSource.MAPS, it.label.take(PLACE_LABEL_MAX), it.detail, it.query, PlaceKind.STAR, it.link)
}

fun draftFromPlace(p: Place): PlaceDraft = PlaceDraft(p.source, p.label, p.detail, p.query, p.kind, lat = p.lat, lng = p.lng)

fun draftFromLocation(lat: Double, lng: Double, accuracyM: Float): PlaceDraft =
    PlaceDraft(PlaceSource.HERE, "", "Your location · within ${accuracyM.roundToInt()} m", latLngQuery(lat, lng), PlaceKind.HOME, lat = lat, lng = lng)

fun typedDraft(): PlaceDraft = PlaceDraft(PlaceSource.TYPED, "", "", "", PlaceKind.STAR)

/** First problem that blocks saving, or null when the draft can be saved. */
fun validateDraft(d: PlaceDraft): String? = when {
    d.query.isBlank() -> "Add an address first"
    d.label.isBlank() -> "Give it a name"
    else -> null
}

/** The stored place. A typed address is its own detail line. */
fun PlaceDraft.toPlace(id: Long, sortOrder: Int): Place {
    val q = query.trim()
    return Place(
        id = id,
        label = label.trim(),
        detail = if (source == PlaceSource.TYPED) q else detail,
        query = q,
        kind = kind,
        source = source,
        sortOrder = sortOrder,
        lat = lat,
        lng = lng,
    )
}

/** Stamp ink for a place source. */
enum class SourceTone { MOSS, SLATE, INK }

fun PlaceSource.stamp(): Pair<String, SourceTone> = when (this) {
    PlaceSource.MAPS -> "Shared from Google Maps" to SourceTone.MOSS
    PlaceSource.HERE -> "Where you stood" to SourceTone.SLATE
    PlaceSource.TYPED -> "Typed address" to SourceTone.INK
    PlaceSource.PARKED -> "From where you parked" to SourceTone.SLATE
    PlaceSource.TRIP -> "Where a ride ended" to SourceTone.SLATE
}

val PlaceKindLabels: List<Pair<PlaceKind, String>> = listOf(
    PlaceKind.HOME to "Home",
    PlaceKind.WORK to "Work",
    PlaceKind.FUEL to "Fuel",
    PlaceKind.STAR to "Favourite",
)
