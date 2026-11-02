package app.safar.sim

import app.safar.core.NavState
import app.safar.core.NavigationBridge
import app.safar.core.PhoneSnapshot
import app.safar.phone.MapsFields
import app.safar.phone.MapsTurn
import app.safar.phone.reduceMaps
import app.safar.phone.toSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

/**
 * The pure half of MapsNavigationBridge: Google Maps notification extras go through [reduceMaps] and
 * [toSnapshot] with the phone state, exactly as the bridge does after it read the [MapsFields] of a
 * notification. [post] is onNotificationPosted, [remove] is onNotificationRemoved.
 */
class MapsFeed(
    phone: StateFlow<PhoneSnapshot>,
    scope: CoroutineScope,
    private val clock: () -> Long,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : NavigationBridge {
    private val active = MutableStateFlow(false)
    private val turn = MutableStateFlow<MapsTurn?>(null)

    override val state: StateFlow<NavState> = combine(active, turn, phone) { on, t, p ->
        NavState(active = on, snapshot = t?.toSnapshot(p, zone))
    }.stateIn(scope, SharingStarted.Eagerly, NavState())

    /** [matchedIcon]: the code the large-icon table matched, null when it had no match. */
    fun post(fields: MapsFields, matchedIcon: Int? = null) {
        turn.value = reduceMaps(if (active.value) turn.value else null, fields, matchedIcon, clock(), zone)
        active.value = true
    }

    fun remove() {
        active.value = false
    }

    override fun openInMaps(label: String, query: String) = Unit

    override fun end() = remove()
}
