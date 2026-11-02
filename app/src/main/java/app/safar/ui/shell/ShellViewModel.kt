package app.safar.ui.shell

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import app.safar.core.AlertCenter
import app.safar.core.BikeController
import app.safar.core.NavigationBridge
import app.safar.core.RideTracker
import app.safar.data.Settings
import app.safar.data.SettingsStore
import app.safar.ui.nav.Navigator
import app.safar.ui.nav.Route
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-off things the shell shows: a toast or the "Ride mode · auto" stamp. */
sealed interface ShellEvent {
    data class Toast(val message: String) : ShellEvent
    data object RideFlash : ShellEvent
}

/**
 * The app shell's state, kept across rotation and (for the stack) process death: the back stack, one
 * [ViewModelStore] per stack entry, auto ride mode and the chrome (tab marks, call telegram, keep screen on).
 * It is the [Navigator] every screen gets.
 */
class ShellViewModel(
    private val saved: SavedStateHandle,
    private val settings: SettingsStore,
    bike: BikeController,
    alerts: AlertCenter,
    navigation: NavigationBridge,
    private val ride: RideTracker,
) : ViewModel(), Navigator {
    private val _stack = MutableStateFlow(NavStack.decode(saved.get<ArrayList<String>>(KEY_STACK)))
    val stack: StateFlow<NavStack> = _stack.asStateFlow()

    private val stores = HashMap<Long, ViewModelStore>()
    private val _events = Channel<ShellEvent>(Channel.BUFFERED)
    val events: Flow<ShellEvent> = _events.receiveAsFlow()

    private val _autoRide = MutableStateFlow(saved.get<Boolean>(KEY_AUTO_RIDE) ?: false)
    /**
     * True while Ride was opened by auto ride mode (it closes itself when the ride ends). Cleared as soon as
     * that Ride page leaves the top of the stack, however it leaves (system back, a page pushed over it).
     */
    val autoRide: StateFlow<Boolean> = _autoRide.asStateFlow()

    val settingsState: StateFlow<Settings> = settings.settings

    val chrome: StateFlow<ShellChrome> = combine(_stack, bike.state, alerts.state, navigation.state, settings.settings) { s, b, a, n, set ->
        shellChrome(s.top?.route, b.link, a, n, set)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ShellChrome())

    private val start = StartRoute(
        settings, viewModelScope,
        object : StartRoute.Nav {
            override val stack: NavStack get() = _stack.value
            override fun go(route: Route) = this@ShellViewModel.go(route)
            override fun resetTo(route: Route) = this@ShellViewModel.resetTo(route)
        },
    )

    init {
        if (_stack.value.entries.isEmpty()) start.decide()
        viewModelScope.launch { watchRide() }
    }

    override fun go(route: Route) = update(_stack.value.go(route))

    override fun back() = update(_stack.value.back())

    override fun resetTo(route: Route) = update(_stack.value.resetTo(route))

    /** Text shared from Google Maps (ACTION_SEND): opens "save a place" once the start page is known. */
    fun openShared(text: String) {
        val route = Route.Place(sharedText = text)
        if (_stack.value.entries.isEmpty()) start.then(route) else go(route)
    }

    fun toast(message: String) {
        _events.trySend(ShellEvent.Toast(message))
    }

    /** The rider left Ride by hand: auto mode no longer owns it. The screen then navigates back. */
    fun leaveRide(stillMoving: Boolean) {
        setAutoRide(false)
        if (stillMoving) toast("Ride mode closed · still recording the trip")
    }

    /** The ViewModels of one stack entry; dropped when the entry leaves the stack. */
    fun storeFor(entryId: Long): ViewModelStore = stores.getOrPut(entryId) { ViewModelStore() }

    private fun update(next: NavStack) {
        val prev = _stack.value
        if (next == prev) return
        _stack.value = next
        saved[KEY_STACK] = next.encode()
        prev.removedIn(next).forEach { id -> stores.remove(id)?.clear() }
        if (_autoRide.value && prev.top?.route == Route.Ride && next.top?.route != Route.Ride) setAutoRide(false)
    }

    private suspend fun watchRide() {
        var wasMoving = ride.state.value.moving
        ride.state.collect { r ->
            val action = autoRideAction(r.moving, wasMoving, settings.settings.value.autoRideMode, _stack.value.top?.route, _autoRide.value)
            wasMoving = r.moving
            when (action) {
                AutoRideAction.ENTER -> {
                    setAutoRide(true)
                    go(Route.Ride)
                    _events.trySend(ShellEvent.RideFlash)
                }
                AutoRideAction.LEAVE -> {
                    setAutoRide(false)
                    back()
                    toast("Ride ended · added to the journal")
                }
                AutoRideAction.NONE -> Unit
            }
        }
    }

    private fun setAutoRide(on: Boolean) {
        _autoRide.value = on
        saved[KEY_AUTO_RIDE] = on
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }

    private companion object {
        const val KEY_STACK = "shell.stack"
        const val KEY_AUTO_RIDE = "shell.autoRide"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
