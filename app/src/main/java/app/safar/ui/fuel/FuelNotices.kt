package app.safar.ui.fuel

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Hands the fuel-up form's confirmation ("Saved · range updated") to the fuel page it returns to, so the toast
 * shows after the form closes. A message older than [MAX_AGE_MS] is dropped: the rider went somewhere else.
 * Only the fuel page shows it, so a form opened from elsewhere (Home's "Log fuel") goes to the fuel page after a
 * new save, as the prototype does (fuel-form.js).
 */
object FuelNotices {
    private const val MAX_AGE_MS = 5_000L
    private val pending = AtomicReference<Pair<Long, String>?>(null)
    private val openedFromFuel = AtomicBoolean(false)

    /** Called by the fuel page right before it opens the form. */
    fun markOpenedFromFuel() = openedFromFuel.set(true)

    /** True when the form being opened came from the fuel page; read once, when the form first composes. */
    fun takeOpenedFromFuel(): Boolean = openedFromFuel.getAndSet(false)

    fun post(message: String, nowMillis: Long = System.currentTimeMillis()) {
        pending.set(nowMillis to message)
    }

    /** The pending message, once, if it is still fresh. */
    fun take(nowMillis: Long = System.currentTimeMillis()): String? {
        val (at, text) = pending.getAndSet(null) ?: return null
        return text.takeIf { nowMillis - at <= MAX_AGE_MS }
    }
}
