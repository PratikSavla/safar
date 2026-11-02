package app.safar.ui.alerts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.safar.core.AlertCenter
import app.safar.core.AlertsState
import app.safar.core.BikeController
import app.safar.core.LinkState
import app.safar.data.Settings
import app.safar.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "SafarAlerts"

data class AlertsScreenState(
    val alerts: AlertsState = AlertsState(),
    val feed: List<FeedRow> = emptyList(),
    val settings: Settings = Settings(),
    val bikeConnected: Boolean = false,
)

class AlertsViewModel(
    private val alerts: AlertCenter,
    private val settings: SettingsStore,
    bike: BikeController,
) : ViewModel() {
    val state: StateFlow<AlertsScreenState> = combine(alerts.state, settings.settings, bike.state) { a, s, b ->
        AlertsScreenState(a, feedRows(a.feed), s, b.link == LinkState.Connected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertsScreenState())

    fun clearMissed() = guard("clear missed calls") { alerts.clearMissedCalls() }

    fun clearMessages() = guard("clear messages") { alerts.clearMessages() }

    fun setCalls(on: Boolean) = write("phone call alerts") { it.copy(alertCalls = on) }

    fun setWhatsAppCalls(on: Boolean) = write("WhatsApp call alerts") { it.copy(alertWhatsAppCalls = on) }

    fun setWhatsAppMessages(on: Boolean) = write("WhatsApp message alerts") { it.copy(alertWhatsAppMessages = on) }

    private fun guard(what: String, block: () -> Unit) = try {
        block()
    } catch (e: RuntimeException) {
        Log.e(TAG, "Could not $what", e)
    }

    private fun write(what: String, change: (Settings) -> Settings) {
        viewModelScope.launch {
            try {
                settings.update(change)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not save $what", e)
            }
        }
    }
}
