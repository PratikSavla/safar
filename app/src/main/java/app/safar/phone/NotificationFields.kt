package app.safar.phone

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.os.BundleCompat
import app.safar.protocol.GOOGLE_MAPS_PACKAGE

// Copies the fields the phone layer needs out of a posted notification (docs 11.3 step 4, 5.7).

/** `Notification.EXTRA_SHORT_CRITICAL_TEXT` (API 36), read by key so older SDK levels work too. */
private const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"
/** `Notification.EXTRA_CALL_TYPE` (API 31). */
private const val EXTRA_CALL_TYPE = "android.callType"
/** `Notification.EXTRA_MESSAGES` (API 24). */
private const val EXTRA_MESSAGES = "android.messages"
private const val MESSAGING_STYLE_SUFFIX = "\$MessagingStyle"

/** Side used when the large icon has no intrinsic size. Larger icons are drawn at most this big. */
private const val ICON_MAX_SIDE = 128

internal fun isMapsNavigation(sbn: StatusBarNotification): Boolean =
    sbn.packageName == GOOGLE_MAPS_PACKAGE && sbn.notification?.category == CATEGORY_NAVIGATION

internal fun isWhatsApp(sbn: StatusBarNotification): Boolean = sbn.packageName in WHATSAPP_PACKAGES

private fun Bundle.text(key: String): String? = getCharSequence(key)?.toString()
private fun Bundle.intOrNull(key: String): Int? = if (containsKey(key)) getInt(key) else null

internal fun mapsFields(n: Notification): MapsFields {
    val e = n.extras ?: Bundle.EMPTY
    return MapsFields(
        title = e.text(Notification.EXTRA_TITLE),
        shortCriticalText = e.text(EXTRA_SHORT_CRITICAL_TEXT),
        subText = e.text(Notification.EXTRA_SUB_TEXT),
        text = e.text(Notification.EXTRA_TEXT),
        progress = e.intOrNull(Notification.EXTRA_PROGRESS),
        progressMax = e.intOrNull(Notification.EXTRA_PROGRESS_MAX),
    )
}

/** The text is only copied for non-message notifications, where it holds the call words; never chat text. */
internal fun waNotification(sbn: StatusBarNotification): WaNotification {
    val n = sbn.notification
    val e = n.extras ?: Bundle.EMPTY
    val isMessage = n.category == CATEGORY_MESSAGE
    val messages = BundleCompat.getParcelableArray(e, EXTRA_MESSAGES, Bundle::class.java)
    return WaNotification(
        key = sbn.key,
        category = n.category,
        title = e.text(Notification.EXTRA_TITLE),
        text = if (isMessage) null else e.text(Notification.EXTRA_TEXT),
        groupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0,
        chronometer = e.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER),
        fullScreenIntent = n.fullScreenIntent != null,
        callType = e.intOrNull(EXTRA_CALL_TYPE),
        messagingStyle = e.getString(Notification.EXTRA_TEMPLATE)?.endsWith(MESSAGING_STYLE_SUFFIX) == true,
        messageCount = messages?.size ?: 0,
    )
}

/** The large icon drawn to a bitmap and its dHash. */
internal class IconCapture(val hash: Long, val bitmap: Bitmap)

/** Draws `android.largeIcon` and hashes it (docs 11.4). Null when there is no icon or it cannot be drawn. */
internal fun captureLargeIcon(context: Context, n: Notification): IconCapture? {
    val icon = n.getLargeIcon() ?: return null
    return try {
        val drawable = icon.loadDrawable(context) ?: return null
        val w = drawable.intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(ICON_MAX_SIDE) ?: ICON_MAX_SIDE
        val h = drawable.intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(ICON_MAX_SIDE) ?: ICON_MAX_SIDE
        val bitmap = createBitmap(w, h)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(Canvas(bitmap))
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        IconCapture(iconHash(pixels, w, h), bitmap)
    } catch (e: RuntimeException) {
        // Resources.NotFoundException, or a hardware bitmap that a software canvas cannot read.
        Log.w(TAG, "Maps large icon could not be drawn", e)
        null
    }
}
