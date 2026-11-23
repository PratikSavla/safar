package app.safar.ble

import app.safar.protocol.DeviceInformationUuids
import app.safar.protocol.GattProperty
import app.safar.protocol.GattUuids
import app.safar.protocol.uuid16
import java.util.Locale
import java.util.UUID

/** One characteristic found by service discovery: its UUID and property bits ([GattProperty]). */
data class GattCharInfo(val uuid: UUID, val properties: Int)

/** One service found by service discovery, characteristics in the order the phone reports them. */
data class GattServiceInfo(val uuid: UUID, val characteristics: List<GattCharInfo>)

/** How [findClusterChannel] found the frame channel, for the log. */
enum class ChannelLookup { UUID, POSITION, PROPERTIES }

/** The cluster's frame channel: frames go to [tx], cluster frames come from [rx], both in [service]. */
data class ClusterChannel(
    val service: UUID,
    val tx: GattCharInfo,
    val rx: GattCharInfo,
    val lookup: ChannelLookup,
) {
    /** Write without response, as the original app does when TX allows it ([GattProperty.writesWithoutResponse]). */
    val txWithoutResponse: Boolean get() = GattProperty.writesWithoutResponse(tx.properties)
}

/** Standard services that never carry the frame channel. */
private val STANDARD_SERVICES: Set<UUID> = setOf(uuid16(0x1800), uuid16(0x1801), DeviceInformationUuids.SERVICE)

/**
 * The frame channel in [services], tried in this order:
 * 1. by UUID: FFF0 with FFF1 (TX) and FFF2 (RX), confirmed on the V-Strom SX (docs 2.2 step 6);
 * 2. by position, as the original app: the 4th service, characteristic 0 is TX and 1 is RX (docs 2.6), when
 *    their properties fit;
 * 3. by properties: the first non-standard service with a writable and a notifying characteristic.
 * Null when none fits.
 */
fun findClusterChannel(services: List<GattServiceInfo>): ClusterChannel? =
    byUuid(services) ?: byPosition(services) ?: byProperties(services)

private fun byUuid(services: List<GattServiceInfo>): ClusterChannel? {
    val service = services.firstOrNull { it.uuid == GattUuids.SERVICE } ?: return null
    val tx = service.characteristics.firstOrNull { it.uuid == GattUuids.TX_CHARACTERISTIC } ?: return null
    val rx = service.characteristics.firstOrNull { it.uuid == GattUuids.RX_CHARACTERISTIC } ?: return null
    return ClusterChannel(service.uuid, tx, rx, ChannelLookup.UUID)
}

private fun byPosition(services: List<GattServiceInfo>): ClusterChannel? {
    val service = services.getOrNull(ORIGINAL_SERVICE_INDEX) ?: return null
    if (service.uuid in STANDARD_SERVICES) return null
    val tx = service.characteristics.getOrNull(0) ?: return null
    val rx = service.characteristics.getOrNull(1) ?: return null
    if (!GattProperty.canWrite(tx.properties) || !GattProperty.canNotify(rx.properties)) return null
    return ClusterChannel(service.uuid, tx, rx, ChannelLookup.POSITION)
}

private fun byProperties(services: List<GattServiceInfo>): ClusterChannel? {
    for (service in services) {
        if (service.uuid in STANDARD_SERVICES) continue
        val tx = service.characteristics.firstOrNull { GattProperty.canWrite(it.properties) } ?: continue
        val rx = service.characteristics.firstOrNull { it !== tx && GattProperty.canNotify(it.properties) }
            ?: service.characteristics.firstOrNull { GattProperty.canNotify(it.properties) }
            ?: continue
        return ClusterChannel(service.uuid, tx, rx, ChannelLookup.PROPERTIES)
    }
    return null
}

/** The original app takes `getServices().get(3)` (docs 2.6). */
private const val ORIGINAL_SERVICE_INDEX = 3

/** "fff1" for a SIG-base UUID, else the full UUID: short enough for the log. */
fun shortUuid(uuid: UUID): String {
    val text = uuid.toString().lowercase(Locale.ROOT)
    return if (text.startsWith("0000") && text.endsWith("-0000-1000-8000-00805f9b34fb")) text.substring(4, 8) else text
}

/** Property bits as text for the log: "write", "write-no-response notify". */
fun propertiesText(properties: Int): String = listOfNotNull(
    "read".takeIf { properties and GattProperty.READ != 0 },
    "write-no-response".takeIf { properties and GattProperty.WRITE_NO_RESPONSE != 0 },
    "write".takeIf { properties and GattProperty.WRITE != 0 },
    "notify".takeIf { properties and GattProperty.NOTIFY != 0 },
    "indicate".takeIf { properties and GattProperty.INDICATE != 0 },
).joinToString(" ").ifEmpty { "0x%02x".format(Locale.ROOT, properties) }

/** Every service and characteristic with its properties, one line for the log (docs 12.6). */
fun layoutText(services: List<GattServiceInfo>): String = services.joinToString("; ") { s ->
    val chars = s.characteristics.joinToString(", ") { "${shortUuid(it.uuid)} ${propertiesText(it.properties)}" }
    if (chars.isEmpty()) shortUuid(s.uuid) else "${shortUuid(s.uuid)} [$chars]"
}
