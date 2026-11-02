package app.safar.ui.trips

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.safar.core.AppContainer
import app.safar.data.ThemeMode
import app.safar.ui.components.AtlasPage
import app.safar.ui.components.AtlasToastHost
import app.safar.ui.components.AtlasToastState
import app.safar.ui.components.BackTopBar
import app.safar.ui.components.EmptyState
import app.safar.ui.components.Note
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.TextAction
import app.safar.ui.components.TopoTransform
import app.safar.ui.components.rememberToastState
import app.safar.ui.art.AtlasIcons
import app.safar.ui.format.LockText
import app.safar.ui.nav.Navigator
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.SafarTheme
import app.safar.ui.trips.preview.TripsPreviewData
import app.safar.ui.vm.NoticeToasts

/** Which sheet is open over the trip page. */
enum class TripSheet { NONE, RENAME, DELETE }

/** One journal entry: ink track, distance, time, speeds, odometer run and fuel bars used (trips.js `trip`). */
@Composable
fun TripScreen(navigator: Navigator, tripId: Long) {
    val vm: TripViewModel = viewModel(key = "trip-$tripId") {
        TripViewModel(tripId, AppContainer.trips, AppContainer.ride.state, AppContainer.bike.state)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val ride by vm.ride.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val toast = rememberToastState()
    NoticeToasts(vm.notices, toast)
    LaunchedEffect(deleted) { if (deleted) navigator.back() }
    TripContent(
        state = state,
        lockReason = LockText.whileMoving(ride.moving, ride.speedKmh),
        toast = toast,
        onBack = navigator::back,
        onRename = vm::rename,
        onDelete = vm::delete,
    )
}

/** Stateless trip page. [lockReason] is non-null while the phone moves (rename is locked). */
@Composable
fun TripContent(
    state: TripDetailState,
    lockReason: String?,
    toast: AtlasToastState,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var sheet by rememberSaveable { mutableStateOf(TripSheet.NONE) }
    val trip = (state as? TripDetailState.Ready)?.trip
    AtlasPage(
        topo = TopoTransform.Journal,
        overlay = {
            if (trip != null) {
                RenameSheet(sheet == TripSheet.RENAME, trip.title, lockReason, { sheet = TripSheet.NONE }) { onRename(it); sheet = TripSheet.NONE }
                DeleteSheet(sheet == TripSheet.DELETE && !trip.recording, trip, { sheet = TripSheet.NONE }) { onDelete(); sheet = TripSheet.NONE }
            }
            AtlasToastHost(toast, bottomPadding = 40.dp)
        },
    ) {
        when (state) {
            TripDetailState.Loading -> BackTopBar("Journal", "Opening…", onBack)
            TripDetailState.Missing -> {
                BackTopBar("Journal", "Not found", onBack)
                EmptyState("This ride isn’t in your journal.", message = "It may have been deleted.", actionLabel = "Back to the journal", onAction = onBack)
            }
            is TripDetailState.Ready -> TripBody(state.trip, onBack, onAsk = { sheet = it })
        }
    }
}

@Composable
private fun TripBody(trip: TripDetailUi, onBack: () -> Unit, onAsk: (TripSheet) -> Unit) {
    BackTopBar("Ride ${entryNumber(trip.number)} · ${trip.day}", trip.title, onBack)
    TrackFigure(trip)
    TripStats(trip)
    OdometerRun(trip)
    FuelRun(trip)
    Row(Modifier.fillMaxWidth().padding(top = 30.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton("Rename", { onAsk(TripSheet.RENAME) }, Modifier.weight(1f), icon = AtlasIcons.edit)
    }
    TextAction(
        "Delete this ride", { onAsk(TripSheet.DELETE) }, Modifier.padding(top = 10.dp),
        icon = AtlasIcons.cross, enabled = !trip.recording, color = Atlas.colors.rust,
    )
    if (trip.recording) Note(RECORDING_DELETE, Modifier.padding(start = 8.dp, top = 4.dp))
}

@Preview
@Composable
private fun TripPreview() = SafarTheme(ThemeMode.LIGHT) {
    TripContent(TripDetailState.Ready(TripsPreviewData.detail), null, rememberToastState(), {}, {}, {})
}

@Preview
@Composable
private fun TripDarkPreview() = SafarTheme(ThemeMode.DARK) {
    val noBike = TripsPreviewData.detail.copy(odoStart = null, odoEnd = null, fuel = null, distanceSource = "phone GPS")
    TripContent(TripDetailState.Ready(noBike), "Locked while moving (42 km/h). Stop to edit.", rememberToastState(), {}, {}, {})
}
