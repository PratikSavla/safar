package app.safar.ui.onboarding

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasIcon
import app.safar.ui.components.Hairline
import app.safar.ui.components.IconBox
import app.safar.ui.components.InkButton
import app.safar.ui.components.Note
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.Stamp
import app.safar.ui.components.StampTone
import app.safar.ui.theme.Atlas

private const val TAG = "SafarOnboarding"

/** One explained permission (onboarding.js `PERMS`). */
private data class PermInfo(val id: PermId, val icon: ImageVector, val title: String, val why: String)

private val PERMS by lazy {
    listOf(
        PermInfo(PermId.NEARBY, AtlasIcons.bt, "Nearby devices", "Find your bike and stay connected to it."),
        PermInfo(PermId.NOTIFICATIONS, AtlasIcons.bell, "Notifications", "A quiet note while Safar stays connected, and a low-fuel reminder."),
        PermInfo(
            PermId.NOTIFICATION_ACCESS, AtlasIcons.msg, "Google Maps and WhatsApp alerts",
            "Turns from Google Maps, and who’s calling on Phone and WhatsApp. Android calls this “Notification access”.",
        ),
        PermInfo(PermId.LOCATION, AtlasIcons.pin, "Location", "Your trip routes, your speed and where you parked."),
        PermInfo(PermId.PHONE, AtlasIcons.call, "Phone, call log and contacts", "Show who’s calling on your bike’s display."),
    )
}

/**
 * Live status per item, with real Android requests. Status refreshes when the rider comes back from Settings.
 * [askedState] holds the items asked during this run; the caller keeps it so it outlives this page.
 */
@Composable
internal fun PermissionsList(askedState: MutableState<Set<String>>) {
    val context = LocalContext.current
    var asked by askedState
    // The item whose request is on screen. It counts as asked only once Android answers: before the first
    // request shouldShowRequestPermissionRationale is false, which would read as "denied in settings".
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick += 1
        onPauseOrDispose { }
    }
    val answered = {
        pending?.let { asked = asked + it }
        pending = null
        tick += 1
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { answered() }
    val system = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { answered() }
    val statuses = remember(tick, asked) { PERMS.associate { it.id to statusOf(context, it.id, it.id.name in asked) } }
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        PERMS.forEach { p ->
            val status = statuses.getValue(p.id)
            PermRow(p, status) {
                pending = p.id.name
                when {
                    status == PermStatus.BLOCKED -> openSystem(context, system::launch, appDetails(context))
                    p.id == PermId.NOTIFICATION_ACCESS -> openSystem(context, system::launch, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    else -> request.launch(SafarPermissions.runtime(p.id).toTypedArray())
                }
            }
        }
        Hairline()
    }
    Note("For Google Maps and WhatsApp alerts, Android opens its settings. The rest ask right here. You can change any of these later.")
}

@Composable
private fun PermRow(p: PermInfo, status: PermStatus, onAction: () -> Unit) {
    val c = Atlas.colors
    val ok = status == PermStatus.ALLOWED
    Hairline()
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconBox(p.icon, tint = if (ok) c.moss else c.ink, outline = if (ok) c.moss else c.rule2)
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { stateDescription = permLabel(status) }) {
            BasicText(p.title, style = Atlas.type.uiStrong.copy(color = c.ink))
            BasicText(p.why, Modifier.padding(top = 2.dp, bottom = 4.dp), style = Atlas.type.uiSmall.copy(color = c.ink2))
            Stamp(permLabel(status), tone = toneOf(status), tilted = false)
        }
        val action = permAction(p.id, status)
        Box(Modifier.align(Alignment.CenterVertically)) {
            when {
                action == null -> AtlasIcon(AtlasIcons.check, Modifier.size(40.dp).padding(8.dp), contentDescription = "${p.title} allowed", tint = c.moss)
                p.id == PermId.NEARBY -> PrimaryButton(action, onAction, small = true)
                else -> InkButton(action, onAction, small = true)
            }
        }
    }
}

private fun toneOf(status: PermStatus): StampTone = when (status) {
    PermStatus.ALLOWED -> StampTone.MOSS
    PermStatus.NOT_ASKED -> StampTone.INK
    PermStatus.DENIED, PermStatus.BLOCKED -> StampTone.RUST
}

private fun statusOf(context: Context, id: PermId, asked: Boolean): PermStatus {
    val granted = SafarPermissions.isGranted(context, id)
    val activity = context.findActivity()
    val canAskAgain = id == PermId.NOTIFICATION_ACCESS || activity == null ||
        SafarPermissions.runtime(id).any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    return permStatus(granted, asked, canAskAgain)
}

private fun appDetails(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

/** Opens a Settings page; falls back to the main Settings screen if this phone has no such page. */
private fun openSystem(context: Context, launch: (Intent) -> Unit, intent: Intent) {
    try {
        launch(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No Settings page for ${intent.action}; opening Settings", e)
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e2: ActivityNotFoundException) {
            Log.e(TAG, "This phone has no Settings app to open", e2)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
