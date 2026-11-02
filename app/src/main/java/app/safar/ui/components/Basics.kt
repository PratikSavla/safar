package app.safar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.safar.data.ThemeMode
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.SafarTheme

/** A 1 dp rule. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Atlas.colors.rule, thickness: Dp = 1.dp) {
    Spacer(modifier.fillMaxWidth().height(thickness).background(color))
}

/** Dashed or dotted rule: `.perf` (dots, the default) or dashed dividers inside cards. */
@Composable
fun DashedRule(
    modifier: Modifier = Modifier,
    color: Color = Atlas.colors.rule2,
    thickness: Dp = 2.dp,
    dash: Dp = 0.01.dp,
    gap: Dp = 5.dp,
) {
    Canvas(modifier.fillMaxWidth().height(thickness)) {
        val y = size.height / 2
        val round = dash < 1.dp
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = size.height,
            cap = if (round) StrokeCap.Round else StrokeCap.Butt,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx())),
        )
    }
}

/** Serif display title with an optional light italic tail ("En route" + " to Café"). */
@Composable
fun DisplayTitle(title: String, em: String?, style: TextStyle, modifier: Modifier = Modifier, color: Color = Atlas.colors.ink) {
    val emColor = Atlas.colors.ink2
    val text = buildAnnotatedString {
        append(title)
        if (em != null) {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight(340), color = emColor)) { append(em) }
        }
    }
    BasicText(text, modifier, style = style.copy(color = color))
}

/** An Atlas line icon, tinted. Decorative unless [contentDescription] is set. */
@Composable
fun AtlasIcon(icon: ImageVector, modifier: Modifier = Modifier, contentDescription: String? = null, tint: Color = Atlas.colors.ink, size: Dp = 22.dp) {
    Image(icon, contentDescription, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** Paper background + padding for component previews. */
@Composable
internal fun PreviewSurface(dark: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    SafarTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
        Column(Modifier.background(Atlas.colors.paper).padding(Atlas.dimens.pad), content = content)
    }
}
