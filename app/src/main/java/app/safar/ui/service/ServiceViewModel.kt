package app.safar.ui.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.BikeState
import app.safar.core.RideState
import app.safar.data.ServiceIcon
import app.safar.data.ServiceItem
import app.safar.data.ServiceRepository
import app.safar.data.SettingsStore
import app.safar.ui.format.Formatters
import app.safar.ui.vm.Notices
import app.safar.ui.vm.launchLogged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

private const val TAG = "ServiceViewModel"

/** Service logbook: items by odometer, mark done, interval, custom items, reminder switch. */
class ServiceViewModel(
    private val service: ServiceRepository,
    private val settings: SettingsStore,
    bike: StateFlow<BikeState>,
    ride: StateFlow<RideState>,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    val state: StateFlow<ServiceUiState> = combine(service.items, settings.settings, bike, ride) { items, s, b, r ->
        serviceUiState(items, b, r, s.serviceReminders)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServiceUiState())

    val notices = Notices()

    private fun item(id: Long): ServiceItem? = state.value.rows.firstOrNull { it.status.item.id == id }?.status?.item

    fun markDone(id: Long) {
        val item = item(id) ?: return
        val odo = markDoneOdo(state.value.odo, item)
        launchLogged(TAG, "mark done of service item $id", { notices.post("Could not log the service") }) {
            service.markDone(id, odo, now())
            notices.post("${item.label} logged at ${Formatters.odometer(odo)} km")
        }
    }

    fun setInterval(id: Long, everyKm: Int) {
        val item = item(id) ?: return
        launchLogged(TAG, "interval of service item $id", { notices.post("Could not save the interval") }) {
            service.upsert(item.copy(everyKm = everyKm))
            notices.post("${item.label}: every ${Formatters.odometer(everyKm)} km")
        }
    }

    fun add(label: String, everyKm: Int) {
        val clean = cleanLabel(label) ?: return
        if (state.value.lockReason != null) return
        val odo = state.value.odo.km ?: 0
        val item = ServiceItem(label = clean, everyKm = everyKm, lastDoneOdoKm = odo, lastDoneMillis = now(), icon = ServiceIcon.GENERIC)
        launchLogged(TAG, "add of service item", { notices.post("Could not add the item") }) {
            service.upsert(item)
            notices.post("$clean added, counted from ${Formatters.odometer(odo)} km")
        }
    }

    fun delete(id: Long) {
        val item = item(id) ?: return
        launchLogged(TAG, "delete of service item $id", { notices.post("Could not remove the item") }) {
            service.delete(id)
            notices.post("${item.label} removed")
        }
    }

    fun setReminders(on: Boolean) {
        launchLogged(TAG, "service reminder switch", { notices.post("Could not save the setting") }) {
            settings.update { it.copy(serviceReminders = on) }
        }
    }
}
