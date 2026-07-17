package me.rerere.rikkahub.data.service

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.companion.CompanionCommitmentStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ProactiveMessageAssemblyTest {
    @Test
    fun `proactive generation leaves system prompt ownership to generation handler`() {
        val history = listOf(
            UIMessage.user("昨晚记得叫我起床"),
            UIMessage.assistant("我记着。"),
        )

        val messages = composeProactiveGenerationMessages(
            historyMessages = history,
            currentContext = UIMessage.user("当前感知和长期记忆"),
        )

        assertEquals(3, messages.size)
        assertEquals("昨晚记得叫我起床", messages.first().toText())
        assertEquals("当前感知和长期记忆", messages.last().toText())
    }

    @Test
    fun `proactive session sync keeps only assistant messages created by current generation`() {
        val initial = listOf(
            UIMessage.user("旧问题"),
            UIMessage.assistant("旧回复"),
            UIMessage.user("当前感知"),
        )
        val generated = listOf(
            UIMessage.assistant("先执行一个动作"),
            UIMessage.assistant("动作完成后的最终回复"),
        )

        val actual = generatedProactiveAssistantMessages(
            initialMessageIds = initial.map { it.id }.toSet(),
            messages = initial + generated,
        )

        assertEquals(generated.map { it.id }, actual.map { it.id })
    }

    @Test
    fun `proactive reply is discarded when user resolves its commitment during generation`() {
        assertEquals(
            true,
            shouldDiscardProactiveReply(
                replyText = "起床了吗？",
                executingCommitmentId = "wake-1",
                currentCommitmentStatus = CompanionCommitmentStatus.FULFILLED,
            ),
        )
        assertEquals(
            true,
            shouldDiscardProactiveReply(
                replyText = "我再叫你一次",
                executingCommitmentId = "wake-1",
                currentCommitmentStatus = CompanionCommitmentStatus.CANCELLED,
            ),
        )
        assertEquals(
            false,
            shouldDiscardProactiveReply(
                replyText = "醒醒，时间到了",
                executingCommitmentId = "wake-1",
                currentCommitmentStatus = CompanionCommitmentStatus.EXECUTING,
            ),
        )
    }
}
