package app.safar.ui.art

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import app.safar.data.ThemeMode
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.ClusterBezel
import app.safar.ui.theme.SafarTheme
import java.util.Locale

/**
 * The cluster's greeting on the LCD (onboard.css `.namelcd`): a dim "WELCOME" and [name] exactly as the
 * bike will draw it. Pass the already-cleaned name (ASCII caps, 19 characters at most).
 */
@Composable
fun LcdNamePreview(name: String, modifier: Modifier = Modifier, caption: String? = "how the cluster shows it") {
    val c = Atlas.colors
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = if (name.isEmpty()) "Cluster shows WELCOME" else "Cluster shows WELCOME $name" }) {
        if (caption != null) {
            BasicText(caption.uppercase(Locale.ROOT), Modifier.align(Alignment.End).padding(end = 12.dp, bottom = 8.dp), style = Atlas.type.kicker.copy(fontSize = 9.sp, color = c.ink3))
        }
        Box(Modifier.padding(horizontal = 6.dp).fillMaxWidth().border(1.dp, c.rule2, LcdShape).padding(1.dp).background(ClusterBezel, LcdShape).padding(5.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().clip(LcdShape).background(c.lcd).lcdGlass().padding(horizontal = 12.dp, vertical = 16.dp)) {
                // A drawing of a fixed display: 19 characters always fit, whatever the system font size.
                val density = LocalDensity.current
                val glyph = min(26.dp, maxWidth / (NAME_CELLS * GLYPH_WIDTH_EM))
                val big = with(density) { glyph.toSp() }
                val small = with(density) { (glyph * 0.7f).toSp() }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText("WELCOME", style = seg7(c.lcdDim, small).copy(shadow = null, textAlign = TextAlign.Center), maxLines = 1)
                    BasicText(
                        name.ifEmpty { " " },
                        Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 30.dp),
                        style = seg7(c.lcdInk, big).copy(textAlign = TextAlign.Center),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

private const val NAME_CELLS = 19

/** Advance of one Plex Mono glyph plus the LCD tracking, in em. */
private const val GLYPH_WIDTH_EM = 0.68f

@Preview
@Composable
private fun LcdNamePreviewPreview() = SafarTheme(ThemeMode.LIGHT) {
    Column(Modifier.background(Atlas.colors.paper).padding(22.dp)) {
        LcdNamePreview("ALEX")
        LcdNamePreview("", caption = null)
    }
}
