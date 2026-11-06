package app.safar.ui.documents

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.safar.core.AppContainer
import app.safar.data.DocKind
import app.safar.data.DocumentFiles
import app.safar.data.ThemeMode
import app.safar.data.label
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasPage
import app.safar.ui.components.AtlasTextField
import app.safar.ui.components.AtlasToastHost
import app.safar.ui.components.AtlasToastState
import app.safar.ui.components.BackTopBar
import app.safar.ui.components.EmptyState
import app.safar.ui.components.LockLine
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.SectionHeader
import app.safar.ui.components.TextAction
import app.safar.ui.components.TopoTransform
import app.safar.ui.components.rememberToastState
import app.safar.ui.nav.Navigator
import app.safar.ui.shell.LocalShellUi
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.SafarTheme
import app.safar.ui.vm.NoticeToasts
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.time.LocalDate

/** Form actions, bundled so the stateless content stays readable. */
class PaperActions(
    val onKind: (DocKind) -> Unit = {},
    val onTitle: (String) -> Unit = {},
    val onNote: (String) -> Unit = {},
    val onChoose: () -> Unit = {},
    val onShow: () -> Unit = {},
    val onPickExpiry: () -> Unit = {},
    val onClearExpiry: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/** Add a paper, or edit / replace / delete one. [docId] null = new. */
@Composable
fun DocumentScreen(navigator: Navigator, docId: Long?) {
    val files = AppContainer.documentFiles
    val vm: DocumentViewModel = viewModel(key = "paper-${docId ?: "new"}") {
        DocumentViewModel(
            docId, AppContainer.documents, files, AppContainer.ride.state, Dispatchers.IO, AppContainer.appScope,
            today = { LocalDate.now().toEpochDay() },
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shell = LocalShellUi.current
    val toast = rememberToastState()
    NoticeToasts(vm.notices, toast)
    LaunchedEffect(done) {
        val message = done ?: return@LaunchedEffect
        navigator.back()
        shell.toast(message)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(pickedSource(context, uri))
    }
    val actions = PaperActions(
        onKind = vm::setKind,
        onTitle = vm::setTitle,
        onNote = vm::setNote,
        onChoose = { picker.launch(DocumentFiles.PICKER_TYPES) },
        onShow = {
            val f = state.file ?: return@PaperActions
            if (!openPaper(context, files.file(f.fileName), f.mimeType)) toast.show("No app on this phone can show this file")
        },
        onPickExpiry = { showExpiryPicker(context, state.form.expiry) { vm.setExpiry(it) } },
        onClearExpiry = { vm.setExpiry(null) },
        onSave = vm::save,
        onDelete = vm::delete,
        onClose = navigator::back,
    )
    DocumentContent(state, files.dir, toast, actions)
}

/** Stateless form page. */
@Composable
fun DocumentContent(state: PaperUiState, dir: File, toast: AtlasToastState, actions: PaperActions) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    val title = state.form.title.trim().ifEmpty { state.form.kind.label() }
    AtlasPage(
        topo = TopoTransform.Quiet,
        overlay = {
            if (!state.isNew) DeletePaperSheet(confirm, title, { confirm = false }) { confirm = false; actions.onDelete() }
            AtlasToastHost(toast, bottomPadding = 40.dp)
        },
    ) {
        BackTopBar(if (state.isNew) "Add a paper" else "Edit paper", if (state.isNew) "A new paper" else title, actions.onClose, titleEm = if (state.isNew) ", kept close." else null)
        when {
            state.loading -> Unit
            state.missing -> EmptyState("This paper is gone.", message = "It may have been deleted.", actionLabel = "Back", onAction = actions.onClose)
            else -> FormBody(state, dir, actions, onAskDelete = { confirm = true })
        }
    }
}

@Composable
private fun FormBody(state: PaperUiState, dir: File, actions: PaperActions, onAskDelete: () -> Unit) {
    val open = state.locked == null
    state.locked?.let { LockLine(it, Modifier.padding(bottom = 8.dp)) }
    SectionHeader("File")
    FileBlock(state.file, dir, state.reading, open, actions.onChoose, actions.onShow)
    SectionHeader("What it is")
    KindChips(state.form.kind) { if (open) actions.onKind(it) }
    AtlasTextField(
        state.form.title, actions.onTitle, "Name", Modifier.padding(top = 14.dp),
        lockedReason = state.locked, placeholder = state.form.kind.label(), maxLength = PAPER_TITLE_MAX,
    )
    SectionHeader("Expiry")
    ExpiryBlock(state.form.kind, state.form.expiry, open, actions.onPickExpiry, actions.onClearExpiry)
    AtlasTextField(
        state.form.note, actions.onNote, "Note · optional", Modifier.padding(top = 14.dp),
        lockedReason = state.locked, placeholder = "e.g. policy number, agent", maxLength = 120,
    )
    Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton("Cancel", actions.onClose, Modifier.weight(1f))
        PrimaryButton(if (state.saving) "Saving…" else "Save", actions.onSave, Modifier.weight(1f), icon = AtlasIcons.check, enabled = state.canSave)
    }
    if (!state.isNew) {
        TextAction("Delete this paper", onAskDelete, Modifier.padding(top = 10.dp), icon = AtlasIcons.cross, color = Atlas.colors.rust, enabled = open)
    }
}

@Preview
@Composable
private fun PaperNewPreview() = SafarTheme(ThemeMode.LIGHT) {
    DocumentContent(PaperUiState(loading = false, form = PaperForm(DocKind.PUC)), File("/nonexistent"), rememberToastState(), PaperActions())
}

@Preview
@Composable
private fun PaperEditPreview() = SafarTheme(ThemeMode.DARK) {
    val state = PaperUiState(
        loading = false, isNew = false,
        form = PaperForm(DocKind.INSURANCE, "Insurance", LocalDate.of(2027, 3, 12).toEpochDay(), "Policy 1234"),
        file = FormFile("a.pdf", "application/pdf", 640_000, "policy.pdf", pending = false),
    )
    DocumentContent(state, File("/nonexistent"), rememberToastState(), PaperActions())
}
