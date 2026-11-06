package app.safar.ui.service

import app.safar.data.ServiceIcon
import app.safar.data.ServiceItem
import app.safar.data.SpanUnit
import app.safar.data.TimeSpan
import app.safar.data.addTo
import app.safar.ui.format.Formatters
import java.time.LocalDate

// The add / edit sheet as plain data: what the rider chose, checks, and the item it saves. No Android imports.

/** How an item comes due. */
enum class DueBy { DISTANCE, TIME, BOTH }

private const val NEW_ITEM_KM = 1000
private val NEW_ITEM_TIME = TimeSpan(6, SpanUnit.MONTHS)

/** Highest count the time stepper reaches for each unit. */
fun maxCount(unit: SpanUnit): Int = when (unit) {
    SpanUnit.DAYS -> 90
    SpanUnit.WEEKS -> 52
    SpanUnit.MONTHS -> 36
}

/**
 * The sheet's fields. [firstAtKmText] is typed (an odometer); [firstByDate] comes from a stepper. Both only count
 * when [firstOn] is set and [dueBy] covers their side.
 */
data class ServiceForm(
    val label: String = "",
    val icon: ServiceIcon = ServiceIcon.GENERIC,
    val dueBy: DueBy = DueBy.DISTANCE,
    val everyKm: Int = NEW_ITEM_KM,
    val everyTime: TimeSpan = NEW_ITEM_TIME,
    val firstOn: Boolean = false,
    val firstAtKmText: String = "",
    val firstByDate: LocalDate? = null,
) {
    val byKm: Boolean get() = dueBy != DueBy.TIME
    val byTime: Boolean get() = dueBy != DueBy.DISTANCE

    /** The typed first odometer, or null when blank or not a whole number. */
    val firstAtKm: Int? get() = firstAtKmText.trim().replace(",", "").toIntOrNull()
}

/** The sheet for an existing item, as it is saved. */
fun formFor(item: ServiceItem): ServiceForm = ServiceForm(
    label = item.label,
    icon = item.icon,
    dueBy = when {
        item.everyKm != null && item.everyTime != null -> DueBy.BOTH
        item.everyTime != null -> DueBy.TIME
        else -> DueBy.DISTANCE
    },
    everyKm = item.everyKm ?: NEW_ITEM_KM,
    everyTime = item.everyTime ?: NEW_ITEM_TIME,
    firstOn = item.firstPending,
    firstAtKmText = item.firstAtKm?.toString().orEmpty(),
    firstByDate = item.firstByDate,
)

/** One step of the time count up ([direction] +1) or down (-1), within 1..[maxCount]. */
fun stepCount(span: TimeSpan, direction: Int): TimeSpan = span.copy(count = (span.count + direction).coerceIn(1, maxCount(span.unit)))

/** A new unit keeps the count, cut to that unit's [maxCount]. */
fun withUnit(span: TimeSpan, unit: SpanUnit): TimeSpan = TimeSpan(span.count.coerceIn(1, maxCount(unit)), unit)

/** The first-service date one unit later or earlier; never today or before. */
fun stepFirstDate(date: LocalDate, unit: SpanUnit, direction: Int, today: LocalDate): LocalDate {
    val moved = TimeSpan(1, unit).addTo(date, direction.toLong())
    return if (moved.isAfter(today)) moved else today.plusDays(1)
}

/** Turning the first service on suggests one interval from now on each side the item counts. */
fun withFirstOn(form: ServiceForm, odoKm: Int?, today: LocalDate): ServiceForm {
    val km = form.firstAtKmText.ifBlank { odoKm?.let { (it + form.everyKm).toString() }.orEmpty() }
    return form.copy(firstOn = true, firstAtKmText = km, firstByDate = form.firstByDate ?: form.everyTime.addTo(today))
}

/**
 * Why the sheet cannot be saved yet, in the rider's words, or null when it can. A missing name is not reported here:
 * the Add button simply stays off until one is typed.
 */
fun formProblem(form: ServiceForm, odoKm: Int?, today: LocalDate, counted: ServiceItem? = null): String? {
    val needsOdo = form.byKm && counted?.everyKm == null
    if (needsOdo && odoKm == null) return "We need your odometer first. Connect your bike or log a fuel-up."
    if (!form.firstOn) return null
    val firstKm = form.firstAtKm.takeIf { form.byKm }
    val firstDate = form.firstByDate.takeIf { form.byTime }
    return when {
        form.byKm && form.firstAtKmText.isNotBlank() && firstKm == null -> "Type the first service odometer in whole km."
        firstKm != null && odoKm != null && firstKm <= odoKm ->
            "The first service must be past your odometer now (${Formatters.odometer(odoKm)} km)."
        firstDate != null && !firstDate.isAfter(today) -> "The first service date must be after today."
        firstKm == null && firstDate == null -> "Set when the first service is due, or turn it off."
        else -> null
    }
}

/**
 * The item the sheet saves. A new item ([base] null) counts from [odoKm] and [nowMillis]. An edited item keeps its
 * last service, except a side it did not count before, which starts counting now.
 */
fun formItem(form: ServiceForm, base: ServiceItem?, odoKm: Int?, nowMillis: Long): ServiceItem {
    val startKm = base?.everyKm == null && form.byKm
    val startTime = base?.everyTime == null && form.byTime && base?.lastDoneMillis == null
    val item = base ?: ServiceItem(label = cleanLabel(form.label).orEmpty(), everyKm = null, lastDoneOdoKm = odoKm ?: 0, lastDoneMillis = nowMillis, icon = form.icon)
    return item.copy(
        everyKm = form.everyKm.takeIf { form.byKm },
        everyTime = form.everyTime.takeIf { form.byTime },
        firstAtKm = form.firstAtKm.takeIf { form.firstOn && form.byKm },
        firstByDate = form.firstByDate.takeIf { form.firstOn && form.byTime },
        icon = form.icon,
        lastDoneOdoKm = if (startKm && odoKm != null) odoKm else item.lastDoneOdoKm,
        lastDoneMillis = if (startTime) nowMillis else item.lastDoneMillis,
    )
}
