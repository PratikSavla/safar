package app.safar.ui.alerts

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.safar.core.ActiveCall
import app.safar.core.AlertCounter
import app.safar.core.AlertSource
import app.safar.core.AlertsState
import app.safar.core.FeedItem
import app.safar.core.FeedKind
import app.safar.data.ThemeMode
import app.safar.ui.settings.AccessStatus
import app.safar.ui.theme.SafarTheme

// Synthetic preview data only.
private const val NOW = 1_760_000_000_000L

private val busy = AlertsState(
    call = ActiveCall("Alex", AlertSource.WHATSAPP, answered = false, sinceMillis = NOW),
    missed = AlertCounter(2, "Rider", AlertSource.PHONE),
    messages = AlertCounter(5, "Alex", AlertSource.WHATSAPP),
    feed = listOf(
        FeedItem(NOW - 60_000, FeedKind.MISSED_CALL, "Rider", AlertSource.PHONE, 2),
        FeedItem(NOW - 30_000, FeedKind.MESSAGE, "Alex", AlertSource.WHATSAPP, 5),
        FeedItem(NOW - 5_000, FeedKind.CALL_RINGING, "Alex", AlertSource.WHATSAPP),
    ),
)

@Composable
private fun Frame(alerts: AlertsState, dark: Boolean) = SafarTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
    AlertsContent(AlertsScreenState(alerts, feedRows(alerts.feed), bikeConnected = true), AccessStatus(notificationAccess = !dark), AlertsActions())
}

@Preview(heightDp = 1700)
@Composable
private fun AlertsBusyPreview() = Frame(busy, dark = false)

@Preview(heightDp = 1500, fontScale = 1.4f)
@Composable
private fun AlertsQuietPreview() = Frame(AlertsState(), dark = true)
