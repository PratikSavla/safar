package app.safar.phone

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IncomingNumberTest {
    @Test
    fun lateIdleBroadcastDoesNotNameTheNextCaller() = runTest {
        val number = IncomingNumber()
        number.onBroadcast(CallPhase.RINGING, "+00 11111 11111")
        // API 31+: the IDLE callback is handled first, then the IDLE broadcast with the ended call's number.
        number.clear()
        number.onBroadcast(CallPhase.IDLE, "+00 11111 11111")
        // The next call's RINGING callback arrives before its own broadcast.
        val ringing = async { number.await(600) }
        number.onBroadcast(CallPhase.RINGING, "+00 22222 22222")
        assertEquals("+00 22222 22222", ringing.await())
    }

    @Test
    fun outgoingCallNumberIsIgnored() = runTest {
        val number = IncomingNumber()
        number.onBroadcast(CallPhase.OFFHOOK, "+00 33333 33333")
        number.onBroadcast(CallPhase.IDLE, "+00 33333 33333")
        assertNull(number.current)
        assertNull(number.await(600), "no RINGING broadcast: the ring waits, then goes without a number")
    }

    @Test
    fun ringingNumberStaysUntilCleared() = runTest {
        val number = IncomingNumber()
        number.onBroadcast(CallPhase.RINGING, "+00 44444 44444")
        number.onBroadcast(CallPhase.RINGING, " ")
        number.onBroadcast(null, "+00 55555 55555")
        assertEquals("+00 44444 44444", number.await(600))
        number.clear()
        assertNull(number.current)
    }
}
