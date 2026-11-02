package app.safar.phone

// Pure mappings for the status frame (docs 6.3). No Android imports.

/**
 * Cellular bars 0..3 from `SignalStrength.getLevel()` 0..4: 0-1 -> 0, 2 -> 1, 3 -> 2, 4 -> 3.
 * 0 in airplane mode, without a ready SIM, or when the level is unknown.
 */
fun signalBars(level: Int?, airplane: Boolean, simReady: Boolean): Int {
    if (airplane || !simReady || level == null) return 0
    return (level.coerceIn(0, 4) - 1).coerceAtLeast(0)
}

/** Battery percent 0..100 from `BatteryManager.EXTRA_LEVEL` / `EXTRA_SCALE`; null when either is missing. */
fun batteryPercent(level: Int, scale: Int): Int? {
    if (level < 0 || scale <= 0) return null
    return (level * 100 / scale).coerceIn(0, 100)
}

/** `BatteryManager.BATTERY_STATUS_CHARGING` and `BATTERY_STATUS_FULL`. */
private const val STATUS_CHARGING = 2
private const val STATUS_FULL = 5

/** Charging when the battery reports charging or full, or a charger is plugged in. */
fun isCharging(status: Int, plugged: Int): Boolean = status == STATUS_CHARGING || status == STATUS_FULL || plugged > 0
