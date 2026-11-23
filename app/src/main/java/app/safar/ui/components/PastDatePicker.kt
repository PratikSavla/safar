package app.safar.ui.components

import android.app.DatePickerDialog
import android.content.Context
import java.time.LocalDate
import java.time.ZoneId

/**
 * Android's date picker for something that already happened (a fill, a service): starts at [day] and stops at
 * [today], both days since 1970-01-01. A [today] of 0 (not known yet) leaves the end open.
 */
fun showPastDatePicker(context: Context, day: Long, today: Long, onPicked: (Long) -> Unit) {
    val start = LocalDate.ofEpochDay(day)
    val dialog = DatePickerDialog(
        context,
        { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d).toEpochDay()) },
        start.year, start.monthValue - 1, start.dayOfMonth,
    )
    if (today > 0) {
        // The last millisecond of today in this zone, so today itself stays selectable.
        dialog.datePicker.maxDate = LocalDate.ofEpochDay(today + 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
    }
    dialog.show()
}
