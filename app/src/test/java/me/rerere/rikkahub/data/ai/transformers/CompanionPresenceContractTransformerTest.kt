package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionPresenceContractTransformerTest {
    @Test
    fun `injects compact presence contract before latest user message`() {
        val messages = listOf(
            UIMessage.system("base"),
            UIMessage.user("hello"),
            UIMessage.assistant("hi"),
            UIMessage.user("how are you"),
        )

        val result = applyCompanionPresenceContract(
            messages = messages,
            assistantName = "阿澈",
        )

        assertEquals(messages.size + 1, result.size)
        assertEquals(MessageRole.SYSTEM, result[result.lastIndex - 1].role)
        assertEquals(MessageRole.USER, result.last().role)
        val injected = result[result.lastIndex - 1].toText()
        assertTrue(injected.contains("<companion_presence_contract>"))
        assertTrue(injected.contains("阿澈"))
        assertTrue(injected.contains("<companion_runtime>"))
        assertTrue(injected.contains("<lulu_presence>"))
        assertTrue(injected.contains("inner_voice:"))
        assertTrue(injected.contains("thought:"))
        assertTrue(injected.contains("mood:"))
        assertTrue(injected.contains("body_state:"))
        assertTrue(injected.contains("mind_state:"))
        assertTrue(injected.contains("activity_mode:"))
        assertTrue(injected.contains("emoji:"))
        assertTrue(injected.contains("sticker:"))
        assertTrue(injected.contains("bubble_pacing:"))
        assertTrue(injected.contains("唯一事实快照"))
        assertTrue(injected.contains("recent_digital_life"))
        assertTrue(injected.contains("active_concerns"))
        assertTrue(injected.contains("active_commitments"))
        assertTrue(injected.contains("状态连续"))
        assertTrue(injected.contains("没有真实内在反应时留空"))
        assertTrue(injected.contains("避免客服总结"))
        assertTrue(injected.contains("多数普通轮次可留空"))
        assertTrue(injected.length < 1_800)
    }

    @Test
    fun `contract does not contain a second companion state snapshot`() {
        val contract = buildCompanionPresenceContract("当前角色")

        assertFalse(contract.contains("当前状态："))
        assertFalse(contract.contains("心情："))
        assertFalse(contract.contains("精力："))
        assertFalse(contract.contains("关系位置："))
        assertFalse(contract.contains("未说出口的想法："))
        assertTrue(contract.contains("唯一事实快照"))
    }

    @Test
    fun `does not change an empty message list`() {
        assertEquals(
            emptyList<UIMessage>(),
            applyCompanionPresenceContract(emptyList(), assistantName = "当前角色"),
        )
    }
}
