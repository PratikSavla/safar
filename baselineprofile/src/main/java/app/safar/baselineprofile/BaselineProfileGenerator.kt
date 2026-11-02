package app.safar.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's Baseline Profile: cold start (onboarding on a fresh install), Today scrolled both ways,
 * every tab opened and scrolled, then Fuel and Service from the garage. Run on a device or emulator:
 * `./gradlew :app:generateBaselineProfile`. The rules land in app/src/main/generated/baselineProfiles.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = targetPackage, includeInStartupProfile = true) {
        startToHome()
        scrollPage()
        for (label in listOf("Route", "Journal", "Alerts", "Settings")) {
            openTab(label)
            scrollPage()
        }
        openTab("Today")
        visitGarage("Fuel")
        visitGarage("Service")
    }
}
