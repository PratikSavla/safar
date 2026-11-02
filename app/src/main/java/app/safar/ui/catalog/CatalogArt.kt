package app.safar.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.safar.core.VehicleFamily
import app.safar.ui.art.AtlasIcons
import app.safar.ui.art.BikeSilhouette
import app.safar.ui.art.LcdClusterPreview
import app.safar.ui.art.TurnArrow
import app.safar.ui.art.TurnCodes
import app.safar.ui.components.AtlasIcon
import app.safar.ui.components.BackTopBar
import app.safar.ui.components.EmptyState
import app.safar.ui.components.Gap
import app.safar.ui.components.Kicker
import app.safar.ui.components.SectionHeader
import app.safar.ui.theme.Atlas

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IconSection() {
    SectionHeader("Icons") { Kicker("${AtlasIcons.all.size} glyphs") }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AtlasIcons.all.forEach { (name, icon) ->
            Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AtlasIcon(icon, contentDescription = name)
                BasicText(name, Modifier.padding(top = 4.dp), style = Atlas.type.src.copy(color = Atlas.colors.ink3, fontSize = Atlas.type.tabLabel.fontSize))
            }
        }
    }
}

@Composable
internal fun BikeSection() {
    SectionHeader("Bikes")
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            BikeSilhouette(VehicleFamily.MOTORCYCLE, width = 140.dp, contentDescription = "Motorcycle")
            Kicker("motorcycle")
        }
        Column {
            BikeSilhouette(VehicleFamily.SCOOTER, width = 140.dp, contentDescription = "Scooter")
            Kicker("scooter")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TurnSection() {
    SectionHeader("Turn icons") { Kicker("codes 1–46") }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TurnCodes.all.forEach { spec ->
            Column(Modifier.width(70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TurnArrow(spec.code, size = 48.dp, color = if (spec.code == 46) Atlas.colors.amber else Atlas.colors.rust)
                BasicText(
                    "${spec.code} · ${spec.name}",
                    Modifier.padding(top = 4.dp),
                    style = Atlas.type.src.copy(color = Atlas.colors.ink3, fontSize = Atlas.type.tabLabel.fontSize, textAlign = TextAlign.Center),
                    maxLines = 4,
                )
            }
        }
    }
}

@Composable
internal fun LcdSection() {
    SectionHeader("On the cluster")
    LcdClusterPreview(turnCode = 1, toTurnMeters = 846, eta = "0412PM", remainingMeters = 11_200)
    Gap(26.dp)
    LcdClusterPreview(turnCode = 23, toTurnMeters = 2_400, eta = "0418PM", remainingMeters = 9_800, caption = "roundabout · straight on")
    Gap(26.dp)
    LcdClusterPreview(turnCode = 46, toTurnMeters = 0, eta = "0412PM", remainingMeters = 11_200, status = '4', caption = "status 4 · GPS off")
}

@Composable
internal fun EmptySection() {
    SectionHeader("Page header & empty state")
    BackTopBar("Journal · NH 48", "Trip", onBack = {}, titleEm = " to Main Rd")
    EmptyState("No rides yet", message = "Connect the bike and ride; trips land here by themselves.", actionLabel = "Pair a bike", onAction = {})
}
