package app.safar.protocol

import java.util.Locale
import java.util.UUID

/*
 * Scan and connection procedure values (docs 2.2) and the standard Device Information service the cluster
 * also has (docs 12.4 E1). Kept beside Gatt.kt so the app holds no protocol numbers of its own.
 */

object ConnectTiming {
    /** The original app scans for 10 s with no filter (docs 2.2 step 2). */
    const val SCAN_DURATION_MS = 10_000L

    /** connectGatt() gives up after 20 s (docs 2.2 step 4). */
    const val CONNECT_TIMEOUT_MS = 20_000L

    /** Wait after STATE_CONNECTED before discoverServices() (docs 2.2 step 4). */
    const val DISCOVER_DELAY_MS = 500L

    /** MTU the app asks for (docs 2.2 step 5). The bike answers 65; a frame needs [MIN_MTU_FOR_FRAME]. */
    const val REQUEST_MTU = 250
}

/** Bluetooth SIG 16-bit UUID in the base UUID. */
fun uuid16(short: Int): UUID = UUID.fromString("0000%04x-0000-1000-8000-00805f9b34fb".format(Locale.ROOT, short))

/** Device Information service 0x180A and its characteristics 2A23..2A2A, 2A50 (docs 12.4 E1). */
object DeviceInformationUuids {
    val SERVICE: UUID = uuid16(0x180A)
    val SYSTEM_ID: UUID = uuid16(0x2A23)
    val MODEL_NUMBER: UUID = uuid16(0x2A24)
    val SERIAL_NUMBER: UUID = uuid16(0x2A25)
    val FIRMWARE_REVISION: UUID = uuid16(0x2A26)
    val HARDWARE_REVISION: UUID = uuid16(0x2A27)
    val SOFTWARE_REVISION: UUID = uuid16(0x2A28)
    val MANUFACTURER_NAME: UUID = uuid16(0x2A29)
    val REGULATORY_CERT: UUID = uuid16(0x2A2A)
    val PNP_ID: UUID = uuid16(0x2A50)

    /** Read order: text fields first, then the binary ones. */
    val ALL: List<UUID> = listOf(
        MANUFACTURER_NAME, MODEL_NUMBER, SERIAL_NUMBER, FIRMWARE_REVISION, HARDWARE_REVISION, SOFTWARE_REVISION,
        SYSTEM_ID, REGULATORY_CERT, PNP_ID,
    )
}
