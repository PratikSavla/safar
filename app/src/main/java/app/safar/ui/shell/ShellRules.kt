package app.safar.ui.shell

import app.safar.core.ActiveCall
import app.safar.core.AlertSource
import app.safar.core.AlertsState
import app.safar.core.LinkState
import app.safar.core.NavState
import app.safar.data.Settings
import app.safar.ui.nav.Route

/** Ticket-stub keys from [app.safar.ui.components.safarTabs] and the route each opens (app.js `TABS`). */
internal val TAB_ROUTES: Map<String, Route> = linkedMapOf(
    "today" to Route.Home,
    "route" to Route.Navigate,
    "journal" to Route.Trips,
    "alerts" to Route.Alerts,
    "settings" to Route.Settings,
)

/**
 * The stub marked for [route] (app.js `TAB_OF`): detail pages mark their section's stub. Null when the
 * route has no tab bar (onboarding, pair, ride, inner pages).
 */
internal fun tabOf(route: Route): String? = when (route) {
    Route.Home -> "today"
    Route.Navigate, is Route.Place -> "route"
    Route.Trips, is Route.Trip -> "journal"
    Route.Alerts -> "alerts"
    Route.Settings -> "settings"
    else -> null
}

/** Routes that draw the stub bar: the five sections themselves (chrome 'tabs' in the prototype). */
internal fun showsTabs(route: Route?): Boolean = route != null && route in TAB_ROUTES.values

/** What auto ride mode should do after a GPS update. */
internal enum class AutoRideAction { NONE, ENTER, LEAVE }

/**
 * Settings → auto ride mode (app.js `autoRideCheck`): open Ride when the phone starts moving, close it when
 * the ride ends, but only if auto mode opened it. Never interrupts onboarding or pairing.
 */
internal fun autoRideAction(moving: Boolean, wasMoving: Boolean, autoRideOn: Boolean, current: Route?, openedByAuto: Boolean): AutoRideAction = when {
    moving == wasMoving -> AutoRideAction.NONE
    moving && autoRideOn && current != null && current !in AUTO_RIDE_BLOCKED -> AutoRideAction.ENTER
    !moving && current == Route.Ride && openedByAuto -> AutoRideAction.LEAVE
    else -> AutoRideAction.NONE
}

private val AUTO_RIDE_BLOCKED = setOf(Route.Onboarding, Route.Pair, Route.Ride)

/** The incoming call telegram above every page (app.js `chrome`). */
data class CallBanner(val name: String, val source: AlertSource, val status: String, val ringing: Boolean)

/** Shell-level facts: tab marks, the call telegram and whether the screen stays on. */
data class ShellChrome(
    val alertBadge: Int = 0,
    val navigating: Boolean = false,
    val call: CallBanner? = null,
    val keepScreenOn: Boolean = false,
)

private val NO_TELEGRAM = setOf(Route.Ride, Route.Onboarding, Route.Alerts)

internal fun shellChrome(route: Route?, link: LinkState, alerts: AlertsState, nav: NavState, settings: Settings): ShellChrome {
    val call = alerts.call?.takeIf { route != null && route !in NO_TELEGRAM }
    return ShellChrome(
        alertBadge = alerts.missed.count + alerts.messages.count,
        navigating = nav.active,
        call = call?.let { callBanner(it, link, settings) },
        keepScreenOn = settings.keepScreenOn && link == LinkState.Connected,
    )
}

/** "On call", "Ringing · shown on the cluster" or "Ringing · cluster alert off". */
internal fun callBanner(call: ActiveCall, link: LinkState, settings: Settings): CallBanner {
    val status = when {
        call.answered -> "On call"
        callForwarded(call.source, link, settings) -> "Ringing · shown on the cluster"
        else -> "Ringing · cluster alert off"
    }
    return CallBanner(call.name, call.source, status, ringing = !call.answered)
}

/** True when this call reaches the cluster: the bike is linked and that alert is switched on. */
internal fun callForwarded(source: AlertSource, link: LinkState, settings: Settings): Boolean {
    val on = if (source == AlertSource.WHATSAPP) settings.alertWhatsAppCalls else settings.alertCalls
    return on && link == LinkState.Connected
}
