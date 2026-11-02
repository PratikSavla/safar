package app.safar.ui.shell

import androidx.compose.runtime.Composable
import app.safar.ui.components.AtlasPage
import app.safar.ui.components.BackTopBar
import app.safar.ui.components.EmptyState
import app.safar.ui.components.Masthead
import app.safar.ui.nav.Navigator

/**
 * Temporary page for a route whose screen is built elsewhere. [RouteHost] calls it with one line per route;
 * each line is replaced by the real screen when it lands. A [tab] page gets the stub bar, others a back arrow.
 */
@Composable
fun PendingScreen(navigator: Navigator, kicker: String, title: String, tab: Boolean = false) {
    AtlasPage(bottomBar = if (tab) TabBarSpace else null) {
        if (tab) Masthead(kicker, title) else BackTopBar(kicker, title, onBack = navigator::back)
        EmptyState("This page is being written.", message = "It arrives with the next build of Safar.")
    }
}
