package app.safar.ui.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import app.safar.core.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "WidgetUpdater"
private const val WIRE_POLL_MS = 500L
private const val WIRE_POLL_MAX_MS = 30_000L
private const val SAMPLE_MS = 3_000L
private const val PERIODIC_MS = 15 * 60_000L

/**
 * Keeps the home-screen widget current while the app process runs: redraws when the bike, navigation, fuel-ups
 * or settings change what the widget prints (at most every 3 s), plus a 15-minute fallback. The system's
 * `updatePeriodMillis` covers the time the process is not running. Started once from `SafarApp.onCreate`.
 */
object WidgetUpdater {
    private val started = AtomicBoolean(false)

    @OptIn(FlowPreview::class)
    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        AppContainer.appScope.launch {
            val src = awaitSources()
            merge(changes(src).sample(SAMPLE_MS).map { }, ticker()).collect { refresh(app) }
        }
    }

    /** Waits until every layer assigned its part of [AppContainer], polling less often as time goes by. */
    private suspend fun awaitSources(): WidgetSources {
        var attempt = 1L
        while (true) {
            WidgetData.sources()?.let { return it }
            delay(minOf(WIRE_POLL_MS * attempt++, WIRE_POLL_MAX_MS))
        }
    }

    /** What the widget prints, so frames that change nothing visible do not redraw it. */
    private fun changes(src: WidgetSources): Flow<WidgetSnapshot> =
        combine(src.bike.state, src.navigation.state, src.fuel.fuelUps, src.settings.settings) { b, n, f, s ->
            widgetSnapshot(b, n, f, s, System.currentTimeMillis(), ZoneId.systemDefault())
        }.distinctUntilChanged()

    private fun ticker(): Flow<Unit> = flow {
        while (true) {
            delay(PERIODIC_MS)
            emit(Unit)
        }
    }

    /** Redraws every placed Safar widget; does nothing when none is on the home screen. */
    suspend fun refresh(context: Context) {
        try {
            val ids = GlanceAppWidgetManager(context).getGlanceIds(SafarWidget::class.java)
            if (ids.isNotEmpty()) SafarWidget().updateAll(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "widget refresh failed", e)
        }
    }
}

/** Hosts [SafarWidget] for the launcher (declared in the manifest with `xml/safar_widget_info`). */
class SafarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = SafarWidget()
}
