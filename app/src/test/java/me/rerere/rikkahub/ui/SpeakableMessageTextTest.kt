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
