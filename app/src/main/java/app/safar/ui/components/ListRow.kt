package app.safar.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.safar.ui.art.AtlasIcons
import app.safar.ui.copy.Phrases
import app.safar.ui.copy.SwitchCopy
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes

/**
 * Settings-style row (`.row`): optional icon box, bold title and small subtitle, trailing content.
 * [onClick] makes the whole row tappable. [divider] draws the hairline below it.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val rowMod = if (onClick != null) modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else modifier
    RowFrame(rowMod, divider) {
        if (icon != null) IconBox(icon)
        RowTexts(title, subtitle, enabled, Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** A row whose whole surface flips an [AtlasSwitch]; TalkBack reads it as one switch. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
) {
    val toggle = modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    RowFrame(toggle, divider) {
        RowTexts(title, subtitle, enabled, Modifier.weight(1f))
        AtlasSwitch(checked && enabled, onCheckedChange = null, enabled = enabled)
    }
}

/** Navigation row with an icon box and a chevron (`.row.link`). */
@Composable
fun LinkRow(title: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, divider: Boolean = true) {
    ListRow(title, modifier, subtitle, icon, onClick = onClick, divider = divider) {
        AtlasIcon(AtlasIcons.chev, tint = Atlas.colors.ink3, size = 18.dp)
    }
}

/** 42 dp outlined square holding a line icon (`.ico-box`). */
@Composable
fun IconBox(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = Atlas.colors.ink, outline: Color = Atlas.colors.rule2) {
    Box(modifier.size(42.dp).border(1.5.dp, outline, AtlasShapes.small), contentAlignment = Alignment.Center) {
        AtlasIcon(icon, tint = tint)
    }
}

@Composable
private fun RowFrame(modifier: Modifier, divider: Boolean, content: @Composable RowScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Atlas.dimens.rowHeight).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
        if (divider) Hairline()
    }
}

@Composable
private fun RowTexts(title: String, subtitle: String?, enabled: Boolean, modifier: Modifier) {
    val c = Atlas.colors
    Column(modifier.semantics(mergeDescendants = true) { if (!enabled) stateDescription = "Unavailable" }) {
        BasicText(title, style = Atlas.type.uiStrong.copy(color = if (enabled) c.ink else c.ink3))
        if (subtitle != null) BasicText(subtitle, Modifier.padding(top = 2.dp), style = Atlas.type.uiSmall.copy(color = c.ink3))
    }
}

@Preview
@Composable
private fun RowsPreview() = PreviewSurface {
    SwitchRow("Phone calls", true, {}, subtitle = SwitchCopy.PHONE_CALLS)
    SwitchRow("SMS", false, {}, subtitle = Phrases.COMING_SOON, enabled = false)
    LinkRow("Bike information", AtlasIcons.bike, {}, subtitle = "Model, serial number and software version")
    ListRow("Software version", subtitle = "Read from your bike") { Stamp("connected", tone = StampTone.MOSS) }
}
