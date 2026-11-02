package app.safar.ui.shell

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import java.util.WeakHashMap

/** What the shell offers screens beyond the [app.safar.ui.nav.Navigator]. */
@Stable
interface ShellUi {
    /** Short confirmation slip above the tabs. */
    fun toast(message: String)

    /** True while Ride was opened by auto ride mode. */
    val autoRide: Boolean

    /** The rider is leaving Ride with the long-press: auto ride mode lets go of it. Navigation is the caller's. */
    fun leaveRide(stillMoving: Boolean)
}

/** No-op shell for previews and tests. */
object PreviewShellUi : ShellUi {
    override fun toast(message: String) = Unit
    override val autoRide: Boolean = false
    override fun leaveRide(stillMoving: Boolean) = Unit
}

val LocalShellUi = staticCompositionLocalOf<ShellUi> { PreviewShellUi }

/**
 * Pass as `AtlasPage(bottomBar = TabBarSpace)` on the five tab pages (HOME, NAVIGATE, TRIPS, ALERTS, SETTINGS):
 * the shell draws the ticket stubs itself, fixed above the page transitions; this keeps the page's
 * bottom clearance for them.
 */
val TabBarSpace: @Composable () -> Unit = {}

/**
 * Keeps the display on while this is in composition and [on] is true (FLAG_KEEP_SCREEN_ON via the view).
 * Requests are counted per view, so two requests on the same view do not undo each other.
 */
@Composable
fun KeepScreenOn(on: Boolean = true) {
    val view: View = LocalView.current
    DisposableEffect(view, on) {
        if (on) ScreenOnRequests.acquire(view)
        onDispose { if (on) ScreenOnRequests.release(view) }
    }
}

/** Main-thread only, like every composition effect. */
private object ScreenOnRequests {
    private val counts = WeakHashMap<View, Int>()

    fun acquire(view: View) {
        counts[view] = (counts[view] ?: 0) + 1
        view.keepScreenOn = true
    }

    fun release(view: View) {
        val left = ((counts[view] ?: 1) - 1).coerceAtLeast(0)
        if (left == 0) counts.remove(view) else counts[view] = left
        view.keepScreenOn = left > 0
    }
}
