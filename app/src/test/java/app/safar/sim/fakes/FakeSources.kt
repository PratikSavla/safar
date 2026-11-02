package app.safar.sim.fakes

import app.safar.core.NavSnapshot
import app.safar.core.NavState
import app.safar.core.NavigationBridge
import app.safar.core.PhoneSnapshot
import app.safar.core.PhoneStatusSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Phone battery, signal, internet and GPS, set by the test. */
class FakePhoneStatus(initial: PhoneSnapshot = GOOD_PHONE) : PhoneStatusSource {
    private val mutable = MutableStateFlow(initial)
    override val state: StateFlow<PhoneSnapshot> = mutable

    fun set(transform: (PhoneSnapshot) -> PhoneSnapshot) = mutable.update(transform)

    companion object {
        /** 80 %, not charging, 3 bars, internet and GPS on. */
        val GOOD_PHONE = PhoneSnapshot(batteryPercent = 80, charging = false, signalBars = 3, hasInternet = true, gpsOn = true)
    }
}

/** A navigation bridge whose snapshots the test sets directly. */
class FakeNavigationBridge : NavigationBridge {
    private val mutable = MutableStateFlow(NavState())
    override val state: StateFlow<NavState> = mutable
    val opened = mutableListOf<Pair<String, String>>()

    /** Google Maps posted a parsed update. */
    fun show(snapshot: NavSnapshot) = mutable.update { it.copy(active = true, waiting = false, snapshot = snapshot) }

    /** Maps opened from Safar, no notification yet. */
    fun waitForMaps(label: String) {
        mutable.value = NavState(waiting = true, destinationLabel = label)
    }

    override fun openInMaps(label: String, query: String) {
        opened += label to query
        waitForMaps(label)
    }

    override fun end() = mutable.update { it.copy(active = false, waiting = false) }
}
