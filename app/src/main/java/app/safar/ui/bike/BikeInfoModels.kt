package app.safar.ui.bike

import app.safar.core.BikeCatalog
import app.safar.core.BikeModel
import app.safar.core.BikeState
import app.safar.core.DeviceInfo
import app.safar.core.LinkState
import app.safar.core.VehicleFamily
import app.safar.data.SavedBikeRecord
import app.safar.protocol.BIKE_MTU
import app.safar.protocol.ChecksumMode
import app.safar.protocol.GattUuids
import app.safar.protocol.checksumModeFor
import java.util.Locale
import java.util.UUID

/** What the Bike information page shows for the saved cluster. */
data class BikeInfo(
    val name: String,
    val address: String,
    val model: BikeModel,
    val connected: Boolean,
    val linkWord: String,
    val deviceInfo: DeviceInfo?,
    val mtu: String,
    val checksum: String,
)

/** The saved bike from the BLE layer, else from the saved-bike store; null when no bike is paired. */
fun bikeInfo(state: BikeState, saved: SavedBikeRecord?): BikeInfo? {
    val name = state.saved?.name ?: saved?.name ?: return null
    val address = state.saved?.address ?: saved?.address.orEmpty()
    val model = state.saved?.model ?: BikeCatalog.forBleName(name)
    val connected = state.link == LinkState.Connected
    return BikeInfo(
        name = name,
        address = address.ifBlank { "—" },
        model = model,
        connected = connected,
        linkWord = linkWord(state.link),
        deviceInfo = state.deviceInfo,
        mtu = state.mtu?.let { "$it" } ?: "$BIKE_MTU requested",
        checksum = checksumLabel(name),
    )
}

fun linkWord(link: LinkState): String = when (link) {
    LinkState.Idle -> "Not connected"
    LinkState.BluetoothOff -> "Bluetooth off"
    LinkState.Searching -> "Searching"
    is LinkState.Connecting -> "Connecting"
    LinkState.Connected -> "Connected"
    is LinkState.Reconnecting -> "Reconnecting"
}

/** Checksum mode by model code (docs 1.3): 'A' models invert the sum. */
fun checksumLabel(bleName: String): String = when (checksumModeFor(bleName)) {
    ChecksumMode.INVERTED -> "inverted (0xFF − sum)"
    ChecksumMode.SUM -> "sum of bytes 1–27"
}

fun familyLabel(family: VehicleFamily): String = if (family == VehicleFamily.SCOOTER) "scooter" else "motorcycle"

/** "FFF0" from a 16-bit SIG-base UUID. */
fun shortUuid(uuid: UUID): String = uuid.toString().substring(4, 8).uppercase(Locale.ROOT)

/** Device Information service rows (0x180A), "—" when the cluster did not report the field. */
fun deviceRows(info: DeviceInfo?): List<Pair<String, String>> {
    val d = info ?: DeviceInfo()
    return listOf(
        "Manufacturer" to d.manufacturer,
        "Model number" to d.modelNumber,
        "Serial" to d.serial,
        "Firmware" to d.firmware,
        "Hardware rev." to d.hardware,
        "Software rev." to d.software,
        "PnP ID" to d.pnpId,
    ).map { (k, v) -> k to (v?.takeIf { it.isNotBlank() } ?: "—") }
}

/** BLE facts for the link card. */
fun linkRows(b: BikeInfo): List<Pair<String, String>> = listOf(
    "BLE name" to b.name,
    "MAC" to b.address,
    "Model" to "${b.model.name} · ${familyLabel(b.model.family)}",
    "Fuel bars" to b.model.fuelBars.toString(),
    "Speed alert range" to "${b.model.alertRange.first}–${b.model.alertRange.last} km/h",
    "Checksum" to b.checksum,
    "MTU" to b.mtu,
    "Service" to shortUuid(GattUuids.SERVICE),
    "Write (TX)" to shortUuid(GattUuids.TX_CHARACTERISTIC),
    "Notify (RX)" to shortUuid(GattUuids.RX_CHARACTERISTIC),
)
