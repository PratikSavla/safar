package app.safar.phone

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import app.safar.core.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wires the phone layer into [AppContainer]: navigation, alerts, phone and ride. Call once from
 * `SafarApp.onCreate` on the main thread, after the other modules. Other layers' singletons (log, outbox, bike,
 * settings, trips, parked) are read lazily when an event needs them.
 *
 * Runtime permissions can be granted after install: each activity resume re-checks them ([refresh]).
 */
object PhoneModule {
    private val installed = AtomicBoolean(false)

    /** Set by [install]; the notification listener routes through it. */
    @Volatile
    internal var router: NotificationRouter? = null
        private set

    private var status: PhoneStatusMonitor? = null
    private var calls: CallMonitor? = null
    private var ride: RideTrackerImpl? = null

    /** The Maps bridge with its debug extras (learnIcon, lastUnmatched); also `AppContainer.navigation`. */
    @Volatile
    var maps: MapsNavigationBridge? = null
        private set

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val app = context.applicationContext
        val scope = AppContainer.appScope
        val log: PhoneEventLog = ContainerLog

        val phone = PhoneStatusMonitor(app).also { it.start() }
        val alerts = AlertCenterImpl(::outboxOrNull, { AppContainer.settings.settings.value }, log)
        val icons = IconLibrary(app, log)
        scope.launch(Dispatchers.IO) { icons.load() }
        val bridge = MapsNavigationBridge(app, phone.state, scope, icons, log)
        router = NotificationRouter(bridge, WhatsAppAlerts(alerts))
        calls = CallMonitor(app, alerts, scope, log).also { it.start() }
        val tracker = RideTrackerImpl(app, scope, log).also { it.start() }
        status = phone
        maps = bridge
        ride = tracker

        AppContainer.navigation = bridge
        AppContainer.alerts = alerts
        AppContainer.phone = phone
        AppContainer.ride = tracker
        (app as? Application)?.registerActivityLifecycleCallbacks(ResumeRefresher)
    }

    /** Re-checks permissions and providers. Called on each activity resume; safe to call any time on main. */
    fun refresh() {
        status?.refresh()
        calls?.refresh()
        ride?.refresh()
    }

    private object ResumeRefresher : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) = refresh()
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
