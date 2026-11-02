package app.safar.phone

import app.safar.core.ClusterReading
import app.safar.data.GpsFix
import app.safar.data.TrackFilter
import app.safar.data.TrackPoint
import app.safar.data.Trip
import app.safar.data.TripRepository
import app.safar.data.autoTripTitle
import app.safar.data.avgMovingSpeedKmh
import java.time.ZoneId

/** Track points written per repository call. */
const val TRACK_BATCH = 20

/**
 * Records one trip at a time (docs 12.4 E5): the Trip row at start, filtered track points in batches, and
 * distance, speeds, odometer and fuel bars at the end. Trips under [MIN_TRIP_KM] are deleted. Pure Kotlin;
 * not thread-safe (the ride tracker calls it from one coroutine). [trips] is read lazily.
 */
class TripRecorder(
    private val trips: () -> TripRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private var trip: Trip? = null
    private var filter = TrackFilter()
    private val fixes = ArrayList<GpsFix>()
    private val pending = ArrayList<TrackPoint>()

    var topSpeedKmh: Float = 0f
        private set

    val tripId: Long? get() = trip?.id

    /** Saves a new Trip row. [reading] is the current cluster frame, if the bike is connected. */
    suspend fun start(nowMillis: Long, reading: ClusterReading?): Long {
        val draft = Trip(
            title = autoTripTitle(nowMillis, zone),
            startMillis = nowMillis,
            endMillis = null,
            distanceKm = 0.0,
            topSpeedKmh = 0f,
            avgSpeedKmh = 0f,
            odoStartKm = reading?.odometerKm,
            odoEndKm = null,
            fuelBarsStart = reading?.fuelBars,
            fuelBarsEnd = null,
        )
        val id = trips().start(draft)
        trip = draft.copy(id = id)
        filter = TrackFilter()
        fixes.clear()
        pending.clear()
        topSpeedKmh = 0f
        return id
    }

    /** Fills the start odometer and fuel bars from the first cluster frame after a movement start. */
    fun onReading(reading: ClusterReading) {
        trip = trip?.let { t ->
            t.copy(odoStartKm = t.odoStartKm ?: reading.odometerKm, fuelBarsStart = t.fuelBarsStart ?: reading.fuelBars)
        }
    }

    /** Adds a fix that passes [TrackFilter]. [speedKmh] counts for the top speed when plausible. */
    suspend fun onFix(fix: GpsFix, speedKmh: Float?) {
        val t = trip ?: return
        if (filter.accept(fix) == null) return
        fixes += fix
        if (speedKmh != null && speedKmh <= MAX_GPS_KMH) topSpeedKmh = maxOf(topSpeedKmh, speedKmh)
        pending += TrackPoint(t.id, fix.atMillis, fix.lat, fix.lng, speedKmh ?: 0f)
        if (pending.size >= TRACK_BATCH) flush()
    }

    /** Ends the trip. Returns the saved trip, or null when it was too short and deleted (or none was running). */
    suspend fun end(nowMillis: Long, reading: ClusterReading?): Trip? {
        val t = trip ?: return null
        val odoEnd = reading?.odometerKm
        val distance = tripDistanceKm(filter.distanceM / 1000.0, t.odoStartKm, odoEnd)
        if (!keepTrip(distance)) {
            pending.clear()
            trip = null
            trips().delete(t.id)
            return null
        }
        flush()
        trip = null
        val done = t.copy(
            endMillis = nowMillis,
            distanceKm = distance,
            topSpeedKmh = topSpeedKmh,
            avgSpeedKmh = avgMovingSpeedKmh(fixes).toFloat(),
            odoEndKm = odoEnd,
            fuelBarsEnd = reading?.fuelBars ?: t.fuelBarsEnd,
        )
        trips().update(done)
        return done
    }

    private suspend fun flush() {
        if (pending.isEmpty()) return
        val batch = pending.toList()
        pending.clear()
        trips().addPoints(batch)
    }
}
