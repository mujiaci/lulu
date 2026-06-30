package me.rerere.rikkahub.service

import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.LuluMood
import me.rerere.rikkahub.data.model.LuluThought
import me.rerere.rikkahub.data.model.LuluThoughtCategory
import me.rerere.rikkahub.data.service.markTargetedProactiveThoughtExpressed
import me.rerere.rikkahub.data.model.currentLuluState
import me.rerere.rikkahub.data.model.toMessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class VoiceCallChatSyncTest {
    @Test
    fun `voice call visible turn stores only real conversation text`() {
        val conversationId = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val assistantId = Uuid.parse("22222222-2222-2222-2222-222222222222")
        val conversation = Conversation.ofId(conversationId, assistantId)

        val updated = appendVoiceCallVisibleTurn(
            conversation = conversation,
            userText = "我刚刚下课了",
            assistantText = "那先喝口水，慢慢走。",
        )

        val messages = updated.currentMessages
        assertEquals(2, messages.size)
        assertEquals(MessageRole.USER, messages[0].role)
        assertEquals("我刚刚下课了", messages[0].toText())
        assertEquals(MessageRole.ASSISTANT, messages[1].role)
        assertEquals("那先喝口水，慢慢走。", messages[1].toText())
        assertFalse(updated.currentMessages.any { it.toText().contains("请只输出") })
    }

    @Test
    fun `voice call opening stores assistant only`() {
        val conversationId = Uuid.parse("33333333-3333-3333-3333-333333333333")
        val assistantId = Uuid.parse("44444444-4444-4444-4444-444444444444")
        val conversation = Conversation.ofId(conversationId, assistantId)

        val updated = appendVoiceCallVisibleTurn(
            conversation = conversation,
            userText = null,
            assistantText = "喂，我在呢。",
        )

        assertEquals(1, updated.currentMessages.size)
        assertTrue(updated.currentMessages.all { it.role == MessageRole.ASSISTANT })
        assertEquals("喂，我在呢。", updated.currentMessages.single().toText())
    }

    @Test
    fun `voice call visible assistant message keeps token usage for stats`() {
        val conversationId = Uuid.parse("77777777-7777-7777-7777-777777777777")
        val assistantId = Uuid.parse("88888888-8888-8888-8888-888888888888")
        val conversation = Conversation.ofId(conversationId, assistantId)
        val assistantMessage = UIMessage
            .assistant("电话回复")
            .copy(usage = TokenUsage(promptTokens = 10, completionTokens = 5, cachedTokens = 3, totalTokens = 15))

        val updated = appendVoiceCallVisibleTurn(
            conversation = conversation,
            userText = "喂",
            assistantText = assistantMessage.toText(),
            assistantMessage = assistantMessage,
        )

        assertEquals(assistantMessage.usage, updated.currentMessages.last().usage)
    }

    @Test
    fun `voice call prompt leak cleanup removes internal instructions`() {
        val conversationId = Uuid.parse("55555555-5555-5555-5555-555555555555")
        val assistantId = Uuid.parse("66666666-6666-6666-6666-666666666666")
        val leakedPrompt = """
            这是一个来自用户的语音电话，现在电话已经接通。
            你是露露，请你主动先开口和用户说第一句话，不要等用户先说话。
            请只输出你要说出口的话，不要复述这段说明，不要输出动作、心理、环境、感受，也不要加标签。
        """.trimIndent()
        val conversation = Conversation.ofId(
            id = conversationId,
            assistantId = assistantId,
            messages = listOf(
                UIMessage.user(leakedPrompt).toMessageNode(),
                UIMessage.assistant("喂，我在。").toMessageNode(),
                UIMessage.user("喂").toMessageNode(),
            ),
        )

        val cleaned = conversation.withoutVoiceCallInstructionLeaks()

        assertEquals(2, cleaned.currentMessages.size)
        assertEquals(listOf("喂，我在。", "喂"), cleaned.currentMessages.map { it.toText() })
        assertFalse(cleaned.currentMessages.any { it.toText().contains("请只输出") })
    }

    @Test
    fun `presence turn records state and thought after assistant reply`() {
        val assistantId = Uuid.parse("99999999-9999-9999-9999-999999999999")
        val settings = Settings(
            assistants = listOf(Assistant(id = assistantId, name = "露露")),
        )

        val updated = settings.recordLuluPresenceTurn(
            assistantId = assistantId,
            userText = "我今天真的难过到想哭",
            assistantText = "我在这里陪你。",
            nowMillis = 10_000L,
            hourOfDay = 21,
        )

        assertEquals(LuluMood.WORRIED, updated.luluStates.currentLuluState(assistantId).mood)
        assertEquals(1, updated.luluThoughts.size)
        assertEquals(LuluThoughtCategory.CONCERN, updated.luluThoughts.single().category)
        assertTrue(updated.luluThoughts.single().content.contains("不太舒服"))
    }

    @Test
    fun `presence turn ignores blank assistant reply`() {
        val assistantId = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val settings = Settings(
            assistants = listOf(Assistant(id = assistantId, name = "露露")),
        )

        val updated = settings.recordLuluPresenceTurn(
            assistantId = assistantId,
            userText = "我好累",
            assistantText = "",
        )

        assertTrue(updated.luluStates.isEmpty())
        assertTrue(updated.luluThoughts.isEmpty())
    }

    @Test
    fun `presence turn resolves pending action before adding new context`() {
        val assistantId = Uuid.parse("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        val pending = LuluThought(
            assistantId = assistantId,
            content = "他去学习了，我想等他回来时轻轻接一下。",
            category = LuluThoughtCategory.PENDING_ACTION,
            importance = 4,
            createdAt = 1_000L,
            expiresAt = 100_000L,
        )
        val settings = Settings(
            assistants = listOf(Assistant(id = assistantId, name = "露露")),
            luluThoughts = listOf(pending),
        )

        val updated = settings.recordLuluPresenceTurn(
            assistantId = assistantId,
            userText = "我回来了，学完了",
            assistantText = "欢迎回来。",
            nowMillis = 2_000L,
        )

        assertTrue(updated.luluThoughts.single { it.id == pending.id }.expressed)
    }

    @Test
    fun `presence turn records proactive reminder as pending action`() {
        val assistantId = Uuid.parse("cccccccc-cccc-cccc-cccc-cccccccccccc")
        val settings = Settings(
            assistants = listOf(Assistant(id = assistantId, name = "露露")),
        )
        val plan = ProactiveReminderPlanner.plan(
            userText = "十分钟后提醒我睡觉",
            assistantText = "好，我十分钟后叫你。",
            nowMillis = 10_000L,
        )

        val updated = settings.recordLuluPresenceTurn(
            assistantId = assistantId,
            userText = "十分钟后提醒我睡觉",
            assistantText = "好，我十分钟后叫你。",
            proactiveReminderPlan = plan,
            nowMillis = 10_000L,
        )

        val pending = updated.luluThoughts.single { it.category == LuluThoughtCategory.PENDING_ACTION }
        assertTrue(pending.content.contains("十分钟后"))
        assertTrue(pending.content.contains("提醒"))
        assertEquals(4, pending.importance)
    }

    @Test
    fun `targeted proactive reply marks matching pending action expressed`() {
        val assistantId = Uuid.parse("dddddddd-dddd-dddd-dddd-dddddddddddd")
        val pending = LuluThought(
            assistantId = assistantId,
            content = "我刚才答应了要提醒他睡觉：十分钟后提醒我睡觉",
            category = LuluThoughtCategory.PENDING_ACTION,
            importance = 4,
            createdAt = 1_000L,
            expiresAt = 100_000L,
        )
        val settings = Settings(
            assistants = listOf(Assistant(id = assistantId, name = "露露")),
            luluThoughts = listOf(pending),
        )

        val updated = settings.markTargetedProactiveThoughtExpressed(
            assistantId = assistantId,
            targetedKind = "sleep",
            nowMillis = 20_000L,
        )

        val resolved = updated.luluThoughts.single { it.id == pending.id }
        assertTrue(resolved.expressed)
        assertEquals(20_000L, resolved.expiresAt)
    }

    @Test
    fun `targeted meal proactive reply marks meal pending action expressed`() {
        val assistantId = Uuid.parse("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee")
        val pending = LuluThought(
            assistantId = assistantId,
            content = "我刚才决定稍后来确认他有没有好好吃饭：我还没吃饭",
            category = LuluThoughtCategory.PENDING_ACTION,
            importance = 4,
            createdAt = 1_000L,
            expiresAt = 100_000L,
        )
        val settings = Settings(
            assistants = listOf(Assistant(id = assistantId, name = "露露")),
            luluThoughts = listOf(pending),
        )

        val updated = settings.markTargetedProactiveThoughtExpressed(
            assistantId = assistantId,
            targetedKind = "meal",
            nowMillis = 20_000L,
        )

        assertTrue(updated.luluThoughts.single { it.id == pending.id }.expressed)
    }

    @Test
    fun `targeted study proactive reply marks study pending action expressed`() {
        val assistantId = Uuid.parse("ffffffff-ffff-ffff-ffff-ffffffffffff")
        val pending = LuluThought(
            assistantId = assistantId,
            content = "我刚才决定晚点确认他的学习/写作业状态：我去写作业了",
            category = LuluThoughtCategory.PENDING_ACTION,
            importance = 4,
            createdAt = 1_000L,
            expiresAt = 100_000L,
        )
        val settings = Settings(
            assistants = listOf(Assistant(id = assistantId, name = "露露")),
            luluThoughts = listOf(pending),
        )

        val updated = settings.markTargetedProactiveThoughtExpressed(
            assistantId = assistantId,
            targetedKind = "study",
            nowMillis = 20_000L,
        )

        assertTrue(updated.luluThoughts.single { it.id == pending.id }.expressed)
    }
}
