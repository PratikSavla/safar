package app.safar.ui.service

import app.safar.data.KmLeg
import app.safar.data.ServiceItem
import app.safar.data.ServiceLead
import app.safar.data.ServiceStatus
import app.safar.data.SpanUnit
import app.safar.data.TimeLeg
import app.safar.data.TimeSpan
import app.safar.ui.format.Formatters
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// Words for service intervals and due points (service.js). Pure: no Android imports.

private fun plural(n: Long, one: String): String = if (n == 1L) "1 $one" else "$n ${one}s"

/** "1 day", "2 weeks", "6 months". */
fun spanText(span: TimeSpan): String = plural(
    span.count.toLong(),
    when (span.unit) {
        SpanUnit.DAYS -> "day"
        SpanUnit.WEEKS -> "week"
        SpanUnit.MONTHS -> "month"
    },
)

/** "1 day", "12 days". */
fun daysText(days: Long): String = plural(days, "day")

/** The recurring interval: "3,000 km or 6 months", "2 weeks", "500 km". */
fun recurringText(item: ServiceItem): String =
    listOfNotNull(item.everyKm?.let { "${Formatters.odometer(it)} km" }, item.everyTime?.let(::spanText)).joinToString(" or ")

/**
 * Under the item's name: "every 3,000 km or 6 months", or while the first service is pending
 * "first at 1,000 km or by 6 Dec, then every 3,000 km or 6 months".
 */
fun everyLine(item: ServiceItem): String {
    val every = "every ${recurringText(item)}"
    if (!item.firstPending) return every
    val first = listOfNotNull(
        item.firstAtKm?.let { "at ${Formatters.odometer(it)} km" },
        item.firstByDate?.let { "by ${Formatters.shortDate(it)}" },
    ).joinToString(" or ")
    return "first $first, then $every"
}

private fun kmDue(leg: KmLeg): String = "in ${Formatters.odometer(leg.leftKm)} km, at ${Formatters.odometer(leg.dueAtKm)} km"

private fun timeDue(leg: TimeLeg): String = when (leg.leftDays) {
    0L -> "today"
    else -> "in ${daysText(leg.leftDays)}, on ${Formatters.shortDate(leg.dueOn)}"
}

private fun overdue(s: ServiceStatus): String {
    val time = s.time
    val km = s.km
    return when {
        s.lead == ServiceLead.TIME && time != null -> "Overdue by ${daysText(abs(time.leftDays))}"
        km != null -> "Overdue by ${Formatters.odometer(-km.leftKm)} km"
        else -> "Overdue"
    }
}

/**
 * "Due in 1,234 km, at 15,000 km", "Due in 12 days, on 18 Nov", "Due today", both sides with the leading one
 * first and "whichever comes first", or "Overdue by 120 km" / "Overdue by 5 days" for the leading side.
 */
fun dueLine(s: ServiceStatus): String {
    val km = s.km
    val time = s.time
    if ((km?.leftKm ?: 0) < 0 || (time?.leftDays ?: 0L) < 0L) return overdue(s)
    val kmPart = km?.let(::kmDue)
    val timePart = time?.let(::timeDue)
    val parts = if (s.lead == ServiceLead.TIME) listOfNotNull(timePart, kmPart) else listOfNotNull(kmPart, timePart)
    return when (parts.size) {
        0 -> "No interval set"
        1 -> "Due ${parts[0]}"
        else -> "Due ${parts[0]}, or ${parts[1]}, whichever comes first"
    }
}

/** What TalkBack reads for the road: how far along, and on which side. */
fun roadSpoken(row: ServiceRowUi): String {
    val side = if (row.status.lead == ServiceLead.TIME) "by time" else "by distance"
    return "${(row.drawn * 100).roundToInt()} percent of the way from the last service to the next, $side"
}

private val longDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.UK)

/** "Sun 6 Dec 2026", for the first-service date stepper. */
fun longDate(date: LocalDate): String = longDate.format(date)
