package app.safar.ui.log

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.safar.BuildConfig
import app.safar.core.AppContainer
import app.safar.core.LogRecord
import app.safar.ui.components.AtlasBottomSheet
import app.safar.ui.components.AtlasLazyPage
import app.safar.ui.components.AtlasToastHost
import app.safar.ui.components.AtlasToastState
import app.safar.ui.components.BackTopBar
import app.safar.ui.components.EmptyState
import app.safar.ui.components.rememberToastState
import app.safar.ui.nav.Navigator
import app.safar.ui.theme.Atlas
import java.io.File

private const val TAG = "SafarLogScreen"

/** FileProvider authority for exported logs (declared by the BLE/log layer). */
const val LOG_FILE_AUTHORITY = "${BuildConfig.APPLICATION_ID}.files"

class LogActions(
    val onBack: () -> Unit = {},
    val onDebugLog: (Boolean) -> Unit = {},
    val onSession: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onDir: (DirFilter) -> Unit = {},
    val onType: (Char?) -> Unit = {},
    val onErrors: (Boolean) -> Unit = {},
    val onOpen: (Long) -> Unit = {},
    val onCloseDetail: () -> Unit = {},
    val onHideNames: (Boolean) -> Unit = {},
    val onShare: () -> Unit = {},
    val onCloseExport: () -> Unit = {},
)

/** Debug log (ux log.js), Developer mode: the live wire with filters, frame detail with marked bytes, session, export. */
@Composable
fun LogScreen(navigator: Navigator, modifier: Modifier = Modifier) {
    val vm = viewModel { LogViewModel(AppContainer.log, AppContainer.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toast = rememberToastState()
    val actions = LogActions(
        onBack = navigator::back,
        onDebugLog = vm::setDebugLog,
        onSession = vm::toggleSession,
        onExport = vm::openExport,
        onDir = vm::setDir,
        onType = vm::setType,
        onErrors = vm::setErrorsOnly,
        onOpen = vm::open,
        onCloseDetail = vm::closeDetail,
        onHideNames = vm::setHideNames,
        onShare = {
            val file = state.export?.file
            if (file != null && shareLog(context, file)) vm.closeExport() else toast.show("Could not open the share sheet")
        },
        onCloseExport = vm::closeExport,
    )
    LogContent(state, actions, toast, modifier)
}

@Composable
internal fun LogContent(state: LogScreenState, actions: LogActions, toast: AtlasToastState, modifier: Modifier = Modifier) {
    // Keep the last record and export on screen while their sheets slide away.
    val lastRecord = remember { arrayOfNulls<LogRecord>(1) }
    state.selected?.let { lastRecord[0] = it }
    val lastExport = remember { arrayOfNulls<ExportSheet>(1) }
    state.export?.let { lastExport[0] = it }
    AtlasLazyPage(
        modifier,
        overlay = {
            AtlasToastHost(toast, Modifier.align(Alignment.BottomCenter), bottomPadding = 40.dp)
            AtlasBottomSheet(state.selected != null, actions.onCloseDetail) {
                lastRecord[0]?.let { RecordDetail(it, actions.onCloseDetail) }
            }
            AtlasBottomSheet(state.export != null, actions.onCloseExport, title = "Share log") {
                lastExport[0]?.let { ExportBody(it, actions.onHideNames, actions.onShare, actions.onCloseExport) }
            }
        },
    ) {
        item(key = "head", contentType = "head") { LogHead(state, actions) }
        if (state.rows.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                EmptyState("Nothing logged yet.", Modifier.padding(top = 8.dp), message = "Connect the bike, or loosen the filters.")
            }
        } else {
            items(state.rows, key = { it.id }, contentType = { "row" }) { LogRowItem(it, actions.onOpen) }
        }
    }
}

@Composable
private fun LogHead(state: LogScreenState, actions: LogActions) {
    Column {
        BackTopBar("Developer · debug log", "Debug", actions.onBack, titleEm = " log.")
        if (!state.debugLogOn) LogOffRow(actions.onDebugLog)
        SessionBar(state.sessionActive, actions.onSession, actions.onExport)
        LogFilters(state.filter, actions.onDir, actions.onType, actions.onErrors)
        val capped = if (state.matching > MAX_LOG_ROWS) " · first $MAX_LOG_ROWS" else ""
        BasicText(
            "${state.total} in buffer · ${state.matching} match$capped",
            Modifier.padding(top = 10.dp, bottom = 4.dp),
            style = Atlas.type.src.copy(color = Atlas.colors.ink3),
        )
    }
}

/** Hands the exported file to the Android share sheet through the app's FileProvider. */
fun shareLog(context: Context, file: File): Boolean {
    val uri = try {
        FileProvider.getUriForFile(context, LOG_FILE_AUTHORITY, file)
    } catch (e: IllegalArgumentException) {
        Log.e(TAG, "No FileProvider path for ${file.name} under $LOG_FILE_AUTHORITY", e)
        return false
    }
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, file.name)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        context.startActivity(Intent.createChooser(send, "Share log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "No app can receive the log ${file.name}", e)
        false
    }
}
