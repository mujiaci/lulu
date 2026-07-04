package me.rerere.rikkahub.ui.components.message

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeakableMessageTextTest {
    @Test
    fun `buildSpeakableMessageText does not read lulupresence metadata aloud`() {
        val message = UIMessage.assistant(
            """
            我在。
            <lulupresence>
            status: 靠近屏幕
            description: 把手机拿近一点，等你继续说
            inner voice: 我有点担心，但不想把担心直接读出来。
            </lulupresence>
            """.trimIndent()
        )

        assertEquals("我在。", buildSpeakableMessageText(message, onlyReadQuoted = false))
    }

    @Test
    fun `buildSpeakableMessageText ignores speaking tool labels and non speech role lines`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Text("动作：她把手机贴近一点\nspeaking: 晚安啦，闭眼。\n语言：晚安啦，闭眼。")
            )
        )

        assertEquals("晚安啦，闭眼。", buildSpeakableMessageText(message, onlyReadQuoted = false))
    }

    @Test
    fun `buildSpeakableMessageText returns null for messages without speakable text`() {
        val message = UIMessage.assistant("动作：她轻轻挥手")

        assertNull(buildSpeakableMessageText(message, onlyReadQuoted = false))
    }

    @Test
    fun `buildSpeakableMessageText can use speaking content when it is the only voice text`() {
        val message = UIMessage.assistant("speaking: 晚安啦，闭眼。")

        assertEquals("晚安啦，闭眼。", buildSpeakableMessageText(message, onlyReadQuoted = false))
    }

    @Test
    fun `buildSpeakableMessageText does not fall back to generated speaking when visible text exists`() {
        val message = UIMessage.assistant("(visible action)\nspeaking: wrong generated voice")

        assertNull(buildSpeakableMessageText(message, onlyReadQuoted = false))
    }

    @Test
    fun `buildSpeakableMessageText reads visible assistant text instead of generated speaking`() {
        val message = UIMessage.assistant("visible reply\nspeaking: wrong generated voice")

        assertEquals("visible reply", buildSpeakableMessageText(message, onlyReadQuoted = false))
    }

    @Test
    fun `buildSpeakableMessageText reads full visible text even when quoted mode is enabled`() {
        val message = UIMessage.assistant("visible before \"quoted part\" visible after")

        assertEquals(
            "visible before \"quoted part\" visible after",
            buildSpeakableMessageText(message, onlyReadQuoted = true)
        )
    }

    @Test
    fun `buildSpeakableMessageText prefers visible assistant text over text to speech tool input`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Tool(
                    toolCallId = "call-1",
                    toolName = "text_to_speech",
                    input = buildJsonObject {
                        put("text", "佳辞！真的吗？！学完了？！露露刚才还一直在催你……")
                    }.toString(),
                    output = listOf(
                        UIMessagePart.Text(
                            buildJsonObject {
                                put("success", JsonPrimitive(true))
                                put("text", "佳辞！真的吗？！学完了？！露露刚才还一直在催你……")
                            }.toString()
                        )
                    )
                ),
                UIMessagePart.Text("露露刚才还傻乎乎地一直催你……呜呜，我是不是唠叨了一整天呀。")
            )
        )

        assertEquals(
            "露露刚才还傻乎乎地一直催你……呜呜，我是不是唠叨了一整天呀。",
            buildSpeakableMessageText(message, onlyReadQuoted = false)
        )
    }
}
