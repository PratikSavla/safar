package app.safar.ui.widget

import android.util.Log
import app.safar.core.AppContainer
import app.safar.core.BikeController
import app.safar.core.NavigationBridge
import app.safar.data.FuelRepository
import app.safar.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.ZoneId

private const val TAG = "SafarWidget"

/** The parts of [AppContainer] the widget reads. */
internal class WidgetSources(
    val bike: BikeController,
    val navigation: NavigationBridge,
    val fuel: FuelRepository,
    val settings: SettingsStore,
)

/** Reads the widget's inputs from [AppContainer], which other layers fill in at start-up. */
internal object WidgetData {
    @Volatile
    private var warned = false

    private val latest = MutableStateFlow<WidgetSnapshot?>(null)

    /**
     * The newest published snapshot, null before the first [publish]. A running widget session reads it inside
     * its composition, so a refresh redraws with fresh values: Glance does not call `provideGlance` again while
     * the session is still open.
     */
    val updates: StateFlow<WidgetSnapshot?> = latest.asStateFlow()

    /** Builds a fresh [snapshot] and hands it to [updates]. */
    suspend fun publish(nowMillis: Long = System.currentTimeMillis()): WidgetSnapshot =
        snapshot(nowMillis).also { latest.value = it }

    /**
     * The wired sources, or null while a layer has not assigned its part yet (the widget can update before the
     * app finished starting, or in a process the receiver woke). Logged once, then quiet.
     */
    fun sources(): WidgetSources? = try {
        WidgetSources(AppContainer.bike, AppContainer.navigation, AppContainer.fuel, AppContainer.settings)
    } catch (e: UninitializedPropertyAccessException) {
        if (!warned) {
            warned = true
            Log.w(TAG, "AppContainer not wired yet; the widget shows placeholders until it is", e)
        }
        null
    }

    suspend fun snapshot(nowMillis: Long = System.currentTimeMillis()): WidgetSnapshot {
        val src = sources() ?: return WidgetSnapshot.Empty
        return widgetSnapshot(
            bike = src.bike.state.value,
            nav = src.navigation.state.value,
            fuelUps = src.fuel.fuelUps.first(),
            settings = src.settings.settings.value,
            nowMillis = nowMillis,
            zone = ZoneId.systemDefault(),
        )
    }
}
