package app.safar.ui.alerts

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.safar.core.AppContainer
import app.safar.ui.components.AtlasButton
import app.safar.ui.components.AtlasPage
import app.safar.ui.components.ButtonTone
import app.safar.ui.components.EmptyState
import app.safar.ui.components.Kicker
import app.safar.ui.components.ListRow
import app.safar.ui.components.Masthead
import app.safar.ui.components.SectionHeader
import app.safar.ui.components.SourceLine
import app.safar.ui.components.SwitchRow
import app.safar.ui.components.TopoTransform
import app.safar.ui.nav.Navigator
import app.safar.ui.settings.AccessStatus
import app.safar.ui.settings.SystemAccess
import app.safar.ui.settings.rememberAccessStatus

class AlertsActions(
    val onClearMissed: () -> Unit = {},
    val onClearMessages: () -> Unit = {},
    val onCalls: (Boolean) -> Unit = {},
    val onWhatsAppCalls: (Boolean) -> Unit = {},
    val onWhatsAppMessages: (Boolean) -> Unit = {},
    val onAccess: () -> Unit = {},
)

/**
 * Alerts tab (ux alerts.js): incoming call, missed and WhatsApp counters with Clear, the alert switches and
 * a live feed of what went to the cluster. [bottomBar] is the shell's tab strip.
 */
@Composable
fun AlertsScreen(navigator: Navigator, modifier: Modifier = Modifier, bottomBar: (@Composable () -> Unit)? = null) {
    val vm = viewModel { AlertsViewModel(AppContainer.alerts, AppContainer.settings, AppContainer.bike) }
    val state by vm.state.collectAsStateWithLifecycle()
    val access by rememberAccessStatus()
    val context = LocalContext.current
    val actions = remember(vm, context) {
        AlertsActions(
            onClearMissed = vm::clearMissed,
            onClearMessages = vm::clearMessages,
            onCalls = vm::setCalls,
            onWhatsAppCalls = vm::setWhatsAppCalls,
            onWhatsAppMessages = vm::setWhatsAppMessages,
            onAccess = { SystemAccess.openNotificationAccess(context) },
        )
    }
    AlertsContent(state, access, actions, modifier, bottomBar)
}

@Composable
internal fun AlertsContent(
    state: AlertsScreenState,
    access: AccessStatus,
    actions: AlertsActions,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
) {
    val a = state.alerts
    AtlasPage(modifier, topo = TopoTransform.Alerts, bottomBar = bottomBar) {
        Masthead("Alerts · to the cluster", "Names", kickerAside = "${a.feed.size} sent", titleEm = ", never words.")
        a.call?.let { CallCard(it, state.settings) }
        Tally(a.missed, a.messages, actions.onClearMissed, actions.onClearMessages)
        SourceLine(
            if (state.bikeConnected) "phone notifications · sent to the bike" else "phone notifications · bike offline",
            Modifier.padding(top = 8.dp),
            age = "live",
        )
        PrivacyNote()
        SectionHeader("Send to the cluster")
        AlertSwitches(state, access, actions)
        SectionHeader("Sent to the cluster") { Kicker("newest first") }
        if (state.feed.isEmpty()) {
            EmptyState("Quiet roads.", message = "Calls and WhatsApp alerts appear here as they go to the cluster.")
        } else {
            Feed(state.feed)
        }
    }
}

@Composable
private fun AlertSwitches(state: AlertsScreenState, access: AccessStatus, actions: AlertsActions) {
    val s = state.settings
    SwitchRow("Phone calls", s.alertCalls, actions.onCalls, subtitle = "Caller name while ringing; missed-call count")
    SwitchRow("WhatsApp calls", s.alertWhatsAppCalls, actions.onWhatsAppCalls, subtitle = "Caller name while ringing")
    SwitchRow("WhatsApp messages", s.alertWhatsAppMessages, actions.onWhatsAppMessages, subtitle = "Sender name and count")
    SwitchRow("SMS", false, {}, subtitle = "Not in version 1", enabled = false)
    NotificationAccessRow(access.notificationAccess, actions.onAccess, divider = false)
}

/** Notification access with Grant (rust) or Review (outline). Shared with Settings. */
@Composable
fun NotificationAccessRow(granted: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier, divider: Boolean = true) {
    ListRow(
        "Notification access",
        modifier,
        subtitle = if (granted) "Granted · Maps, Phone, WhatsApp" else "Off · Maps turns and WhatsApp alerts cannot be read",
        divider = divider,
    ) {
        Row {
            AtlasButton(
                if (granted) "Review" else "Grant",
                onOpen,
                tone = if (granted) ButtonTone.GHOST else ButtonTone.RUST,
                small = true,
            )
        }
    }
}
