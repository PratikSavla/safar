package app.safar.core

import app.safar.protocol.engineTempC as engineTempFromByte

/** Scooter or motorcycle: decides fuel bar count, speed alert range and artwork (docs 7.2). */
enum class VehicleFamily { SCOOTER, MOTORCYCLE }

/**
 * App-side facts about a model code (BLE name chars [1..4]). The protocol module knows the checksum;
 * this adds what the UI needs. [tankLitres] is the model spec; the rider can override it in Settings.
 */
data class BikeModel(
    val code: String,
    val name: String,
    val family: VehicleFamily,
    val fuelBars: Int,
    val alertRange: IntRange,
    val tankLitres: Double,
)

/** A cluster found by the scan. [rssi] in dBm. */
data class ScannedBike(
    val name: String,
    val address: String,
    val rssi: Int,
    val model: BikeModel,
)

/** The cluster the rider paired with. Saved so the app can reconnect without a scan (docs 2.4). */
data class SavedBike(
    val name: String,
    val address: String,
    val model: BikeModel,
)

/** Connection state shown as the one large state on the home screen (docs 12.5 rule 1). */
sealed interface LinkState {
    data object Idle : LinkState
    data object BluetoothOff : LinkState
    data object Searching : LinkState
    data class Connecting(val name: String) : LinkState
    /** GATT connected, MTU set, notify on. Frames flow in both directions. */
    data object Connected : LinkState
    /** Lost the link. [attempt] starts at 1; [nextAttemptAtMillis] is wall-clock time of the next try. */
    data class Reconnecting(val attempt: Int, val nextAttemptAtMillis: Long) : LinkState
}

/**
 * One decoded cluster frame '7' (docs 3.1). [fuelBars] is null for the first frame after a connection,
 * which the app discards (docs 3.3). [gear] is byte 23 as a char ('N' in neutral); probable, not confirmed,
 * and not shown. [raw25] is the engine temperature, see [engineTempC].
 */
data class ClusterReading(
    val speedKmh: Int,
    val odometerKm: Int,
    val tripAKm: Double,
    val tripBKm: Double,
    val fuelBars: Int?,
    val gear: Char?,
    val raw23: Int,
    val raw25: Int,
    val receivedAtMillis: Long,
) {
    /** Engine temperature in °C (byte 25), null when the cluster sends no reading. */
    val engineTempC: Int? get() = engineTempFromByte(raw25)
}

/** Device Information service 0x180A (docs 12.4 E1). Any field may be missing on a given cluster. */
data class DeviceInfo(
    val manufacturer: String? = null,
    val modelNumber: String? = null,
    val serial: String? = null,
    val firmware: String? = null,
    val hardware: String? = null,
    val software: String? = null,
    val pnpId: String? = null,
)

/** Everything the UI knows about the bike link. [lastCluster] survives a disconnect ("last seen on bike"). */
data class BikeState(
    val link: LinkState = LinkState.Idle,
    val scanning: Boolean = false,
    val scan: List<ScannedBike> = emptyList(),
    val saved: SavedBike? = null,
    val mtu: Int? = null,
    val cluster: ClusterReading? = null,
    val lastCluster: ClusterReading? = null,
    val deviceInfo: DeviceInfo? = null,
    /** Count of frames received since the connection. Shown in the home card. */
    val framesReceived: Int = 0,
)
