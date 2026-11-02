package app.safar.ui.pair

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.safar.core.VehicleFamily
import app.safar.protocol.sanitizeName
import app.safar.ui.art.AtlasIcons
import app.safar.ui.art.BikeSilhouette
import app.safar.ui.components.AtlasCard
import app.safar.ui.components.DisplayTitle
import app.safar.ui.components.InkButton
import app.safar.ui.components.Kicker
import app.safar.ui.components.Note
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.SecondaryButton
import app.safar.ui.components.Stamp
import app.safar.ui.theme.Atlas
import java.util.Locale

private val PageEase = CubicBezierEasing(0.22f, 0.8f, 0.24f, 1f)

@Composable
internal fun Connecting(ui: PairUi) {
    PairLink(ui.model?.family ?: VehicleFamily.MOTORCYCLE)
    DisplayTitle(
        "Connecting", " to the ${ui.model?.name ?: "bike"}…", Atlas.type.displayMedium,
        Modifier.fillMaxWidth().semantics { heading() },
    )
    HandshakeSteps(ui.mtuSet)
}

/** "Paired" stamp lands, the bike, and how the cluster greets the rider (pair.js `success`). */
@Composable
internal fun PairSuccess(ui: PairUi, onDone: () -> Unit) {
    val c = Atlas.colors
    val land = remember { Animatable(0f) }
    LaunchedEffect(Unit) { land.animateTo(1f, tween(1000, easing = PageEase)) }
    val greeting = sanitizeName(ui.riderName.uppercase(Locale.ROOT))
    Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Stamp(
            "paired",
            Modifier.graphicsLayer {
                val s = 2.2f - 1.2f * land.value
                scaleX = s; scaleY = s; alpha = land.value; rotationZ = -4.5f
            },
            solid = true,
        )
        BikeSilhouette(ui.model?.family ?: VehicleFamily.MOTORCYCLE, Modifier.padding(vertical = 8.dp), width = 200.dp, contentDescription = ui.model?.name)
        DisplayTitle(ui.model?.name ?: "Suzuki", null, Atlas.type.displayMedium, Modifier.semantics { heading() })
        BasicText(listOfNotNull(ui.bikeName, ui.address).joinToString(" · "), style = Atlas.type.src.copy(color = c.ink3, textAlign = TextAlign.Center))
        BasicText(
            buildAnnotatedString {
                append("No PIN, no code. ")
                if (greeting.isNotEmpty()) {
                    append("The cluster now says ")
                    withStyle(SpanStyle(fontWeight = FontWeight(600), color = c.ink)) { append("WELCOME $greeting") }
                    append(".")
                } else {
                    append("Add your name in Settings and the cluster greets you by it.")
                }
            },
            style = Atlas.type.lede.copy(color = c.ink2, textAlign = TextAlign.Center),
        )
        Note("Next time, Safar reconnects by itself when you turn the key.")
        PrimaryButton("Open today’s page", onDone, Modifier.fillMaxWidth().padding(top = 18.dp), icon = AtlasIcons.arrow)
    }
}

/** Connected already: keep this bike unless the rider picks another (pair.js `conn-already`). */
@Composable
internal fun AlreadyLinked(ui: PairUi, a: PairActions) {
    AtlasCard(Modifier.padding(bottom = 16.dp)) {
        Kicker("Already linked")
        BasicText(ui.model?.name ?: "Suzuki", Modifier.padding(vertical = 6.dp), style = Atlas.type.displaySmall.copy(color = Atlas.colors.ink))
        Note("Scanning again keeps this bike until you pick another one.")
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Scan anyway", a.scan, Modifier.weight(1f))
            InkButton("Today", a.home, Modifier.weight(1f))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ui.results.forEach { bike -> ScanResult(bike) { a.pick(bike.address) } }
    }
}
