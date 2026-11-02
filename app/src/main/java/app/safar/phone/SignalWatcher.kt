package app.safar.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * Cellular signal bars for the status frame. Listens with `TelephonyCallback.SignalStrengthsListener` on
 * API 31+ and `PhoneStateListener` below; neither needs a runtime permission. Airplane mode and SIM state are
 * re-read on each update. Create and [start] on the main thread (PhoneStateListener needs a Looper).
 */
internal class SignalWatcher(private val context: Context, private val onBars: (Int) -> Unit) {
    private val telephony: TelephonyManager? = context.getSystemService(TelephonyManager::class.java)
    @Volatile private var level: Int? = null
    /** A TelephonyCallback on API 31+ (typed Any so this class loads below 31). */
    private var callback31: Any? = null
    /** A PhoneStateListener below API 31 (typed Any to keep the deprecated type in suppressed code only). */
    private var legacy: Any? = null

    private val airplaneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = publish()
    }

    fun start() {
        val tm = telephony
        if (tm == null) {
            Log.w(TAG, "No TelephonyManager: signal stays at 0 bars")
            publish()
            return
        }
        ContextCompat.registerReceiver(
            context, airplaneReceiver, IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) callback31 = register31(tm) else legacy = registerLegacy(tm)
        } catch (e: SecurityException) {
            Log.w(TAG, "Signal strength listener refused; signal stays at 0 bars", e)
        }
        publish()
    }

    fun stop() {
        val tm = telephony ?: return
        try {
            context.unregisterReceiver(airplaneReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Airplane receiver was not registered", e)
        }
        val cb = callback31
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && cb != null) unregister31(tm, cb)
        legacy?.let { unregisterLegacy(tm, it) }
        callback31 = null
        legacy = null
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun unregister31(tm: TelephonyManager, cb: Any) = tm.unregisterTelephonyCallback(cb as TelephonyCallback)

    @RequiresApi(Build.VERSION_CODES.S)
    private fun register31(tm: TelephonyManager): TelephonyCallback {
        val cb = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) = onLevel(signalStrength.level)
        }
        tm.registerTelephonyCallback(ContextCompat.getMainExecutor(context), cb)
        return cb
    }

    @Suppress("DEPRECATION") // PhoneStateListener is the only option below API 31.
    private fun registerLegacy(tm: TelephonyManager): Any {
        val listener = object : android.telephony.PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) = onLevel(signalStrength.level)
        }
        tm.listen(listener, android.telephony.PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        return listener
    }

    @Suppress("DEPRECATION")
    private fun unregisterLegacy(tm: TelephonyManager, listener: Any) =
        tm.listen(listener as android.telephony.PhoneStateListener, android.telephony.PhoneStateListener.LISTEN_NONE)

    private fun onLevel(value: Int) {
        level = value
        publish()
    }

    fun publish() {
        val airplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        val simReady = telephony?.simState == TelephonyManager.SIM_STATE_READY
        onBars(signalBars(level, airplane, simReady))
    }
}
