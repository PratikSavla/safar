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
 * number without '+', else [UNKNOWN_CALLER]. The latest call log entry (READ_CALL_LOG) is the fallback number
 * when the broadcast had none. Without a permission the step is skipped. Blocking: call off the main thread.
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

    /** Number of the newest call log entry at or after [sinceMillis], or null. */
    fun latestCallLogNumber(sinceMillis: Long): String? {
        if (!context.granted(Manifest.permission.READ_CALL_LOG)) return null
        return query("call log") {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER),
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(sinceMillis.toString()),
                "${CallLog.Calls.DATE} DESC",
            )?.use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null }
        }
    }

    private fun query(what: String, block: () -> String?): String? = try {
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
