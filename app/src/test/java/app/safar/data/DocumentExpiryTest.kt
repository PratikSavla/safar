package app.safar.data

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DocumentExpiryTest {
    private val today = 20_000L

    private fun doc(expiryIn: Long?, reminded: Int = ReminderStage.NONE, kind: DocKind = DocKind.PUC) = BikeDocument(
        id = 1, title = "PUC", kind = kind, fileName = "a.pdf", mimeType = "application/pdf", sizeBytes = 1, addedMillis = 0,
        expiryEpochDay = expiryIn?.let { today + it }, remindedStage = reminded,
    )

    @Test
    fun stagesChangeOnTheThirtiethSeventhAndLastDay() {
        val expected = mapOf(31L to 0, 30L to 1, 8L to 1, 7L to 2, 1L to 2, 0L to 3, -1L to 3)
        expected.forEach { (left, stage) -> assertEquals(stage, reminderStage(today, today + left), "$left days left") }
        assertEquals(ReminderStage.NONE, reminderStage(today, null))
    }

    @Test
    fun eachReminderGoesOutOnce() {
        assertEquals(ReminderStage.SOON, reminderToSend(doc(20), today))
        assertNull(reminderToSend(doc(20, reminded = ReminderStage.SOON), today))
        assertEquals(ReminderStage.CLOSE, reminderToSend(doc(5, reminded = ReminderStage.SOON), today))
        assertNull(reminderToSend(doc(0, reminded = ReminderStage.DUE), today))
        assertNull(reminderToSend(doc(null), today))
        assertNull(reminderToSend(doc(90), today))
    }

    @Test
    fun aPhoneOffForAWhileGetsOnlyTheLatestReminder() {
        assertEquals(ReminderStage.DUE, reminderToSend(doc(-3), today))
    }

    @Test
    fun expiryStatesAndPhrases() {
        assertEquals(ExpiryState.NONE, expiryState(today, null))
        assertEquals(ExpiryState.OK, expiryState(today, today + 31))
        assertEquals(ExpiryState.SOON, expiryState(today, today))
        assertEquals(ExpiryState.EXPIRED, expiryState(today, today - 1))
        assertEquals("expires today", expiryPhrase(today, today))
        assertEquals("expires in 6 days", expiryPhrase(today, today + 6))
        assertEquals("expired 3 days ago", expiryPhrase(today, today - 3))
    }

    @Test
    fun theMostUrgentPaperIsTheEarliestWithinThirtyDays() {
        val later = doc(20).copy(id = 2)
        val sooner = doc(3).copy(id = 3)
        val far = doc(200).copy(id = 4)
        assertEquals(3, mostUrgent(listOf(later, far, sooner), today)?.id)
        assertNull(mostUrgent(listOf(far, doc(null)), today))
    }
}
