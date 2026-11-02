package app.safar.phone

import android.content.Context
import android.util.Log
import app.safar.core.AppContainer
import app.safar.core.ClusterReading
import app.safar.core.LinkState
import app.safar.core.RideState
import app.safar.core.RideTracker
import app.safar.data.GpsFix
import app.safar.data.ParkedSpot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/** A fix this accurate or better can mark the parked spot. */
private const val PARK_ACCURACY_M = 50f
/** A fix older than this is not used for the parked spot. */
private const val PARK_MAX_AGE_MS = 10 * 60_000L
private const val TOP_SPEED_ACCURACY_M = 30f

/** Maps the BLE link to what the trip rules need. Idle = the rider disconnected. */
fun bikeLinkOf(link: LinkState): BikeLink = when (link) {
    LinkState.Connected -> BikeLink.CONNECTED
    LinkState.Idle -> BikeLink.OFF
    else -> BikeLink.LOST
}

/**
 * Phone GPS speed, trips and parked spot (docs 12.4 E5, E7, E8). Uses `LocationManager` only: GPS 1 s / 0 m
 * while the bike is connected, the phone moves or a trip records; passive fixes otherwise. All events run in
 * one coroutine. Without ACCESS_FINE_LOCATION nothing is tracked and no trip starts.
 */
class RideTrackerImpl(context: Context, private val scope: CoroutineScope, private val log: PhoneEventLog) : RideTracker {
    private sealed interface Event {
        data class Fix(val sample: GpsSample) : Event
        data class Link(val link: BikeLink) : Event
        data class Reading(val reading: ClusterReading) : Event
        data object Tick : Event
    }

    private val mutable = MutableStateFlow(RideState())
    override val state: StateFlow<RideState> = mutable.asStateFlow()

    private val events = Channel<Event>(Channel.UNLIMITED)
    private val location = LocationSource(context) { events.trySend(Event.Fix(it)) }
    private val motion = MotionDetector()
    private val rules = TripRules()
    private val recorder = TripRecorder({ AppContainer.trips })

    private var link = BikeLink.OFF
    private var reading: ClusterReading? = null
    private var previousFix: GpsFix? = null
    private var parkCandidate: GpsFix? = null
    private var parkCandidateAt = 0L
    private var lastFixAt: Long? = null
    private var topSpeed = 0f

    fun start() {
        scope.launch { for (event in events) handleLogged(event) }
        scope.launch {
            while (isActive) {
                delay(1_000)
                events.send(Event.Tick)
            }
        }
        scope.launch { observeBike() }
        refresh()
    }

    /** Re-checks the location permission (after the rider grants it) on the next event. Any thread. */
    fun refresh() {
        events.trySend(Event.Tick)
    }

    private suspend fun observeBike() {
        val bike = awaitInstalled("Bike controller") { AppContainer.bike }
        scope.launch {
            bike.state.map { bikeLinkOf(it.link) }.distinctUntilChanged().collect { events.send(Event.Link(it)) }
        }
        bike.state.map { it.cluster }.filterNotNull().distinctUntilChanged().collect { events.send(Event.Reading(it)) }
    }

    private suspend fun handleLogged(event: Event) {
        try {
            handle(event)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Ride tracker failed on ${event.javaClass.simpleName}", e)
            log.error("ride.failed", mapOf("event" to event.javaClass.simpleName, "error" to e.javaClass.simpleName))
        }
    }

    private suspend fun handle(event: Event) {
        when (event) {
            is Event.Fix -> onFix(event.sample)
            is Event.Link -> onLink(event.link)
            is Event.Reading -> {
                reading = event.reading
                recorder.onReading(event.reading)
            }
            Event.Tick -> evaluate()
        }
    }

    private suspend fun onFix(sample: GpsSample) {
        val now = System.currentTimeMillis()
        val fix = sample.fix
        val kmh = sample.speedKmh ?: fallbackSpeedKmh(previousFix, fix)
        previousFix = fix
        lastFixAt = now
        val accurate = (fix.accuracyM ?: 0f) <= TOP_SPEED_ACCURACY_M
        if (kmh != null && accurate) motion.onSpeed(kmh, now)
        if ((fix.accuracyM ?: 0f) <= PARK_ACCURACY_M) {
            parkCandidate = fix
            parkCandidateAt = now
        }
        if (rules.recording) {
            recorder.onFix(fix, kmh?.takeIf { accurate })
            topSpeed = recorder.topSpeedKmh
        }
        publish(now)
    }

    private suspend fun onLink(next: BikeLink) {
        val previous = link
        link = next
        if (next == BikeLink.CONNECTED) reading = null // a frame from an earlier connection is stale
        if (previous == BikeLink.CONNECTED && next != BikeLink.CONNECTED) saveParked()
        evaluate()
    }

    private suspend fun evaluate() {
        val now = System.currentTimeMillis()
        motion.evaluate(now)
        if (location.hasPermission || rules.recording) {
            when (val decision = rules.evaluate(now, link, motion.speedAt(now))) {
                is TripDecision.Start -> startTrip(now, decision.viaBike)
                TripDecision.End -> endTrip(now)
                TripDecision.None -> Unit
            }
        }
        location.set(wantedMode())
        publish(now)
    }

    private suspend fun startTrip(now: Long, viaBike: Boolean) {
        topSpeed = 0f
        val id = recorder.start(now, reading)
        log.info("trip.started", mapOf("id" to id.toString(), "via" to if (viaBike) "bike" else "movement"))
    }

    private suspend fun endTrip(now: Long) {
        val done = recorder.end(now, reading)
        if (done == null) {
            log.info("trip.discarded", mapOf("reason" to "under $MIN_TRIP_KM km"))
        } else {
            log.info("trip.saved", mapOf("id" to done.id.toString(), "km" to String.format(Locale.ROOT, "%.2f", done.distanceKm)))
        }
    }

    private suspend fun saveParked() {
        val now = System.currentTimeMillis()
        val fresh = parkCandidate?.takeIf { now - parkCandidateAt <= PARK_MAX_AGE_MS }
        val fix = fresh ?: location.lastKnownGps()?.fix?.takeIf { now - it.atMillis <= PARK_MAX_AGE_MS }
        if (fix == null) {
            log.info("parked.no_fix")
            return
        }
        AppContainer.parked.save(ParkedSpot(fix.lat, fix.lng, fix.accuracyM ?: 0f, now))
        log.info("parked.saved", mapOf("accuracyM" to (fix.accuracyM?.toString() ?: "unknown")))
    }

    private fun wantedMode(): LocationMode =
        if (link == BikeLink.CONNECTED || motion.moving || rules.recording) LocationMode.GPS else LocationMode.PASSIVE

    private fun publish(now: Long) {
        mutable.value = RideState(
            speedKmh = motion.speedAt(now),
            topSpeedKmh = topSpeed,
            moving = motion.moving,
            recordingTripId = recorder.tripId,
            lastFixAtMillis = lastFixAt,
        )
    }
}
