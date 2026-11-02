package app.safar.phone

import app.safar.core.AlertSource
import app.safar.core.FeedKind
import app.safar.data.Settings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CallAlertsTest {
    /** Drives the tracker the way CallMonitor does, with a fixed caller name. */
    private fun drive(rig: AlertRig, vararg phases: CallPhase, name: String = "Alex") {
        val tracker = CallTracker()
        for (phase in phases) {
            when (tracker.next(phase)) {
                CallTransition.RINGING -> rig.center.callRinging(name, AlertSource.PHONE)
                CallTransition.ANSWERED -> rig.center.callAnswered(name, AlertSource.PHONE)
                CallTransition.MISSED -> rig.center.callMissed(name, AlertSource.PHONE)
                CallTransition.ENDED -> rig.center.callEnded(AlertSource.PHONE)
                null -> Unit
            }
        }
    }

    @Test
    fun transitionsFollowDocs54() {
        assertEquals(CallTransition.RINGING, callTransition(CallPhase.IDLE, CallPhase.RINGING))
        assertEquals(CallTransition.ANSWERED, callTransition(CallPhase.RINGING, CallPhase.OFFHOOK))
        assertEquals(CallTransition.MISSED, callTransition(CallPhase.RINGING, CallPhase.IDLE))
        assertEquals(CallTransition.ENDED, callTransition(CallPhase.OFFHOOK, CallPhase.IDLE))
        assertNull(callTransition(CallPhase.IDLE, CallPhase.OFFHOOK), "outgoing call sends nothing")
        assertNull(callTransition(CallPhase.RINGING, CallPhase.RINGING))
    }

    @Test
    fun answeredCallSendsRingingThenAnswered() {
        val rig = AlertRig()
        drive(rig, CallPhase.IDLE, CallPhase.RINGING, CallPhase.RINGING, CallPhase.OFFHOOK)
        assertEquals(listOf("2:Alex:ringing:N", "2:Alex:answered:N"), rig.outbox.frames)
        assertEquals(true, rig.center.state.value.call?.answered)
        drive(rig, CallPhase.IDLE)
        assertEquals(2, rig.outbox.frames.size, "call end sends nothing")
    }

    @Test
    fun callWaitingDuringAnOutgoingCallIsNeverAnAnswer() {
        val rig = AlertRig()
        // Outgoing call, a second call waits and is rejected, the first call goes on, then ends.
        drive(rig, CallPhase.OFFHOOK, CallPhase.RINGING, CallPhase.OFFHOOK, CallPhase.IDLE)
        assertEquals(emptyList(), rig.outbox.frames)
        assertNull(rig.center.state.value.call)
        val tracker = CallTracker()
        assertEquals(listOf(null, null, CallTransition.ENDED), listOf(CallPhase.OFFHOOK, CallPhase.RINGING, CallPhase.IDLE).map(tracker::next))
        // The next call from idle rings and is answered as usual.
        assertEquals(listOf(CallTransition.RINGING, CallTransition.ANSWERED), listOf(CallPhase.RINGING, CallPhase.OFFHOOK).map(tracker::next))
    }

    @Test
    fun endOfAnsweredCallClearsActiveCall() {
        val rig = AlertRig()
        drive(rig, CallPhase.RINGING, CallPhase.OFFHOOK, CallPhase.IDLE)
        assertNull(rig.center.state.value.call)
        assertEquals(2, rig.outbox.frames.size)
    }

    @Test
    fun unansweredCallsCountUp() {
        val rig = AlertRig()
        drive(rig, CallPhase.RINGING, CallPhase.IDLE)
        drive(rig, CallPhase.RINGING, CallPhase.IDLE, name = "Rider")
        assertEquals(
            listOf("2:Alex:ringing:N", "4:Alex:1:N", "2:Rider:ringing:N", "4:Rider:2:N"),
            rig.outbox.frames,
        )
        val missed = rig.center.state.value.missed
        assertEquals(2, missed.count)
        assertEquals("Rider", missed.lastName)
        assertNull(rig.center.state.value.call)
    }

    @Test
    fun outgoingCallSendsNothing() {
        val rig = AlertRig()
        drive(rig, CallPhase.IDLE, CallPhase.OFFHOOK, CallPhase.IDLE)
        assertTrue(rig.outbox.frames.isEmpty())
        assertTrue(rig.center.state.value.feed.isEmpty())
    }

    @Test
    fun callSwitchOffSendsNothing() {
        val rig = AlertRig(Settings(alertCalls = false))
        drive(rig, CallPhase.RINGING, CallPhase.OFFHOOK, CallPhase.IDLE, CallPhase.RINGING, CallPhase.IDLE)
        assertTrue(rig.outbox.frames.isEmpty())
        assertEquals(0, rig.center.state.value.missed.count)
        assertTrue("alert.off" in rig.log.events)
    }

    @Test
    fun callerNameFallsBackToNumberThenUnknown() {
        assertEquals("Alex", callerDisplayName("+00 12345 67890", "Alex"))
        assertEquals("00 12345 67890", callerDisplayName("+00 12345 67890", null))
        assertEquals("00 12345 67890", callerDisplayName("+00 12345 67890", "  "))
        assertEquals(UNKNOWN_CALLER, callerDisplayName(null, null))
        assertEquals(UNKNOWN_CALLER, callerDisplayName("", null))
    }

    @Test
    fun phaseFromTelephonyValues() {
        assertEquals(CallPhase.RINGING, callPhaseOf(1))
        assertEquals(CallPhase.OFFHOOK, callPhaseOf(2))
        assertEquals(CallPhase.IDLE, callPhaseOf(0))
        assertEquals(CallPhase.OFFHOOK, callPhaseOf("OFFHOOK"))
        assertNull(callPhaseOf("BUSY"))
    }

    @Test
    fun feedRecordsWhatWasSent() {
        val rig = AlertRig()
        drive(rig, CallPhase.RINGING, CallPhase.IDLE)
        val kinds = rig.center.state.value.feed.map { it.kind }
        assertEquals(listOf(FeedKind.MISSED_CALL, FeedKind.CALL_RINGING), kinds, "newest first")
        assertEquals(1, rig.center.state.value.feed.first().count)
    }
}
