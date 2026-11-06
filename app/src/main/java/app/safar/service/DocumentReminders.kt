package app.safar.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.safar.MainActivity
import app.safar.R
import app.safar.core.AppContainer
import app.safar.data.BikeDocument
import app.safar.data.daysLeft
import app.safar.data.reminderToSend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Expiry reminders for papers (PUC, insurance): 30 and 7 days before, and on the day. A daily
 * [DocumentExpiryWorker] sends each one once ([reminderToSend]); `Settings.documentReminders` switches them off.
 */
object DocumentReminders {
    private const val TAG = "DocumentReminders"
    private const val CHANNEL_ID = "paper_expiry"
    private const val WORK_NAME = "paper-expiry-check"
    private const val START_WORK_NAME = "paper-expiry-check-now"
    private const val NOTIFICATION_BASE = 3000
    private const val REQUEST_OPEN = 5
    private val DATE = DateTimeFormatter.ofPattern("d MMM")

    /**
     * Starts the daily check (kept across restarts; calling again changes nothing) and runs one check now, so
     * a reminder due today does not wait for the next daily slot after an install or a phone restart.
     */
    fun schedule(context: Context) {
        val work = WorkManager.getInstance(context)
        val daily = PeriodicWorkRequestBuilder<DocumentExpiryWorker>(1, TimeUnit.DAYS).build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, daily)
        work.enqueueUniqueWork(START_WORK_NAME, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<DocumentExpiryWorker>().build())
    }

    /** Removes every reminder on screen ("Delete everything"). */
    fun cancelAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.activeNotifications.filter { it.notification.channelId == CHANNEL_ID }.forEach { manager.cancel(it.id) }
    }

    /** Sends what is due today and records it. Returns how many reminders went out. */
    internal suspend fun check(context: Context, todayEpochDay: Long): Int {
        val repo = AppContainer.documents
        val on = AppContainer.settings.settings.value.documentReminders
        var sent = 0
        for (doc in repo.documents.first()) {
            val stage = reminderToSend(doc, todayEpochDay) ?: continue
            // Recorded even when switched off, so turning reminders on later does not replay old ones.
            if (on && post(context, doc, todayEpochDay)) sent++
            repo.setRemindedStage(doc.id, stage)
        }
        return sent
    }

    private fun post(context: Context, doc: BikeDocument, today: Long): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Paper reminder not shown: notifications are off")
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Paper reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A heads-up before your PUC, insurance or other papers run out."
            },
        )
        val expiry = doc.expiryEpochDay ?: return false
        val (title, text) = reminderText(doc.title, today, expiry)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_safar)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPapers(context))
        return try {
            manager.notify(NOTIFICATION_BASE + (doc.id % 100_000).toInt(), builder.build())
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Posting the reminder for paper ${doc.id} was refused", e)
            false
        }
    }

    private fun reminderText(name: String, today: Long, expiry: Long): Pair<String, String> {
        val left = daysLeft(today, expiry)
        val date = DATE.format(LocalDate.ofEpochDay(expiry))
        return when {
            left > 1 -> "$name expires in $left days" to "It runs out on $date. A good time to renew it."
            left == 1L -> "$name expires tomorrow" to "It runs out on $date. Renew it before your next ride."
            left == 0L -> "$name expires today" to "Renew it before you ride."
            else -> "$name has expired" to "It ran out on $date. Renew it and add the new copy in Safar."
        }
    }

    private fun openPapers(context: Context): PendingIntent {
        val intent = MainActivity.openPapersIntent(context)
        return PendingIntent.getActivity(context, REQUEST_OPEN, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/** The daily paper check. Failures are logged and retried by WorkManager. */
class DocumentExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val sent = DocumentReminders.check(applicationContext, LocalDate.now().toEpochDay())
        if (sent > 0) Log.i("DocumentReminders", "Sent $sent paper reminder(s)")
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("DocumentReminders", "Checking paper expiry failed", e)
        Result.retry()
    }
}
