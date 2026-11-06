package app.safar.ui.documents

import app.safar.data.BikeDocument
import app.safar.data.DocKind
import app.safar.data.DocumentFiles
import app.safar.data.ExpiryState
import app.safar.data.daysLeft
import app.safar.data.expiryState
import app.safar.data.label
import app.safar.ui.components.StampTone
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EXPIRY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** The note at the top of the papers page: friendly, not alarming. */
const val PAPERS_LOCAL_NOTE =
    "Your papers stay on this phone. Your phone’s backup doesn’t include them, so to keep a copy somewhere " +
        "safe, use Export everything in Settings."

/** One paper in the list. [stamp] warns about the expiry; null when it is far off or there is none. */
data class PaperRow(
    val id: Long,
    val title: String,
    val subtitle: String,
    val stamp: String?,
    val tone: StampTone,
    val fileName: String,
    val mimeType: String,
)

/** Papers in list order (the repository's: soonest expiry first). */
fun paperRows(docs: List<BikeDocument>, todayEpochDay: Long): List<PaperRow> = docs.map { paperRow(it, todayEpochDay) }

fun paperRow(doc: BikeDocument, today: Long): PaperRow {
    val state = expiryState(today, doc.expiryEpochDay)
    val kind = doc.kind.label().takeIf { doc.kind != DocKind.OTHER && !doc.title.equals(it, ignoreCase = true) }
    val validity = doc.expiryEpochDay?.let { validityText(state, it) }
    val type = if (DocumentFiles.isPdf(doc.mimeType)) "PDF" else "Photo"
    return PaperRow(
        id = doc.id,
        title = doc.title,
        subtitle = listOfNotNull(kind, validity, type).joinToString(" · "),
        stamp = stampText(state, today, doc.expiryEpochDay),
        tone = if (state == ExpiryState.EXPIRED) StampTone.RUST else StampTone.AMBER,
        fileName = doc.fileName,
        mimeType = doc.mimeType,
    )
}

/** "valid till 12 Mar 2027", or "expired on 3 Nov 2026". */
fun validityText(state: ExpiryState, expiryEpochDay: Long): String {
    val date = EXPIRY_DATE.format(LocalDate.ofEpochDay(expiryEpochDay))
    return if (state == ExpiryState.EXPIRED) "expired on $date" else "valid till $date"
}

/** The expiry date for the form ("12 Mar 2027"). */
fun expiryDateText(expiryEpochDay: Long): String = EXPIRY_DATE.format(LocalDate.ofEpochDay(expiryEpochDay))

private fun stampText(state: ExpiryState, today: Long, expiry: Long?): String? {
    if (expiry == null) return null
    val left = daysLeft(today, expiry)
    return when (state) {
        ExpiryState.EXPIRED -> "expired"
        ExpiryState.SOON -> when (left) {
            0L -> "today"
            1L -> "1 day left"
            else -> "$left days left"
        }
        else -> null
    }
}

/** "2.4 MB", "640 KB". */
fun sizeText(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.ENGLISH, "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> "${(bytes + 1023) / 1024} KB"
}
