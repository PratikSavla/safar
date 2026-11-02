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
import app.safar.R
import app.safar.core.BikeState
import app.safar.core.DebugLog
import app.safar.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Posts the "Low-fuel reminder" (Settings and Fuel switch, `Settings.lowFuelAlert`) when the cluster's fuel
 * bars drop to 1 while connected ([LowFuelRule]). Started once from `BleModule.install`.
 */
object LowFuelNotifier {
    private const val TAG = "LowFuelNotifier"
    private const val CHANNEL_ID = "low_fuel"
    private const val NOTIFICATION_ID = 1002
    private const val REQUEST_OPEN = 3

    fun start(context: Context, bike: Flow<BikeState>, settings: Flow<Settings>, log: DebugLog, scope: CoroutineScope) {
        val app = context.applicationContext
        val rule = LowFuelRule()
        scope.launch {
            combine(bike, settings) { b, s -> b.cluster?.fuelBars to s.lowFuelAlert }
                .distinctUntilChanged()
                .collect { (bars, on) -> if (rule.next(bars, on)) post(app, log) }
        }
    }

    private fun post(context: Context, log: DebugLog) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            log.event("Low-fuel reminder not shown: notifications are off")
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Low fuel", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A reminder when the cluster's fuel gauge reaches its last bar."
            },
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_safar)
            .setContentTitle("Low fuel: 1 bar left")
            .setContentText("The cluster reads its last fuel bar. Time to look for a pump.")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        openApp(context)?.let(builder::setContentIntent)
        try {
            manager.notify(NOTIFICATION_ID, builder.build())
            log.event("Low-fuel reminder shown")
        } catch (e: SecurityException) {
            Log.w(TAG, "Posting the low-fuel reminder was refused", e)
            log.event("Low-fuel reminder refused", mapOf("message" to (e.message ?: "")), error = true)
        }
    }

    private fun openApp(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, REQUEST_OPEN, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
