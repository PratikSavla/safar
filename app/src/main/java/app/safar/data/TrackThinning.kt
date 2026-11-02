package app.safar.data

import kotlinx.coroutines.flow.first

// Keeps stored GPS tracks small (see TrackSimplifier.kt). Pure Kotlin: no Android imports.

/** Track points of one trip [before] and [after] thinning. Equal when nothing could be dropped. */
data class TrackThinning(val before: Int, val after: Int)

/**
 * Simplifies trip [tripId]'s stored track ([simplifyTrack]) and stores the result in one transaction. Call it for
 * a finished trip only: points added meanwhile would be lost. Safe to repeat: a thinned track stays as it is.
 */
suspend fun TripRepository.thinTrack(tripId: Long): TrackThinning {
    val points = track(tripId).first()
    val thin = simplifyTrack(points)
    if (thin.size < points.size) replaceTrack(tripId, thin)
    return TrackThinning(points.size, thin.size)
}

/** One-time maintenance flags (a flag, once set, stays set). */
interface OneTimeFlags {
    suspend fun isSet(name: String): Boolean
    suspend fun set(name: String)
}

/** Set once every trip saved before tracks were thinned at the end of a trip has been thinned. */
const val TRACK_THINNING_FLAG = "once_track_thinning_v1"

/**
 * Thins the stored track of every finished trip, once: trips saved before thinning existed can hold a point a
 * second. The flag lives beside the data it describes, so a restored backup brings it along. Returns the points removed, or null when [flags] says it already ran. The flag is set
 * only after every trip is done, so a pass cut short runs again; that is safe because thinning is idempotent.
 */
suspend fun thinFinishedTracks(trips: TripRepository, flags: OneTimeFlags): Int? {
    if (flags.isSet(TRACK_THINNING_FLAG)) return null
    var removed = 0
    for (trip in trips.trips.first().filter { it.endMillis != null }) {
        val result = trips.thinTrack(trip.id)
        removed += result.before - result.after
    }
    flags.set(TRACK_THINNING_FLAG)
    return removed
}
