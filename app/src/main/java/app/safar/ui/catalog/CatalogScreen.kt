package app.safar.ui.catalog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.safar.data.ThemeMode
import app.safar.ui.components.AtlasBottomSheet
import app.safar.ui.components.AtlasPage
import app.safar.ui.components.AtlasToastHost
import app.safar.ui.components.AtlasToastState
import app.safar.ui.components.Gap
import app.safar.ui.components.Masthead
import app.safar.ui.components.Note
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.SectionHeader
import app.safar.ui.components.SegmentedChoice
import app.safar.ui.components.TicketStubTabs
import app.safar.ui.components.TopoTransform
import app.safar.ui.components.rememberToastState
import app.safar.ui.components.safarTabs
import app.safar.ui.theme.isDark

/** Contour placement per tab, so switching tabs shows the layer glide like the prototype. */
private val TopoByTab = mapOf(
    "today" to TopoTransform.Home,
    "route" to TopoTransform.Route,
    "journal" to TopoTransform.Journal,
    "alerts" to TopoTransform.Alerts,
    "settings" to TopoTransform.Quiet,
)

/**
 * Temporary "Atlas catalog": every design-system component in the current theme, with a theme
 * toggle, for review before the real screens land. Synthetic data only.
 */
@Composable
fun CatalogScreen(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit) {
    var tab by rememberSaveable { mutableStateOf("today") }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val toast = rememberToastState()
    AtlasPage(
        topo = TopoByTab[tab] ?: TopoTransform.Home,
        bottomBar = { TicketStubTabs(safarTabs(alertCount = 3, navigating = true), tab, { tab = it }) },
        overlay = {
            AtlasToastHost(toast, Modifier.align(Alignment.BottomCenter))
            AtlasBottomSheet(sheetOpen, { sheetOpen = false }, title = "Paper sheet") {
                SheetSample(onDone = { sheetOpen = false; toast.show("Saved · sheet closed") })
            }
        },
    ) {
        Masthead("Atlas · component catalog", "Field", kickerAside = if (themeMode.isDark()) "night map" else "warm paper", titleEm = " notebook.")
        SectionHeader("Theme")
        SegmentedChoice(
            listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"),
            themeMode,
            onThemeModeChange,
        )
        TypeSection()
        ColourSection()
        LedgerSection()
        StampSection()
        ButtonSection()
        CardSection()
        InputSection()
        RowSection()
        OverlaySection(onOpenSheet = { sheetOpen = true }, toast = toast)
        IconSection()
        BikeSection()
        TurnSection()
        LcdSection()
        EmptySection()
    }
}

@Composable
private fun OverlaySection(onOpenSheet: () -> Unit, toast: AtlasToastState) {
    SectionHeader("Sheet & toast")
    PrimaryButton("Open paper sheet", onOpenSheet, Modifier.fillMaxWidth())
    Gap(10.dp)
    SecondaryButton("Show a toast", { toast.show("Opening Google Maps…") }, Modifier.fillMaxWidth())
}

@Composable
private fun SheetSample(onDone: () -> Unit) {
    Note("System back or a tap on the scrim closes it. Only names and counts go to the bike, never message text.")
    Gap(18.dp)
    PrimaryButton("Done", onDone, Modifier.fillMaxWidth())
}
