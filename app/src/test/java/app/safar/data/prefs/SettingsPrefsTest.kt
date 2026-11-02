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
import kotlin.test.assertFalse
import kotlin.test.assertNull

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
            lowFuelAlert = false, tankLitres = 11.5, currencySymbol = "$",
        )
        val prefs = mutablePreferencesOf()
        writeSettings(prefs, custom)
        assertEquals(custom, readSettings(prefs))
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
    fun ioErrorReadsAsEmpty() = runTest {
        val broken = flow<Preferences> { throw IOException("disk") }
        val prefs = broken.orEmptyOnIoError("settings").first()
        assertEquals(Settings(), readSettings(prefs))
        assertFalse(readSettings(prefs).onboarded)
    }

    @Test
    fun otherErrorsPropagate() = runTest {
        val broken = flow<Preferences> { throw IllegalStateException("bug") }
        assertFailsWith<IllegalStateException> { broken.orEmptyOnIoError("settings").first() }
    }
}
