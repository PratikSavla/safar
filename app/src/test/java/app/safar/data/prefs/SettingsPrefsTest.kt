package app.safar.data.prefs

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.safar.data.Settings
import app.safar.data.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsPrefsTest {
    @Test
    fun emptyPreferencesGiveDefaults() {
        assertEquals(Settings(), readSettings(emptyPreferences()))
    }

    @Test
    fun everyFieldRoundTrips() {
        val custom = Settings(
            onboarded = true, riderName = "Alex", speedAlertOn = true, speedAlertKmh = 85, alertCalls = false,
            alertWhatsAppCalls = false, alertWhatsAppMessages = false, debugLog = true, hideNames = false,
            theme = ThemeMode.DARK, keepScreenOn = false, autoRideMode = false, serviceReminders = false,
            lowFuelAlert = false, tankLitres = 11.5, currencySymbol = "$", developerMode = true,
            dismissedPermissions = setOf("PHONE", "LOCATION"),
        )
        val prefs = mutablePreferencesOf()
        writeSettings(prefs, custom)
        assertEquals(custom, readSettings(prefs))
    }

    @Test
    fun developerModeIsOffByDefaultAndPersists() {
        assertEquals(false, readSettings(emptyPreferences()).developerMode)
        val prefs = mutablePreferencesOf()
        writeSettings(prefs, Settings(developerMode = true))
        assertEquals(true, prefs[SettingsKeys.DEVELOPER_MODE])
        assertTrue(readSettings(prefs).developerMode)
        writeSettings(prefs, Settings(developerMode = false))
        assertEquals(false, readSettings(prefs).developerMode)
    }

    @Test
    fun clearingTankOverrideRemovesKey() {
        val prefs = mutablePreferencesOf()
        writeSettings(prefs, Settings(tankLitres = 9.0))
        writeSettings(prefs, Settings(tankLitres = null))
        assertNull(prefs[SettingsKeys.TANK_LITRES])
        assertNull(readSettings(prefs).tankLitres)
    }

    @Test
    fun unknownThemeFallsBackToSystem() {
        val prefs = mutablePreferencesOf(SettingsKeys.THEME to "NEON")
        assertEquals(ThemeMode.SYSTEM, readSettings(prefs).theme)
    }

    @Test
    fun ioErrorIsRetriedWithBackoffUntilReadSucceeds() = runTest {
        var reads = 0
        val flaky = flow {
            reads++
            if (reads <= 3) throw IOException("disk")
            emit(mutablePreferencesOf(SettingsKeys.ONBOARDED to true) as Preferences)
        }
        val prefs = flaky.retryOnIoError("settings").first()
        assertTrue(readSettings(prefs).onboarded)
        assertEquals(4, reads)
        // Waits of 500, 1000 and 2000 ms on the virtual clock.
        assertEquals(3_500, testScheduler.currentTime)
    }

    @Test
    fun retryDelayIsCapped() = runTest {
        var reads = 0
        val flaky = flow {
            reads++
            if (reads <= 8) throw IOException("disk")
            emit(emptyPreferences())
        }
        flaky.retryOnIoError("settings", firstDelayMs = 500, maxDelayMs = 4_000).first()
        // 500 + 1000 + 2000 + 4000 * 5.
        assertEquals(23_500, testScheduler.currentTime)
    }

    @Test
    fun otherErrorsPropagate() = runTest {
        val broken = flow<Preferences> { throw IllegalStateException("bug") }
        assertFailsWith<IllegalStateException> { broken.retryOnIoError("settings").first() }
    }
}
