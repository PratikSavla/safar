package app.safar.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.util.Log
import androidx.core.content.ContextCompat
import app.safar.core.PhoneSnapshot
import app.safar.core.PhoneStatusSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Battery, charging, signal bars, internet and GPS for the status frame (docs 6.3, 12.4 R9).
 * Battery: sticky `ACTION_BATTERY_CHANGED` plus its receiver. Internet: default network with
 * `NET_CAPABILITY_VALIDATED`. GPS: `isProviderEnabled(GPS_PROVIDER)` plus `PROVIDERS_CHANGED_ACTION`.
 * Signal: [SignalWatcher]. Call [start] once on the main thread; it lives for the process.
 */
class PhoneStatusMonitor(context: Context) : PhoneStatusSource {
    private val context = context.applicationContext
    private val mutable = MutableStateFlow(PhoneSnapshot())
    override val state: StateFlow<PhoneSnapshot> = mutable.asStateFlow()

    private val signal = SignalWatcher(this.context) { bars -> mutable.update { it.copy(signalBars = bars) } }
    private val locationManager: LocationManager? = this.context.getSystemService(LocationManager::class.java)
    private var started = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = applyBattery(intent)
    }

    private val gpsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshGps()
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = setInternet(validated(caps))
        override fun onLost(network: Network) = setInternet(false)
    }

    fun start() {
        if (started) return
        started = true
        val sticky = ContextCompat.registerReceiver(
            context, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        sticky?.let(::applyBattery)
        ContextCompat.registerReceiver(
            context, gpsReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refreshGps()
        startNetwork()
        signal.start()
    }

    /** Re-reads the values that have no change broadcast we can rely on (after a permission change, say). */
    fun refresh() {
        refreshGps()
        signal.publish()
    }

    private fun applyBattery(intent: Intent) {
        val percent = batteryPercent(
            intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
        )
        val charging = isCharging(
            intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
            intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
        )
        mutable.update { it.copy(batteryPercent = percent ?: it.batteryPercent, charging = charging) }
    }

    private fun refreshGps() {
        val on = try {
            locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "No GPS provider on this phone", e)
            false
        }
        mutable.update { it.copy(gpsOn = on) }
    }

    private fun startNetwork() {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        if (cm == null) {
            Log.w(TAG, "No ConnectivityManager: internet state unknown")
            return
        }
        try {
            setInternet(cm.getNetworkCapabilities(cm.activeNetwork)?.let(::validated) == true)
            cm.registerDefaultNetworkCallback(networkCallback)
        } catch (e: SecurityException) {
            Log.w(TAG, "Network state not readable; internet assumed on", e)
        }
    }

    private fun validated(caps: NetworkCapabilities): Boolean =
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private fun setInternet(on: Boolean) = mutable.update { it.copy(hasInternet = on) }
}
