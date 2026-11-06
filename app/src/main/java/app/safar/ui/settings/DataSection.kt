package app.safar.ui.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.safar.BuildConfig
import app.safar.core.AppContainer
import app.safar.service.DocumentReminders
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.LinkRow
import app.safar.ui.components.ListRow
import app.safar.ui.components.LockLine
import app.safar.ui.components.Note
import app.safar.ui.nav.Navigator
import app.safar.ui.nav.Route
import app.safar.ui.shell.LocalShellUi
import app.safar.ui.theme.Atlas
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything "Your data" needs on the page and in its sheets. */
class DataControls(
    val state: DataUiState,
    val askWipe: Boolean,
    val onExport: () -> Unit,
    val onImport: () -> Unit,
    val onAskWipe: (Boolean) -> Unit,
    val onWipe: () -> Unit,
    val onConfirmImport: () -> Unit,
    val onCancelImport: () -> Unit,
)

/** The ViewModel, the file pickers and the toasts for Settings → Your data. */
@Composable
fun rememberDataControls(navigator: Navigator): DataControls {
    val context = LocalContext.current
    val shell = LocalShellUi.current
    val vm = viewModel {
        val app = context.applicationContext
        DataViewModel(
            AppContainer.userData, AppContainer.ride.state, BuildConfig.VERSION_NAME,
            forgetBike = { AppContainer.bike.forgetBike() },
            afterWipe = { clearOutsideData(app) },
            today = { LocalDate.now().toEpochDay() },
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val wiped by vm.wiped.collectAsStateWithLifecycle()
    var askWipe by rememberSaveable { mutableStateOf(false) }
    val notice by vm.notices.current.collectAsStateWithLifecycle()
    LaunchedEffect(notice) {
        val n = notice ?: return@LaunchedEffect
        shell.toast(n.second)
        vm.notices.consume()
    }
    LaunchedEffect(wiped) { if (wiped) navigator.resetTo(Route.Onboarding) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.export { context.contentResolver.openOutputStream(uri, "wt") }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.read { context.contentResolver.openInputStream(uri) }
    }
    return DataControls(
        state = state,
        askWipe = askWipe,
        onExport = { create.launch("safar-backup-${LocalDate.now()}.zip") },
        onImport = { open.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) },
        onAskWipe = { askWipe = it },
        onWipe = { askWipe = false; vm.wipe() },
        onConfirmImport = vm::confirmImport,
        onCancelImport = vm::cancelImport,
    )
}

/** Debug log and its files, reminders on screen, missed-call counts: what [app.safar.data.backup.UserData] doesn't hold. */
private suspend fun clearOutsideData(app: Context) {
    AppContainer.log.clear()
    AppContainer.alerts.clearMissedCalls()
    AppContainer.alerts.clearMessages()
    DocumentReminders.cancelAll(app)
    withContext(Dispatchers.IO) {
        File(app.filesDir, "logs").deleteRecursively()
        File(app.cacheDir, "exports").deleteRecursively()
    }
}

/** Settings → Your data: papers, export, import, delete everything. */
@Composable
internal fun DataRows(c: DataControls, onPapers: () -> Unit) {
    val busy = c.state.work != DataWork.NONE
    val open = !busy && c.state.locked == null
    LinkRow("Papers", AtlasIcons.papers, onPapers, subtitle = "RC, insurance, PUC and licence, with expiry reminders")
    Note(DATA_NOTE, Modifier.padding(top = 12.dp, bottom = 4.dp), color = Atlas.colors.ink3)
    c.state.locked?.let { LockLine(it) }
    ListRow(
        "Export everything",
        subtitle = if (c.state.work == DataWork.EXPORTING) "Exporting…" else "Rides, fuel, service, places, papers and settings in one .zip file",
        icon = AtlasIcons.export, enabled = open, onClick = c.onExport,
    )
    ListRow(
        "Import from a file",
        subtitle = when (c.state.work) {
            DataWork.READING -> "Reading the file…"
            DataWork.RESTORING -> "Importing…"
            else -> "Replace what’s here with a Safar export"
        },
        icon = AtlasIcons.download, enabled = open, onClick = c.onImport,
    )
    ListRow(
        "Delete everything",
        subtitle = if (c.state.work == DataWork.WIPING) "Deleting…" else "Start fresh: removes all your data from this phone",
        icon = AtlasIcons.cross, enabled = open, onClick = { c.onAskWipe(true) }, divider = false,
    )
}

/** The sheets: confirm an import, confirm delete everything. Place in the page's overlay. */
@Composable
internal fun DataSheets(c: DataControls) {
    ImportSheet(c.state.offer, c.onExport, c.onConfirmImport, c.onCancelImport)
    WipeSheet(c.askWipe, c.onExport, c.onWipe) { c.onAskWipe(false) }
}

private const val DATA_NOTE =
    "Everything Safar keeps is on this phone. Export it to one file to keep a copy, or to move to a new phone."
