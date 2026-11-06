package app.safar.data.prefs

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.safar.core.ClusterReading

/** Keys of the saved bike's last [ClusterReading], stored beside the bike's name and address. */
private object ReadingKeys {
    val speed = intPreferencesKey("reading_speed_kmh")
    val odometer = intPreferencesKey("reading_odometer_km")
    val tripA = doublePreferencesKey("reading_trip_a_km")
    val tripB = doublePreferencesKey("reading_trip_b_km")
    val fuelBars = intPreferencesKey("reading_fuel_bars")
    val gear = stringPreferencesKey("reading_gear")
    val raw23 = intPreferencesKey("reading_raw23")
    val raw25 = intPreferencesKey("reading_raw25")
    val receivedAt = longPreferencesKey("reading_received_at")

    val all: List<Preferences.Key<*>> = listOf(speed, odometer, tripA, tripB, fuelBars, gear, raw23, raw25, receivedAt)
}

/** The stored reading, or null when none is kept or a required key is missing. */
internal fun readReading(prefs: Preferences): ClusterReading? {
    val odometer = prefs[ReadingKeys.odometer] ?: return null
    val receivedAt = prefs[ReadingKeys.receivedAt] ?: return null
    return ClusterReading(
        speedKmh = prefs[ReadingKeys.speed] ?: 0,
        odometerKm = odometer,
        tripAKm = prefs[ReadingKeys.tripA] ?: 0.0,
        tripBKm = prefs[ReadingKeys.tripB] ?: 0.0,
        fuelBars = prefs[ReadingKeys.fuelBars],
        gear = prefs[ReadingKeys.gear]?.firstOrNull(),
        raw23 = prefs[ReadingKeys.raw23] ?: 0,
        raw25 = prefs[ReadingKeys.raw25] ?: 0,
        receivedAtMillis = receivedAt,
    )
}

/** Replaces the stored reading with [reading]; a null fuel or gear removes its key. */
internal fun writeReading(prefs: MutablePreferences, reading: ClusterReading) {
    prefs[ReadingKeys.speed] = reading.speedKmh
    prefs[ReadingKeys.odometer] = reading.odometerKm
    prefs[ReadingKeys.tripA] = reading.tripAKm
    prefs[ReadingKeys.tripB] = reading.tripBKm
    reading.fuelBars?.let { prefs[ReadingKeys.fuelBars] = it } ?: prefs.remove(ReadingKeys.fuelBars)
    reading.gear?.let { prefs[ReadingKeys.gear] = it.toString() } ?: prefs.remove(ReadingKeys.gear)
    prefs[ReadingKeys.raw23] = reading.raw23
    prefs[ReadingKeys.raw25] = reading.raw25
    prefs[ReadingKeys.receivedAt] = reading.receivedAtMillis
}

/** Removes every key of the stored reading. */
internal fun removeReading(prefs: MutablePreferences) {
    ReadingKeys.all.forEach { prefs.remove(it) }
}
