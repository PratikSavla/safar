package app.safar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.safar.BuildConfig
import app.safar.data.Settings
import app.safar.ui.components.AtlasCard
import app.safar.ui.components.AtlasSlider
import app.safar.ui.components.AtlasTextField
import app.safar.ui.components.CardStyle
import app.safar.ui.components.DisplayTitle
import app.safar.ui.components.LockLine
import app.safar.ui.components.Note
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.SourceLine
import app.safar.ui.components.Stamp
import app.safar.ui.components.StampTone
import app.safar.ui.components.SwitchRow
import app.safar.ui.theme.Atlas

/** Rider name with the cleaned cluster preview (`.set-name`). Locked while moving. */
@Composable
internal fun RiderBlock(state: SettingsState, onName: (String) -> Unit) {
    val c = Atlas.colors
    AtlasTextField(
        state.nameText,
        onName,
        "Rider name · shown on the cluster",
        lockedReason = state.lockReason,
        placeholder = "e.g. Alex",
        trailing = { BasicText(nameCounter(state.nameText), style = Atlas.type.src.copy(color = c.ink3)) },
    )
    state.nameHint?.let { LockLine(it) }
    BasicText(
        buildAnnotatedString {
            append("cluster: ")
            withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.Medium)) { append("WELCOME ${clusterName(state.nameText)}") }
            append(" · sent on next connect")
        },
        Modifier.padding(top = 4.dp),
        style = Atlas.type.src.copy(color = c.ink3),
    )
}

/** Speed alert switch and slider; the range comes from the saved bike (`.slider`). */
@Composable
internal fun SpeedBlock(state: SettingsState, onSettings: (String, (Settings) -> Settings) -> Unit, onSpeed: (Int) -> Unit) {
    val on = state.settings.speedAlertOn
    val r = state.alertRange
    SwitchRow(
        "Speed alert",
        on,
        { v -> onSettings("speed alert") { it.copy(speedAlertOn = v) } },
        subtitle = "The cluster warns above this speed (${r.first}–${r.last} km/h for a ${familyWord(state.model)})",
        divider = false,
    )
    AtlasSlider(state.speedKmh, onSpeed, r, label = "Speed alert", unit = "km/h", enabled = on)
    state.speedOutOfRange?.let { LockLine("Saved alert is $it km/h, outside this ${familyWord(state.model)}'s range. Move the slider to set it.") }
}

/** Tank size: model spec, the rider's override, reset (`.tank-set`). Locked while moving. */
@Composable
internal fun TankBlock(state: SettingsState, onTank: (String) -> Unit, onReset: () -> Unit) {
    val own = state.settings.tankLitres
    BasicText(tankSubtitle(state.model, own), Modifier.padding(top = 4.dp), style = Atlas.type.uiSmall.copy(color = Atlas.colors.ink3))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AtlasTextField(
            state.tankText,
            onTank,
            "Tank size · litres",
            Modifier.weight(1f),
            lockedReason = state.lockReason?.let { "Locked while moving. Stop to change the tank size." },
            keyboardType = KeyboardType.Decimal,
            trailing = { BasicText("L", style = Atlas.type.serifItalic.copy(color = Atlas.colors.ink2)) },
        )
        if (own != null) SecondaryButton("Reset", onReset, enabled = state.lockReason == null, small = true)
    }
    state.tankError?.let { LockLine(it) }
    SourceLine(if (own != null) "you · override" else "model spec", age = null)
    Note("Used with your fuel-ups to work out litres left and range.", Modifier.padding(top = 6.dp), color = Atlas.colors.ink3)
}

/** Currency symbol shown before fuel prices and costs. Locked while moving, like the other text fields. */
@Composable
internal fun CurrencyBlock(state: SettingsState, onCurrency: (String) -> Unit) {
    AtlasTextField(
        state.currencyText,
        onCurrency,
        "Currency symbol · fuel costs",
        Modifier.padding(top = 12.dp),
        lockedReason = state.lockReason?.let { "Locked while moving. Stop to change the currency." },
        placeholder = "₹",
        maxLength = CURRENCY_MAX,
    )
    state.currencyHint?.let { LockLine(it) }
}

/** Version, and the promises: open source, no account, no analytics (`.about`). */
@Composable
internal fun AboutCard() {
    AtlasCard(style = CardStyle.LINE, contentPadding = PaddingValues(16.dp)) {
        DisplayTitle("Safar", " ${BuildConfig.VERSION_NAME}", Atlas.type.displaySmall)
        Note(
            "Open source. No account, no server, no analytics. Nothing leaves this phone unless you share a log.",
            Modifier.padding(top = 6.dp),
        )
        FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Stamp("open source")
            Stamp("no account", tone = StampTone.MOSS)
            Stamp("no analytics", tone = StampTone.SLATE)
        }
    }
}
