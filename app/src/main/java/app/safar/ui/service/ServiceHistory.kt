package app.safar.ui.service

import app.safar.data.ServiceIcon
import app.safar.data.ServiceItem
import app.safar.data.ServiceRecord
import app.safar.ui.format.Formatters
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Pure logic of the service history: the list, and logging a service done earlier. Unit-tested.

private val RECORD_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private const val NOTE_MAX = 80

/** One line of the history: "Engine oil · 12 Mar 2026", then "11,820 km · ₹450". */
data class HistoryRowUi(
    val id: Long,
    val label: String,
    val icon: ServiceIcon,
    val date: String,
    /** Odometer and cost, when known; empty otherwise. */
    val detail: String,
    val note: String?,
)

/** History rows, newest first (the repository's order). */
fun historyRows(records: List<ServiceRecord>, currency: String, zone: ZoneId): List<HistoryRowUi> = records.map { r ->
    val date = RECORD_DATE.format(Instant.ofEpochMilli(r.atMillis).atZone(zone))
    val detail = listOfNotNull(
        r.odoKm?.let { "${Formatters.odometer(it)} km" },
        r.cost?.let { Formatters.money(it, currency) },
    ).joinToString(" · ")
    HistoryRowUi(r.id, r.label, r.icon, date, detail, r.note)
}

/** What the "Log a past service" sheet holds. [day] is days since 1970-01-01. */
data class PastForm(
    val itemId: Long? = null,
    val day: Long,
    val odo: String = "",
    val cost: String = "",
    val note: String = "",
)

/** Why the sheet cannot save yet, or null. Items counted by distance need the odometer of that service. */
fun pastProblem(form: PastForm, items: List<ServiceItem>, today: LocalDate): String? {
    val item = items.firstOrNull { it.id == form.itemId } ?: return "Choose what was done."
    if (form.day > today.toEpochDay()) return "Pick a day up to today."
    val odo = form.odo.trim()
    if (odo.isNotEmpty() && (odo.toIntOrNull() ?: 0) <= 0) return "The odometer is whole km, as on your bike."
    if (odo.isEmpty() && item.everyKm != null) return "Enter the odometer at that service."
    val cost = form.cost.trim()
    if (cost.isNotEmpty() && (cost.replace(',', '.').toDoubleOrNull() ?: -1.0) < 0) return "The cost is a number, or leave it empty."
    return null
}

/** The record to save, or null while [pastProblem] has something to say. Saved at noon on the chosen day. */
fun pastRecord(form: PastForm, items: List<ServiceItem>, today: LocalDate, zone: ZoneId): ServiceRecord? {
    if (pastProblem(form, items, today) != null) return null
    val item = items.first { it.id == form.itemId }
    val at = LocalDate.ofEpochDay(form.day).atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
    return ServiceRecord(
        itemId = item.id,
        label = item.label,
        icon = item.icon,
        atMillis = at,
        odoKm = form.odo.trim().toIntOrNull(),
        cost = form.cost.trim().replace(',', '.').toDoubleOrNull(),
        note = form.note.trim().take(NOTE_MAX).ifEmpty { null },
    )
}

/**
 * The item counting from [record] when it is newer than the item's last service, else null (the history
 * changes, the reminder does not). A pending first service ends, as when the item is marked done.
 */
fun itemAfterPast(item: ServiceItem, record: ServiceRecord): ServiceItem? {
    val last = item.lastDoneMillis
    val odo = record.odoKm
    if (last != null && record.atMillis <= last) return null
    if (odo != null && odo < item.lastDoneOdoKm) return null
    if (odo == null && item.everyKm != null) return null
    return item.copy(lastDoneMillis = record.atMillis, lastDoneOdoKm = odo ?: item.lastDoneOdoKm, firstAtKm = null, firstByDate = null)
}

/** The toast after logging: says whether the reminder now counts from it. */
fun pastLoggedText(record: ServiceRecord, moved: Boolean, zone: ZoneId): String {
    val date = RECORD_DATE.format(Instant.ofEpochMilli(record.atMillis).atZone(zone))
    return if (moved) "${record.label} on $date logged. Next one counted from it." else "${record.label} on $date added to the history"
}
