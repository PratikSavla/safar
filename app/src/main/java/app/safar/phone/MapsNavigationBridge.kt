package app.safar.phone

import android.app.Notification
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import app.safar.core.NavState
import app.safar.core.NavigationBridge
import app.safar.core.PhoneSnapshot
import app.safar.data.navUri
import app.safar.protocol.GOOGLE_MAPS_PACKAGE
import app.safar.protocol.IconMatch
import app.safar.protocol.MAX_ICON_CODE
import app.safar.protocol.MIN_ICON_CODE
import app.safar.protocol.hashToHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

/** A turn icon with no table match, for the future "learn icons" debug screen (docs 11.4 step 5). */
class UnmatchedIcon(val hash: Long, val bitmap: Bitmap, val instruction: String, val atMillis: Long) {
    val hashHex: String get() = hashToHex(hash)
}

/** Distinct unmatched hashes logged per process, so a long route does not flood the debug log. */
private const val UNMATCHED_LOG_LIMIT = 200

/**
 * Google Maps notification bridge (docs 11). [SafarNotificationListener] passes the navigation notifications
 * of `com.google.android.apps.maps` here; routes started directly in Maps attach the same way. The exposed
 * [state] recomputes the snapshot status whenever the phone's GPS, signal or internet changes.
 * Notification callbacks and the UI calls arrive on the main thread.
 */
class MapsNavigationBridge(
    context: Context,
    phone: StateFlow<PhoneSnapshot>,
    private val scope: CoroutineScope,
    private val icons: IconLibrary,
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
    private val unmatched = MutableStateFlow<UnmatchedIcon?>(null)
    private val loggedHashes = LinkedHashSet<Long>()
    private var lastLogged: MapsFields? = null

    override val state: StateFlow<NavState> = combine(core, phone) { c, p ->
        NavState(c.active, c.waiting, c.label, c.turn?.toSnapshot(p, ZoneId.systemDefault()))
    }.stateIn(scope, SharingStarted.Eagerly, NavState())

    /** The last large icon that had no match. */
    val lastUnmatched: StateFlow<UnmatchedIcon?> = unmatched.asStateFlow()

    fun onPosted(notification: Notification) {
        val current = core.value
        if (current.suppressed) return
        val fields = mapsFields(notification)
        val capture = captureLargeIcon(app, notification)
        val match: IconMatch? = capture?.let { icons.match(it.hash) }
        val turn = reduceMaps(if (current.active) current.turn else null, fields, match?.code, clock())
        logFields(fields, capture, match)
        if (capture != null && match == null) noteUnmatched(capture, turn.instruction)
        if (!current.active) log.info("maps.started", mapOf("fromSafar" to current.waiting.toString()))
        core.update { it.copy(active = true, waiting = false, turn = turn) }
    }

    /** Maps removed its navigation notification: the BLE session sends the "ended" frame. */
    fun onRemoved() {
        if (core.value.active) log.info("maps.ended")
        core.update { it.copy(active = false, waiting = false, suppressed = false) }
    }

    override fun openInMaps(label: String, query: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(navUri(query)))
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

    /** Saves a hash -> cluster code pair in the learned table (debug screen). */
    fun learnIcon(hash: Long, code: Int) {
        require(code in MIN_ICON_CODE..MAX_ICON_CODE) { "icon code must be 1..45, got $code" }
        scope.launch(Dispatchers.IO) { icons.learn(hash, code) }
    }

    fun isMapsInstalled(): Boolean = PhoneAccess.isMapsInstalled(app)

    fun hasNotificationAccess(): Boolean = PhoneAccess.hasNotificationAccess(app)

    private fun logFields(fields: MapsFields, capture: IconCapture?, match: IconMatch?) {
        if (fields == lastLogged) return
        lastLogged = fields
        log.info("maps.notification", fields.toLogDetail(capture?.hash?.let(::hashToHex), match?.code))
    }

    private fun noteUnmatched(capture: IconCapture, instruction: String) {
        unmatched.value = UnmatchedIcon(capture.hash, capture.bitmap, instruction, clock())
        if (capture.hash in loggedHashes || loggedHashes.size >= UNMATCHED_LOG_LIMIT) return
        loggedHashes += capture.hash
        log.info("maps.icon.unmatched", mapOf("hash" to hashToHex(capture.hash), "instruction" to instruction))
    }
}
