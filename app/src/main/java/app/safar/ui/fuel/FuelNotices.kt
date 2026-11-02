package app.safar.ui.fuel

import java.util.concurrent.atomic.AtomicReference

/**
 * Hands the fuel-up form's confirmation ("Saved · range updated") to the fuel page it returns to, so the toast
 * shows after the form closes. A message older than [MAX_AGE_MS] is dropped: the rider went somewhere else.
 */
object FuelNotices {
    private const val MAX_AGE_MS = 5_000L
    private val pending = AtomicReference<Pair<Long, String>?>(null)

    fun post(message: String, nowMillis: Long = System.currentTimeMillis()) {
        pending.set(nowMillis to message)
    }

    /** The pending message, once, if it is still fresh. */
    fun take(nowMillis: Long = System.currentTimeMillis()): String? {
        val (at, text) = pending.getAndSet(null) ?: return null
        return text.takeIf { nowMillis - at <= MAX_AGE_MS }
    }
}
