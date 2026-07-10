package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionConcernReducerTest {
    @Test
    fun `same assistant and subject updates one concern`() {
        val original = concern(id = "original", event = "明天考试", sourceMessageIds = listOf("m1"))
        val update = concern(id = "replacement", event = "考试改到下午", sourceMessageIds = listOf("m2"))

        val result = CompanionConcernReducer.apply(
            current = listOf(original),
            changes = listOf(CompanionConcernChange.Upsert(update)),
            nowMillis = 200L,
        )

        assertEquals(1, result.size)
        assertEquals("original", result.single().id)
        assertEquals("考试改到下午", result.single().event)
        assertEquals(listOf("m1", "m2"), result.single().sourceMessageIds)
    }

    @Test
    fun `same subject remains isolated between assistants`() {
        val result = CompanionConcernReducer.apply(
            current = listOf(concern(assistantId = "assistant-a")),
            changes = listOf(
                CompanionConcernChange.Upsert(concern(assistantId = "assistant-b", id = "other")),
            ),
            nowMillis = 200L,
        )

        assertEquals(setOf("assistant-a", "assistant-b"), result.map { it.assistantId }.toSet())
    }

    @Test
    fun `different deadline subjects remain separate`() {
        val result = CompanionConcernReducer.apply(
            current = emptyList(),
            changes = listOf(
                CompanionConcernChange.Upsert(concern(id = "exam-a", subjectKey = "exam:2026-12-20")),
                CompanionConcernChange.Upsert(concern(id = "exam-b", subjectKey = "exam:2026-12-22")),
            ),
            nowMillis = 200L,
        )

        assertEquals(2, result.size)
    }

    @Test
    fun `completed concern is not reopened by upsert`() {
        val completed = concern(id = "done").copy(
            status = CompanionConcernStatus.COMPLETED,
            completedReason = "用户已经完成考试",
        )

        val result = CompanionConcernReducer.apply(
            current = listOf(completed),
            changes = listOf(CompanionConcernChange.Upsert(concern(id = "retry"))),
            nowMillis = 200L,
        )

        assertEquals(1, result.size)
        assertEquals(CompanionConcernStatus.COMPLETED, result.single().status)
        assertEquals("done", result.single().id)
    }

    @Test
    fun `due concerns sort by importance then perception time`() {
        val result = CompanionConcernReducer.apply(
            current = listOf(
                concern(id = "later", importance = 5, nextPerceptionAt = 300L),
                concern(id = "low", importance = 2, nextPerceptionAt = 50L),
                concern(id = "first", importance = 5, nextPerceptionAt = 100L),
            ),
            changes = emptyList(),
            nowMillis = 200L,
        )

        assertEquals(listOf("first", "later", "low"), result.map { it.id })
    }

    private fun concern(
        id: String = "concern-1",
        assistantId: String = "assistant-a",
        subjectKey: String = " exam : 2026-12-20 ",
        event: String = "用户明天考试",
        importance: Int = 4,
        nextPerceptionAt: Long? = 100L,
        sourceMessageIds: List<String> = emptyList(),
    ): CompanionConcern = CompanionConcern(
        id = id,
        assistantId = assistantId,
        subjectKey = subjectKey,
        event = event,
        goal = "确认用户按时参加",
        importance = importance,
        nextPerceptionAt = nextPerceptionAt,
        sourceMessageIds = sourceMessageIds,
        createdAt = 10L,
        lastUpdatedAt = 10L,
    )
}
