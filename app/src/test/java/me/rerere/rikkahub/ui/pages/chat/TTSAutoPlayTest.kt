package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class TTSAutoPlayTest {
    @Test
    fun `returns null when every assistant message is already marked spoken`() {
        val first = assistantMessage("first")
        val second = assistantMessage("second")

        assertTrue(
            findAutoPlayTTSMessages(
                nodes = listOf(MessageNode.of(first), MessageNode.of(second)),
                spokenMessageIds = setOf(first.id, second.id),
            ).isEmpty(),
        )
    }

    @Test
    fun `loaded history becomes spoken baseline before autoplay starts`() {
        val history = listOf(
            MessageNode.of(assistantMessage("loaded first")),
            MessageNode.of(assistantMessage("loaded second")),
        )
        val spoken = history.finishedAssistantMessageIdsForAutoPlay()

        assertTrue(
            findAutoPlayTTSMessages(
                nodes = history,
                spokenMessageIds = spoken,
            ).isEmpty(),
        )
    }

    @Test
    fun `selects newly finished assistant messages as one autoplay batch`() {
        val first = assistantMessage("first")
        val second = assistantMessage("second")
        val third = assistantMessage("third")

        assertEquals(
            listOf(second.id, third.id),
            findAutoPlayTTSMessages(
                nodes = listOf(MessageNode.of(first), MessageNode.of(second), MessageNode.of(third)),
                spokenMessageIds = setOf(first.id),
            ).map { it.id },
        )
    }

    @Test
    fun `does not replay the same assistant message`() {
        val message = assistantMessage("already spoken")

        assertTrue(
            findAutoPlayTTSMessages(
                nodes = listOf(MessageNode.of(message)),
                spokenMessageIds = setOf(message.id),
            ).isEmpty(),
        )
    }

    @Test
    fun `builds autoplay batch text from visible messages without duplicate lines`() {
        val first = assistantMessage("visible reply")
        val second = assistantMessage("visible reply")

        assertEquals(
            "visible reply",
            buildAutoPlayTTSBatchText(listOf(first, second), onlyReadQuoted = false),
        )
    }

    private fun assistantMessage(text: String): UIMessage =
        UIMessage.assistant(text).copy(
            finishedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        )
}
