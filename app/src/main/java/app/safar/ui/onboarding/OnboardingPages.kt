package app.safar.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.safar.core.VehicleFamily
import app.safar.ui.art.BikeSilhouette
import app.safar.ui.art.LcdNamePreview
import app.safar.ui.components.AtlasTextField
import app.safar.ui.components.DashedRule
import app.safar.ui.components.Kicker
import app.safar.ui.components.Lede
import app.safar.ui.components.Note
import app.safar.ui.components.Stamp
import app.safar.ui.components.StampTone
import app.safar.ui.theme.Atlas
import java.util.Locale

private val PageEase = CubicBezierEasing(0.22f, 0.8f, 0.24f, 1f)
private const val ROUTE_PATH = "M10 100 C 70 100, 80 30, 150 40 S 240 110, 300 60 S 340 20, 352 18"
private const val NAME_FIELD_MAX = 40

/** Page title with its light italic second line (onboard.css `.ob-title`). */
@Composable
internal fun ObTitle(kicker: String, title: String, em: String) {
    val c = Atlas.colors
    val big = Atlas.type.displayLarge
    Kicker(kicker)
    Column(Modifier.padding(top = 14.dp, bottom = 16.dp).semantics(mergeDescendants = true) { heading() }) {
        BasicText(title, style = big.copy(color = c.ink))
        BasicText(
            em,
            Modifier.padding(top = 6.dp),
            style = big.copy(fontSize = big.fontSize * 0.62f, lineHeight = big.fontSize * 0.7f, fontStyle = FontStyle.Italic, fontWeight = FontWeight(330), color = c.ink2, letterSpacing = (-0.01).em),
        )
    }
}

/** Page 1: what Safar reads, shows and keeps, and the three promises. */
@Composable
internal fun CoverPage() {
    ObTitle("1 of 3 · welcome", "Safar", "turns and calls on your Suzuki’s display.")
    RouteArt()
    listOf(
        "Reads" to "Odometer, trips, fuel and gear from your bike.",
        "Shows" to "Google Maps turns, who’s calling, WhatsApp alerts, the time and your name on your bike’s display.",
        "Keeps" to "Your trips, fuel history, service dates and where you parked. On this phone only.",
    ).forEach { (k, v) ->
        DashedRule(thickness = 1.dp, dash = 4.dp, gap = 3.dp)
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp).semantics(mergeDescendants = true) { }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText(k.uppercase(Locale.ROOT), Modifier.width(64.dp).padding(top = 3.dp), style = Atlas.type.kicker.copy(color = Atlas.colors.rust))
            BasicText(v, Modifier.weight(1f), style = Atlas.type.lede.copy(fontSize = Atlas.type.lede.fontSize * 0.96f, color = Atlas.colors.ink))
        }
    }
    FlowRow(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stamp("no account")
        Stamp("no cloud", Modifier.rotate(4.5f), tone = StampTone.MOSS)
        Stamp("no tracking", Modifier.rotate(1.5f), tone = StampTone.SLATE)
    }
}

/** A dotted rust route drawing itself towards the bike (onboard.css `.ob-art`). Decorative. */
@Composable
private fun RouteArt() {
    val c = Atlas.colors
    val path = remember { PathParser().parsePathString(ROUTE_PATH).toPath() }
    val measure = remember(path) { PathMeasure().apply { setPath(path, false) } }
    val drawn = remember { Animatable(0f) }
    LaunchedEffect(Unit) { drawn.animateTo(1f, tween(2000, easing = PageEase)) }
    Box(Modifier.fillMaxWidth().height(120.dp).padding(bottom = 10.dp).clearAndSetSemantics { }) {
        Canvas(Modifier.fillMaxSize()) {
            val part = Path()
            measure.getSegment(0f, measure.length * drawn.value, part, true)
            scale(size.width / 360f, size.height / 120f, pivot = Offset.Zero) {
                drawPath(
                    part, c.rust,
                    style = Stroke(2.5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f))),
                )
            }
        }
        BikeSilhouette(VehicleFamily.MOTORCYCLE, Modifier.align(Alignment.TopEnd).padding(end = 8.dp), width = 92.dp)
    }
}

/** Page 2: the permissions explainer. */
@Composable
internal fun PermissionsPage(asked: MutableState<Set<String>>) {
    ObTitle("2 of 3 · permissions", "What Safar asks for", "and why.")
    PermissionsList(asked)
}

/** Page 3: the rider name with a live LCD preview. Locked while moving (UX rule 3). */
@Composable
internal fun RiderPage(ui: OnboardingUi, onName: (String) -> Unit) {
    ObTitle("3 of 3 · your name", "Your name", "for your bike’s welcome.")
    Lede("Your bike’s display says WELCOME and your name each time it connects.")
    AtlasTextField(
        value = ui.name,
        onValueChange = onName,
        label = "Rider name",
        modifier = Modifier.padding(top = 10.dp),
        lockedReason = if (ui.locked) MOVING_LOCK_REASON else null,
        placeholder = "Your name",
        maxLength = NAME_FIELD_MAX,
        trailing = { BasicText("${ui.cleaned.length}/19", style = Atlas.type.src.copy(color = Atlas.colors.ink3)) },
    )
    LcdNamePreview(ui.cleaned, Modifier.padding(top = 26.dp, bottom = 16.dp))
    Note("The display shows capital letters and numbers, up to 19. Other characters become spaces.")
}
