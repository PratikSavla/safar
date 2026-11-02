package app.safar.ui.components

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditEchoesTest {
    @Test
    fun `late copies of our own edits are echoes, in order`() {
        val e = EditEchoes()
        listOf("A", "Al", "Ale", "Alex").forEach(e::sent)
        // The owner answers late: "Al" arrives while the field already shows "Alex".
        assertTrue(e.isEcho("Al"))
        assertTrue(e.isEcho("Ale"))
        assertTrue(e.isEcho("Alex"))
        // Settled: an older text coming back now is a change from outside (a reset).
        assertFalse(e.isEcho("A"))
    }

    @Test
    fun `a text typed twice is matched oldest first`() {
        val e = EditEchoes()
        listOf("a", "ab", "a").forEach(e::sent)
        assertTrue(e.isEcho("a"))
        assertTrue(e.isEcho("ab"))
        assertTrue(e.isEcho("a"))
        assertFalse(e.isEcho("ab"))
    }

    @Test
    fun `a value we never sent is not an echo and the queue is bounded`() {
        val e = EditEchoes(max = 2)
        assertFalse(e.isEcho("Rider"))
        listOf("1", "12", "123").forEach(e::sent)
        assertFalse(e.isEcho("1"))
        assertTrue(e.isEcho("12"))
    }
}
