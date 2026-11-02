package app.safar.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.safar.ui.components.AtlasCard
import app.safar.ui.components.CardStyle
import app.safar.ui.components.Kicker
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.copy.Phrases
import app.safar.ui.onboarding.PermId
import app.safar.ui.onboarding.SafarPermissions
import app.safar.ui.theme.Atlas

/** Today's card for permissions that are off. [lines] are "Location · trip routes, …", one per item. */
@Immutable
data class PermissionsCardUi(val title: String, val lines: List<String>, val canDismiss: Boolean)

private fun permName(id: PermId): String = when (id) {
    PermId.NEARBY -> "Nearby devices"
    PermId.NOTIFICATIONS -> "Notifications"
    PermId.NOTIFICATION_ACCESS -> Phrases.ALERTS_ACCESS
    PermId.LOCATION -> "Location"
    PermId.PHONE -> "Phone and contacts"
}

private fun permUse(id: PermId): String = when (id) {
    PermId.NEARBY -> "finding your bike"
    PermId.NOTIFICATIONS -> "the connection note and low-fuel reminder"
    PermId.NOTIFICATION_ACCESS -> "turns and WhatsApp calls on your bike’s display"
    PermId.LOCATION -> "trip routes, your speed and where you parked"
    PermId.PHONE -> "who’s calling"
}

/**
 * The card, or null when nothing is off or the rider said "Not now" to everything that is. Without Nearby devices
 * Safar cannot reach the bike at all, so that card cannot be waved away.
 */
internal fun permissionsCardUi(missing: Set<PermId>, dismissed: Set<String>): PermissionsCardUi? {
    if (PermId.NEARBY in missing) {
        return PermissionsCardUi("Safar can’t reach your bike", listOf("Allow Nearby devices so Safar can find your bike."), canDismiss = false)
    }
    if (missing.all { it.name in dismissed }) return null
    val off = PermId.entries.filter { it in missing }
    val title = if (off.size == 1) "One permission is off" else "${off.size} permissions are off"
    return PermissionsCardUi(title, off.map { "${permName(it)} · ${permUse(it)}" }, canDismiss = true)
}

/** Permissions that are off, read again every time Today resumes (the rider may come back from Settings). */
@Composable
internal fun rememberMissingPermissions(): State<Set<PermId>> {
    val context = LocalContext.current
    val missing = remember { mutableStateOf(SafarPermissions.missing(context)) }
    LifecycleResumeEffect(context) {
        missing.value = SafarPermissions.missing(context)
        onPauseOrDispose { }
    }
    return missing
}

@Composable
internal fun PermissionsCard(ui: PermissionsCardUi, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val c = Atlas.colors
    AtlasCard(Modifier.padding(bottom = 18.dp), style = CardStyle.LINE) {
        Kicker("Permissions", color = c.rust)
        BasicText(ui.title, Modifier.padding(top = 4.dp, bottom = 6.dp), style = Atlas.type.uiStrong.copy(color = c.ink))
        ui.lines.forEach { BasicText(it, Modifier.padding(bottom = 2.dp), style = Atlas.type.uiSmall.copy(color = c.ink2)) }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Turn on", onOpen, small = true)
            if (ui.canDismiss) SecondaryButton("Not now", onDismiss, small = true)
        }
    }
}
