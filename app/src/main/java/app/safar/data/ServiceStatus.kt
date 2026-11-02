package app.safar.data

import kotlin.math.max

/** Overall state of a service item. */
enum class ServiceState { OK, DUE_SOON, OVERDUE }

/**
 * Where a service item stands at the current odometer.
 * [kmToNext] is negative when overdue. [progress] runs from 0 at the last service to 1 when due, and past 1 when
 * overdue (never below 0, even if the odometer reads lower than the last service).
 */
data class ServiceStatus(
    val item: ServiceItem,
    val kmSinceLast: Int,
    val kmToNext: Int,
    val dueAtKm: Int,
    val progress: Double,
    val state: ServiceState,
)

/**
 * Due-soon rule: an item is [ServiceState.DUE_SOON] once the km left are at most 10 % of its interval
 * (oil every 3,000 km: from 300 km left; tyre pressure every 300 km: from 30 km left). It is
 * [ServiceState.OVERDUE] once the km left drop below zero, matching the Atlas prototype.
 */
const val DUE_SOON_FRACTION = 0.10

fun serviceStatus(item: ServiceItem, odometerKm: Int): ServiceStatus {
    val since = max(0, odometerKm - item.lastDoneOdoKm)
    val left = item.everyKm - since
    val progress = if (item.everyKm > 0) since.toDouble() / item.everyKm else 1.0
    val state = when {
        left < 0 -> ServiceState.OVERDUE
        left <= item.everyKm * DUE_SOON_FRACTION -> ServiceState.DUE_SOON
        else -> ServiceState.OK
    }
    return ServiceStatus(item, since, left, item.lastDoneOdoKm + item.everyKm, progress, state)
}

/** Statuses for all items, most urgent (fewest km left) first. */
fun serviceStatuses(items: List<ServiceItem>, odometerKm: Int): List<ServiceStatus> =
    items.map { serviceStatus(it, odometerKm) }.sortedBy { it.kmToNext }

/** Default intervals, seeded when a bike is first paired (labels match the Atlas prototype). */
object DefaultServiceItems {
    private val specs = listOf(
        Triple("Engine oil", 3000, ServiceIcon.OIL),
        Triple("Chain clean & lube", 500, ServiceIcon.CHAIN),
        Triple("Air filter", 6000, ServiceIcon.AIR),
        Triple("Brake pads check", 5000, ServiceIcon.BRAKE),
        Triple("Tyre pressure", 300, ServiceIcon.TYRE),
    )

    /** New items (id 0) counted from [odometerKm], as if each was done at pairing time [atMillis]. */
    fun create(odometerKm: Int, atMillis: Long): List<ServiceItem> = specs.map { (label, every, icon) ->
        ServiceItem(label = label, everyKm = every, lastDoneOdoKm = odometerKm, lastDoneMillis = atMillis, icon = icon)
    }
}
