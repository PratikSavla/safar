package app.safar.phone

import android.Manifest
import android.content.Context
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log

/**
 * Caller name for the cluster (docs 5.3 step 1): contact name from `PhoneLookup` (READ_CONTACTS), else the
 * number without '+', else [UNKNOWN_CALLER]. The ringing call's call log entry (READ_CALL_LOG) is the fallback
 * number when the broadcast had none. Without a permission the step is skipped. Blocking: call off the main thread.
 */
internal class CallerLookup(context: Context) {
    private val context = context.applicationContext

    fun displayName(number: String?): String = callerDisplayName(number, number?.let(::contactName))

    private fun contactName(number: String): String? {
        if (number.isBlank() || !context.granted(Manifest.permission.READ_CONTACTS)) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return query("contact lookup") {
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }
    }

    /** Highest call log `_ID` now, or null without READ_CALL_LOG. The ringing call's entry comes after it. */
    fun callLogMark(): Long? {
        if (!context.granted(Manifest.permission.READ_CALL_LOG)) return null
        return query("call log mark") {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID), null, null, "${CallLog.Calls._ID} DESC",
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        }
    }

    /**
     * Number of the newest incoming, missed or rejected entry written after [mark]: "" when that entry has no
     * number (private caller), null when there is none yet. Telecom writes it after the call is removed.
     */
    fun incomingNumberAfter(mark: Long): String? {
        if (!context.granted(Manifest.permission.READ_CALL_LOG)) return null
        val types = listOf(CallLog.Calls.INCOMING_TYPE, CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE)
        return query("call log") {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER),
                "${CallLog.Calls._ID} > ? AND ${CallLog.Calls.TYPE} IN (${types.joinToString()})",
                arrayOf(mark.toString()),
                "${CallLog.Calls._ID} DESC",
            )?.use { c -> if (c.moveToFirst()) c.getString(0).orEmpty() else null }
        }
    }

    private fun <T : Any> query(what: String, block: () -> T?): T? = try {
        block()
    } catch (e: SecurityException) {
        Log.w(TAG, "$what: permission revoked", e)
        null
    } catch (e: SQLiteException) {
        Log.w(TAG, "$what failed", e)
        null
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "$what: provider rejected the query", e)
        null
    }
}
