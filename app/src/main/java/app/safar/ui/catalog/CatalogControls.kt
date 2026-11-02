package app.safar.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasCard
import app.safar.ui.components.AtlasChip
import app.safar.ui.components.AtlasSlider
import app.safar.ui.components.AtlasTextField
import app.safar.ui.components.CardStyle
import app.safar.ui.components.DashedRule
import app.safar.ui.components.Gap
import app.safar.ui.components.InkButton
import app.safar.ui.components.Kicker
import app.safar.ui.components.LeaderRow
import app.safar.ui.components.LinkRow
import app.safar.ui.components.ListRow
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.SectionHeader
import app.safar.ui.components.SegmentedChoice
import app.safar.ui.components.Stamp
import app.safar.ui.components.StampTone
import app.safar.ui.components.SwitchRow
import app.safar.ui.components.TextAction
import app.safar.ui.theme.Atlas

@Composable
internal fun ButtonSection() {
    SectionHeader("Buttons")
    PrimaryButton("Start in Google Maps", {}, Modifier.fillMaxWidth(), icon = AtlasIcons.maps)
    Gap(10.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryButton("End", {}, Modifier.weight(1f), icon = AtlasIcons.cross)
        InkButton("Connect", {}, Modifier.weight(1f), icon = AtlasIcons.bt)
    }
    Gap(6.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        InkButton("Grant", {}, small = true)
        SecondaryButton("Reset", {}, small = true)
        PrimaryButton("Off", {}, small = true, enabled = false)
    }
    TextAction("Save a place", {}, icon = AtlasIcons.plus)
}

@Composable
internal fun CardSection() {
    SectionHeader("Cards")
    AtlasCard {
        Kicker("Bike information")
        LeaderRow("Model number", "SBM01A1B2C3D")
        LeaderRow("Firmware", "1.04.2")
        DashedRule(Modifier.padding(vertical = 8.dp))
        LeaderRow("MTU", "65")
    }
    Gap(12.dp)
    AtlasCard(style = CardStyle.LINE) {
        Kicker("Line card")
        Gap(4.dp)
        Stamp("tip", tone = StampTone.SLATE)
    }
    Gap(12.dp)
    AtlasCard(style = CardStyle.TICKET, onClick = {}, onClickLabel = "Pick destination") {
        Kicker("Ticket · tappable")
        BasicText("Main Rd", style = Atlas.type.serifTitle.copy(color = Atlas.colors.ink))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InputSection() {
    var name by rememberSaveable { mutableStateOf("Alex") }
    var moving by rememberSaveable { mutableStateOf(false) }
    var speed by rememberSaveable { mutableIntStateOf(100) }
    var units by rememberSaveable { mutableStateOf("km") }
    var tx by rememberSaveable { mutableStateOf(true) }
    var errs by rememberSaveable { mutableStateOf(false) }
    SectionHeader("Inputs")
    SwitchRow("Simulate moving", moving, { moving = it }, subtitle = "Locks text entry (UX rule 3)")
    AtlasTextField(
        name, { name = it }, label = "Rider name · shown on the cluster", maxLength = 19,
        lockedReason = if (moving) "Locked while moving (42 km/h). No typing on a ride; stop to edit." else null,
    )
    AtlasSlider(speed, { speed = it }, 10..299, label = "Speed alert", unit = "km/h")
    SegmentedChoice(listOf("km" to "Kilometres", "mi" to "Miles"), units, { units = it })
    Gap(8.dp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AtlasChip("TX", tx, { tx = it })
        AtlasChip("RX", !tx, { tx = !it })
        AtlasChip("Errors only", errs, { errs = it }, danger = true)
    }
}

@Composable
internal fun RowSection() {
    var calls by rememberSaveable { mutableStateOf(true) }
    var wa by rememberSaveable { mutableStateOf(false) }
    SectionHeader("Rows")
    SwitchRow("Phone calls", calls, { calls = it }, subtitle = "Caller name and missed calls")
    SwitchRow("WhatsApp messages", wa, { wa = it }, subtitle = "Sender name and count, never text")
    SwitchRow("SMS", false, {}, subtitle = "Coming soon", enabled = false)
    LinkRow("Debug log", AtlasIcons.log, {}, subtitle = "TX, RX and decoded frames")
    ListRow("Notification access", subtitle = "Granted · Maps turns can be read", icon = AtlasIcons.bell, divider = false) {
        Stamp("Linked", tone = StampTone.MOSS)
    }
}
