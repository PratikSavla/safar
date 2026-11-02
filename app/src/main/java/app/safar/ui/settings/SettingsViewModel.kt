package app.safar.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.BikeCatalog
import app.safar.core.BikeController
import app.safar.core.BikeModel
import app.safar.core.RideTracker
import app.safar.data.SavedBikeStore
import app.safar.data.Settings
import app.safar.data.SettingsStore
import app.safar.data.ThemeMode
import app.safar.ui.format.InputLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "SafarSettings"
private const val TYPING_DEBOUNCE_MS = 600L
private const val SLIDER_DEBOUNCE_MS = 300L

/** A debounced write waiting for its delay; [change] is replayed if the screen goes away first. */
private class PendingSave(val job: Job, val change: (Settings) -> Settings)

/** Unsaved field text. Null = show the stored value. */
private data class Drafts(val name: String? = null, val tank: String? = null, val speed: Int? = null, val currency: String? = null)

data class SettingsState(
    val settings: Settings = Settings(),
    val model: BikeModel? = null,
    val nameText: String = "",
    val nameHint: String? = null,
    val tankText: String = "",
    val tankError: String? = null,
    val speedKmh: Int = 100,
    val alertRange: IntRange = DEFAULT_ALERT_RANGE,
    /** The stored alert speed when this bike's range does not hold it; the slider shows it clamped. */
    val speedOutOfRange: Int? = null,
    val currencyText: String = "",
    val currencyHint: String? = null,
    val lockReason: String? = null,
)

/**
 * Settings screen state. Typed fields and the slider save after a short pause; [flushScope] (the app scope)
 * writes any edit still waiting when the screen closes, so leaving right after typing keeps the change.
 */
class SettingsViewModel(
    private val store: SettingsStore,
    ride: RideTracker,
    bike: BikeController,
    savedBike: SavedBikeStore,
    private val flushScope: CoroutineScope,
) : ViewModel() {
    private val drafts = MutableStateFlow(Drafts())
    private val pending = mutableMapOf<String, PendingSave>()
    private val versionTaps = VersionTaps()

    val state: StateFlow<SettingsState> = combine(store.settings, drafts, ride.state, bike.state, savedBike.saved) { s, d, r, b, saved ->
        val model = b.saved?.model ?: saved?.let { BikeCatalog.forBleName(it.name) }
        buildState(s, d, model, InputLock.reason(r))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    private fun buildState(s: Settings, d: Drafts, model: BikeModel?, lock: String?): SettingsState {
        val range = alertRangeOf(model)
        val name = d.name ?: s.riderName
        val tank = d.tank ?: (s.tankLitres ?: model?.tankLitres)?.let(::litresText).orEmpty()
        return SettingsState(
            settings = s,
            model = model,
            nameText = name,
            nameHint = if (d.name != null && clusterName(name).isEmpty()) "The cluster needs at least one letter or digit" else null,
            tankText = tank,
            tankError = d.tank?.let { (parseTank(it, model?.tankLitres) as? TankInput.Invalid)?.reason },
            speedKmh = (d.speed ?: s.speedAlertKmh).coerceIn(range),
            alertRange = range,
            speedOutOfRange = s.speedAlertKmh.takeIf { d.speed == null && it !in range },
            currencyText = d.currency ?: s.currencySymbol,
            currencyHint = d.currency?.takeIf { cleanCurrency(it) == null }?.let { "Enter a symbol, like ₹ or $" },
            lockReason = lock,
        )
    }

    fun setRiderName(text: String) {
        if (state.value.lockReason != null) return
        val value = text.take(RIDER_NAME_FIELD_MAX)
        drafts.update { it.copy(name = value) }
        if (clusterName(value).isEmpty()) return
        debounce("name", TYPING_DEBOUNCE_MS) { it.copy(riderName = value.trim()) }
    }

    fun setTank(text: String) {
        if (state.value.lockReason != null) return
        drafts.update { it.copy(tank = text) }
        val parsed = parseTank(text, state.value.model?.tankLitres)
        if (parsed is TankInput.Valid) debounce("tank", TYPING_DEBOUNCE_MS) { it.copy(tankLitres = parsed.litres) }
    }

    fun resetTank() {
        if (state.value.lockReason != null) return
        pending.remove("tank")?.job?.cancel()
        drafts.update { it.copy(tank = null) }
        write("tank size") { it.copy(tankLitres = null) }
    }

    fun setCurrency(text: String) {
        if (state.value.lockReason != null) return
        drafts.update { it.copy(currency = text) }
        val symbol = cleanCurrency(text) ?: return
        debounce("currency", TYPING_DEBOUNCE_MS) { it.copy(currencySymbol = symbol) }
    }

    fun setSpeedAlert(kmh: Int) {
        drafts.update { it.copy(speed = kmh) }
        debounce("speed", SLIDER_DEBOUNCE_MS) { it.copy(speedAlertKmh = kmh) }
    }

    fun setTheme(mode: ThemeMode) = write("theme") { it.copy(theme = mode) }

    fun setDeveloperMode(on: Boolean) = write("developer mode") { it.copy(developerMode = on) }

    /** A tap on the version number. The seventh quick tap flips Developer mode and returns the new value; else null. */
    fun tapVersion(atMillis: Long): Boolean? {
        if (!versionTaps.tap(atMillis)) return null
        val on = !store.settings.value.developerMode
        setDeveloperMode(on)
        return on
    }

    /** Any plain switch: alerts, riding, fuel, diagnostics. */
    fun toggle(what: String, change: (Settings) -> Settings) = write(what, change)

    private fun debounce(key: String, delayMs: Long, change: (Settings) -> Settings) {
        pending.remove(key)?.job?.cancel()
        val job = viewModelScope.launch {
            delay(delayMs)
            pending.remove(key)
            save(key, change)
        }
        pending[key] = PendingSave(job, change)
    }

    /** viewModelScope is already cancelled here; write what was still waiting in one update. */
    override fun onCleared() {
        val left = pending.values.toList()
        pending.clear()
        left.forEach { it.job.cancel() }
        if (left.isEmpty()) return
        flushScope.launch { save("unsaved edits") { s -> left.fold(s) { acc, p -> p.change(acc) } } }
    }

    private fun write(what: String, change: (Settings) -> Settings) {
        viewModelScope.launch { save(what, change) }
    }

    private suspend fun save(what: String, change: (Settings) -> Settings) {
        try {
            store.update(change)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not save setting: $what", e)
        }
    }
}
