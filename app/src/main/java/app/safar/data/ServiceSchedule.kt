package app.safar.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max

// The two ways an item comes due: by distance and by time. ServiceStatus.kt combines them.

/** [date] moved [count] whole units of this span forward (or back, for a negative [count]). */
fun TimeSpan.addTo(date: LocalDate, times: Long = 1): LocalDate = when (unit) {
    SpanUnit.DAYS -> date.plusDays(count * times)
    SpanUnit.WEEKS -> date.plusWeeks(count * times)
    SpanUnit.MONTHS -> date.plusMonths(count * times)
}

/**
 * Distance side of a status. [fromKm] is the last service, [dueAtKm] the next one. [leftKm] is negative when
 * overdue; [sinceKm] never drops below 0, even if the odometer reads lower than the last service.
 */
data class KmLeg(val fromKm: Int, val dueAtKm: Int, val sinceKm: Int, val leftKm: Int, val progress: Double, val state: ServiceState)

/** Time side of a status, in whole calendar days. [leftDays] is negative when overdue. */
data class TimeLeg(val from: LocalDate, val dueOn: LocalDate, val sinceDays: Long, val leftDays: Long, val progress: Double, val state: ServiceState)

/**
 * Due-soon rule: an item is [ServiceState.DUE_SOON] once what is left is at most 10 % of its interval (oil every
 * 3,000 km: from 300 km left; every 6 months: from 18 days left) and [ServiceState.OVERDUE] once it drops below
 * zero, matching the Atlas prototype. By time it is due soon from at least 1 day left.
 */
const val DUE_SOON_FRACTION = 0.10

private fun legState(left: Double, span: Double, minSoon: Double = 0.0): ServiceState = when {
    left < 0 -> ServiceState.OVERDUE
    left <= max(minSoon, span * DUE_SOON_FRACTION) -> ServiceState.DUE_SOON
    else -> ServiceState.OK
}

private fun progress(since: Double, span: Double): Double = if (span > 0) since / span else 1.0

/** Null when the item is not counted by distance. A pending [ServiceItem.firstAtKm] is the due point. */
fun kmLeg(item: ServiceItem, odometerKm: Int): KmLeg? {
    val due = item.firstAtKm ?: item.everyKm?.let { item.lastDoneOdoKm + it } ?: return null
    val span = due - item.lastDoneOdoKm
    val since = max(0, odometerKm - item.lastDoneOdoKm)
    val left = span - since
    return KmLeg(item.lastDoneOdoKm, due, since, left, progress(since.toDouble(), span.toDouble()), legState(left.toDouble(), span.toDouble()))
}

/** Day of the last service in [zone]; null when it was never dated. */
fun lastDoneDate(item: ServiceItem, zone: ZoneId): LocalDate? =
    item.lastDoneMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

/**
 * Null when the item is not counted by time, or has no dated last service to count from. A pending
 * [ServiceItem.firstByDate] is the due date.
 */
fun timeLeg(item: ServiceItem, today: LocalDate, zone: ZoneId): TimeLeg? {
    val from = lastDoneDate(item, zone) ?: return null
    val due = item.firstByDate ?: item.everyTime?.addTo(from) ?: return null
    val span = ChronoUnit.DAYS.between(from, due)
    val since = max(0L, ChronoUnit.DAYS.between(from, today))
    val left = span - since
    return TimeLeg(from, due, since, left, progress(since.toDouble(), span.toDouble()), legState(left.toDouble(), span.toDouble(), minSoon = 1.0))
}
