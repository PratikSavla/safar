package app.safar.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.safar.core.AlertSource
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasIcon
import app.safar.ui.components.Stamp
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes

private val PageEase = CubicBezierEasing(0.22f, 0.8f, 0.24f, 1f)

/** Height the telegram takes at the top, so pages can move down while it shows. */
internal val TelegramSpace = 76.dp

/**
 * Incoming call telegram (chrome.css `.telegram`): rust slip under the status bar, caller and state,
 * a pulsing ring while it rings. Tapping it opens Alerts.
 */
@Composable
internal fun CallTelegram(banner: CallBanner?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val last = remember { arrayOfNulls<CallBanner>(1) }
    if (banner != null) last[0] = banner
    AnimatedVisibility(
        banner != null,
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 12.dp, vertical = 4.dp),
        enter = slideInVertically(tween(400, easing = PageEase)) { -it * 2 },
        exit = slideOutVertically(tween(400, easing = PageEase)) { -it * 2 },
    ) {
        last[0]?.let { TelegramBody(it, onOpen) }
    }
}

@Composable
private fun TelegramBody(b: CallBanner, onOpen: () -> Unit) {
    val c = Atlas.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .dropShadow(AtlasShapes.small, Shadow(radius = 30.dp, spread = (-12).dp, offset = DpOffset(0.dp, 14.dp), color = c.rust.copy(alpha = 0.45f)))
            .background(c.rust, AtlasShapes.small)
            .clickable(onClickLabel = "Open alerts", role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val icon = if (b.source == AlertSource.WHATSAPP) AtlasIcons.wa else AtlasIcons.call
        AtlasIcon(icon, tint = c.onRust, size = 26.dp, contentDescription = if (b.source == AlertSource.WHATSAPP) "WhatsApp call" else "Phone call")
        Column(Modifier.weight(1f)) {
            BasicText(b.name, style = Atlas.type.serifTitle.copy(color = c.onRust, fontWeight = FontWeight(600)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(b.status, style = Atlas.type.src.copy(color = c.onRust.copy(alpha = 0.85f)), maxLines = 1)
        }
        if (b.ringing) RingPulse()
    }
}

@Composable
private fun RingPulse() {
    val ink = Atlas.colors.onRust
    val t = rememberInfiniteTransition(label = "ring")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart), label = "ringPhase")
    Canvas(Modifier.size(28.dp)) {
        val r = 5.dp.toPx()
        drawCircle(ink.copy(alpha = 0.7f * (1 - p)), r + 12.dp.toPx() * p, Offset(size.width / 2, size.height / 2))
        drawCircle(ink, r, Offset(size.width / 2, size.height / 2))
    }
}

/**
 * "Ride mode · auto" stamp that lands and fades (chrome.css `.ride-flash`). [serial] changes for each flash.
 */
@Composable
internal fun RideFlash(serial: Int) {
    if (serial == 0) return
    val anim = remember(serial) { Animatable(0f) }
    LaunchedEffect(serial) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(FLASH_MS, easing = LinearEasing))
    }
    val p = anim.value
    if (p >= 1f) return
    // 0..18 %: scale 2.4 -> 1.5 and fade in; hold; 80..100 %: fade out.
    val alpha = when {
        p < 0.18f -> p / 0.18f
        p > 0.8f -> (1f - p) / 0.2f
        else -> 1f
    }
    val scale = if (p < 0.18f) 2.4f - 0.9f * (p / 0.18f) else 1.5f
    Box(Modifier.fillMaxSize().semantics { liveRegion = LiveRegionMode.Polite }, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale; rotationZ = -6f }
                .background(Atlas.colors.paper, AtlasShapes.stamp),
        ) {
            Stamp("Ride mode · auto", tilted = false, solid = true)
        }
    }
}

private const val FLASH_MS = 1700
