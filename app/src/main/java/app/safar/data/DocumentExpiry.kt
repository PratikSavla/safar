package app.safar.data

/** Days before expiry when the first reminder goes out, and when the papers start to warn. */
const val EXPIRY_SOON_DAYS = 30

/** Days before expiry of the second reminder. */
const val EXPIRY_CLOSE_DAYS = 7

/** Reminder stages, in order. [BikeDocument.remindedStage] holds the last one sent. */
object ReminderStage {
    const val NONE = 0
    const val SOON = 1
    const val CLOSE = 2
    const val DUE = 3
}

/** Where a paper stands against its expiry date. */
enum class ExpiryState { NONE, OK, SOON, EXPIRED }

/** Days from [todayEpochDay] to [expiryEpochDay]: 0 on the day, negative once it has passed. */
fun daysLeft(todayEpochDay: Long, expiryEpochDay: Long): Long = expiryEpochDay - todayEpochDay

/**
 * The reminder a paper is due for today: [ReminderStage.SOON] within [EXPIRY_SOON_DAYS] days,
 * [ReminderStage.CLOSE] within [EXPIRY_CLOSE_DAYS], [ReminderStage.DUE] on the day and after.
 */
fun reminderStage(todayEpochDay: Long, expiryEpochDay: Long?): Int {
    if (expiryEpochDay == null) return ReminderStage.NONE
    val left = daysLeft(todayEpochDay, expiryEpochDay)
    return when {
        left <= 0 -> ReminderStage.DUE
        left <= EXPIRY_CLOSE_DAYS -> ReminderStage.CLOSE
        left <= EXPIRY_SOON_DAYS -> ReminderStage.SOON
        else -> ReminderStage.NONE
    }
}

/**
 * The reminder to send now for [doc], or null. Each stage goes out once: a paper that skipped a stage (the
 * phone was off) gets only the latest one.
 */
fun reminderToSend(doc: BikeDocument, todayEpochDay: Long): Int? {
    val stage = reminderStage(todayEpochDay, doc.expiryEpochDay)
    return stage.takeIf { it != ReminderStage.NONE && it > doc.remindedStage }
}

fun expiryState(todayEpochDay: Long, expiryEpochDay: Long?): ExpiryState {
    if (expiryEpochDay == null) return ExpiryState.NONE
    val left = daysLeft(todayEpochDay, expiryEpochDay)
    return when {
        left < 0 -> ExpiryState.EXPIRED
        left <= EXPIRY_SOON_DAYS -> ExpiryState.SOON
        else -> ExpiryState.OK
    }
}

/** The paper that needs attention first: the earliest expiry within [EXPIRY_SOON_DAYS] days, or expired. */
fun mostUrgent(docs: List<BikeDocument>, todayEpochDay: Long): BikeDocument? =
    docs.filter { expiryState(todayEpochDay, it.expiryEpochDay).let { s -> s == ExpiryState.SOON || s == ExpiryState.EXPIRED } }
        .minByOrNull { it.expiryEpochDay ?: Long.MAX_VALUE }

/** Short name of a kind, for titles and hints ("PUC in 6 days"). */
fun DocKind.label(): String = when (this) {
    DocKind.RC -> "RC"
    DocKind.INSURANCE -> "Insurance"
    DocKind.PUC -> "PUC"
    DocKind.LICENCE -> "Licence"
    DocKind.SERVICE_BILL -> "Service bill"
    DocKind.OTHER -> "Other"
}

/** Kinds that usually carry an expiry date: the form suggests one. */
fun DocKind.usuallyExpires(): Boolean = this == DocKind.INSURANCE || this == DocKind.PUC || this == DocKind.LICENCE

/** "expires today", "expires in 6 days", "expired 3 days ago". Null without an expiry date. */
fun expiryPhrase(todayEpochDay: Long, expiryEpochDay: Long?): String? {
    if (expiryEpochDay == null) return null
    val left = daysLeft(todayEpochDay, expiryEpochDay)
    return when {
        left == 0L -> "expires today"
        left == 1L -> "expires tomorrow"
        left > 1 -> "expires in $left days"
        left == -1L -> "expired yesterday"
        else -> "expired ${-left} days ago"
    }
}
