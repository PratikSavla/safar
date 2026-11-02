package app.safar.ui.components

private const val MAX_ECHOES = 64

/**
 * Texts a field has sent to its owner that have not come back yet. An owner that answers through an async
 * flow hands back each edit late, sometimes after newer ones; those late copies must not overwrite the field.
 */
internal class EditEchoes(private val max: Int = MAX_ECHOES) {
    private val pending = ArrayDeque<String>()

    fun sent(text: String) {
        if (pending.lastOrNull() == text) return
        pending.addLast(text)
        while (pending.size > max) pending.removeFirst()
    }

    /** True when [external] is one of our own edits coming back; it and every older edit are then settled. */
    fun isEcho(external: String): Boolean {
        val i = pending.indexOf(external)
        if (i < 0) return false
        repeat(i + 1) { pending.removeFirst() }
        return true
    }
}
