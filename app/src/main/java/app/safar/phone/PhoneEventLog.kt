package app.safar.phone

/**
 * Debug log sink for the `phone` package. Pure classes take it as a parameter, so JVM tests can record events
 * and the app can route them to `AppContainer.log` (see [ContainerLog]). Never pass message text here.
 */
fun interface PhoneEventLog {
    fun event(name: String, detail: Map<String, String>, error: Boolean)
}

/** Shorthand for a non-error event. */
fun PhoneEventLog.info(name: String, detail: Map<String, String> = emptyMap()) = event(name, detail, false)

/** Shorthand for an error event. */
fun PhoneEventLog.error(name: String, detail: Map<String, String> = emptyMap()) = event(name, detail, true)

/** Discards everything. For tests and previews. */
object NoPhoneLog : PhoneEventLog {
    override fun event(name: String, detail: Map<String, String>, error: Boolean) = Unit
}
