package app.safar.phone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The ringing caller's number, from `PHONE_STATE` broadcasts (`EXTRA_INCOMING_NUMBER`). Only RINGING broadcasts
 * are taken: Telecom also puts the ended call's number on the IDLE broadcast and the dialled number on OFFHOOK,
 * and on API 31+ those arrive after the call state callback has already cleared the number. Pure Kotlin;
 * thread-safe (broadcasts on the main thread, reads on the call event coroutine).
 */
class IncomingNumber {
    private val number = MutableStateFlow<String?>(null)

    /** The number of the ringing call, if its RINGING broadcast has arrived. */
    val current: String? get() = number.value

    /** A `PHONE_STATE` broadcast in [phase] carrying [incoming] (null when the extra was absent). */
    fun onBroadcast(phase: CallPhase?, incoming: String?) {
        if (phase != CallPhase.RINGING) return
        incoming?.takeIf { it.isNotBlank() }?.let { number.value = it }
    }

    /** The call is over (IDLE handled): the next ring waits for its own broadcast. */
    fun clear() {
        number.value = null
    }

    /** The number, waiting up to [timeoutMs] for the RINGING broadcast. Null when none came. */
    suspend fun await(timeoutMs: Long): String? =
        number.value ?: withTimeoutOrNull(timeoutMs) { number.filterNotNull().first() }
}
