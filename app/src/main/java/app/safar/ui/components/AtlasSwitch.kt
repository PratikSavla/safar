package app.safar.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes

private val LeverEase = CubicBezierEasing(0.22f, 0.8f, 0.24f, 1f)

/**
 * Rectangular lever with ON/OFF engraving (`.sw`). The 58 x 30 lever sits in a 48 dp touch target.
 * When it is part of a [SwitchRow], pass `onCheckedChange = null` and let the row toggle.
 */
@Composable
fun AtlasSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val c = Atlas.colors
    val knobX by animateDpAsState(if (checked) 32.dp else 4.dp, tween(250, easing = LeverEase), label = "switchKnob")
    val track by animateColorAsState(if (checked) c.ink else c.paper2, tween(200), label = "switchTrack")
    val interactive = if (onCheckedChange != null) {
        Modifier
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Box(modifier.then(interactive).size(width = 62.dp, height = Atlas.dimens.touchTarget), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(58.dp, 30.dp)
                .alpha(if (enabled) 1f else 0.45f)
                .background(track, AtlasShapes.small)
                .then(if (checked) Modifier else Modifier.border(1.5.dp, c.rule2, AtlasShapes.small)),
        ) {
            val engraving = Atlas.type.stamp.copy(fontSize = 9.sp, letterSpacing = Atlas.type.stamp.letterSpacing * 0.6f)
            if (checked) {
                BasicText("ON", Modifier.align(Alignment.CenterStart).padding(start = 8.dp), style = engraving.copy(color = c.paper))
            } else {
                BasicText("OFF", Modifier.align(Alignment.CenterEnd).padding(end = 6.dp), style = engraving.copy(color = c.ink3))
            }
            Box(
                Modifier
                    .offset(x = knobX, y = 4.dp)
                    .size(22.dp)
                    .shadow(if (checked) 0.dp else 1.dp, AtlasShapes.tiny)
                    .background(if (checked) c.paper else c.card, AtlasShapes.tiny)
                    .then(if (checked) Modifier else Modifier.border(1.dp, c.rule2, AtlasShapes.tiny)),
            )
        }
    }
}

@Preview
@Composable
private fun SwitchPreview() = PreviewSurface {
    Row {
        AtlasSwitch(true, {})
        AtlasSwitch(false, {})
        AtlasSwitch(false, {}, enabled = false)
    }
}
