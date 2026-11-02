package app.safar.protocol

/*
 * Vehicle detection from the BLE advertised name (port of docs/helper/src/vehicle.ts).
 *
 * The cluster name has 12 characters and contains "SA" or "SB" (pattern "SBM01xxxxxxx" is a V-Strom SX).
 * Characters [1..4] are the model code. Character [1] also selects the checksum mode.
 */

enum class VehicleType(val label: String) { SCOOTER("Scooter"), MOTORCYCLE("Motorcycle") }

data class Vehicle(
    /** Model code: characters [1..4] of the BLE name. */
    val code: String,
    val name: String,
    val type: VehicleType,
    val checksum: ChecksumMode,
)

/** Length of the cluster's advertised BLE name. */
const val BLE_NAME_LENGTH = 12

val VEHICLES: List<Vehicle> = listOf(
    Vehicle("AS01", "Access 125", VehicleType.SCOOTER, ChecksumMode.INVERTED),
    Vehicle("AS02", "Access 125", VehicleType.SCOOTER, ChecksumMode.INVERTED),
    Vehicle("AS11", "Burgman Street", VehicleType.SCOOTER, ChecksumMode.INVERTED),
    Vehicle("AS12", "Burgman Street", VehicleType.SCOOTER, ChecksumMode.INVERTED),
    Vehicle("BS11", "Burgman Street", VehicleType.SCOOTER, ChecksumMode.SUM),
    Vehicle("BS21", "Avenis", VehicleType.SCOOTER, ChecksumMode.SUM),
    Vehicle("BS31", "Burgman Street EX", VehicleType.SCOOTER, ChecksumMode.SUM),
    Vehicle("BM01", "V-STROM SX", VehicleType.MOTORCYCLE, ChecksumMode.SUM),
    Vehicle("BM11", "GIXXER / GIXXER SF", VehicleType.MOTORCYCLE, ChecksumMode.SUM),
    Vehicle("BM21", "GIXXER 250 / GIXXER SF 250", VehicleType.MOTORCYCLE, ChecksumMode.SUM),
)

/** Same filter as the original scan list: 12 characters and contains "SA" or "SB". */
fun isSuzukiCluster(bleName: String?): Boolean {
    if (bleName == null || bleName.length != BLE_NAME_LENGTH) return false
    return "SA" in bleName || "SB" in bleName
}

/** Checksum mode from the name. Char [1] 'A' = inverted, anything else = sum. */
fun checksumModeFor(bleName: String): ChecksumMode =
    if (bleName.getOrNull(1) == 'A') ChecksumMode.INVERTED else ChecksumMode.SUM

/** Model code (chars [1..4]) of a BLE name; shorter names give what is there, like JS `slice(1, 5)`. */
fun modelCode(bleName: String): String =
    if (bleName.length <= 1) "" else bleName.substring(1, minOf(5, bleName.length))

/** Returns null for an unknown model code. Use [checksumModeFor] for the checksum in that case. */
fun identifyVehicle(bleName: String): Vehicle? {
    val code = modelCode(bleName)
    return VEHICLES.firstOrNull { it.code == code }
}
