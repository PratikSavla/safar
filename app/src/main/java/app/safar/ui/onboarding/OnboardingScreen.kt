package app.safar.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.safar.data.ThemeMode
import app.safar.ui.art.AtlasIcons
import app.safar.ui.components.AtlasPage
import app.safar.ui.components.Gap
import app.safar.ui.components.PrimaryButton
import app.safar.ui.components.TextAction
import app.safar.ui.components.TopoTransform
import app.safar.ui.nav.Navigator
import app.safar.ui.nav.Route
import app.safar.ui.theme.Atlas
import app.safar.ui.theme.AtlasShapes
import app.safar.ui.theme.SafarTheme

private val PageEase = CubicBezierEasing(0.22f, 0.8f, 0.24f, 1f)

/** Onboarding (onboarding.js): three journal pages, then PAIR as the new root. */
@Composable
fun OnboardingScreen(navigator: Navigator, vm: OnboardingViewModel = viewModel { OnboardingViewModel.fromContainer() }) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    BackHandler(enabled = ui.step > 0) { vm.prev() }
    OnboardingContent(
        ui,
        onName = vm::setName,
        onPrev = { vm.prev() },
        onNext = { vm.next { navigator.resetTo(Route.Pair) } },
    )
}

@Composable
internal fun OnboardingContent(ui: OnboardingUi, onName: (String) -> Unit, onPrev: () -> Unit, onNext: () -> Unit) {
    val shift = with(LocalDensity.current) { 26.dp.roundToPx() }
    // Held here, not on page 2: the page leaves composition on Back or Next, and a permission Android no longer
    // asks for would read "not asked" again on return.
    val askedPermissions = rememberSaveable { mutableStateOf(emptySet<String>()) }
    AtlasPage(topo = TopoTransform.Onboarding) {
        Gap(40.dp)
        AnimatedContent(
            ui.step,
            transitionSpec = { (fadeIn(tween(500, easing = PageEase)) + slideInHorizontally(tween(500, easing = PageEase)) { shift }).togetherWith(fadeOut(tween(120))) },
            label = "obPage",
        ) { step ->
            Column(Modifier.fillMaxWidth()) {
                when (step) {
                    0 -> CoverPage()
                    1 -> PermissionsPage(askedPermissions)
                    else -> RiderPage(ui, onName)
                }
            }
        }
        Foot(ui, onPrev, onNext)
    }
}

/** Progress dashes, Back and the next action (onboard.css `.ob-foot`). */
@Composable
private fun Foot(ui: OnboardingUi, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Dots(ui.step)
        if (ui.step > 0) TextAction("Back", onPrev)
        Spacer(Modifier.weight(1f))
        PrimaryButton(ui.nextLabel, onNext, Modifier.widthIn(min = 150.dp), icon = AtlasIcons.arrow)
    }
}

@Composable
private fun Dots(step: Int) {
    val c = Atlas.colors
    Row(
        Modifier.clearAndSetSemantics { contentDescription = "Page ${step + 1} of $ONBOARDING_PAGES" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(ONBOARDING_PAGES) { i ->
            val color = when {
                i == step -> c.rust
                i < step -> c.ink2
                else -> c.rule2
            }
            Spacer(Modifier.size(if (i == step) 34.dp else 22.dp, 4.dp).background(color, AtlasShapes.tiny))
        }
    }
}

@Preview(heightDp = 1000)
@Composable
private fun OnboardingCoverPreview() = SafarTheme(ThemeMode.LIGHT) {
    OnboardingContent(OnboardingUi(0, "", false), {}, {}, {})
}

@Preview(heightDp = 900)
@Composable
private fun OnboardingRiderDarkPreview() = SafarTheme(ThemeMode.DARK) {
    OnboardingContent(OnboardingUi(2, "Alex", locked = true), {}, {}, {})
}
