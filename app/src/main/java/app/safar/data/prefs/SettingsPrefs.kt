package app.safar.data.prefs

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.safar.data.Settings
import app.safar.data.ThemeMode

/** Typed DataStore keys for [Settings]. Key names are stored on disk: never rename one. */
internal object SettingsKeys {
    val ONBOARDED = booleanPreferencesKey("onboarded")
    val RIDER_NAME = stringPreferencesKey("rider_name")
    val SPEED_ALERT_ON = booleanPreferencesKey("speed_alert_on")
    val SPEED_ALERT_KMH = intPreferencesKey("speed_alert_kmh")
    val ALERT_CALLS = booleanPreferencesKey("alert_calls")
    val ALERT_WA_CALLS = booleanPreferencesKey("alert_whatsapp_calls")
    val ALERT_WA_MESSAGES = booleanPreferencesKey("alert_whatsapp_messages")
    val DEBUG_LOG = booleanPreferencesKey("debug_log")
    val HIDE_NAMES = booleanPreferencesKey("hide_names")
    val THEME = stringPreferencesKey("theme")
    val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    val AUTO_RIDE_MODE = booleanPreferencesKey("auto_ride_mode")
    val SERVICE_REMINDERS = booleanPreferencesKey("service_reminders")
    val LOW_FUEL_ALERT = booleanPreferencesKey("low_fuel_alert")
    val DOCUMENT_REMINDERS = booleanPreferencesKey("document_reminders")
    val TANK_LITRES = doublePreferencesKey("tank_litres")
    val CURRENCY = stringPreferencesKey("currency_symbol")
    val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
    val DISMISSED_PERMISSIONS = stringSetPreferencesKey("dismissed_permissions")
}

private val DEFAULTS = Settings()

/** Reads [Settings] from [prefs]; a missing key, or an unknown theme name, takes the default. */
internal fun readSettings(prefs: Preferences): Settings = with(SettingsKeys) {
    Settings(
        onboarded = prefs[ONBOARDED] ?: DEFAULTS.onboarded,
        riderName = prefs[RIDER_NAME] ?: DEFAULTS.riderName,
        speedAlertOn = prefs[SPEED_ALERT_ON] ?: DEFAULTS.speedAlertOn,
        speedAlertKmh = prefs[SPEED_ALERT_KMH] ?: DEFAULTS.speedAlertKmh,
        alertCalls = prefs[ALERT_CALLS] ?: DEFAULTS.alertCalls,
        alertWhatsAppCalls = prefs[ALERT_WA_CALLS] ?: DEFAULTS.alertWhatsAppCalls,
        alertWhatsAppMessages = prefs[ALERT_WA_MESSAGES] ?: DEFAULTS.alertWhatsAppMessages,
        debugLog = prefs[DEBUG_LOG] ?: DEFAULTS.debugLog,
        hideNames = prefs[HIDE_NAMES] ?: DEFAULTS.hideNames,
        theme = ThemeMode.entries.firstOrNull { it.name == prefs[THEME] } ?: DEFAULTS.theme,
        keepScreenOn = prefs[KEEP_SCREEN_ON] ?: DEFAULTS.keepScreenOn,
        autoRideMode = prefs[AUTO_RIDE_MODE] ?: DEFAULTS.autoRideMode,
        serviceReminders = prefs[SERVICE_REMINDERS] ?: DEFAULTS.serviceReminders,
        lowFuelAlert = prefs[LOW_FUEL_ALERT] ?: DEFAULTS.lowFuelAlert,
        documentReminders = prefs[DOCUMENT_REMINDERS] ?: DEFAULTS.documentReminders,
        tankLitres = prefs[TANK_LITRES] ?: DEFAULTS.tankLitres,
        currencySymbol = prefs[CURRENCY] ?: DEFAULTS.currencySymbol,
        developerMode = prefs[DEVELOPER_MODE] ?: DEFAULTS.developerMode,
        dismissedPermissions = prefs[DISMISSED_PERMISSIONS] ?: DEFAULTS.dismissedPermissions,
    )
}

/** Writes every field of [s] into [prefs]. A null tank override removes its key. */
internal fun writeSettings(prefs: MutablePreferences, s: Settings) = with(SettingsKeys) {
    prefs[ONBOARDED] = s.onboarded
    prefs[RIDER_NAME] = s.riderName
    prefs[SPEED_ALERT_ON] = s.speedAlertOn
    prefs[SPEED_ALERT_KMH] = s.speedAlertKmh
    prefs[ALERT_CALLS] = s.alertCalls
    prefs[ALERT_WA_CALLS] = s.alertWhatsAppCalls
    prefs[ALERT_WA_MESSAGES] = s.alertWhatsAppMessages
    prefs[DEBUG_LOG] = s.debugLog
    prefs[HIDE_NAMES] = s.hideNames
    prefs[THEME] = s.theme.name
    prefs[KEEP_SCREEN_ON] = s.keepScreenOn
    prefs[AUTO_RIDE_MODE] = s.autoRideMode
    prefs[SERVICE_REMINDERS] = s.serviceReminders
    prefs[LOW_FUEL_ALERT] = s.lowFuelAlert
    prefs[DOCUMENT_REMINDERS] = s.documentReminders
    val tank = s.tankLitres
    if (tank == null) prefs.remove(TANK_LITRES) else prefs[TANK_LITRES] = tank
    prefs[CURRENCY] = s.currencySymbol
    prefs[DEVELOPER_MODE] = s.developerMode
    prefs[DISMISSED_PERMISSIONS] = s.dismissedPermissions
}
