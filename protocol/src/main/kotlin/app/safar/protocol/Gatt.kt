package app.safar.protocol

import java.util.UUID

/*
 * GATT layout of the bike cluster (port of docs/helper/src/gatt.ts). Confirmed from an HCI snoop
 * capture of a V-Strom SX. Select the service and characteristics by these UUIDs, never by position.
 */

const val SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"

/** App -> bike. Properties: WRITE (with response). */
const val TX_CHARACTERISTIC_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"

/** Bike -> app. Properties: NOTIFY. */
const val RX_CHARACTERISTIC_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"

/** Client Characteristic Configuration descriptor (enables notify on RX). */
const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"

/** The same UUIDs as [java.util.UUID] for the Android BLE API. */
object GattUuids {
    val SERVICE: UUID = UUID.fromString(SERVICE_UUID)
    val TX_CHARACTERISTIC: UUID = UUID.fromString(TX_CHARACTERISTIC_UUID)
    val RX_CHARACTERISTIC: UUID = UUID.fromString(RX_CHARACTERISTIC_UUID)
    val CCCD: UUID = UUID.fromString(CCCD_UUID)
}

/** The bike answers an MTU request with 65. A frame needs 30 + 3 = 33. */
const val BIKE_MTU = 65
const val MIN_MTU_FOR_FRAME = 33

/** The bike sends one cluster frame about every 5 s. */
const val CLUSTER_PERIOD_MS = 5000L

/**
 * Send rates for a new app: the official app v7.6 capture (docs 1.6), which the bike accepts.
 * Send one write at a time and wait for `onCharacteristicWrite()` before the next.
 */
object Timing {
    /** Cluster frame '7' from the bike. */
    const val CLUSTER_PERIOD_MS: Long = app.safar.protocol.CLUSTER_PERIOD_MS

    /** Status frame '3': about every 1 s, one write. */
    const val STATUS_PERIOD_MS = 1000L
    const val STATUS_REPEAT = 1

    /** Navigation frame '1': the capture shows about every 220 ms. */
    const val NAVIGATION_PERIOD_MS = 250L

    /**
     * Call frame '2': v7.6 repeats it while the phone rings (15 writes in one test). The interval is
     * not in the capture notes; 1 s matches the status tick and is an assumption.
     */
    const val CALL_REPEAT_PERIOD_MS = 1000L

    /** Missed call frame '4': 2 writes, 200 ms apart. */
    const val MISSED_CALL_REPEAT = 2
    const val MISSED_CALL_GAP_MS = 200L

    /** Message frame '5' and other one-off events: the v2.15 repeat (docs 5.2). */
    const val EVENT_REPEAT = SEND_REPEAT
    const val EVENT_GAP_MS = SEND_GAP_MS

    /** Auto-reconnect back-off (docs 12.4 R3): 2 s, 5 s, 10 s, then every 30 s. */
    val RECONNECT_BACKOFF_MS: List<Long> = listOf(2_000L, 5_000L, 10_000L, 30_000L)

    /** Delay before reconnect [attempt] (1-based). Attempts past the list use the last value. */
    fun reconnectDelayMs(attempt: Int): Long {
        require(attempt >= 1) { "attempt starts at 1, got $attempt" }
        return RECONNECT_BACKOFF_MS[minOf(attempt, RECONNECT_BACKOFF_MS.size) - 1]
    }
}
