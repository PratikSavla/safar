package app.safar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.safar.ui.art.AtlasIcons
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes

/** Button inks: rust (primary), ink (default, filled) and ghost (ink outline). */
enum class ButtonTone { RUST, INK, GHOST }

/** Main action in rust: "Start in Google Maps", "Connect". */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, small: Boolean = false) =
    AtlasButton(text, onClick, modifier, ButtonTone.RUST, icon, enabled, small)

/** Secondary action, ink outline: "End navigation", "Cancel". */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, small: Boolean = false) =
    AtlasButton(text, onClick, modifier, ButtonTone.GHOST, icon, enabled, small)

/** Filled ink button for neutral actions ("Grant", "Clear"). */
@Composable
fun InkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, small: Boolean = false) =
    AtlasButton(text, onClick, modifier, ButtonTone.INK, icon, enabled, small)

/**
 * Inked label with a slight press (`.btn`): sinks 2 dp while pressed. Small buttons draw 40 dp
 * tall but keep a 48 dp touch target.
 */
@Composable
fun AtlasButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.INK,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    small: Boolean = false,
) {
    val c = Atlas.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val (bg, fg) = when (tone) {
        ButtonTone.RUST -> c.rust to c.onRust
        ButtonTone.INK -> c.ink to c.paper
        ButtonTone.GHOST -> Color.Transparent to c.ink
    }
    val height = if (small) Atlas.dimens.buttonHeightSmall else Atlas.dimens.buttonHeight
    val outer = modifier
        .heightIn(min = Atlas.dimens.touchTarget)
        .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
    Box(outer, contentAlignment = Alignment.Center) {
        var face = Modifier
            .graphicsLayer { translationY = if (pressed) 2.dp.toPx() else 0f }
            .heightIn(min = height)
            .background(bg, AtlasShapes.small)
        if (tone == ButtonTone.GHOST) face = face.border(Atlas.dimens.outline, c.ink, AtlasShapes.small)
        Row(
            face.alpha(if (enabled) 1f else 0.4f).padding(horizontal = if (small) 14.dp else 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) AtlasIcon(icon, tint = fg, size = 20.dp)
            val style = Atlas.type.button.let { if (small) it.copy(fontSize = it.fontSize * 0.9f) else it }
            BasicText(text, style = style.copy(color = fg, textAlign = TextAlign.Center), maxLines = 2)
        }
    }
}

/** Quiet text action with a dotted underline (`.btn.quiet`): "Edit list", "Save a place". */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, color: Color = Atlas.colors.ink2) {
    Row(
        modifier
            .heightIn(min = Atlas.dimens.touchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) AtlasIcon(icon, tint = color, size = 18.dp)
        BasicText(text, Modifier.dottedUnderline(color), style = Atlas.type.button.copy(color = color, fontWeight = Atlas.type.uiStrong.fontWeight))
    }
}

private fun Modifier.dottedUnderline(color: Color) = drawBehind {
    val y = size.height + 5.dp.toPx()
    drawLine(
        color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 3.5.dp.toPx())),
    )
}

@Preview
@Composable
private fun ButtonsPreview() = PreviewSurface {
    PrimaryButton("Start in Google Maps", {}, icon = AtlasIcons.maps)
    SecondaryButton("End navigation", {}, icon = AtlasIcons.cross)
    InkButton("Grant", {}, small = true)
    TextAction("Save a place", {}, icon = AtlasIcons.plus)
}
