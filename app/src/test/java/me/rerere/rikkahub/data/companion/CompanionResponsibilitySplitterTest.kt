package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionResponsibilitySplitterTest {
    @Test
    fun `one request creates separate wake sleep and study responsibilities`() {
        val draft = CompanionFollowUpDraft(
            assistantId = "assistant-a",
            category = "general",
            reason = "explicit responsibility",
            sourceText = "以后帮我监督起床、睡觉和学习",
            dueAt = 100L,
            sourceConversationId = "conversation-a",
            sourceMessageId = "message-a",
        )

        val result = expandExplicitCompanionResponsibilities(listOf(draft))

        assertEquals(listOf("wake", "sleep", "study"), result.map { it.category })
        assertEquals(3, result.map { it.toCommitment(10L).subjectKey }.distinct().size)
        assertEquals(
            listOf(
                "到点叫醒你，并继续确认你已经醒来。",
                "在起床时间之前提醒你早点休息。",
                "按你现在的状态继续跟进学习节奏。",
            ),
            result.map { it.toCommitment(10L).promise },
        )
    }

    @Test
    fun `ordinary topic mentions never invent responsibilities`() {
        val draft = CompanionFollowUpDraft(
            assistantId = "assistant-a",
            category = "general",
            reason = "ordinary chat",
            sourceText = "我今天起床以后学习了一会儿，晚上准备早点睡觉",
            dueAt = 100L,
        )

        assertEquals(listOf(draft), expandExplicitCompanionResponsibilities(listOf(draft)))
    }

    @Test
    fun `already separated drafts remain one per responsibility`() {
        val source = "以后帮我监督起床、睡觉和学习"
        val drafts = listOf("wake", "sleep", "study").map { category ->
            CompanionFollowUpDraft(
                assistantId = "assistant-a",
                category = category,
                reason = "explicit responsibility",
                sourceText = source,
                dueAt = 100L,
                sourceConversationId = "conversation-a",
                sourceMessageId = "message-a",
            )
        }

        val result = expandExplicitCompanionResponsibilities(drafts)

        assertEquals(listOf("wake", "sleep", "study"), result.map { it.category })
    }
}
