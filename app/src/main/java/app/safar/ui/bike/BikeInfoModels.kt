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
import app.safar.ui.copy.LinkCopy
import java.util.Locale
import java.util.UUID

private const val UNKNOWN = "—"

/**
 * What the Bike information page shows for the saved bike. [stamp] is the rider's connection word
 * ([LinkCopy.stamp]); [name], [address], [linkWord], [mtu], [checksum] and [framesReceived] are Developer mode.
 */
data class BikeInfo(
    val name: String,
    val address: String,
    val model: BikeModel,
    val connected: Boolean,
    val stamp: String,
    val linkWord: String,
    val deviceInfo: DeviceInfo?,
    val mtu: String,
    val checksum: String,
    val framesReceived: Int,
)

/** The saved bike from the BLE layer, else from the saved-bike store; null when no bike is paired. */
fun bikeInfo(state: BikeState, saved: SavedBikeRecord?): BikeInfo? {
    val name = state.saved?.name ?: saved?.name ?: return null
    val address = state.saved?.address ?: saved?.address.orEmpty()
    val model = state.saved?.model ?: BikeCatalog.forBleName(name)
    return BikeInfo(
        name = name,
        address = address.ifBlank { UNKNOWN },
        model = model,
        connected = state.link == LinkState.Connected,
        stamp = LinkCopy.stamp(state.link, hasSavedBike = true),
        linkWord = linkWord(state.link),
        deviceInfo = state.deviceInfo,
        mtu = state.mtu?.let { "$it" } ?: "$BIKE_MTU requested",
        checksum = checksumLabel(name),
        framesReceived = state.framesReceived,
    )
}

/** The link state in Developer mode words. */
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

private fun shown(value: String?): String = value?.takeIf { it.isNotBlank() } ?: UNKNOWN

/**
 * What a rider reads about the bike: model, type, serial number, the display's software and hardware versions
 * and the fuel gauge. "—" when the bike has not said yet.
 */
fun riderRows(b: BikeInfo): List<Pair<String, String>> {
    val d = b.deviceInfo ?: DeviceInfo()
    return listOf(
        "Model" to b.model.name,
        "Type" to familyLabel(b.model.family).replaceFirstChar { it.titlecase(Locale.ROOT) },
        "Serial number" to shown(d.serial),
        "Software version" to shown(d.firmware),
        "Hardware version" to shown(d.hardware),
        "Fuel gauge" to "${b.model.fuelBars} bars",
    )
}

/** The Device Information fields (0x180A) the rider rows leave out. Developer mode. */
fun developerDeviceRows(info: DeviceInfo?): List<Pair<String, String>> {
    val d = info ?: DeviceInfo()
    return listOf(
        "Manufacturer" to d.manufacturer,
        "Model number" to d.modelNumber,
        "Software rev." to d.software,
        "PnP ID" to d.pnpId,
    ).map { (k, v) -> k to shown(v) }
}

/** BLE facts for the link card. Developer mode. */
fun linkRows(b: BikeInfo): List<Pair<String, String>> = listOf(
    "BLE name" to b.name,
    "Address" to b.address,
    "Model code" to "${b.model.code} · ${familyLabel(b.model.family)}",
    "Speed alert range" to "${b.model.alertRange.first}–${b.model.alertRange.last} km/h",
    "Checksum" to b.checksum,
    "MTU" to b.mtu,
    "Service" to shortUuid(GattUuids.SERVICE),
    "Write (TX)" to shortUuid(GattUuids.TX_CHARACTERISTIC),
    "Notify (RX)" to shortUuid(GattUuids.RX_CHARACTERISTIC),
    "Frames received" to b.framesReceived.toString(),
)
