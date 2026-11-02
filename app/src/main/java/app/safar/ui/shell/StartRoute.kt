package app.safar.ui.shell

import android.util.Log
import app.safar.data.SettingsStore
import app.safar.ui.nav.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Picks the first page: ONBOARDING until the rider finished it, else HOME (app.js last line).
 *
 * [SettingsStore.settings] starts at defaults (`onboarded = false`) and has no "loaded" signal, so this waits
 * up to [FIRST_READ_MS] for `onboarded` to turn true. If the file is slower than that, the rider briefly sees
 * onboarding and is moved to HOME as soon as the value arrives (while onboarding is still the only page).
 */
internal class StartRoute(
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val nav: Nav,
) {
    /** The shell operations this needs. */
    interface Nav {
        val stack: NavStack
        fun go(route: Route)
        fun resetTo(route: Route)
    }

    private var pending: Route? = null
    private var decided = false

    fun decide() {
        scope.launch {
            val onboarded = withTimeoutOrNull(FIRST_READ_MS) { settings.settings.first { it.onboarded } } != null
            nav.resetTo(if (onboarded) Route.Home else Route.Onboarding)
            decided = true
            pending?.let(nav::go)
            pending = null
            if (!onboarded) healLateRead()
        }
    }

    /** Opens [route] on top of the start page once it is known. */
    fun then(route: Route) {
        if (decided) nav.go(route) else pending = route
    }

    private suspend fun healLateRead() {
        withTimeoutOrNull(LATE_READ_MS) { settings.settings.first { it.onboarded } } ?: return
        val entries = nav.stack.entries
        if (entries.size == 1 && entries[0].route == Route.Onboarding) {
            Log.i(TAG, "Settings arrived after $FIRST_READ_MS ms; moving from onboarding to home")
            nav.resetTo(Route.Home)
        }
    }

    private companion object {
        const val TAG = "SafarShell"
        const val FIRST_READ_MS = 700L
        const val LATE_READ_MS = 8_000L
    }
}
