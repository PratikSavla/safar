package app.safar.ble

import app.safar.core.DebugLog
import app.safar.core.DeviceInfo
import app.safar.protocol.ConnectTiming
import app.safar.protocol.DEFAULT_ATT_MTU
import app.safar.protocol.DeviceInformationUuids
import app.safar.protocol.MIN_MTU_FOR_FRAME
import app.safar.protocol.toHex
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * A link ready for frames: MTU exchanged (or the default kept), frame [channel] found, notify on. Device
 * Information is read later, once the session runs ([readDeviceInfo]).
 */
class LinkReady(val mtu: Int, val channel: ClusterChannel)

class LinkSetupException(message: String) : Exception(message)

private fun fail(reason: String): Nothing = throw LinkSetupException(reason)

/** Fails [step], naming the disconnect reason when the link dropped during it. */
private fun GattPort.failStep(step: String): Nothing {
    val lost = link.value as? GattLink.Disconnected
    fail(if (lost != null) "$step: link lost (${lost.reason})" else step)
}

/**
 * The connection sequence of docs 2.2 steps 4-7, as forgiving as the original app: a refused MTU exchange is
 * logged and the link goes on with the default MTU, and the frame channel is found by UUID, else by the
 * original app's positions, else by properties ([findClusterChannel]). Throws [LinkSetupException] with the
 * reason when a step fails or the link drops; the caller logs it and closes the client.
 */
suspend fun setUpLink(client: GattPort, log: DebugLog): LinkReady {
    if (!client.connect(ConnectTiming.CONNECT_TIMEOUT_MS)) {
        val lost = client.link.value as? GattLink.Disconnected
        fail(lost?.reason ?: "connect failed")
    }
    log.event("GATT connected")
    delay(ConnectTiming.DISCOVER_DELAY_MS)
    if (!client.discoverServices()) client.failStep("service discovery failed")
    val mtu = exchangeMtu(client, log)
    val services = client.services()
    log.event("GATT layout", mapOf("services" to layoutText(services)))
    val channel = findClusterChannel(services) ?: fail("no frame channel (service FFF0 with FFF1 and FFF2) found")
    log.event("Frame channel", channelDetail(channel))
    if (!client.enableNotify(channel.service, channel.rx.uuid)) {
        client.failStep("enabling notify on ${shortUuid(channel.rx.uuid)} failed")
    }
    log.event("Notify on", mapOf("rx" to shortUuid(channel.rx.uuid)))
    if (client.link.value != GattLink.Connected) client.failStep("notify")
    return LinkReady(mtu, channel)
}

/**
 * MTU exchange (docs 2.2 step 5). The original app only logs a failure, so a refused or failed exchange keeps
 * [DEFAULT_ATT_MTU] and a small MTU is logged; only a lost link fails the step.
 */
private suspend fun exchangeMtu(client: GattPort, log: DebugLog): Int {
    val requested = ConnectTiming.REQUEST_MTU
    val accepted = client.requestMtu(requested)
    if (accepted == null) {
        if (client.link.value != GattLink.Connected) client.failStep("MTU request failed")
        log.event("MTU request failed", mapOf("requested" to "$requested", "using" to "$DEFAULT_ATT_MTU"), error = true)
        return DEFAULT_ATT_MTU
    }
    log.event("MTU changed", mapOf("mtu" to "$accepted", "requested" to "$requested"))
    if (accepted < MIN_MTU_FOR_FRAME) {
        log.event("MTU below frame size", mapOf("mtu" to "$accepted", "needed" to "$MIN_MTU_FOR_FRAME"), error = true)
    }
    return accepted
}

private fun channelDetail(channel: ClusterChannel): Map<String, String> = mapOf(
    "lookup" to channel.lookup.name.lowercase(),
    "service" to shortUuid(channel.service),
    "tx" to "${shortUuid(channel.tx.uuid)} ${propertiesText(channel.tx.properties)}",
    "rx" to "${shortUuid(channel.rx.uuid)} ${propertiesText(channel.rx.properties)}",
    "write" to if (channel.txWithoutResponse) "without response" else "with response",
)

/** Device Information read: what was read, and whether every read the bike was asked for succeeded. */
class DeviceInfoRead(val info: DeviceInfo, val complete: Boolean)

/**
 * Reads the Device Information characteristics the bike has, stopping at the first failed read: a read the
 * bike does not answer holds the link until the stack's ATT timeout, so no further read is risked. The
 * original app never reads them; Safar reads them only once the session runs (docs 12.4 E1).
 */
suspend fun readDeviceInfo(client: GattPort, log: DebugLog): DeviceInfoRead {
    val service = DeviceInformationUuids.SERVICE
    if (!client.has(service)) {
        log.event("Device information service not found")
        return DeviceInfoRead(DeviceInfo(), complete = true)
    }
    val values = mutableMapOf<UUID, ByteArray>()
    var complete = true
    for (uuid in DeviceInformationUuids.ALL) {
        if (!client.has(service, uuid)) continue
        val value = client.read(service, uuid)
        if (value == null) {
            log.event("Device information read failed", mapOf("uuid" to shortUuid(uuid)), error = true)
            complete = false
            break
        }
        values[uuid] = value
    }
    val info = deviceInfoFrom(values)
    log.event("Device information", deviceInfoDetail(info, values))
    return DeviceInfoRead(info, complete)
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
