package app.safar.ui.widget

import android.util.Log
import app.safar.core.AppContainer
import app.safar.core.BikeController
import app.safar.core.NavigationBridge
import app.safar.data.FuelRepository
import app.safar.data.SettingsStore
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
