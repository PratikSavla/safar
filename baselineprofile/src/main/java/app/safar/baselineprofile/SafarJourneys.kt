package app.safar.baselineprofile

import android.os.SystemClock
import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

// Shared UiAutomator journeys for the profile generator and the benchmarks. Safar has no test tags:
// widgets are found by the text and content descriptions TalkBack reads (ui/onboarding, ui/home, the stub tabs).

private const val TAG = "SafarJourneys"
private const val WAIT_MS = 5_000L
private const val START_WAIT_MS = 10_000L
private const val POLL_MS = 100L

/** Package under test, passed by the Gradle build as `targetAppId`. */
val targetPackage: String
    get() = InstrumentationRegistry.getArguments().getString("targetAppId") ?: "app.safar"

/** The five stub tabs, read as "<label>" or "<label>, 3 new" / "<label>, navigating". */
private fun tab(label: String): BySelector = By.desc(Pattern.compile("^${Pattern.quote(label)}(,.*)?$"))

/** Cold start to Today. A fresh install shows onboarding first: it is completed, then the app restarts on Today. */
fun MacrobenchmarkScope.startToHome() {
    pressHome()
    startActivityAndWait()
    if (awaitOnboardingOrToday()) {
        completeOnboarding()
        // Onboarding ends on the pairing page; with no bike nearby the next launch opens Today.
        killProcess()
        pressHome()
        startActivityAndWait()
    }
    check(device.wait(Until.hasObject(tab("Today")), START_WAIT_MS)) { "Today did not open" }
}

/** Waits for whichever first screen appears: true for onboarding ("Begin"), false for Today or on timeout. */
private fun MacrobenchmarkScope.awaitOnboardingOrToday(): Boolean {
    val deadline = SystemClock.uptimeMillis() + START_WAIT_MS
    while (SystemClock.uptimeMillis() < deadline) {
        if (device.hasObject(tab("Today"))) return false
        if (device.hasObject(By.text("Begin"))) return true
        SystemClock.sleep(POLL_MS)
    }
    return false
}

private fun MacrobenchmarkScope.completeOnboarding() {
    for (label in listOf("Begin", "Continue", "Find my bike")) {
        val button = device.wait(Until.findObject(By.text(label)), WAIT_MS) ?: error("Onboarding button \"$label\" not found")
        button.click()
        device.waitForIdle()
    }
    device.wait(Until.hasObject(By.text("Scan for my bike")), WAIT_MS)
}

/** The page's vertical scroller (AtlasPage), kept clear of the gesture-navigation edges. */
private fun MacrobenchmarkScope.pageScroller(): UiObject2? =
    device.wait(Until.findObject(By.scrollable(true)), WAIT_MS)?.also { it.setGestureMargin(device.displayWidth / 5) }

/** Flings the current page to the bottom and back to the top. */
fun MacrobenchmarkScope.scrollPage() {
    val scroller = pageScroller()
    if (scroller == null) {
        Log.w(TAG, "No scrollable page on screen")
        return
    }
    repeat(2) { scroller.fling(Direction.DOWN) }
    device.waitForIdle()
    repeat(2) { scroller.fling(Direction.UP) }
    device.waitForIdle()
}

/** Taps a stub tab: Today, Route, Journal, Alerts or Settings. */
fun MacrobenchmarkScope.openTab(label: String) {
    val stub = device.wait(Until.findObject(tab(label)), WAIT_MS) ?: error("Tab \"$label\" not found")
    stub.click()
    device.waitForIdle()
}

/** Opens a garage tile on Today ("Fuel, 120 km left", "Service, not set up"), scrolls it, and comes back. */
fun MacrobenchmarkScope.visitGarage(name: String) {
    val tile = By.desc(Pattern.compile("^${Pattern.quote(name)}, .*"))
    val found = device.findObject(tile) ?: pageScroller()?.scrollUntil(Direction.DOWN, Until.findObject(tile))
    if (found == null) {
        Log.w(TAG, "Garage tile \"$name\" not found")
        return
    }
    found.click()
    device.waitForIdle()
    scrollPage()
    device.pressBack()
    device.wait(Until.hasObject(tab("Today")), WAIT_MS)
}
