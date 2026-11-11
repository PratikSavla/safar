package app.safar.ui.trips

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.safar.core.AppContainer
import app.safar.data.ThemeMode
import app.safar.ui.components.AtlasLazyPage
import app.safar.ui.components.AtlasToastHost
import app.safar.ui.components.EmptyState
import app.safar.ui.components.Gap
import app.safar.ui.components.Kicker
import app.safar.ui.components.Masthead
import app.safar.ui.components.SectionHeader
import app.safar.ui.components.TextAction
import app.safar.ui.components.TopoTransform
import app.safar.ui.components.rememberToastState
import app.safar.ui.nav.Navigator
import app.safar.ui.nav.Route
import app.safar.ui.theme.SafarTheme
import app.safar.ui.trips.preview.TripsPreviewData
import app.safar.ui.vm.NoticeToasts

private const val PAGE = 30

/** Journal tab: rides as postcards with their ink tracks, week and 30-day totals (trips.js). */
@Composable
fun TripsScreen(navigator: Navigator, bottomBar: (@Composable () -> Unit)? = null) {
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    val vm: TripsViewModel = viewModel {
        TripsViewModel(AppContainer.trips, AppContainer.ride.state, AppContainer.bike.state, is24h = is24h)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val toast = rememberToastState()
    NoticeToasts(vm.notices, toast)
    Box(Modifier.fillMaxSize()) {
        TripsContent(
            state = state,
            selection = selection,
            canUndo = canUndo,
            onOpen = { navigator.go(Route.Trip(it)) },
            onToggleSelect = vm::toggleSelect,
            onMerge = vm::merge,
            onClearSelect = vm::clearSelection,
            onUndoMerge = vm::undoMerge,
            thumbFor = { id ->
                val flow = remember(id) { vm.thumb(id) }
                flow.collectAsStateWithLifecycle(TrackThumb.Empty).value
            },
            bottomBar = bottomBar,
        )
        AtlasToastHost(toast, bottomPadding = 40.dp)
    }
}

/** Stateless journal page; [thumbFor] loads one postcard's thinned GPS track. */
@Composable
fun TripsContent(
    state: TripsUiState,
    selection: Set<Long> = emptySet(),
    canUndo: Boolean = false,
    onOpen: (Long) -> Unit,
    onToggleSelect: (Long) -> Unit = {},
    onMerge: () -> Unit = {},
    onClearSelect: () -> Unit = {},
    onUndoMerge: () -> Unit = {},
    thumbFor: @Composable (Long) -> TrackThumb,
    bottomBar: (@Composable () -> Unit)? = null,
) {
    var shown by rememberSaveable { mutableIntStateOf(PAGE) }
    val inSelection = selection.isNotEmpty()
    AtlasLazyPage(
        topo = TopoTransform.Journal,
        bottomBar = bottomBar,
        overlay = {
            if (inSelection) {
                Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 24.dp)) {
                    MergeBar(count = selection.size, enabled = selection.size >= 2, onMerge = onMerge, onCancel = onClearSelect)
                }
            } else if (canUndo) {
                Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 24.dp)) {
                    UndoBar(onUndo = onUndoMerge)
                }
            }
        },
    ) {
        item(key = "head", contentType = "head") { TripsHead(state, onOpen) }
        itemsIndexed(state.rows.take(shown), key = { _, row -> row.id }, contentType = { _, _ -> "postcard" }) { i, row ->
            Box(Modifier.padding(top = if (i > 0) 14.dp else 0.dp)) {
                Postcard(
                    row = row,
                    tilt = if (i % 2 == 1) 0.5f else -0.6f,
                    thumb = thumbFor(row.id),
                    selected = row.id in selection,
                    onLongPress = { onToggleSelect(row.id) },
                    onClick = { if (inSelection) onToggleSelect(row.id) else onOpen(row.id) },
                )
            }
        }
        item(key = "foot", contentType = "foot") {
            Column {
                if (state.rows.size > shown) {
                    TextAction("Show older rides", { shown += PAGE }, Modifier.padding(top = 12.dp))
                }
                Footnote("Routes come from your phone’s GPS. Distances come from your bike’s odometer.")
                Gap(72.dp) // clear of the shell's ticket-stub tabs
            }
        }
    }
}

@Composable
private fun TripsHead(state: TripsUiState, onOpen: (Long) -> Unit) {
    Column {
        Masthead(
            kicker = "Trips · ${state.bikeName ?: "your bike"}",
            title = "Roads",
            kickerAside = ridesText(state.rows.size),
            titleEm = " already ridden.",
        )
        TotalsRow(state.week, state.month)
        state.live?.let { LiveEntry(it, Modifier.padding(top = 14.dp), onOpen) }
        SectionHeader("Rides") { Kicker("newest first") }
        if (state.rows.isEmpty() && !state.loading) {
            if (state.live != null) {
                EmptyState("Recording your first ride.", message = "It shows here when you stop.")
            } else {
                EmptyState("No rides yet.", message = "Connect your bike and ride. Each ride is saved here on its own.")
            }
        }
    }
}

@Preview
@Composable
private fun TripsPreview() = SafarTheme(ThemeMode.LIGHT) {
    TripsContent(TripsPreviewData.list, onOpen = {}, thumbFor = { trackThumb(TripsPreviewData.track) })
}

@Preview
@Composable
private fun TripsDarkPreview() = SafarTheme(ThemeMode.DARK) {
    TripsContent(TripsPreviewData.list.copy(live = null), onOpen = {}, thumbFor = { trackThumb(TripsPreviewData.track) })
}

@Preview
@Composable
private fun TripsEmptyPreview() = SafarTheme(ThemeMode.LIGHT) {
    TripsContent(TripsUiState(loading = false), onOpen = {}, thumbFor = { TrackThumb.Empty })
}
