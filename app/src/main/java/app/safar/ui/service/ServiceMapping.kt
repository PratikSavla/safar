package app.safar.ui.service

import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.core.RideState
import app.safar.data.FuelUp
import app.safar.data.ServiceItem
import app.safar.data.ServiceLead
import app.safar.data.ServiceState
import app.safar.data.ServiceStatus
import app.safar.data.serviceStatuses
import app.safar.ui.format.Formatters
import app.safar.ui.format.LockText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

// Pure state mapping for the service logbook (ux/variant-b/js/screens/service.js). Maths: data/ServiceStatus.

private const val BIG_STEP = 500
private const val SMALL_STEP = 50
private const val BIG_FROM = 2000

/** Where the logbook's odometer came from. [developer] is the Developer mode caption. */
enum class OdoFrom(val developer: String) {
    BIKE("bike"),
    LAST_RIDE("bike · last known"),
    FUEL_UP("last fuel-up · no bike reading"),
    SERVICE("last service · no bike reading"),
}

/** The odometer the logbook counts from, and where it came from. [km] is null before any reading or item. */
data class OdoNowUi(val km: Int?, val atMillis: Long?, val live: Boolean, val from: OdoFrom)

/**
 * The rider's note under a typed odometer: where it came from, or how to get one. Null for a bike reading, which
 * [app.safar.ui.components.Freshness] words (nothing while fresh, "From your last ride" after a disconnect).
 */
fun odoNote(odo: OdoNowUi): String? = when {
    odo.km == null -> "Connect your bike or log a fuel-up to see it."
    odo.from == OdoFrom.FUEL_UP -> "From your last fuel-up"
    odo.from == OdoFrom.SERVICE -> "From your last service"
    else -> null
}

data class ServiceRowUi(
    val status: ServiceStatus,
    val word: String,
    /** Under the name: "every 3,000 km or 6 months", or the pending first service and then the interval. */
    val everyLine: String,
    val line: String,
    /** Under the road, for the side that leads: odometer ("9,200 km") or day ("6 Jun"). */
    val fromLabel: String,
    val toLabel: String,
    /** 0..1 for the road drawing. */
    val drawn: Float,
)

data class ServiceUiState(
    val loading: Boolean = true,
    val odo: OdoNowUi = OdoNowUi(null, null, false, OdoFrom.SERVICE),
    val rows: List<ServiceRowUi> = emptyList(),
    val needYou: Int = 0,
    val reminders: Boolean = true,
    val lockReason: String? = null,
    /** The day the logbook counts to, for the sheets' date steppers. */
    val today: LocalDate = LocalDate.MIN,
)

/**
 * The one "odometer now" for Home and the logbook: the live reading, else the last one kept after a disconnect,
 * else the highest odometer the rider typed (at a fuel-up or a service).
 */
fun odoNow(bike: BikeState, items: List<ServiceItem>, fuelUps: List<FuelUp> = emptyList()): OdoNowUi {
    val live = bike.link == LinkState.Connected && bike.cluster != null
    val reading = bike.cluster ?: bike.lastCluster
    val fuel = fuelUps.maxByOrNull { it.odometerKm }
    // Items counted by time only may have been added before any odometer was known: theirs is not a reading.
    val service = items.filter { it.everyKm != null }.maxOfOrNull { it.lastDoneOdoKm }
    return when {
        reading != null && live -> OdoNowUi(reading.odometerKm, reading.receivedAtMillis, true, OdoFrom.BIKE)
        reading != null -> OdoNowUi(reading.odometerKm, reading.receivedAtMillis, false, OdoFrom.LAST_RIDE)
        fuel != null && fuel.odometerKm >= (service ?: 0) -> OdoNowUi(fuel.odometerKm, fuel.atMillis, false, OdoFrom.FUEL_UP)
        else -> OdoNowUi(service, null, false, OdoFrom.SERVICE)
    }
}

fun stateWord(state: ServiceState): String = when (state) {
    ServiceState.OVERDUE -> "overdue"
    ServiceState.DUE_SOON -> "due soon"
    ServiceState.OK -> "on track"
}

fun serviceRow(s: ServiceStatus): ServiceRowUi {
    val (from, to) = roadLabels(s)
    return ServiceRowUi(
        status = s,
        word = stateWord(s.state),
        everyLine = everyLine(s.item),
        line = dueLine(s),
        fromLabel = from,
        toLabel = to,
        drawn = s.progress.toFloat().coerceIn(0f, 1f),
    )
}

/** Odometers or days at both ends of the road, for the side that leads. */
fun roadLabels(s: ServiceStatus): Pair<String, String> {
    val time = s.time
    val km = s.km
    return when {
        s.lead == ServiceLead.TIME && time != null -> Formatters.shortDate(time.from) to Formatters.shortDate(time.dueOn)
        km != null -> "${Formatters.odometer(km.fromKm)} km" to "${Formatters.odometer(km.dueAtKm)} km"
        else -> "" to ""
    }
}

/** ", all good." / ", 1 needs you." / ", 3 need you." */
fun titleTail(needYou: Int): String = when (needYou) {
    0 -> ", all good."
    1 -> ", 1 needs you."
    else -> ", $needYou need you."
}

fun serviceUiState(
    items: List<ServiceItem>,
    bike: BikeState,
    ride: RideState,
    reminders: Boolean,
    fuelUps: List<FuelUp> = emptyList(),
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): ServiceUiState {
    val odo = odoNow(bike, items, fuelUps)
    val statuses = serviceStatuses(items, odo.km ?: 0, nowMillis, zone)
    return ServiceUiState(
        loading = false,
        odo = odo,
        rows = statuses.map(::serviceRow),
        needYou = statuses.count { it.state != ServiceState.OK },
        reminders = reminders,
        lockReason = LockText.whileMoving(ride.moving, ride.speedKmh),
        today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate(),
    )
}

/** Interval stepper: 500 km steps from 2,000 km up, else 50 km (service.js `editSheet`). */
fun intervalStep(everyKm: Int): Int = if (everyKm >= BIG_FROM) BIG_STEP else SMALL_STEP

/** One step up ([direction] +1) or down (-1), never below one step. */
fun stepInterval(everyKm: Int, direction: Int): Int {
    val step = intervalStep(everyKm)
    return max(step, everyKm + direction * step)
}

/** Odometer to log a service at: the current (or last known) reading, else the item's last service. */
fun markDoneOdo(odo: OdoNowUi, item: ServiceItem): Int = odo.km ?: item.lastDoneOdoKm

/** A custom item's label: trimmed, at most 32 characters; null when blank. */
fun cleanLabel(input: String, max: Int = 32): String? = input.trim().take(max).ifEmpty { null }

/** Where a new item starts counting; distance needs a known odometer, time counts from today. */
fun addFromLine(odoKm: Int?, dueBy: DueBy = DueBy.DISTANCE): String = when {
    dueBy == DueBy.TIME -> "Counted from today."
    odoKm == null -> "We need your odometer first. Connect your bike or log a fuel-up."
    dueBy == DueBy.BOTH -> "Counted from ${Formatters.odometer(odoKm)} km, your odometer now, and from today."
    else -> "Counted from ${Formatters.odometer(odoKm)} km, your odometer now."
}
