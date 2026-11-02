package app.safar.ble

import android.bluetooth.BluetoothGattCharacteristic
import app.safar.core.DebugLog
import app.safar.core.DeviceInfo
import app.safar.protocol.ConnectTiming
import app.safar.protocol.DeviceInformationUuids
import app.safar.protocol.GattUuids
import app.safar.protocol.MIN_MTU_FOR_FRAME
import app.safar.protocol.toHex
import kotlinx.coroutines.delay
import java.util.UUID

/** A link ready for frames: MTU set, TX found, notify on, Device Information read. */
class LinkReady(val mtu: Int, val tx: BluetoothGattCharacteristic, val deviceInfo: DeviceInfo)

class LinkSetupException(message: String) : Exception(message)

private fun fail(reason: String): Nothing = throw LinkSetupException(reason)

/** Fails [step], naming the disconnect reason when the link dropped during it. */
private fun GattClient.failStep(step: String): Nothing {
    val lost = link.value as? GattLink.Disconnected
    fail(if (lost != null) "$step: link lost (${lost.reason})" else step)
}

/**
 * The connection sequence of docs 2.2 steps 4-7, then Device Information (docs 12.4 E1). Throws
 * [LinkSetupException] with the reason when a step fails or the link drops; the caller logs it and closes
 * the client.
 */
suspend fun setUpLink(client: GattClient, log: DebugLog): LinkReady {
    if (!client.connect(ConnectTiming.CONNECT_TIMEOUT_MS)) {
        val lost = client.link.value as? GattLink.Disconnected
        fail(lost?.reason ?: "connect failed")
    }
    log.event("GATT connected")
    delay(ConnectTiming.DISCOVER_DELAY_MS)
    if (!client.discoverServices()) client.failStep("service discovery failed")
    val mtu = client.requestMtu(ConnectTiming.REQUEST_MTU) ?: client.failStep("MTU request failed")
    log.event("MTU changed", mapOf("mtu" to mtu.toString(), "requested" to ConnectTiming.REQUEST_MTU.toString()))
    if (mtu < MIN_MTU_FOR_FRAME) fail("MTU $mtu is below $MIN_MTU_FOR_FRAME")
    val service = client.service(GattUuids.SERVICE) ?: fail("service FFF0 not found")
    val tx = service.getCharacteristic(GattUuids.TX_CHARACTERISTIC) ?: fail("TX characteristic FFF1 not found")
    val rx = service.getCharacteristic(GattUuids.RX_CHARACTERISTIC) ?: fail("RX characteristic FFF2 not found")
    log.event("Characteristics found by UUID", mapOf("service" to "FFF0", "tx" to "FFF1", "rx" to "FFF2"))
    if (!client.enableNotify(rx)) client.failStep("enabling notify on FFF2 failed")
    log.event("Notify on", mapOf("cccd" to "01 00"))
    val info = readDeviceInfo(client, log)
    if (client.link.value != GattLink.Connected) client.failStep("device information read")
    return LinkReady(mtu, tx, info)
}

/** Reads every Device Information characteristic the bike has. Missing ones stay null; no step is fatal. */
suspend fun readDeviceInfo(client: GattClient, log: DebugLog): DeviceInfo {
    val service = client.service(DeviceInformationUuids.SERVICE)
    if (service == null) {
        log.event("Device information service not found")
        return DeviceInfo()
    }
    val values = mutableMapOf<UUID, ByteArray>()
    for (uuid in DeviceInformationUuids.ALL) {
        val characteristic = service.getCharacteristic(uuid) ?: continue
        val value = client.read(characteristic)
        if (value == null) {
            log.event("Device information read failed", mapOf("uuid" to uuid.toString().substring(4, 8)), error = true)
        } else {
            values[uuid] = value
        }
    }
    val info = deviceInfoFrom(values)
    log.event("Device information", deviceInfoDetail(info, values))
    return info
}

/** Text characteristics are UTF-8 strings, often NUL padded; PnP ID is binary and kept as hex. */
fun deviceInfoFrom(values: Map<UUID, ByteArray>): DeviceInfo {
    fun text(uuid: UUID): String? = values[uuid]?.toString(Charsets.UTF_8)?.trim('\u0000', ' ')?.ifEmpty { null }
    return DeviceInfo(
        manufacturer = text(DeviceInformationUuids.MANUFACTURER_NAME),
        modelNumber = text(DeviceInformationUuids.MODEL_NUMBER),
        serial = text(DeviceInformationUuids.SERIAL_NUMBER),
        firmware = text(DeviceInformationUuids.FIRMWARE_REVISION),
        hardware = text(DeviceInformationUuids.HARDWARE_REVISION),
        software = text(DeviceInformationUuids.SOFTWARE_REVISION),
        pnpId = values[DeviceInformationUuids.PNP_ID]?.let(::toHex),
    )
}

/** Log detail; the serial uses the "serial" key so "Hide names" masks it in exports. */
private fun deviceInfoDetail(info: DeviceInfo, values: Map<UUID, ByteArray>): Map<String, String> = buildMap {
    info.manufacturer?.let { put("manufacturer", it) }
    info.modelNumber?.let { put("model", it) }
    info.serial?.let { put("serial", it) }
    info.firmware?.let { put("firmware", it) }
    info.hardware?.let { put("hardware", it) }
    info.software?.let { put("software", it) }
    info.pnpId?.let { put("pnpId", it) }
    values[DeviceInformationUuids.SYSTEM_ID]?.let { put("systemId", toHex(it)) }
    values[DeviceInformationUuids.REGULATORY_CERT]?.let { put("regulatory", toHex(it)) }
}
