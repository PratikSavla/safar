package app.safar.bikesim.sim

import app.safar.protocol.ChecksumMode
import app.safar.protocol.checksumModeFor
import app.safar.protocol.identifyVehicle
import app.safar.protocol.isSuzukiCluster

/** Default advertised name: a V-Strom SX code with a synthetic suffix. */
const val DEFAULT_SIM_NAME = "SBM01A1B2C3D"

/** Advertised names the simulator offers. Char [1] 'A' selects the inverted checksum (docs 1.4). */
val NAME_PRESETS: List<String> = listOf(DEFAULT_SIM_NAME, "SBM11A1B2C3D", "SBS21A1B2C3D", "SAS01A1B2C3D")

/** What a name means to Safar: does its scan filter match, which vehicle and which checksum mode. */
data class NameInfo(val name: String, val matchesFilter: Boolean, val vehicle: String?, val mode: ChecksumMode) {
    val summary: String
        get() {
            val filter = if (matchesFilter) "matches the Safar filter" else "Safar ignores it (12 chars with SA/SB)"
            val checksum = if (mode == ChecksumMode.INVERTED) "inverted checksum" else "sum checksum"
            return "${vehicle ?: "unknown model"} · $checksum · $filter"
        }
}

fun nameInfo(name: String): NameInfo =
    NameInfo(name, isSuzukiCluster(name), identifyVehicle(name)?.name, checksumModeFor(name))
