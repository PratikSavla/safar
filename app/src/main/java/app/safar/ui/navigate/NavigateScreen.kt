package app.safar.ui.navigate

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.safar.core.AppContainer
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasPage
import app.safar.ui.components.AtlasToastHost
import app.safar.ui.components.AtlasToastState
import app.safar.ui.components.Gap
import app.safar.ui.components.Masthead
import app.safar.ui.components.Note
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SectionHeader
import app.safar.ui.components.TextAction
import app.safar.ui.components.TopoTransform
import app.safar.ui.components.WarnLine
import app.safar.ui.components.rememberToastState
import app.safar.ui.nav.Navigator
import app.safar.ui.nav.Route
import app.safar.ui.settings.AccessStatus
import app.safar.ui.settings.SystemAccess
import app.safar.ui.settings.rememberAccessStatus
import app.safar.ui.theme.Atlas

/** Everything a Route page row or button can do. */
class NavigateActions(
    val onPick: (String) -> Unit = {},
    val onStart: () -> Unit = {},
    val onEnd: () -> Unit = {},
    val onGrant: () -> Unit = {},
    val onOther: () -> Unit = {},
    val onCancelSearch: () -> Unit = {},
    val onToggleEdit: () -> Unit = {},
    val onMove: (Long, Int) -> Unit = { _, _ -> },
    val onEditPlace: (Long) -> Unit = {},
    val onAddPlace: () -> Unit = {},
)

/**
 * Route tab (ux navigate.js): Google Maps status, destinations, "Somewhere else", and while navigating a
 * mirror of exactly what the cluster receives. No map drawing. [bottomBar] is the shell's tab strip.
 */
@Composable
fun NavigateScreen(navigator: Navigator, modifier: Modifier = Modifier, bottomBar: (@Composable () -> Unit)? = null) {
    val vm = viewModel { NavigateViewModel(AppContainer.navigation, AppContainer.places, AppContainer.parked, AppContainer.bike) }
    val state by vm.state.collectAsStateWithLifecycle()
    val access by rememberAccessStatus()
    val latestAccess by rememberUpdatedState(access)
    val context = LocalContext.current
    val toast = rememberToastState()
    LaunchedEffect(vm) { vm.messages.collect(toast::show) }
    val actions = remember(vm, navigator, context) {
        NavigateActions(
            onPick = vm::pick,
            onStart = vm::start,
            onEnd = { vm.end() },
            onGrant = { SystemAccess.openNotificationAccess(context) },
            onOther = {
                if (!latestAccess.notificationAccess) {
                    SystemAccess.openNotificationAccess(context)
                } else {
                    vm.searchOpened(SystemAccess.openMapsSearch(context))
                }
            },
            onCancelSearch = vm::cancelSearch,
            onToggleEdit = vm::toggleEditing,
            onMove = vm::move,
            onEditPlace = { navigator.go(Route.Place(placeId = it)) },
            onAddPlace = {
                vm.stopEditing()
                navigator.go(Route.Place())
            },
        )
    }
    NavigateContent(state, access, actions, toast, modifier, bottomBar)
}

@Composable
internal fun NavigateContent(
    state: NavigateState,
    access: AccessStatus,
    actions: NavigateActions,
    toast: AtlasToastState,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
) {
    val live = state.phase == RoutePhase.ACTIVE || state.phase == RoutePhase.WAITING_FIRST_UPDATE
    AtlasPage(
        modifier,
        topo = TopoTransform.Route,
        bottomBar = bottomBar,
        overlay = { AtlasToastHost(toast, Modifier.align(Alignment.BottomCenter), bottomPadding = if (bottomBar != null) 120.dp else 40.dp) },
    ) {
        val dest = state.nav.destinationLabel
        Masthead(
            "Route · via Google Maps",
            if (live) "En route" else "Where to",
            kickerAside = if (live) "live" else "idle",
            titleEm = if (live) (dest?.let { " to $it" } ?: "") else "?",
        )
        if (live && !state.bikeConnected) {
            WarnLine("Bike not connected. Turns will reach the cluster once it reconnects.", Modifier.padding(bottom = 14.dp))
        }
        when (state.phase) {
            RoutePhase.ACTIVE -> state.mirror?.let { ActiveRoute(it, state.bikeConnected, actions.onEnd) }
            RoutePhase.WAITING_FIRST_UPDATE -> WaitingFirstUpdate(actions.onEnd)
            RoutePhase.AWAITING_MAPS -> {
                ChecksCard(access, actions.onGrant)
                SectionHeader("Destinations")
                AwaitingMaps(actions.onCancelSearch)
            }
            RoutePhase.PICK -> PickBody(state, access, actions)
        }
        BasicText(
            "No map here on purpose. Maps is the map; the cluster is the display.",
            Modifier.fillMaxWidth().padding(top = 28.dp),
            style = Atlas.type.src.copy(color = Atlas.colors.ink3, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun ColumnScope.PickBody(state: NavigateState, access: AccessStatus, actions: NavigateActions) {
    ChecksCard(access, actions.onGrant)
    SectionHeader("Destinations") {
        TextAction(if (state.editing) "Done" else "Edit list", actions.onToggleEdit)
    }
    DestinationList(state, actions)
    val selected = state.selected
    if (!state.editing) {
        Gap(16.dp)
        PrimaryButton(
            "Start in Google Maps",
            actions.onStart,
            Modifier.fillMaxWidth().heightIn(min = 60.dp),
            icon = AtlasIcons.maps,
            enabled = selected != null && access.notificationAccess,
        )
        val hint = when {
            selected == null -> "Save a place, or search in Google Maps."
            !access.notificationAccess -> "Grant notification access first, so Safar can read the turns."
            else -> readableNavUri(selected.query)
        }
        BasicText(
            hint,
            Modifier.fillMaxWidth().padding(top = 10.dp),
            style = Atlas.type.src.copy(color = Atlas.colors.ink3, textAlign = TextAlign.Center),
        )
    }
    Gap(6.dp)
    TextAction("Save a place", actions.onAddPlace, Modifier.align(Alignment.CenterHorizontally), icon = AtlasIcons.plus)
    if (state.destinations.isEmpty()) {
        Note("Share a place from Google Maps to Safar, use where you stand, or type an address.", Modifier.padding(top = 8.dp))
    }
}
