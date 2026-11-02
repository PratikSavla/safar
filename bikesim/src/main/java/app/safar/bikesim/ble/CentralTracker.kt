package app.safar.bikesim.ble

/**
 * The central of the GATT server and the LE devices that enabled notify on FFF2, keyed by address.
 *
 * GATT server callbacks fire for every LE link of the adapter, not only Safar's. So a device becomes the central
 * only when no central is set or when it enables the CCCD, and a link that comes and goes never resets the notify
 * state of another device. Notify is on while at least one device has it enabled.
 */
internal class CentralTracker {
    /** State after one callback; the GATT server reports a changed central or notify state to the screen. */
    data class Change(
        val central: String?,
        val centralChanged: Boolean,
        val notifyOn: Boolean,
        val notifyChanged: Boolean,
    )

    /** Devices with notify enabled, oldest first. */
    private val subscribed = LinkedHashSet<String>()

    private var current: String? = null

    val central: String? @Synchronized get() = current

    @Synchronized
    fun subscribers(): List<String> = subscribed.toList()

    @Synchronized
    fun isSubscribed(address: String): Boolean = address in subscribed

    /** A device linked up. It becomes the central only when there is none. */
    @Synchronized
    fun connected(address: String): Change = track {
        if (current == null) current = address
    }

    /** A device went away. When it was the central, the newest device with notify enabled takes over, if any. */
    @Synchronized
    fun disconnected(address: String): Change = track {
        subscribed -= address
        if (current == address) current = subscribed.lastOrNull()
    }

    /** A device wrote the FFF2 CCCD. Enabling makes it the central; disabling only turns its own notify off. */
    @Synchronized
    fun cccd(address: String, enabled: Boolean): Change = track {
        subscribed -= address
        if (enabled) {
            subscribed += address
            current = address
        } else if (current == address) {
            current = subscribed.lastOrNull() ?: address
        }
    }

    @Synchronized
    fun clear() {
        subscribed.clear()
        current = null
    }

    private inline fun track(change: () -> Unit): Change {
        val oldCentral = current
        val oldNotify = subscribed.isNotEmpty()
        change()
        val notify = subscribed.isNotEmpty()
        return Change(current, current != oldCentral, notify, notify != oldNotify)
    }
}
