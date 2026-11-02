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

/** Sends each notification to the Maps bridge or the WhatsApp alerts. */
internal class NotificationRouter(private val maps: MapsNavigationBridge, private val whatsApp: WhatsAppAlerts) {
    /** Attach to a route that was already running in Google Maps when the listener connected. */
    fun connected(active: Array<out StatusBarNotification>) {
        active.filter(::isMapsNavigation).maxByOrNull { it.postTime }?.let { maps.onPosted(it.notification) }
    }

    fun posted(sbn: StatusBarNotification) {
        when {
            isMapsNavigation(sbn) -> maps.onPosted(sbn.notification)
            isWhatsApp(sbn) -> whatsApp.posted(waNotification(sbn))
        }
    }

    fun removed(sbn: StatusBarNotification) {
        when {
            isMapsNavigation(sbn) -> maps.onRemoved()
            isWhatsApp(sbn) -> whatsApp.removed(sbn.key)
        }
    }
}
