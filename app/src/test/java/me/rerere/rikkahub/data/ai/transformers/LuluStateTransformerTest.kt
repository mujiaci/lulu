package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.LuluEnergy
import me.rerere.rikkahub.data.model.LuluMode
import me.rerere.rikkahub.data.model.LuluMood
import me.rerere.rikkahub.data.model.LuluRelationship
import me.rerere.rikkahub.data.model.LuluState
import me.rerere.rikkahub.data.model.LuluThought
import me.rerere.rikkahub.data.model.LuluThoughtCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LuluStateTransformerTest {
    @Test
    fun `injects current state before latest user message`() {
        val assistantId = Uuid.parse("77777777-7777-7777-7777-777777777777")
        val messages = listOf(
            UIMessage.system("base"),
            UIMessage.user("hello"),
            UIMessage.assistant("hi"),
            UIMessage.user("how are you"),
        )
        val state = LuluState(
            assistantId = assistantId,
            statusText = "有点困了",
            innerVoice = "夜里想安静陪着你。",
            mood = LuluMood.SOFT,
            moodIntensity = 0.62f,
            energy = LuluEnergy.SLEEPY,
            energyIntensity = 0.70f,
            relationship = LuluRelationship.CLOSE,
            relationshipIntensity = 0.81f,
            mode = LuluMode.RESTING,
            updatedAt = 1234L,
            sinceAt = 1000L,
            selfScene = "露露把手机放在枕边，声音压得很轻，像是在夜里陪着。",
            perceptionSummary = "深夜 / 休息中 / 用户信号：睡眠偏少、电量低",
        )

        val result = applyLuluStateContext(
            messages = messages,
            assistantId = assistantId,
            states = listOf(state),
            thoughts = listOf(
                LuluThought(
                    assistantId = assistantId,
                    content = "我想等他先说完，再轻轻接住。",
                    category = LuluThoughtCategory.PENDING_ACTION,
                    importance = 4,
                    createdAt = 1_000L,
                    expiresAt = 99_999L,
                )
            ),
            nowMillis = 1_500L,
        )

        assertEquals(messages.size + 1, result.size)
        assertEquals(MessageRole.SYSTEM, result[result.lastIndex - 1].role)
        assertEquals(MessageRole.USER, result.last().role)
        val injected = result[result.lastIndex - 1].toText()
        assertTrue(injected.contains("<lulu_presence>"))
        assertTrue(injected.contains("有点困了"))
        assertTrue(injected.contains("夜里想安静陪着你。"))
        assertTrue(injected.contains("露露自己的场景：露露把手机放在枕边"))
        assertTrue(injected.contains("精力：有点困"))
        assertTrue(injected.contains("强度 0.70"))
        assertTrue(injected.contains("状态持续"))
        assertTrue(injected.contains("当前感知：深夜 / 休息中 / 用户信号：睡眠偏少、电量低"))
        assertTrue(injected.contains("[未完成动作] 我想等他先说完"))
        assertTrue(injected.contains("表达建议"))
        assertTrue(injected.contains("动作描写建议"))
        assertTrue(injected.contains("可参考素材"))
        assertTrue(injected.contains("set_lulu_expression_state"))
    }

    @Test
    fun `does not change messages when assistant has no stored state`() {
        val assistantId = Uuid.parse("88888888-8888-8888-8888-888888888888")
        val messages = listOf(UIMessage.user("hello"))

        val result = applyLuluStateContext(
            messages = messages,
            assistantId = assistantId,
            states = emptyList(),
        )

        assertEquals(messages, result)
    }

    @Test
    fun `injects projected silence scene instead of stale stored scene`() {
        val assistantId = Uuid.parse("99999999-9999-9999-9999-999999999999")
        val messages = listOf(UIMessage.user("你在吗"))
        val state = LuluState(
            assistantId = assistantId,
            statusText = "陪着你",
            mood = LuluMood.HAPPY,
            mode = LuluMode.COMPANION,
            updatedAt = 1_000L,
            selfScene = "旧场景",
        )

        val result = applyLuluStateContext(
            messages = messages,
            assistantId = assistantId,
            states = listOf(state),
            nowMillis = 2 * 60 * 60_000L + 1_000L,
        )

        val injected = result.first { it.role == MessageRole.SYSTEM }.toText()
        assertTrue(injected.contains("有点想你"))
        assertTrue(injected.contains("反复看了几次上一条消息"))
    }
}
