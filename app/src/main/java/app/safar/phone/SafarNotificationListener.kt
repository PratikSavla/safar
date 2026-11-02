package app.safar.phone

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Reads Google Maps navigation and WhatsApp notifications (docs 11.3, 5.7). The rider grants access in
 * system settings ([PhoneAccess.notificationAccessIntent]). Work is routed to [PhoneModule]'s handlers; a
 * failure in one notification is logged and never takes the listener down.
 */
class SafarNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        ContainerLog.info("notifications.connected")
        val router = PhoneModule.router ?: return warnNotInstalled()
        val active = try {
            activeNotifications.orEmpty()
        } catch (e: SecurityException) {
            Log.w(TAG, "Active notifications not readable right after connect", e)
            emptyArray()
        }
        guarded("connect") { router.connected(active) }
    }

    override fun onListenerDisconnected() {
        ContainerLog.info("notifications.disconnected")
        val router = PhoneModule.router ?: return warnNotInstalled()
        guarded("disconnect") { router.disconnected() }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        val router = PhoneModule.router ?: return warnNotInstalled()
        guarded("posted ${n.packageName}") { router.posted(n) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        val router = PhoneModule.router ?: return warnNotInstalled()
        guarded("removed ${n.packageName}") { router.removed(n) }
    }

    private fun warnNotInstalled() {
        Log.w(TAG, "Notification ignored: PhoneModule.install was not called")
    }

    private inline fun guarded(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: RuntimeException) {
            Log.e(TAG, "Notification listener: $what failed", e)
            ContainerLog.error("notifications.failed", mapOf("what" to what, "error" to e.javaClass.simpleName))
        }
    }
}

/**
 * Sends each notification to the Maps bridge, the WhatsApp alerts, or (the phone app's missed-call notification
 * being read or dismissed) [phoneMissedRead]. [dialer] is the default phone app's package.
 */
internal class NotificationRouter(
    private val maps: MapsNavigationBridge,
    private val whatsApp: WhatsAppAlerts,
    private val dialer: () -> String? = { null },
    private val phoneMissedRead: () -> Unit = {},
) {
    /** Keys of the phone app's missed-call notifications still posted. Callbacks arrive on the main thread. */
    private val phoneMissedKeys = mutableSetOf<String>()

    /** Attach to a Maps route already running, and record the WhatsApp notifications already posted. */
    fun connected(active: Array<out StatusBarNotification>) {
        val phone = dialer()
        phoneMissedKeys.clear()
        active.filter { isPhoneMissedCall(it, phone) }.mapTo(phoneMissedKeys) { it.key }
        val route = active.filter(::isMapsNavigation).maxByOrNull { it.postTime }
        // No Maps route now: one that ended while we were unbound must not stay active.
        if (route != null) maps.onPosted(route.notification) else maps.onLost()
        whatsApp.seed(active.filter(::isWhatsApp).map(::waNotification))
    }

    /** Unbound: no removal callbacks arrive until the listener is back, so stop trusting the last turn. */
    fun disconnected() = maps.onLost()

    fun posted(sbn: StatusBarNotification) {
        when {
            isMapsNavigation(sbn) -> maps.onPosted(sbn.notification)
            isWhatsApp(sbn) -> whatsApp.posted(waNotification(sbn))
            isPhoneMissedCall(sbn, dialer()) -> phoneMissedKeys += sbn.key
        }
    }

    fun removed(sbn: StatusBarNotification) {
        when {
            isMapsNavigation(sbn) -> maps.onRemoved()
            isWhatsApp(sbn) -> whatsApp.removed(sbn.key)
            isPhoneMissedCall(sbn, dialer()) -> {
                phoneMissedKeys -= sbn.key
                if (phoneMissedKeys.isEmpty()) phoneMissedRead()
            }
        }
    }
}
