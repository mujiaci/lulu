package me.rerere.rikkahub.ui.pages.cihai

import me.rerere.rikkahub.data.companion.CompanionActionPlan
import me.rerere.rikkahub.data.companion.CompanionActionType
import me.rerere.rikkahub.data.companion.CompanionCommitment
import me.rerere.rikkahub.data.companion.CompanionCommitmentStatus
import me.rerere.rikkahub.data.companion.CompanionConcern
import me.rerere.rikkahub.data.companion.CompanionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionConcernCardsTest {
    @Test
    fun `cards use selected snapshot and absolute perception time`() {
        val snapshot = CompanionSnapshot(
            assistantId = "lulu",
            concerns = listOf(
                concern(id = "later", subject = "study", nextAt = NOW + 120_000L),
                concern(id = "sooner", subject = "wake", nextAt = NOW + 60_000L),
            ),
        )

        val cards = buildCompanionConcernCards(snapshot, nowMillis = NOW)

        assertEquals(listOf("concern:sooner", "concern:later"), cards.map { it.id })
        assertTrue(cards.first().nextPerceptionText.startsWith("计划留意时间："))
        assertFalse(cards.first().overdue)
    }

    @Test
    fun `linked commitment supplies title and promise without duplicate card`() {
        val snapshot = CompanionSnapshot(
            assistantId = "lulu",
            concerns = listOf(concern(id = "concern", subject = "wake", nextAt = NOW - 1L)),
            commitments = listOf(
                CompanionCommitment(
                    id = "commitment",
                    assistantId = "lulu",
                    subjectKey = "wake",
                    promise = "七点半前确认你已经起床",
                    dueAt = NOW - 1L,
                    status = CompanionCommitmentStatus.DUE,
                    actionPlan = CompanionActionPlan(
                        type = CompanionActionType.REMINDER,
                        category = "wake_up",
                    ),
                )
            ),
        )

        val card = buildCompanionConcernCards(snapshot, nowMillis = NOW).single()

        assertEquals("起床提醒", card.title)
        assertEquals("已经到点", card.statusText)
        assertEquals("七点半前确认你已经起床", card.commitmentText)
        assertTrue(card.overdue)
        assertTrue(card.nextPerceptionText.startsWith("原定留意时间："))
    }

    @Test
    fun `commitment without concern is still visible`() {
        val snapshot = CompanionSnapshot(
            assistantId = "lulu",
            commitments = listOf(
                CompanionCommitment(
                    id = "calendar",
                    assistantId = "lulu",
                    subjectKey = "calendar",
                    promise = "提醒你参加会议",
                    dueAt = NOW + 60_000L,
                    actionPlan = CompanionActionPlan(type = CompanionActionType.CALENDAR),
                )
            ),
        )

        val card = buildCompanionConcernCards(snapshot, nowMillis = NOW).single()

        assertEquals("commitment:calendar", card.id)
        assertEquals("日程安排", card.title)
    }

    @Test
    fun `legacy records with the same subject collapse into one card`() {
        val snapshot = CompanionSnapshot(
            assistantId = "lulu",
            concerns = listOf(
                concern(id = "old-concern", subject = " wake : morning ", nextAt = NOW + 30_000L),
                concern(id = "new-concern", subject = "wake:morning", nextAt = NOW + 60_000L),
            ),
            commitments = listOf(
                commitment(id = "old-commitment", subject = "wake:morning", dueAt = NOW + 30_000L),
                commitment(id = "new-commitment", subject = " wake : morning ", dueAt = NOW + 60_000L),
            ),
        )

        val cards = buildCompanionConcernCards(snapshot, nowMillis = NOW)

        assertEquals(1, cards.size)
        assertEquals("起床提醒", cards.single().title)
    }

    @Test
    fun `different planner keys link when they came from the same user message`() {
        val snapshot = CompanionSnapshot(
            assistantId = "lulu",
            concerns = listOf(
                concern(
                    id = "concern",
                    subject = "check-in:morning",
                    nextAt = NOW + 60_000L,
                    sourceMessageId = "message-1",
                ),
            ),
            commitments = listOf(
                commitment(
                    id = "commitment",
                    subject = "schedule:wake-up",
                    dueAt = NOW + 60_000L,
                    sourceMessageId = "message-1",
                ),
            ),
        )

        val cards = buildCompanionConcernCards(snapshot, nowMillis = NOW)

        assertEquals(1, cards.size)
        assertEquals("起床提醒", cards.single().title)
    }

    @Test
    fun `legacy planner keys link by matching meaning and nearby target time`() {
        val snapshot = CompanionSnapshot(
            assistantId = "lulu",
            concerns = listOf(
                CompanionConcern(
                    id = "legacy-concern",
                    assistantId = "lulu",
                    subjectKey = "care:important-task",
                    event = "记着这件有时间要求的事",
                    goal = "在约定时间继续提醒和确认",
                    nextPerceptionAt = NOW + 60_000L,
                ),
            ),
            commitments = listOf(
                CompanionCommitment(
                    id = "legacy-commitment",
                    assistantId = "lulu",
                    subjectKey = "schedule:important-task",
                    promise = "在约定时间继续提醒和确认",
                    dueAt = NOW + 60_500L,
                    status = CompanionCommitmentStatus.ACTIVE,
                    actionPlan = CompanionActionPlan(
                        type = CompanionActionType.CHECK_IN,
                        category = "schedule",
                    ),
                ),
            ),
        )

        val cards = buildCompanionConcernCards(snapshot, nowMillis = NOW)

        assertEquals(1, cards.size)
        assertEquals("时间提醒", cards.single().title)
    }

    private fun concern(
        id: String,
        subject: String,
        nextAt: Long,
        sourceMessageId: String? = null,
    ) = CompanionConcern(
        id = id,
        assistantId = "lulu",
        subjectKey = subject,
        event = "需要继续留意",
        goal = "确认事情得到处理",
        nextPerceptionAt = nextAt,
        sourceMessageIds = listOfNotNull(sourceMessageId),
    )

    private fun commitment(
        id: String,
        subject: String,
        dueAt: Long,
        sourceMessageId: String? = null,
    ) = CompanionCommitment(
        id = id,
        assistantId = "lulu",
        subjectKey = subject,
        promise = "确认你已经起床",
        dueAt = dueAt,
        status = CompanionCommitmentStatus.ACTIVE,
        actionPlan = CompanionActionPlan(
            type = CompanionActionType.CHECK_IN,
            category = "wake",
        ),
        sourceMessageId = sourceMessageId,
    )

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
