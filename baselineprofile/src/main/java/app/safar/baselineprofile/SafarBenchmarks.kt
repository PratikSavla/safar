package app.safar.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val STARTUP_RUNS = 10
private const val SCROLL_RUNS = 5

/**
 * Cold start with and without the Baseline Profile. Run on a device:
 * `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = STARTUP_RUNS,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }
}

/** Frame timing while Today scrolls down and up, with and without the Baseline Profile. */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollHomeNoCompilation() = scrollHome(CompilationMode.None())

    @Test
    fun scrollHomeBaselineProfile() = scrollHome(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun scrollHome(mode: CompilationMode) = rule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = SCROLL_RUNS,
        setupBlock = { startToHome() },
    ) {
        scrollPage()
    }
}
