package app.safar.ui.shell

import android.util.Log
import app.safar.core.AlertCenter
import app.safar.core.AppContainer
import app.safar.core.BikeController
import app.safar.core.DebugLog
import app.safar.core.NavigationBridge
import app.safar.core.PhoneStatusSource
import app.safar.core.RideTracker
import java.util.concurrent.ConcurrentHashMap

/**
 * Screens read the BLE and phone layers through here. Those layers assign their parts of [AppContainer] in
 * their own `install(...)`; until one has (a build without it, or a preview process), an idle stand-in
 * from [IdleServices] answers and a warning is logged once, instead of an `UninitializedPropertyAccessException`.
 * The data layer is always installed by `DataModule`, so its repositories are read directly.
 */
object ShellDeps {
    private const val TAG = "SafarShell"
    private val warned: MutableSet<String> = ConcurrentHashMap.newKeySet()

    val bike: BikeController get() = orIdle("bike", { AppContainer.bike }) { IdleServices.bike }
    val navigation: NavigationBridge get() = orIdle("navigation", { AppContainer.navigation }) { IdleServices.navigation }
    val alerts: AlertCenter get() = orIdle("alerts", { AppContainer.alerts }) { IdleServices.alerts }
    val phone: PhoneStatusSource get() = orIdle("phone", { AppContainer.phone }) { IdleServices.phone }
    val ride: RideTracker get() = orIdle("ride", { AppContainer.ride }) { IdleServices.ride }
    val log: DebugLog get() = orIdle("log", { AppContainer.log }) { IdleServices.log }

    private inline fun <T> orIdle(name: String, read: () -> T, idle: () -> T): T = try {
        read()
    } catch (e: UninitializedPropertyAccessException) {
        if (warned.add(name)) Log.w(TAG, "AppContainer.$name is not installed yet; screens use an idle stand-in", e)
        idle()
    }
}
