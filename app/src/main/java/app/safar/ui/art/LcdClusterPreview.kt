package app.safar.ui.art

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.safar.data.ThemeMode
import app.safar.ui.format.ClusterText
import app.safar.ui.format.Formatters
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.ClusterBezel
import app.safar.ui.theme.PlexMono
import app.safar.ui.theme.SafarTheme
import java.util.Locale

internal val LcdShape = RoundedCornerShape(10.dp, 10.dp, 18.dp, 18.dp)

/**
 * Stylised cluster LCD (navigate screen `.lcd`): what the bike shows for the current nav frame.
 * Plain values in, nothing else: [turnCode] 1..46, metres to the turn and to the destination,
 * [eta] exactly as sent ("0412PM"), and the status code ('1' normal, '2' recalculating, ...).
 */
@Composable
fun LcdClusterPreview(
    turnCode: Int,
    toTurnMeters: Int,
    eta: String?,
    remainingMeters: Int,
    modifier: Modifier = Modifier,
    status: Char = '1',
    caption: String? = "cluster preview",
) {
    val c = Atlas.colors
    val dist = ClusterText.distance(toTurnMeters)
    val remain = ClusterText.distance(remainingMeters)
    val word = ClusterText.statusWord(status)
    val spoken = "Cluster preview: ${TurnCodes.spec(turnCode).name}, " +
        (word?.lowercase() ?: "${Formatters.navDistance(toTurnMeters)} to the turn") +
        ", ETA ${Formatters.clusterEta(eta)}, ${Formatters.navDistance(remainingMeters)} remaining"
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }) {
        if (caption != null) {
            BasicText(caption.uppercase(Locale.ROOT), Modifier.align(Alignment.End).padding(end = 12.dp, bottom = 8.dp), style = Atlas.type.kicker.copy(fontSize = 9.sp, color = c.ink3))
        }
        Box(Modifier.padding(horizontal = 6.dp).fillMaxWidth().border(1.dp, c.rule2, LcdShape).padding(1.dp).background(ClusterBezel, LcdShape).padding(5.dp)) {
            Column(
                Modifier.fillMaxWidth().clip(LcdShape).background(c.lcd).lcdGlass().padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TurnArrow(turnCode, size = 72.dp, color = c.lcdInk, contentDescription = null)
                    if (word != null) {
                        BasicText(word, style = seg7(c.lcdInk, 24.sp).copy(letterSpacing = 0.12.em), maxLines = 1)
                    } else {
                        BasicText(
                            buildAnnotatedString {
                                append(dist.value)
                                withStyle(SpanStyle(fontSize = 22.sp)) { append(" " + dist.unit) }
                            },
                            style = seg7(c.lcdInk, 48.sp),
                            maxLines = 1,
                        )
                    }
                }
                LcdFooter(eta, remain.value + remain.unit, c.lcdInk, c.lcdDim)
            }
        }
    }
}

@Composable
private fun LcdFooter(eta: String?, remaining: String, ink: Color, dim: Color) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp).background(dim).padding(top = 1.5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val small = seg7(ink, 16.sp)
            BasicText("ETA ${eta ?: "----"}", style = small, maxLines = 1)
            BasicText(remaining, style = small, maxLines = 1)
            BasicText("ODO", style = small.copy(color = dim, shadow = null), maxLines = 1)
        }
    }
}

/** Segment-display text: mono semibold, wide tracking, a faint glow. */
internal fun seg7(ink: Color, size: TextUnit) = TextStyle(
    fontFamily = PlexMono,
    fontWeight = FontWeight.SemiBold,
    fontSize = size,
    lineHeight = size,
    letterSpacing = 0.08.em,
    color = ink,
    shadow = Shadow(ink.copy(alpha = 0.6f), Offset.Zero, blurRadius = 1.5f),
)

/** Inset shade at the top and faint scan lines over the glass. */
internal fun Modifier.lcdGlass() = drawWithContent {
    drawContent()
    drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.22f), 0.12f to Color.Transparent))
    val step = 3.dp.toPx()
    var y = 2.dp.toPx()
    while (y < size.height) {
        drawLine(Color.Black.copy(alpha = 0.05f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        y += step
    }
}

@Preview
@Composable
private fun LcdPreview() = SafarTheme(ThemeMode.LIGHT) {
    Column(Modifier.background(Atlas.colors.paper).padding(22.dp)) {
        LcdClusterPreview(turnCode = 1, toTurnMeters = 850, eta = "0412PM", remainingMeters = 11_200)
        LcdClusterPreview(turnCode = 46, toTurnMeters = 0, eta = "0412PM", remainingMeters = 11_200, status = '4', caption = null)
    }
}
