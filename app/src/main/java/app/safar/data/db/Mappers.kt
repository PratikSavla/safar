package app.safar.data.db

import app.safar.data.FuelUp
import app.safar.data.OdoSource
import app.safar.data.ParkedSpot
import app.safar.data.Place
import app.safar.data.PlaceKind
import app.safar.data.PlaceSource
import app.safar.data.ServiceIcon
import app.safar.data.ServiceItem
import app.safar.data.TrackPoint
import app.safar.data.Trip

// Entity <-> model mappers. Pure: no Android imports, unit-tested.

/** Enum by stored name; [fallback] for a name this build does not know (for example after a downgrade). */
internal inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback

fun FuelUpEntity.toModel() = FuelUp(
    id = id, atMillis = atMillis, odometerKm = odometerKm, litres = litres, totalPaid = totalPaid, full = full,
    odoSource = enumOr(odoSource, OdoSource.MANUAL), barsBefore = barsBefore,
)

fun FuelUp.toEntity() = FuelUpEntity(
    id = id, atMillis = atMillis, odometerKm = odometerKm, litres = litres, totalPaid = totalPaid, full = full,
    odoSource = odoSource.name, barsBefore = barsBefore,
)

fun TripEntity.toModel() = Trip(
    id = id, title = title, startMillis = startMillis, endMillis = endMillis, distanceKm = distanceKm,
    topSpeedKmh = topSpeedKmh, avgSpeedKmh = avgSpeedKmh, odoStartKm = odoStartKm, odoEndKm = odoEndKm,
    fuelBarsStart = fuelBarsStart, fuelBarsEnd = fuelBarsEnd,
)

fun Trip.toEntity() = TripEntity(
    id = id, title = title, startMillis = startMillis, endMillis = endMillis, distanceKm = distanceKm,
    topSpeedKmh = topSpeedKmh, avgSpeedKmh = avgSpeedKmh, odoStartKm = odoStartKm, odoEndKm = odoEndKm,
    fuelBarsStart = fuelBarsStart, fuelBarsEnd = fuelBarsEnd,
)

fun TrackPointEntity.toModel() = TrackPoint(tripId = tripId, atMillis = atMillis, lat = lat, lng = lng, speedKmh = speedKmh)

fun TrackPoint.toEntity() = TrackPointEntity(tripId = tripId, atMillis = atMillis, lat = lat, lng = lng, speedKmh = speedKmh)

fun ServiceItemEntity.toModel() = ServiceItem(
    id = id, label = label, everyKm = everyKm, lastDoneOdoKm = lastDoneOdoKm, lastDoneMillis = lastDoneMillis,
    icon = enumOr(icon, ServiceIcon.GENERIC),
)

fun ServiceItem.toEntity() = ServiceItemEntity(
    id = id, label = label, everyKm = everyKm, lastDoneOdoKm = lastDoneOdoKm, lastDoneMillis = lastDoneMillis,
    icon = icon.name,
)

fun PlaceEntity.toModel() = Place(
    id = id, label = label, detail = detail, query = query, kind = enumOr(kind, PlaceKind.STAR),
    source = enumOr(source, PlaceSource.TYPED), sortOrder = sortOrder, lat = lat, lng = lng,
)

fun Place.toEntity() = PlaceEntity(
    id = id, label = label, detail = detail, query = query, kind = kind.name, source = source.name,
    sortOrder = sortOrder, lat = lat, lng = lng,
)

fun ParkedSpotEntity.toModel() = ParkedSpot(lat = lat, lng = lng, accuracyM = accuracyM, atMillis = atMillis, label = label)

fun ParkedSpot.toEntity() = ParkedSpotEntity(lat = lat, lng = lng, accuracyM = accuracyM, atMillis = atMillis, label = label)

/**
 * Swaps place [id] with its neighbour in [direction] (-1 up, +1 down) in [ordered] and renumbers 0..n-1, so
 * equal or gapped sort orders cannot block a move. Returns only the rows whose sort order changed; empty at
 * either end or for an unknown id.
 */
fun reorderPlaces(ordered: List<PlaceEntity>, id: Long, direction: Int): List<PlaceEntity> {
    val list = ordered.toMutableList()
    val i = list.indexOfFirst { it.id == id }
    val j = i + direction
    if (i < 0 || j !in list.indices) return emptyList()
    list[i] = list[j].also { list[j] = list[i] }
    return list.mapIndexedNotNull { index, row -> if (row.sortOrder != index) row.copy(sortOrder = index) else null }
}
