package app.safar.phone

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import app.safar.core.AlertSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How long a ringing event waits for the PHONE_STATE broadcast that carries the number. */
private const val NUMBER_WAIT_MS = 600L

/**
 * Phone calls -> [AlertCenterImpl] (docs 5.4, 5.7). Call state comes from `TelephonyCallback.CallStateListener`
 * on API 31+ and from the `PHONE_STATE` broadcast below. The broadcast is registered on every API level for
 * the incoming number (`EXTRA_INCOMING_NUMBER`, needs READ_CALL_LOG), taken from RINGING broadcasts only
 * ([IncomingNumber]). Events run one at a time in order, so a ringing frame that waits for the caller name
 * is sent before the answered or missed frame.
 * Needs READ_PHONE_STATE; without it nothing is registered until [refresh] finds it granted.
 */
internal class CallMonitor(
    context: Context,
    private val alerts: AlertCenterImpl,
    private val scope: CoroutineScope,
    private val log: PhoneEventLog,
) {
    private val context = context.applicationContext
    private val lookup = CallerLookup(this.context)
    private val telephony: TelephonyManager? = this.context.getSystemService(TelephonyManager::class.java)
    private val events = Channel<CallPhase>(Channel.UNLIMITED)
    private val number = IncomingNumber()
    private val tracker = CallTracker()
    private var ringingName: String? = null
    private var ringStartedAt = 0L
    private var receiverRegistered = false
    private var callbackRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onPhoneState(intent)
    }

    fun start() {
        scope.launch(Dispatchers.Default) { for (phase in events) handle(phase) }
        refresh()
    }

    /** Registers the listeners once READ_PHONE_STATE is granted. Safe to call often (main thread). */
    fun refresh() {
        if (!context.granted(Manifest.permission.READ_PHONE_STATE)) return
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                context, receiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
            log.info("calls.listening", mapOf("api" to Build.VERSION.SDK_INT.toString()))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !callbackRegistered) {
            callbackRegistered = registerCallState()
        }
    }

    @Suppress("DEPRECATION") // EXTRA_INCOMING_NUMBER is the only source of the ringing number.
    private fun onPhoneState(intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val phase = callPhaseOf(intent.getStringExtra(TelephonyManager.EXTRA_STATE))
        number.onBroadcast(phase, intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) phase?.let { events.trySend(it) }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerCallState(): Boolean {
        val tm = telephony ?: return false
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                events.trySend(callPhaseOf(state))
            }
        }
        return try {
            tm.registerTelephonyCallback(ContextCompat.getMainExecutor(context), callback)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Call state listener refused", e)
            false
        }
    }

    private suspend fun handle(phase: CallPhase) {
        when (tracker.next(phase)) {
            CallTransition.RINGING -> {
                ringStartedAt = System.currentTimeMillis()
                val name = ringingCallerName()
                ringingName = name
                alerts.callRinging(name, AlertSource.PHONE)
            }
            CallTransition.ANSWERED -> alerts.callAnswered(ringingName ?: UNKNOWN_CALLER, AlertSource.PHONE)
            CallTransition.MISSED -> alerts.callMissed(missedCallerName(), AlertSource.PHONE)
            CallTransition.ENDED -> alerts.callEnded(AlertSource.PHONE)
            null -> Unit
        }
        if (phase == CallPhase.IDLE) {
            number.clear()
            ringingName = null
        }
    }

    private suspend fun ringingCallerName(): String {
        val incoming = number.await(NUMBER_WAIT_MS)
        return withContext(Dispatchers.IO) { lookup.displayName(incoming) }
    }

    private suspend fun missedCallerName(): String {
        ringingName?.takeIf { it != UNKNOWN_CALLER }?.let { return it }
        return withContext(Dispatchers.IO) {
            lookup.displayName(number.current ?: lookup.latestCallLogNumber(ringStartedAt))
        }
    }
}
