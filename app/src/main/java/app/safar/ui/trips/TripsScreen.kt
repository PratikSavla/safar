package app.safar.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.safar.core.AppContainer
import app.safar.data.ThemeMode
import app.safar.ui.components.AtlasPage
import app.safar.ui.components.EmptyState
import app.safar.ui.components.Gap
import app.safar.ui.components.Kicker
import app.safar.ui.components.Masthead
import app.safar.ui.components.SectionHeader
import app.safar.ui.components.TextAction
import app.safar.ui.components.TopoTransform
import app.safar.ui.nav.Navigator
import app.safar.ui.nav.Route
import app.safar.ui.theme.SafarTheme
import app.safar.ui.trips.preview.TripsPreviewData

private const val PAGE = 30

/** Journal tab: rides as postcards with their ink tracks, week and 30-day totals (trips.js). */
@Composable
fun TripsScreen(navigator: Navigator, bottomBar: (@Composable () -> Unit)? = null) {
    val vm: TripsViewModel = viewModel {
        TripsViewModel(AppContainer.trips, AppContainer.ride.state, AppContainer.bike.state)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    TripsContent(
        state = state,
        onOpen = { navigator.go(Route.Trip(it)) },
        thumbFor = { id ->
            val flow = remember(id) { vm.thumb(id) }
            flow.collectAsStateWithLifecycle(TrackThumb.Empty).value
        },
        bottomBar = bottomBar,
    )
}

/** Stateless journal page; [thumbFor] loads one postcard's thinned GPS track. */
@Composable
fun TripsContent(
    state: TripsUiState,
    onOpen: (Long) -> Unit,
    thumbFor: @Composable (Long) -> TrackThumb,
    bottomBar: (@Composable () -> Unit)? = null,
) {
    var shown by rememberSaveable { mutableIntStateOf(PAGE) }
    AtlasPage(topo = TopoTransform.Journal, bottomBar = bottomBar) {
        Masthead(
            kicker = "Journal · ${state.bikeName ?: "your bike"}",
            title = "Roads",
            kickerAside = "${state.rows.size} entries",
            titleEm = " already ridden.",
        )
        TotalsRow(state.week, state.month)
        state.live?.let { LiveEntry(it, Modifier.padding(top = 14.dp), onOpen) }
        SectionHeader("Entries") { Kicker("newest first") }
        if (state.rows.isEmpty() && !state.loading) {
            EmptyState("No rides yet.", message = "Connect the bike and ride; trips land here by themselves.")
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            state.rows.take(shown).forEachIndexed { i, row ->
                Postcard(row, tilt = if (i % 2 == 1) 0.5f else -0.6f, thumb = thumbFor(row.id), onClick = { onOpen(row.id) })
            }
        }
        if (state.rows.size > shown) {
            TextAction("Show older entries", { shown += PAGE }, Modifier.padding(top = 12.dp))
        }
        Footnote("Tracks come from phone GPS. Distances from the bike’s odometer at connect and disconnect.")
        Gap(72.dp) // clear of the shell's ticket-stub tabs
    }
}

@Preview
@Composable
private fun TripsPreview() = SafarTheme(ThemeMode.LIGHT) {
    TripsContent(TripsPreviewData.list, {}, { trackThumb(TripsPreviewData.track) })
}

@Preview
@Composable
private fun TripsDarkPreview() = SafarTheme(ThemeMode.DARK) {
    TripsContent(TripsPreviewData.list.copy(live = null), {}, { trackThumb(TripsPreviewData.track) })
}

@Preview
@Composable
private fun TripsEmptyPreview() = SafarTheme(ThemeMode.LIGHT) {
    TripsContent(TripsUiState(loading = false), {}, { TrackThumb.Empty })
}
