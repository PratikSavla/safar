package app.safar.ui.service

import app.safar.data.ServiceItem
import app.safar.data.ServiceRecord
import app.safar.ui.fuel.jsNumber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Editing a line of the service history. Pure, unit-tested.

/** The sheet's fields for an existing [record]. */
fun formForRecord(record: ServiceRecord, zone: ZoneId): PastForm = PastForm(
    itemId = record.itemId,
    day = localDayOf(record.atMillis, zone),
    odo = record.odoKm?.toString().orEmpty(),
    cost = record.cost?.let(::jsNumber).orEmpty(),
    note = record.note.orEmpty(),
)

/**
 * [original] changed as the sheet says, or null while it has a problem. An unchanged day keeps the saved time,
 * so editing a note does not move the service within its day.
 */
fun editedRecord(form: PastForm, items: List<ServiceItem>, today: LocalDate, zone: ZoneId, original: ServiceRecord): ServiceRecord? {
    val r = pastRecord(form, items, today, zone) ?: return null
    val sameDay = form.day == localDayOf(original.atMillis, zone)
    return r.copy(id = original.id, atMillis = if (sameDay) original.atMillis else r.atMillis)
}

/**
 * The items whose last service moves because [original] became [edited]. An item that counted from [original]
 * now counts from its newest record (the edited one included); the item [edited] belongs to moves forward when
 * it is newer than that item's last service ([itemAfterPast]). Items that do not change are left out.
 */
fun itemsAfterEdit(items: List<ServiceItem>, records: List<ServiceRecord>, original: ServiceRecord, edited: ServiceRecord): List<ServiceItem> {
    val after = records.map { if (it.id == edited.id) edited else it }
    return items.mapNotNull { item ->
        val countedFromOriginal = original.itemId == item.id && original.atMillis == item.lastDoneMillis
        when {
            countedFromOriginal -> recountFromNewest(item, after)
            edited.itemId == item.id -> itemAfterPast(item, edited)
            else -> null
        }
    }
}

/** [item] counting from its newest usable record; null when none is left or nothing changes. */
private fun recountFromNewest(item: ServiceItem, records: List<ServiceRecord>): ServiceItem? {
    val newest = records
        .filter { it.itemId == item.id && (it.odoKm != null || item.everyKm == null) }
        .maxByOrNull { it.atMillis } ?: return null
    val moved = item.copy(
        lastDoneMillis = newest.atMillis, lastDoneOdoKm = newest.odoKm ?: item.lastDoneOdoKm, firstAtKm = null, firstByDate = null,
    )
    return moved.takeIf { it != item }
}

private fun localDayOf(atMillis: Long, zone: ZoneId): Long = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate().toEpochDay()
