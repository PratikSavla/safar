package app.safar.ui.documents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.safar.core.AppContainer
import app.safar.data.ThemeMode
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasIcon
import app.safar.ui.components.AtlasPage
import app.safar.ui.components.BackTopBar
import app.safar.ui.components.EmptyState
import app.safar.ui.components.Hairline
import app.safar.ui.components.Note
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.Stamp
import app.safar.ui.components.StampTone
import app.safar.ui.components.TopoTransform
import app.safar.ui.nav.Navigator
import app.safar.ui.nav.Route
import app.safar.ui.shell.LocalShellUi
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.SafarTheme
import java.io.File

/** What the papers list can do, bundled so the stateless content stays readable. */
class PapersActions(
    val onOpen: (PaperRow) -> Unit = {},
    val onEdit: (Long) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Bike papers: RC, insurance, PUC, licence and bills, with their expiry. Tapping a paper shows it. */
@Composable
fun DocumentsScreen(navigator: Navigator) {
    val vm = viewModel { DocumentsViewModel(AppContainer.documents.documents) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shell = LocalShellUi.current
    val files = AppContainer.documentFiles
    val actions = PapersActions(
        onOpen = { row ->
            if (!openPaper(context, files.file(row.fileName), row.mimeType)) shell.toast("No app on this phone can show this file")
        },
        onEdit = { navigator.go(Route.Document(it)) },
        onAdd = { navigator.go(Route.Document()) },
        onBack = navigator::back,
    )
    DocumentsContent(rows, files.dir, actions)
}

/** Stateless list. [rows] null while loading. [dir] holds the files, for thumbnails. */
@Composable
fun DocumentsContent(rows: List<PaperRow>?, dir: File, actions: PapersActions) {
    AtlasPage(topo = TopoTransform.Quiet) {
        BackTopBar("Papers", "Your papers", actions.onBack, titleEm = ", in one place.")
        Note(PAPERS_LOCAL_NOTE, color = Atlas.colors.ink3)
        when {
            rows == null -> Unit
            rows.isEmpty() -> EmptyState(
                "No papers yet",
                Modifier.padding(top = 18.dp),
                message = "Keep your RC, insurance, PUC and licence here, ready when someone asks. Add a PDF or a photo.",
                actionLabel = "Add a paper",
                onAction = actions.onAdd,
            )
            else -> {
                PrimaryButton("Add a paper", actions.onAdd, Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), icon = AtlasIcons.plus)
                rows.forEachIndexed { i, row -> PaperListRow(row, File(dir, row.fileName), actions, divider = i < rows.lastIndex) }
            }
        }
    }
}

@Composable
private fun PaperListRow(row: PaperRow, file: File, actions: PapersActions, divider: Boolean) {
    val c = Atlas.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Atlas.dimens.rowHeight).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.weight(1f).clickable(onClickLabel = "Show ${row.title}", role = Role.Button) { actions.onOpen(row) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PaperThumb(file, row.mimeType, 52.dp)
                Column(Modifier.weight(1f)) {
                    BasicText(row.title, style = Atlas.type.uiStrong.copy(color = c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(row.subtitle, Modifier.padding(top = 2.dp), style = Atlas.type.uiSmall.copy(color = c.ink3))
                }
                row.stamp?.let { Stamp(it, tone = row.tone) }
            }
            AtlasIcon(
                AtlasIcons.edit,
                Modifier
                    .clickable(onClickLabel = "Edit ${row.title}", role = Role.Button) { actions.onEdit(row.id) }
                    .padding(12.dp)
                    .size(20.dp),
                contentDescription = "Edit ${row.title}",
                tint = c.ink2,
                size = 20.dp,
            )
        }
        if (divider) Hairline()
    }
}

@Preview
@Composable
private fun PapersPreview() = SafarTheme(ThemeMode.LIGHT) {
    val rows = listOf(
        PaperRow(1, "PUC", "valid till 12 Nov 2026 · PDF", "6 days left", StampTone.AMBER, "a.pdf", "application/pdf"),
        PaperRow(2, "Registration (RC)", "RC · Photo", null, StampTone.AMBER, "b.jpg", "image/jpeg"),
    )
    DocumentsContent(rows, File("/nonexistent"), PapersActions())
}

@Preview
@Composable
private fun PapersEmptyPreview() = SafarTheme(ThemeMode.DARK) {
    DocumentsContent(emptyList(), File("/nonexistent"), PapersActions())
}
