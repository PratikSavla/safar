package app.safar.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontFamily
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * The 4x2 home-screen widget in Atlas dress (widget.js): link state, odometer and Trip A, fuel bars and range,
 * and the next turn while Google Maps navigates. A tap opens the app.
 */
class SafarWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetData.snapshot()
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
        provideContent {
            GlanceTheme(colors = WidgetColors.theme) {
                var root = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(22.dp)
                    .semantics { contentDescription = "Safar: ${snapshot.link}, odometer ${snapshot.odometer} km, range ${snapshot.range} km" }
                if (open != null) root = root.clickable(actionStartActivity(open))
                Column(root.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Head(snapshot)
                    Body(snapshot, GlanceModifier.defaultWeight())
                    Foot(snapshot)
                }
            }
        }
    }
}

private fun mono(color: ColorProvider, size: Float = 10f) = TextStyle(color = color, fontSize = size.sp, fontFamily = FontFamily.Monospace)

private fun serif(color: ColorProvider, size: Float, italic: Boolean = false) = TextStyle(
    color = color, fontSize = size.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium,
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
)

@Composable
private fun Head(s: WidgetSnapshot) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Safar", style = serif(WidgetColors.ink, 20f, italic = true).copy(fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.width(8.dp))
        Text(s.model.uppercase(), GlanceModifier.defaultWeight(), style = mono(WidgetColors.ink3), maxLines = 1)
        Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(WidgetColors.tone(s.tone))) {}
        Spacer(GlanceModifier.width(5.dp))
        Text(s.link.uppercase(), style = mono(WidgetColors.tone(s.tone)), maxLines = 1)
    }
}

@Composable
private fun Body(s: WidgetSnapshot, modifier: GlanceModifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
        Column(GlanceModifier.defaultWeight()) {
            Text("ODOMETER", style = mono(WidgetColors.ink3))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(s.odometer, style = serif(WidgetColors.ink, 34f), maxLines = 1)
                Text(" km", style = serif(WidgetColors.ink2, 14f, italic = true))
            }
            Text("TRIP A  ${s.tripA}", style = mono(WidgetColors.ink2, 11f))
        }
        Column(horizontalAlignment = Alignment.End) {
            if (s.bars != null) BarsGauge(s.bars, s.barsMax)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(s.range, style = serif(WidgetColors.ink, 22f), maxLines = 1)
                Text(" km", style = serif(WidgetColors.ink2, 11f, italic = true))
            }
            Text(s.rangeSource, style = mono(WidgetColors.ink3, 8f))
        }
    }
}

/** Rising fuel bars: ink, amber at one bar, rust outline-like ticks when empty. */
@Composable
private fun BarsGauge(bars: Int, max: Int) {
    val heights = widgetBarHeights(max, 30f)
    val on = when (bars) {
        0 -> WidgetColors.rust
        1 -> WidgetColors.amber
        else -> WidgetColors.ink
    }
    Row(GlanceModifier.height(30.dp), verticalAlignment = Alignment.Bottom) {
        heights.forEachIndexed { i, h ->
            if (i > 0) Spacer(GlanceModifier.width(3.dp))
            val color = if (i < bars) on else WidgetColors.rule2
            Box(GlanceModifier.width(8.dp).height(h.dp).cornerRadius(2.dp).background(color)) {}
        }
    }
}

@Composable
private fun Foot(s: WidgetSnapshot) {
    Box(GlanceModifier.fillMaxWidth().height(1.dp).background(WidgetColors.rule2)) {}
    Row(GlanceModifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val turn = s.turn
        if (turn == null) {
            Text(s.footer, style = mono(WidgetColors.ink3), maxLines = 1)
        } else {
            Text(turn.text, GlanceModifier.defaultWeight(), style = serif(WidgetColors.rust, 13f, italic = true), maxLines = 1)
            if (turn.distance != null) Text(turn.distance, style = mono(WidgetColors.ink, 12f))
        }
    }
}
