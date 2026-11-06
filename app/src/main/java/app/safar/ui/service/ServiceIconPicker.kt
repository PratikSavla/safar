package app.safar.ui.service

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.safar.data.ServiceIcon
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.IconBox
import app.safar.ui.components.Kicker
import app.safar.ui.theme.Atlas

fun ServiceIcon.vector(): ImageVector = when (this) {
    ServiceIcon.OIL -> AtlasIcons.drop
    ServiceIcon.CHAIN -> AtlasIcons.chain
    ServiceIcon.AIR -> AtlasIcons.wind
    ServiceIcon.BRAKE -> AtlasIcons.disc
    ServiceIcon.TYRE -> AtlasIcons.gauge
    ServiceIcon.GENERIC -> AtlasIcons.wrench
    ServiceIcon.BIKE -> AtlasIcons.bike
    ServiceIcon.FUEL -> AtlasIcons.fuel
    ServiceIcon.CLOCK -> AtlasIcons.clock
    ServiceIcon.BELL -> AtlasIcons.bell
    ServiceIcon.STAR -> AtlasIcons.star
    ServiceIcon.FLAG -> AtlasIcons.flag
    ServiceIcon.BATTERY -> AtlasIcons.battery
    ServiceIcon.COOLANT -> AtlasIcons.coolant
    ServiceIcon.SPARK -> AtlasIcons.spark
    ServiceIcon.LIGHT -> AtlasIcons.light
}

/** What TalkBack calls each icon in the picker. */
fun ServiceIcon.spoken(): String = when (this) {
    ServiceIcon.OIL -> "Oil drop"
    ServiceIcon.CHAIN -> "Chain"
    ServiceIcon.AIR -> "Air"
    ServiceIcon.BRAKE -> "Brake disc"
    ServiceIcon.TYRE -> "Gauge"
    ServiceIcon.GENERIC -> "Wrench"
    ServiceIcon.BIKE -> "Bike"
    ServiceIcon.FUEL -> "Fuel"
    ServiceIcon.CLOCK -> "Clock"
    ServiceIcon.BELL -> "Bell"
    ServiceIcon.STAR -> "Star"
    ServiceIcon.FLAG -> "Flag"
    ServiceIcon.BATTERY -> "Battery"
    ServiceIcon.COOLANT -> "Coolant"
    ServiceIcon.SPARK -> "Spark plug"
    ServiceIcon.LIGHT -> "Light"
}

/** Every service icon as a wrapping grid of boxes; the chosen one is inked. Each box is a radio for TalkBack. */
@Composable
internal fun ServiceIconPicker(selected: ServiceIcon, onSelect: (ServiceIcon) -> Unit) {
    val c = Atlas.colors
    Kicker("Icon", Modifier.padding(top = 14.dp))
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 10.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ServiceIcon.entries.forEach { icon ->
            val on = icon == selected
            IconBox(
                icon.vector(),
                Modifier
                    .selectable(on, role = Role.RadioButton, onClick = { onSelect(icon) })
                    .semantics { contentDescription = icon.spoken() },
                tint = if (on) c.rust else c.ink2,
                outline = if (on) c.ink else c.rule2,
            )
        }
    }
}
