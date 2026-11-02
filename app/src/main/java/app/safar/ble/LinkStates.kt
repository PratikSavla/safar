package app.safar.ble

import app.safar.core.LinkState

/**
 * The one large connection state of the home screen (docs 12.5 rule 1) from the reconnect policy.
 * [name] is the BLE name of the bike being connected (the address when unknown); [nextAttemptAtMillis] is the
 * wall-clock time the back-off timer fires.
 */
fun linkStateFor(
    policy: ReconnectPolicy.State,
    scanning: Boolean,
    name: String,
    nextAttemptAtMillis: Long,
): LinkState = when (policy) {
    ReconnectPolicy.State.Idle -> if (scanning) LinkState.Searching else LinkState.Idle
    is ReconnectPolicy.State.BluetoothOff -> LinkState.BluetoothOff
    is ReconnectPolicy.State.Connecting -> LinkState.Connecting(name)
    // Looking for the bike by name is still part of getting it back: show it as connecting to that bike.
    is ReconnectPolicy.State.Searching -> LinkState.Connecting(name)
    is ReconnectPolicy.State.Connected -> LinkState.Connected
    is ReconnectPolicy.State.Waiting -> LinkState.Reconnecting(policy.attempt, nextAttemptAtMillis)
}
