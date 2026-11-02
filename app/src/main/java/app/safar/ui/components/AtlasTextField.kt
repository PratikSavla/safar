package app.safar.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.safar.ui.art.AtlasIcons
import app.safar.ui.theme.Atlas

/**
 * Notebook-style text field (`.field`): kicker label, large serif entry on an ink baseline that
 * turns rust when focused. [lockedReason] locks it (UX rule 3: no typing while moving): the
 * baseline goes dashed, a lock shows, and the reason is spelled out underneath in amber.
 */
@Composable
fun AtlasTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    lockedReason: String? = null,
    placeholder: String? = null,
    maxLength: Int? = null,
    textStyle: TextStyle = Atlas.type.displayMedium.copy(fontSize = Atlas.type.displayMedium.fontSize * 0.85f),
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Atlas.colors
    val locked = lockedReason != null
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val line = when {
        locked -> c.ink3
        focused -> c.rust
        else -> c.ink
    }
    Column(modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Kicker(label)
        Row(
            Modifier.fillMaxWidth().heightIn(min = Atlas.dimens.touchTarget).padding(top = 8.dp).baseline(line, dashed = locked),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = { v -> onValueChange(if (maxLength != null) v.take(maxLength) else v) },
                modifier = Modifier.weight(1f).padding(vertical = 10.dp).semantics {
                    contentDescription = label
                    if (locked) stateDescription = "Locked. $lockedReason"
                },
                enabled = !locked,
                singleLine = true,
                textStyle = textStyle.copy(color = if (locked) c.ink3 else c.ink),
                cursorBrush = SolidColor(c.rust),
                interactionSource = source,
                keyboardOptions = KeyboardOptions(KeyboardCapitalization.Words, keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty() && placeholder != null) BasicText(placeholder, style = textStyle.copy(color = c.ink3.copy(alpha = 0.6f)))
                        inner()
                    }
                },
            )
            when {
                locked -> AtlasIcon(AtlasIcons.lock, tint = c.amber, size = 20.dp)
                trailing != null -> trailing()
                maxLength != null -> BasicText("${value.length}/$maxLength", style = Atlas.type.src.copy(color = c.ink3))
            }
        }
        if (lockedReason != null) LockLine(lockedReason)
    }
}

/** Amber "locked while moving" line with a lock (`.lockline`). */
@Composable
fun LockLine(reason: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AtlasIcon(AtlasIcons.lock, tint = Atlas.colors.amber, size = 16.dp)
        BasicText(reason, style = Atlas.type.note.copy(color = Atlas.colors.amber))
    }
}

private fun Modifier.baseline(color: Color, dashed: Boolean) = drawBehind {
    val w = 2.dp.toPx()
    val y = size.height - w / 2
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null
    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = w, pathEffect = effect)
}

@Preview
@Composable
private fun TextFieldPreview() = PreviewSurface {
    AtlasTextField("Alex", {}, label = "Rider name · shown on the cluster", maxLength = 19)
    AtlasTextField("Alex", {}, label = "Rider name", lockedReason = "Locked while moving (42 km/h). Stop to edit.")
}
