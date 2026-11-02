package app.safar.phone

// Phone call state machine (docs 5.4). Pure Kotlin: no Android imports.

/** Telephony call state, as `TelephonyManager.CALL_STATE_*` and `EXTRA_STATE_*` report it. */
enum class CallPhase { IDLE, RINGING, OFFHOOK }

/** What a phone state change means for the cluster. [ENDED] sends nothing; it only clears the active call. */
enum class CallTransition { RINGING, ANSWERED, MISSED, ENDED }

/** Name shown when the caller has no number (private number) and no contact. */
const val UNKNOWN_CALLER = "Unknown"

/**
 * Docs 5.4: IDLE -> RINGING rings, RINGING -> OFFHOOK is answered, RINGING -> IDLE is missed.
 * An outgoing call (IDLE -> OFFHOOK) sends nothing; its end (OFFHOOK -> IDLE) only clears the call.
 */
fun callTransition(from: CallPhase, to: CallPhase): CallTransition? = when {
    from == CallPhase.IDLE && to == CallPhase.RINGING -> CallTransition.RINGING
    from == CallPhase.RINGING && to == CallPhase.OFFHOOK -> CallTransition.ANSWERED
    from == CallPhase.RINGING && to == CallPhase.IDLE -> CallTransition.MISSED
    from == CallPhase.OFFHOOK && to == CallPhase.IDLE -> CallTransition.ENDED
    else -> null
}

/**
 * Remembers the last phase so repeated broadcasts of the same state do nothing. Not thread-safe.
 * Android reports a call waiting during another call as RINGING too (OFFHOOK -> RINGING). That ring was never
 * sent to the cluster, so its end is not an answer: back to OFFHOOK sends nothing (the first call goes on),
 * and IDLE only ends the call.
 */
class CallTracker {
    var phase: CallPhase = CallPhase.IDLE
        private set
    private var waiting = false

    fun next(to: CallPhase): CallTransition? {
        val from = phase
        phase = to
        if (to == CallPhase.RINGING && from != CallPhase.RINGING) waiting = from == CallPhase.OFFHOOK
        if (from != CallPhase.RINGING || to == CallPhase.RINGING || !waiting) return callTransition(from, to)
        waiting = false
        return if (to == CallPhase.IDLE) CallTransition.ENDED else null
    }
}

/** `TelephonyManager.CALL_STATE_*` (0 idle, 1 ringing, 2 off-hook) to a phase. Unknown values are idle. */
fun callPhaseOf(state: Int): CallPhase = when (state) {
    1 -> CallPhase.RINGING
    2 -> CallPhase.OFFHOOK
    else -> CallPhase.IDLE
}

/** `TelephonyManager.EXTRA_STATE_*` text ("IDLE", "RINGING", "OFFHOOK") to a phase, or null. */
fun callPhaseOf(extra: String?): CallPhase? = when (extra) {
    "IDLE" -> CallPhase.IDLE
    "RINGING" -> CallPhase.RINGING
    "OFFHOOK" -> CallPhase.OFFHOOK
    else -> null
}

/**
 * Name for the cluster (docs 5.3 step 1): the contact name, else the number without '+', else [UNKNOWN_CALLER].
 * The protocol's sanitizeName() does the ASCII clean-up when the frame is built.
 */
fun callerDisplayName(number: String?, contactName: String?): String {
    contactName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return number?.replace("+", "")?.trim()?.takeIf { it.isNotEmpty() } ?: UNKNOWN_CALLER
}
