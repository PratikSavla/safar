package app.safar.data

/**
 * Result of merging two or more trips. [trip] holds the combined statistics; [track] is the
 * concatenated GPS points in time order; [stops] marks each join point between legs.
 */
data class MergeResult(val trip: Trip, val track: List<TrackPoint>, val stops: List<TripStop>)

/**
 * Merges [trips] (must be ≥ 2, in ascending startMillis order) and their [tracks] into one entry.
 *
 * Rules:
 * - distanceKm = sum of all source distances
 * - topSpeedKmh = max of all source top speeds
 * - avgSpeedKmh = weighted average by duration; falls back to 0 when total duration is 0
 * - odoStartKm = first trip's odoStartKm; odoEndKm = last trip's odoEndKm
 * - fuelBarsStart = first trip's fuelBarsStart; fuelBarsEnd = last trip's fuelBarsEnd
 * - note = null (user sets it afterwards)
 * - Each join point (last GPS fix of trip N, for N = 0..size-2) becomes a TripStop with index N.
 *   TripStop.tripId is set to 0 — the caller must replace it with the merged trip's real id.
 */
fun mergeTrips(trips: List<Trip>, tracks: Map<Long, List<TrackPoint>>, title: String): MergeResult {
    require(trips.size >= 2) { "Need at least 2 trips to merge" }
    val sorted = trips.sortedBy { it.startMillis }
    val merged = Trip(
        id = 0,
        title = title,
        startMillis = sorted.first().startMillis,
        endMillis = sorted.last().endMillis,
        distanceKm = sorted.sumOf { it.distanceKm },
        topSpeedKmh = sorted.maxOf { it.topSpeedKmh },
        avgSpeedKmh = weightedAvgSpeed(sorted),
        odoStartKm = sorted.first().odoStartKm,
        odoEndKm = sorted.last().odoEndKm,
        fuelBarsStart = sorted.first().fuelBarsStart,
        fuelBarsEnd = sorted.last().fuelBarsEnd,
        note = null,
    )
    val track = sorted.flatMap { t -> (tracks[t.id] ?: emptyList()).sortedBy { it.atMillis } }
    val stops = sorted.dropLast(1).mapIndexedNotNull { i, t ->
        val lastFix = (tracks[t.id] ?: emptyList()).maxByOrNull { it.atMillis } ?: return@mapIndexedNotNull null
        TripStop(tripId = 0, index = i, lat = lastFix.lat, lng = lastFix.lng, atMillis = lastFix.atMillis)
    }
    return MergeResult(merged, track, stops)
}

private fun weightedAvgSpeed(trips: List<Trip>): Float {
    val totalMs = trips.sumOf { (it.endMillis ?: it.startMillis) - it.startMillis }
    if (totalMs == 0L) return 0f
    val weighted = trips.sumOf { t ->
        val ms = (t.endMillis ?: t.startMillis) - t.startMillis
        t.avgSpeedKmh.toDouble() * ms
    }
    return (weighted / totalMs).toFloat()
}
