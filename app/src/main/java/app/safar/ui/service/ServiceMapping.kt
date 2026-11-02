package app.safar.ui.service

import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.core.RideState
import app.safar.data.ServiceItem
import app.safar.data.ServiceState
import app.safar.data.ServiceStatus
import app.safar.data.serviceStatuses
import app.safar.ui.format.Formatters
import app.safar.ui.format.LockText
import kotlin.math.max

// Pure state mapping for the service logbook (ux/variant-b/js/screens/service.js). Maths: data/ServiceStatus.

private const val BIG_STEP = 500
private const val SMALL_STEP = 50
private const val BIG_FROM = 2000

/** The odometer the logbook counts from, and where it came from. [km] is null before any reading or item. */
data class OdoNowUi(val km: Int?, val atMillis: Long?, val live: Boolean, val source: String)

data class ServiceRowUi(
    val status: ServiceStatus,
    val word: String,
    val line: String,
    val fromKm: Int,
    val toKm: Int,
    /** 0..1 for the road drawing. */
    val drawn: Float,
)

data class ServiceUiState(
    val loading: Boolean = true,
    val odo: OdoNowUi = OdoNowUi(null, null, false, "no bike reading"),
    val rows: List<ServiceRowUi> = emptyList(),
    val needYou: Int = 0,
    val reminders: Boolean = true,
    val lockReason: String? = null,
)

/** Live reading, else the last one kept after a disconnect, else the latest service odometer. */
fun odoNow(bike: BikeState, items: List<ServiceItem>): OdoNowUi {
    val live = bike.link == LinkState.Connected && bike.cluster != null
    val reading = bike.cluster ?: bike.lastCluster
    return when {
        reading != null && live -> OdoNowUi(reading.odometerKm, reading.receivedAtMillis, true, "bike")
        reading != null -> OdoNowUi(reading.odometerKm, reading.receivedAtMillis, false, "bike · last known")
        else -> OdoNowUi(items.maxOfOrNull { it.lastDoneOdoKm }, null, false, "last service · no bike reading")
    }
}

fun stateWord(state: ServiceState): String = when (state) {
    ServiceState.OVERDUE -> "overdue"
    ServiceState.DUE_SOON -> "due soon"
    ServiceState.OK -> "on track"
}

/** "Due in 1,234 km · at 15,000" or "Overdue by 120 km". */
fun dueLine(s: ServiceStatus): String =
    if (s.kmToNext < 0) {
        "Overdue by ${Formatters.odometer(-s.kmToNext)} km"
    } else {
        "Due in ${Formatters.odometer(s.kmToNext)} km · at ${Formatters.odometer(s.dueAtKm)}"
    }

fun serviceRow(s: ServiceStatus) = ServiceRowUi(
    status = s,
    word = stateWord(s.state),
    line = dueLine(s),
    fromKm = s.item.lastDoneOdoKm,
    toKm = s.dueAtKm,
    drawn = s.progress.toFloat().coerceIn(0f, 1f),
)

/** ", all good." / ", 1 needs you." / ", 3 need you." */
fun titleTail(needYou: Int): String = when (needYou) {
    0 -> ", all good."
    1 -> ", 1 needs you."
    else -> ", $needYou need you."
}

fun serviceUiState(items: List<ServiceItem>, bike: BikeState, ride: RideState, reminders: Boolean): ServiceUiState {
    val odo = odoNow(bike, items)
    val statuses = serviceStatuses(items, odo.km ?: 0)
    return ServiceUiState(
        loading = false,
        odo = odo,
        rows = statuses.map(::serviceRow),
        needYou = statuses.count { it.state != ServiceState.OK },
        reminders = reminders,
        lockReason = LockText.whileMoving(ride.moving, ride.speedKmh),
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
