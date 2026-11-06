package app.safar.data

import java.time.Instant
import java.time.ZoneId

/** Overall state of a service item. Ordered from calm to urgent. */
enum class ServiceState { OK, DUE_SOON, OVERDUE }

/** Which side of an item comes due first. */
enum class ServiceLead { KM, TIME }

/**
 * Where a service item stands at the current odometer and day: its distance side [km] and time side [time]
 * (each null when the item is not counted that way). The [lead] is the side further along; [progress] is its
 * progress (0 at the last service, 1 when due, past 1 when overdue) and [state] the worse of the two.
 */
data class ServiceStatus(
    val item: ServiceItem,
    val km: KmLeg?,
    val time: TimeLeg?,
    val lead: ServiceLead,
    val progress: Double,
    val state: ServiceState,
)

fun serviceStatus(item: ServiceItem, odometerKm: Int, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): ServiceStatus {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val km = kmLeg(item, odometerKm)
    val time = timeLeg(item, today, zone)
    val lead = if (time != null && (km == null || time.progress > km.progress)) ServiceLead.TIME else ServiceLead.KM
    val progress = maxOf(km?.progress ?: 0.0, time?.progress ?: 0.0)
    val state = listOfNotNull(km?.state, time?.state).maxOrNull() ?: ServiceState.OK
    return ServiceStatus(item, km, time, lead, progress, state)
}

/** Statuses for all items, most urgent first: overdue, then due soon, then on track; further along first. */
fun serviceStatuses(items: List<ServiceItem>, odometerKm: Int, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): List<ServiceStatus> =
    items.map { serviceStatus(it, odometerKm, nowMillis, zone) }
        .sortedWith(compareByDescending<ServiceStatus> { it.state }.thenByDescending { it.progress })

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
