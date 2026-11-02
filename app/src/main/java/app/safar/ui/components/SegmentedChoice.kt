package app.safar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes

/**
 * Pages of a tab divider (`.seg`): an ink-outlined strip, dashed separators, the chosen page
 * filled in ink. Each option is a radio button for TalkBack.
 */
@Composable
fun <T> SegmentedChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Atlas.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = Atlas.dimens.touchTarget)
            .clip(AtlasShapes.small)
            .border(Atlas.dimens.outline, c.ink, AtlasShapes.small)
            .selectableGroup(),
    ) {
        options.forEachIndexed { i, (value, label) ->
            val on = value == selected
            if (i > 0) DashedDivider(dim = on || options[i - 1].first == selected)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(on, role = Role.RadioButton, onClick = { onSelect(value) })
                    .background(if (on) c.ink else c.paper.copy(alpha = 0f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(label, style = Atlas.type.uiStrong.copy(fontSize = Atlas.type.uiSmall.fontSize * 1.03f, fontWeight = FontWeight.Medium, color = if (on) c.paper else c.ink2, textAlign = TextAlign.Center))
            }
        }
    }
}

@Composable
private fun DashedDivider(dim: Boolean) {
    val color = if (dim) Atlas.colors.ink else Atlas.colors.rule2
    Box(
        Modifier.width(1.5.dp).fillMaxHeight().drawBehind {
            drawLine(
                color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = size.width,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
            )
        },
    )
}

/** Mono filter chip (`.chip`): TX, RX, errors only. 48 dp touch target around a 34 dp face. */
@Composable
fun AtlasChip(text: String, selected: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier, danger: Boolean = false) {
    val c = Atlas.colors
    val bg = when {
        selected && danger -> c.rust
        selected -> c.ink
        else -> c.card2
    }
    val fg = when {
        selected && danger -> c.onRust
        selected -> c.paper
        else -> c.ink2
    }
    Box(
        modifier.heightIn(min = Atlas.dimens.touchTarget).toggleable(selected, role = Role.Checkbox, onValueChange = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        val face = Modifier.heightIn(min = 34.dp).background(bg, AtlasShapes.stamp)
        Box(
            (if (selected) face else face.border(1.dp, c.rule2, AtlasShapes.stamp)).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(text, style = Atlas.type.monoValue.copy(fontSize = Atlas.type.src.fontSize * 1.09f, letterSpacing = Atlas.type.src.letterSpacing * 2, color = fg))
        }
    }
}

@Preview
@Composable
private fun SegmentedPreview() = PreviewSurface {
    SegmentedChoice(listOf("km" to "Kilometres", "mi" to "Miles"), "km", {})
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AtlasChip("TX", true, {})
        AtlasChip("RX", false, {})
        AtlasChip("Errors", true, {}, danger = true)
    }
}
