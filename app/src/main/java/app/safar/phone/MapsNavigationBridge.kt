package app.safar.phone

import android.app.Notification
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import app.safar.core.NavState
import app.safar.core.NavigationBridge
import app.safar.core.PhoneSnapshot
import app.safar.data.navUri
import app.safar.protocol.GOOGLE_MAPS_PACKAGE
import app.safar.protocol.ManeuverMatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.ZoneId

/** Distinct unmatched Maps drawables or instructions logged per process, so a long route does not flood the log. */
private const val UNMATCHED_LOG_LIMIT = 50

/**
 * Google Maps notification bridge (docs 11). [SafarNotificationListener] passes the navigation notifications
 * of `com.google.android.apps.maps` here; routes started directly in Maps attach the same way. The exposed
 * [state] recomputes the snapshot status whenever the phone's GPS, signal or internet changes.
 * Notification callbacks and the UI calls arrive on the main thread.
 */
class MapsNavigationBridge(
    context: Context,
    phone: StateFlow<PhoneSnapshot>,
    scope: CoroutineScope,
    private val icons: MapsManeuverIcons,
    private val log: PhoneEventLog,
    private val clock: () -> Long = System::currentTimeMillis,
) : NavigationBridge {
    /** [suppressed]: the rider ended navigation in Safar while Maps still navigates; ignore Maps until removal. */
    private data class Core(
        val active: Boolean = false,
        val waiting: Boolean = false,
        val label: String? = null,
        val turn: MapsTurn? = null,
        val suppressed: Boolean = false,
    )

    private val app = context.applicationContext
    private val core = MutableStateFlow(Core())
    private val loggedUnmatched = HashSet<String>()
    private var lastLogged: MapsFields? = null

    override val state: StateFlow<NavState> = combine(core, phone) { c, p ->
        NavState(c.active, c.waiting, c.label, c.turn?.toSnapshot(p, ZoneId.systemDefault()))
    }.stateIn(scope, SharingStarted.Eagerly, NavState())

    fun onPosted(notification: Notification) {
        val current = core.value
        if (current.suppressed) return
        val fields = mapsFields(notification)
        val match = captureLargeIcon(app, notification)?.let { icons.match(it.argb, it.width, it.height) }
        val code = match?.takeIf { it.accepted }?.code
        val turn = reduceMaps(if (current.active) current.turn else null, fields, code, clock())
        logFields(fields, match, code)
        if (code == null) noteUnmatched(match, turn.instruction)
        if (!current.active) log.info("maps.started", mapOf("fromSafar" to current.waiting.toString()))
        core.update { it.copy(active = true, waiting = false, turn = turn) }
    }

    /** Maps removed its navigation notification: the BLE session sends the "ended" frame. */
    fun onRemoved() {
        if (core.value.active) log.info("maps.ended")
        core.update { it.copy(active = false, waiting = false, suppressed = false) }
    }

    /**
     * The listener lost track of Maps: it was unbound (no removal callback will come), or it rebound and Maps has
     * no navigation notification. An active route ends, so the BLE session sends the "ended" frame instead of
     * repeating a stale turn. A route Safar is still waiting for is left alone.
     */
    fun onLost() {
        if (!core.value.active) return
        log.info("maps.lost")
        core.update { it.copy(active = false, suppressed = false) }
    }

    override fun openInMaps(label: String, query: String) {
        val intent = Intent(Intent.ACTION_VIEW, navUri(query).toUri())
            .setPackage(GOOGLE_MAPS_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            app.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Google Maps cannot navigate; opening the Play Store", e)
            log.error("maps.missing")
            PhoneAccess.openMapsInPlayStore(app)
            return
        }
        log.info("maps.open", mapOf("label" to label))
        core.value = Core(waiting = true, label = label)
    }

    override fun end() {
        core.update { it.copy(active = false, waiting = false, suppressed = it.active) }
        log.info("maps.end_by_rider")
    }

    fun isMapsInstalled(): Boolean = PhoneAccess.isMapsInstalled(app)

    fun hasNotificationAccess(): Boolean = PhoneAccess.hasNotificationAccess(app)

    private fun logFields(fields: MapsFields, match: ManeuverMatch?, code: Int?) {
        if (fields == lastLogged) return
        lastLogged = fields
        log.info("maps.notification", fields.toLogDetail(match, code))
    }

    /** The icon matched no Maps drawable (or there was none): the code came from the text. Logged once per key. */
    private fun noteUnmatched(match: ManeuverMatch?, instruction: String) {
        val key = match?.name ?: "none:$instruction"
        if (key in loggedUnmatched || loggedUnmatched.size >= UNMATCHED_LOG_LIMIT) return
        loggedUnmatched += key
        log.info("maps.icon.unmatched", buildMap {
            put("instruction", instruction)
            match?.let { put("nearest", it.name); put("diff", "%.2f".format(java.util.Locale.ROOT, it.diff)) }
        })
    }
}
