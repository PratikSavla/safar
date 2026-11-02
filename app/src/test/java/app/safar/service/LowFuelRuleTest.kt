package app.safar.service

import org.junit.Test
import kotlin.test.assertEquals

class LowFuelRuleTest {
    private fun fired(rule: LowFuelRule, bars: List<Int?>, enabled: Boolean = true) = bars.map { rule.next(it, enabled) }

    @Test
    fun `fires once when the bars drop to 1 while connected`() {
        assertEquals(listOf(false, false, false, true, false), fired(LowFuelRule(), listOf(null, 3, 2, 1, 1)))
    }

    @Test
    fun `a gauge bouncing between 1 and 2 posts once, a refill arms it again`() {
        val rule = LowFuelRule()
        assertEquals(listOf(false, true, false, false, false, false, true), fired(rule, listOf(2, 1, 2, 1, 6, 2, 1)))
    }

    @Test
    fun `connecting on 1 bar, or with the switch off, posts nothing`() {
        assertEquals(listOf(false, false, false), fired(LowFuelRule(), listOf(null, 1, 1)))
        assertEquals(listOf(false, false), fired(LowFuelRule(), listOf(2, 1), enabled = false))
        // A reconnect: the first frame has no fuel value, so 2 -> null -> 1 is not a drop seen live.
        assertEquals(listOf(false, false, false), fired(LowFuelRule(), listOf(2, null, 1)))
    }
}
