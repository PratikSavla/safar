package app.safar.ui.navigate

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.safar.core.AppContainer
import app.safar.data.PlaceKind
import app.safar.ui.components.AtlasPage
import app.safar.ui.components.AtlasToastHost
import app.safar.ui.components.AtlasToastState
import app.safar.ui.components.BackTopBar
import app.safar.ui.components.Note
import app.safar.ui.components.TopoTransform
import app.safar.ui.components.rememberToastState
import app.safar.ui.nav.Navigator
import app.safar.ui.settings.SystemAccess

class PlaceActions(
    val onBack: () -> Unit = {},
    val onMaps: () -> Unit = {},
    val onHere: () -> Unit = {},
    val onTyped: () -> Unit = {},
    val onChangeSource: () -> Unit = {},
    val onLabel: (String) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onKind: (PlaceKind) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

/**
 * Save a place / Edit place (ux place.js). [placeId] null = new; [sharedText] = text Google Maps shared to
 * Safar (parsed with `parseMapsShare`). Typing is locked while the bike moves.
 */
@Composable
fun PlaceScreen(navigator: Navigator, placeId: Long?, sharedText: String?, modifier: Modifier = Modifier) {
    val vm = viewModel(key = "place:$placeId:${sharedText?.hashCode()}") {
        PlaceViewModel(placeId, sharedText, AppContainer.places, AppContainer.ride)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toast = rememberToastState()
    LaunchedEffect(vm) {
        vm.messages.collect { e -> if (e is PlaceEvent.Done) navigator.back() else toast.show(e.message) }
    }
    val readLocation = {
        val fix = LastLocation.read(context)
        if (fix != null) vm.useLocation(fix.latitude, fix.longitude, fix.accuracy) else vm.locationUnavailable("No recent location. Open Google Maps once, then try again.")
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) readLocation() else vm.locationUnavailable("Location is off for Safar. Share from Maps or type an address.")
    }
    val actions = PlaceActions(
        onBack = navigator::back,
        onMaps = {
            if (SystemAccess.openMapsApp(context)) toast.show("In Maps: open a place → Share → Safar") else toast.show("Google Maps isn’t installed")
        },
        onHere = { if (LastLocation.permitted(context)) readLocation() else permission.launch(LastLocation.PERMISSIONS) },
        onTyped = vm::chooseTyped,
        onChangeSource = vm::changeSource,
        onLabel = vm::setLabel,
        onQuery = vm::setQuery,
        onKind = vm::setKind,
        onSave = vm::save,
        onDelete = vm::delete,
    )
    PlaceContent(state, actions, toast, modifier)
}

@Composable
internal fun PlaceContent(state: PlaceState, actions: PlaceActions, toast: AtlasToastState, modifier: Modifier = Modifier) {
    AtlasPage(
        modifier,
        topo = TopoTransform.Route,
        overlay = { AtlasToastHost(toast, Modifier.align(Alignment.BottomCenter), bottomPadding = 40.dp) },
    ) {
        BackTopBar("Route · your places", if (state.editing) "Edit place" else "Save a place", actions.onBack)
        val draft = state.draft
        when {
            state.loading -> Note("Opening the place…")
            draft == null -> SourceChooser(state.lockReason, actions)
            else -> PlaceForm(draft, state, actions)
        }
    }
}
