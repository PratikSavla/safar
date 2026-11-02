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
