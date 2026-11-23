package app.safar.ui.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.BikeState
import app.safar.core.RideState
import app.safar.data.FuelUp
import app.safar.data.ServiceItem
import app.safar.data.ServiceRepository
import app.safar.data.SettingsStore
import app.safar.ui.format.Formatters
import app.safar.ui.vm.Notices
import app.safar.ui.vm.launchLogged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

private const val TAG = "ServiceViewModel"

/** Service logbook: items by distance and time, mark done, edit, custom items, reminder switch. */
class ServiceViewModel(
    private val service: ServiceRepository,
    private val settings: SettingsStore,
    bike: StateFlow<BikeState>,
    ride: StateFlow<RideState>,
    fuelUps: Flow<List<FuelUp>> = flowOf(emptyList()),
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    val state: StateFlow<ServiceUiState> = combine(service.items, settings.settings, bike, ride, fuelUps) { items, s, b, r, f ->
        serviceUiState(items, b, r, s.serviceReminders, f, now(), zone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServiceUiState())

    val notices = Notices()

    private fun item(id: Long): ServiceItem? = state.value.rows.firstOrNull { it.status.item.id == id }?.status?.item

    fun markDone(id: Long) {
        val item = item(id) ?: return
        val odo = markDoneOdo(state.value.odo, item)
        launchLogged(TAG, "mark done of service item $id", { notices.post("Couldn’t save that. Try again.") }) {
            service.markDone(id, odo, now())
            notices.post(if (item.everyKm == null) "${item.label} done today" else "${item.label} done at ${Formatters.odometer(odo)} km")
        }
    }

    /** Saves the edit sheet: interval, first service and icon. Ignored when the sheet has a problem left. */
    fun save(id: Long, form: ServiceForm) {
        val item = item(id) ?: return
        val s = state.value
        if (formProblem(form, s.odo.km, s.today, item) != null) return
        val saved = formItem(form, item, s.odo.km, now())
        launchLogged(TAG, "edit of service item $id", { notices.post("Couldn’t save the item. Try again.") }) {
            service.upsert(saved)
            notices.post("${saved.label}: every ${recurringText(saved)}")
        }
    }

    fun add(form: ServiceForm) {
        val clean = cleanLabel(form.label) ?: return
        val s = state.value
        if (s.lockReason != null) return
        // Never count distance from a made-up 0 km: it reads as massively overdue once the real odometer is known.
        if (formProblem(form, s.odo.km, s.today) != null) return
        val item = formItem(form.copy(label = clean), null, s.odo.km, now())
        launchLogged(TAG, "add of service item", { notices.post("Couldn’t add the item. Try again.") }) {
            service.upsert(item)
            notices.post("$clean added, every ${recurringText(item)}")
        }
    }

    fun delete(id: Long) {
        val item = item(id) ?: return
        launchLogged(TAG, "delete of service item $id", { notices.post("Couldn’t remove the item. Try again.") }) {
            service.delete(id)
            notices.post("${item.label} removed")
        }
    }

    fun setReminders(on: Boolean) {
        launchLogged(TAG, "service reminder switch", { notices.post("Couldn’t save the setting. Try again.") }) {
            settings.update { it.copy(serviceReminders = on) }
        }
    }
}
