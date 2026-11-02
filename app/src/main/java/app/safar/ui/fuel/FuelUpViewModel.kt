package app.safar.ui.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.BikeState
import app.safar.core.LinkState
import app.safar.core.RideState
import app.safar.data.FuelRepository
import app.safar.data.FuelUp
import app.safar.data.OdoSuggestion
import app.safar.data.SettingsStore
import app.safar.data.odoSuggestion
import app.safar.data.tankLitres
import app.safar.ui.vm.Notices
import app.safar.ui.vm.launchLogged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "FuelUpViewModel"

/** Where the form starts: the entry being edited (null = new) and the bike's odometer at open time. */
private data class FormSeed(val entry: FuelUp?, val sugg: OdoSuggestion, val missing: Boolean)

/** Log or edit one fuel-up. [fuelUpId] null = new. Input locks while the phone moves (UX rule 3). */
class FuelUpViewModel(
    private val fuelUpId: Long?,
    private val fuel: FuelRepository,
    settings: SettingsStore,
    private val bike: StateFlow<BikeState>,
    ride: StateFlow<RideState>,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val seed = MutableStateFlow<FormSeed?>(null)
    private val form = MutableStateFlow(FuelForm())
    private val _done = MutableStateFlow<String?>(null)

    /** Set once the entry is saved or deleted: the message for the fuel page's toast. */
    val done: StateFlow<String?> = _done.asStateFlow()
    val notices = Notices()

    /** True from the tap on Save or Delete until the write fails (on success the form closes). */
    private val busy = MutableStateFlow(false)

    val state: StateFlow<FuelUpUiState> = combine(seed, form, fuel.fuelUps, settings.settings, combine(bike, ride, busy, ::Triple)) { sd, f, list, s, (b, r, w) ->
        if (sd == null) return@combine FuelUpUiState()
        fuelUpUiState(sd.entry, sd.missing, sd.sugg, f, list, tankLitres(s, b.saved?.model), s.currencySymbol, r).copy(saving = w)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FuelUpUiState())

    init {
        viewModelScope.launch {
            val list = fuel.fuelUps.first()
            val entry = fuelUpId?.let { id -> list.firstOrNull { it.id == id } }
            val b = bike.value
            val sugg = odoSuggestion(b.cluster, b.lastCluster, b.link == LinkState.Connected)
            form.value = initialForm(entry, sugg)
            seed.value = FormSeed(entry, sugg, missing = fuelUpId != null && entry == null)
        }
    }

    fun setLitres(text: String) = form.update { it.copy(litres = text) }
    fun setTotal(text: String) = form.update { it.copy(total = text) }
    fun setOdo(text: String) = form.update { it.copy(odo = text.filter(Char::isDigit)) }
    fun editOdo() = form.update { it.copy(editOdo = true) }
    fun setFull(full: Boolean) = form.update { it.copy(full = full) }

    fun save() {
        val s = state.value
        val sd = seed.value ?: return
        if (s.locked != null || !s.canSave) return
        val b = bike.value
        val barsNow = if (b.link == LinkState.Connected) b.cluster?.fuelBars else null
        val record = toFuelUp(form.value, s.validation ?: return, sd.entry, sd.sugg, now(), barsNow) ?: return
        if (!busy.compareAndSet(expect = false, update = true)) return
        launchLogged(TAG, "save of fuel-up ${record.id}", { busy.value = false; notices.post("Couldn’t save the fuel-up. Try again.") }) {
            if (sd.entry == null) fuel.add(record) else fuel.update(record)
            _done.value = if (sd.entry == null) "Fuel-up saved. Range updated." else "Changes saved. Range updated."
        }
    }

    fun delete() {
        val entry = seed.value?.entry ?: return
        if (!busy.compareAndSet(expect = false, update = true)) return
        launchLogged(TAG, "delete of fuel-up ${entry.id}", { busy.value = false; notices.post("Couldn’t delete the fuel-up. Try again.") }) {
            fuel.delete(entry.id)
            _done.value = "Fuel-up deleted"
        }
    }
}
