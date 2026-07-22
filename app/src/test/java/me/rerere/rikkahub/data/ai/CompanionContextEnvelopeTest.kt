package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionContextEnvelopeTest {
    @Test
    fun `long chat uses a bounded sliding window and keeps latest messages`() {
        val messages = (1..500).map { index -> UIMessage.user("message-$index") }

        val envelope = buildCompanionContextEnvelope(
            assistant = Assistant(contextMessageSize = 0),
            source = ApiUsageSource.CHAT,
            messages = messages,
            characterCore = "persona",
            globalLorebook = "mandatory world",
            roleLorebook = "",
            otherMandatoryPrompt = "",
        )

        assertEquals(48, envelope.messages.size)
        assertEquals(452, envelope.droppedHistoryMessages)
        assertEquals("message-500", envelope.messages.last().toText())
    }

    @Test
    fun `phone has its own smaller history budget`() {
        val messages = (1..100).map { UIMessage.user("turn-$it") }

        val envelope = buildCompanionContextEnvelope(
            assistant = Assistant(contextMessageSize = 100),
            source = ApiUsageSource.PHONE,
            messages = messages,
            characterCore = "persona",
            globalLorebook = "world",
            roleLorebook = "",
            otherMandatoryPrompt = "",
        )

        assertEquals(24, envelope.messages.size)
        assertEquals(76, envelope.droppedHistoryMessages)
    }

    @Test
    fun `all required diagnostic sections are always present`() {
        val envelope = buildCompanionContextEnvelope(
            assistant = Assistant(),
            source = ApiUsageSource.CHAT,
            messages = listOf(
                UIMessage.system("<lulu_memory>remember this</lulu_memory>"),
                UIMessage.user("hello"),
            ),
            characterCore = "persona",
            globalLorebook = "global",
            roleLorebook = "conditional",
            otherMandatoryPrompt = "other",
        )

        assertEquals(
            listOf("角色核心", "全局世界书", "角色世界书", "最近消息", "滚动摘要", "记忆", "关系/状态", "承诺/关注", "其他提示词"),
            envelope.sections.map { it.label },
        )
        assertTrue(envelope.sections.first { it.label == "记忆" }.estimatedTokens > 0)
    }

    @Test
    fun `rolling summary replaces old raw turns and survives the sliding window`() {
        val messages = buildList {
            add(UIMessage.user("<rolling_summary>the first 200 turns</rolling_summary>"))
            addAll((201..500).map { UIMessage.user("message-$it") })
        }

        val envelope = buildCompanionContextEnvelope(
            assistant = Assistant(contextMessageSize = 20),
            source = ApiUsageSource.CHAT,
            messages = messages,
            characterCore = "persona",
            globalLorebook = "world",
            roleLorebook = "",
            otherMandatoryPrompt = "",
        )

        assertTrue(envelope.messages.first().toText().contains("first 200 turns"))
        assertEquals(20, envelope.messages.count { !it.toText().contains("<rolling_summary") })
        assertTrue(envelope.sections.first { it.label == "滚动摘要" }.estimatedTokens > 0)
    }

    @Test
    fun `only newest rolling summary is sent even when multiple paths injected it`() {
        val oldSummary = "<rolling_summary>old facts</rolling_summary>"
        val newSummary = "<rolling_summary>new facts</rolling_summary>"
        val envelope = buildCompanionContextEnvelope(
            assistant = Assistant(),
            source = ApiUsageSource.CHAT,
            messages = listOf(
                UIMessage.system(oldSummary),
                UIMessage.user("hello"),
                UIMessage.user(newSummary),
                UIMessage.system(newSummary),
                UIMessage.user("continue"),
            ),
            characterCore = "persona",
            globalLorebook = "",
            roleLorebook = "",
            otherMandatoryPrompt = "",
        )

        val summaries = envelope.messages.filter { it.toText().contains("<rolling_summary") }
        assertEquals(1, summaries.size)
        assertEquals(newSummary, summaries.single().toText())
    }

    @Test
    fun `adjacent duplicate conversational messages are sent once`() {
        val envelope = buildCompanionContextEnvelope(
            assistant = Assistant(),
            source = ApiUsageSource.CHAT,
            messages = listOf(
                UIMessage.user("same request"),
                UIMessage.user("same request"),
                UIMessage.assistant("same answer"),
                UIMessage.assistant("same answer"),
                UIMessage.user("next"),
            ),
            characterCore = "persona",
            globalLorebook = "",
            roleLorebook = "",
            otherMandatoryPrompt = "",
        )

        assertEquals(
            listOf("same request", "same answer", "next"),
            envelope.messages.map { it.toText() },
        )
        assertEquals(2, envelope.droppedHistoryMessages)
    }

    @Test(expected = CompanionContextOverflowException::class)
    fun `mandatory global context is never silently truncated`() {
        buildCompanionContextEnvelope(
            assistant = Assistant(),
            source = ApiUsageSource.PHONE,
            messages = listOf(UIMessage.user("hello")),
            characterCore = "persona",
            globalLorebook = "x".repeat(70_000),
            roleLorebook = "",
            otherMandatoryPrompt = "",
        )
    }
}
