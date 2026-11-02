package app.safar.ui.parked

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.safar.core.AppContainer
import app.safar.data.ParkedSpot
import app.safar.data.ThemeMode
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasPage
import app.safar.ui.components.AtlasToastHost
import app.safar.ui.components.AtlasToastState
import app.safar.ui.components.BackTopBar
import app.safar.ui.components.EmptyState
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.TextAction
import app.safar.ui.components.TopoTransform
import app.safar.ui.components.rememberToastState
import app.safar.ui.nav.Navigator
import app.safar.ui.parked.preview.ParkedPreviewData
import app.safar.ui.theme.SafarTheme
import app.safar.ui.vm.NoticeToasts

private const val TAG = "ParkedScreen"

/** Parked page actions. */
data class ParkedActions(
    val onBack: () -> Unit = {},
    val onMaps: () -> Unit = {},
    val onShare: (ParkedSpot) -> Unit = {},
    val onParkHere: () -> Unit = {},
    val onAllowLocation: () -> Unit = {},
)

/** Where the bike was left (parked.js): place, time since, distance and a pointer to it, the walk back in Maps. */
@Composable
fun ParkedScreen(navigator: Navigator) {
    val app = LocalContext.current.applicationContext
    val context = LocalContext.current
    val vm: ParkedViewModel = viewModel {
        ParkedViewModel(
            AppContainer.parked, { AppContainer.navigation }, { phoneLocation(app) }, { compassHeading(app) }, ::magneticDeclination,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = rememberToastState()
    NoticeToasts(vm.notices, toast)
    LifecycleResumeEffect(Unit) {
        vm.setLocationAllowed(hasLocationPermission(app))
        onPauseOrDispose { }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.setLocationAllowed(hasLocationPermission(app))
    }
    val actions = ParkedActions(
        onBack = navigator::back,
        onMaps = vm::navigateBack,
        onShare = { spot -> if (!share(context, spot)) toast.show("No app to share with") },
        onParkHere = vm::parkHere,
        onAllowLocation = { ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
    )
    ParkedContent(state, toast, actions)
}

/** Opens the share sheet with a Google Maps link. False when no app can take it. */
private fun share(context: Context, spot: ParkedSpot): Boolean {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "My bike is parked here")
        .putExtra(Intent.EXTRA_TEXT, shareText(spot))
    return try {
        context.startActivity(Intent.createChooser(send, "Share the parked spot").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "no activity for the parked-spot share", e)
        false
    }
}

/** Stateless parked page. */
@Composable
fun ParkedContent(state: ParkedUiState, toast: AtlasToastState, actions: ParkedActions) {
    AtlasPage(topo = TopoTransform.Quiet, overlay = { AtlasToastHost(toast, bottomPadding = 40.dp) }) {
        BackTopBar("Parked · saved at disconnect", "Where you", actions.onBack, titleEm = " left it.")
        val spot = state.spot
        if (spot == null) {
            if (!state.loading) NoSpot(state, actions)
            return@AtlasPage
        }
        PlaceHeader(spot, state.place)
        CompassBlock(state, actions)
        AccuracyNote(spot, state.hasCompass)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Navigate back in Google Maps", actions.onMaps, Modifier.fillMaxWidth(), icon = AtlasIcons.maps)
            SecondaryButton("Share", { actions.onShare(spot) }, Modifier.fillMaxWidth(), icon = AtlasIcons.share)
            TextAction("I moved the bike: park here", actions.onParkHere, icon = AtlasIcons.pin, enabled = state.phoneFix != null)
        }
        ParkedFootnote("No map here: Google Maps does the walking directions.")
    }
}

@Composable
private fun NoSpot(state: ParkedUiState, actions: ParkedActions) {
    EmptyState(
        "No parked spot yet.",
        message = "Safar saves the phone’s location when the bike disconnects.",
    )
    if (!state.locationAllowed) {
        LocationOff(actions.onAllowLocation, Modifier.padding(top = 14.dp))
    } else {
        TextAction("Park here now", actions.onParkHere, Modifier.padding(top = 10.dp), icon = AtlasIcons.pin, enabled = state.phoneFix != null)
    }
}

@Preview
@Composable
private fun ParkedPreview() = SafarTheme(ThemeMode.LIGHT) {
    ParkedContent(ParkedPreviewData.near, rememberToastState(), ParkedActions())
}

@Preview
@Composable
private fun ParkedNoLocationPreview() = SafarTheme(ThemeMode.DARK) {
    ParkedContent(ParkedPreviewData.noLocation, rememberToastState(), ParkedActions())
}

@Preview
@Composable
private fun ParkedEmptyPreview() = SafarTheme(ThemeMode.LIGHT) {
    ParkedContent(ParkedUiState(loading = false), rememberToastState(), ParkedActions())
}
