package app.safar.ui.icontest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.safar.core.AppContainer
import app.safar.ui.art.AtlasIcons
import app.safar.ui.art.TurnArrow
import app.safar.ui.components.AtlasCard
import app.safar.ui.components.AtlasPage
import app.safar.ui.components.BackTopBar
import app.safar.ui.components.DeveloperFreshness
import app.safar.ui.components.Gap
import app.safar.ui.components.Note
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.SectionHeader
import app.safar.ui.components.WarnLine
import app.safar.ui.format.FreshnessText
import app.safar.ui.nav.Navigator
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes

private const val COLUMNS = 4

class IconTestActions(val onBack: () -> Unit = {}, val onSend: (Int) -> Unit = {}, val onNext: () -> Unit = {}, val onPrevious: () -> Unit = {})

/** Developer mode: send each cluster turn icon 1..45 and compare the cluster with the drawing (assignment task 4). */
@Composable
fun IconTestScreen(navigator: Navigator, modifier: Modifier = Modifier) {
    val vm = viewModel { IconTestViewModel(AppContainer.bike) }
    val state by vm.state.collectAsStateWithLifecycle()
    IconTestContent(state, IconTestActions(navigator::back, vm::send, vm::next, vm::previous), modifier)
}

@Composable
internal fun IconTestContent(state: IconTestState, actions: IconTestActions, modifier: Modifier = Modifier) {
    AtlasPage(modifier) {
        BackTopBar("Developer · icon test", "Turn", actions.onBack, titleEm = " icons.")
        Note("Tap an icon to send one navigation frame with that code. The display should show the same arrow.")
        Gap(14.dp)
        if (!state.connected) {
            WarnLine("Bike not connected. Frames are dropped until it connects.", Modifier.padding(bottom = 14.dp))
        }
        CurrentCard(state, actions)
        SectionHeader("Codes 1–45")
        ICON_CELLS.chunked(COLUMNS).forEach { line ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                line.forEach { cell -> Cell(cell, cell.code == state.current, { actions.onSend(cell.code) }, Modifier.weight(1f)) }
                repeat(COLUMNS - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CurrentCard(state: IconTestState, actions: IconTestActions) {
    val c = Atlas.colors
    AtlasCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TurnArrow(state.current, size = 88.dp, color = c.rust)
            Column(Modifier.weight(1f)) {
                BasicText(state.current.toString(), style = Atlas.type.numberLarge.copy(color = c.ink, fontWeight = FontWeight.SemiBold))
                BasicText(iconName(state.current), style = Atlas.type.serifItalic.copy(color = c.ink2))
            }
        }
        val sent = if (state.lastSentCode != null) "code ${state.lastSentCode} sent to the bike" else "nothing sent yet"
        val at = state.lastSentAtMillis
        DeveloperFreshness(Modifier.padding(top = 10.dp)) { now, zone -> FreshnessText.developer(sent, at, now, zone = zone) }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Previous", actions.onPrevious, Modifier.weight(1f), icon = AtlasIcons.back)
            PrimaryButton("Next", actions.onNext, Modifier.weight(1f), icon = AtlasIcons.arrow)
        }
    }
}

@Composable
private fun Cell(cell: IconCell, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = Atlas.colors
    val face = modifier
        .heightIn(min = 96.dp)
        .background(if (selected) c.rustWash else c.card, AtlasShapes.small)
        .border(if (selected) 2.dp else 1.dp, if (selected) c.rust else c.rule, AtlasShapes.small)
        .selectable(selected, role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = "Send icon ${cell.code}, ${cell.name}" }
        .padding(horizontal = 4.dp, vertical = 8.dp)
    Column(face, horizontalAlignment = Alignment.CenterHorizontally) {
        TurnArrow(cell.code, size = 40.dp, color = if (selected) c.rust else c.ink, contentDescription = null)
        BasicText(cell.code.toString(), Modifier.padding(top = 4.dp), style = Atlas.type.monoValue.copy(color = c.ink))
        BasicText(
            cell.name,
            style = Atlas.type.src.copy(fontSize = 9.sp, color = c.ink3, textAlign = TextAlign.Center),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
