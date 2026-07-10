package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.LocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.content
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LuluExpressionOutputTransformerTest {
    @Test
    fun `splits long assistant speech into short bubbles`() {
        val original = assistantMessage("A\u3002B\u3002C\u3002")

        val result = splitLuluAssistantExpressionMessages(listOf(original))

        assertEquals(3, result.size)
        assertEquals("A\u3002", result[0].toText())
        assertEquals("B\u3002", result[1].toText())
        assertEquals("C\u3002", result[2].toText())
        assertEquals(original.id, result[0].id)
        assertEquals(original.modelId, result[1].modelId)
    }

    @Test
    fun `keeps message unchanged when reply is short`() {
        val original = assistantMessage("OK.")

        val result = splitLuluAssistantExpressionMessages(listOf(original))

        assertEquals(1, result.size)
        assertSame(original, result.single())
    }

    @Test
    fun `does not split assistant messages with non text parts`() {
        val original = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Text("I will check. Then I will tell you."),
                UIMessagePart.Tool(
                    toolCallId = "tool-1",
                    toolName = "get_battery",
                    input = "{}",
                ),
            ),
        )

        val result = splitLuluAssistantExpressionMessages(listOf(original))

        assertEquals(listOf(original), result)
    }

    @Test
    fun `does not split code blocks or markdown lists`() {
        val code = assistantMessage("```kotlin\nprintln(\"hi\")\n```")
        val list = assistantMessage("Things:\n- rest\n- study")

        assertEquals(listOf(code), splitLuluAssistantExpressionMessages(listOf(code)))
        assertEquals(listOf(list), splitLuluAssistantExpressionMessages(listOf(list)))
    }

    @Test
    fun `only splits the final assistant message`() {
        val user = UIMessage.user("hello")
        val assistant = assistantMessage("I picked up. I was slow. I can hear you now.")

        val result = splitLuluAssistantExpressionMessages(listOf(user, assistant))

        assertEquals(MessageRole.USER, result[0].role)
        assertEquals(4, result.size)
        assertEquals("I picked up.", result[1].toText())
        assertEquals("I was slow.", result[2].toText())
        assertEquals("I can hear you now.", result[3].toText())
    }

    @Test
    fun `strips leaked lulu presence fields before showing assistant message`() {
        val assistant = assistantMessage(
            """
            <lulu_presence>
            动作描写建议：请合成一句自然动作描写
            可参考素材：表情=✨；动作=开心贴近；姿势=靠近
            </lulu_presence>
            我来啦。
            """.trimIndent()
        )

        val result = splitLuluAssistantExpressionMessages(listOf(assistant))

        assertEquals(1, result.size)
        assertEquals("我来啦。", result.single().toText())
    }

    @Test
    fun `stores lulu presence block as hidden metadata annotation`() {
        val assistant = assistantMessage(
            """
            我在。
            <lulu_presence>
            status: 在靠近你
            description: 我把手机握近了一点，认真等你说完。
            inner_voice: 我有点担心，但不想把你逼得更累。
            thought: 我想记得他现在需要被温柔接住。
            mood: 关切但克制
            body_state: 没有可确认的身体状态
            mind_state: 专注倾听
            activity_mode: conversation
            </lulu_presence>
            """.trimIndent()
        )

        val result = splitLuluAssistantExpressionMessages(listOf(assistant))
        val annotation = result.single().annotations
            .filterIsInstance<UIMessageAnnotation.Metadata>()
            .single()

        assertEquals("我在。", result.single().toText())
        assertEquals(LULU_PRESENCE_METADATA_TYPE, annotation.type)
        assertEquals("在靠近你", annotation.data["status"]?.jsonPrimitive?.content)
        assertEquals("我有点担心，但不想把你逼得更累。", annotation.data["inner_voice"]?.jsonPrimitive?.content)
        assertEquals("关切但克制", annotation.data["mood"]?.jsonPrimitive?.content)
        assertEquals("没有可确认的身体状态", annotation.data["body_state"]?.jsonPrimitive?.content)
        assertEquals("专注倾听", annotation.data["mind_state"]?.jsonPrimitive?.content)
        assertEquals("conversation", annotation.data["activity_mode"]?.jsonPrimitive?.content)
    }

    @Test
    fun `strips compact lulupresence blocks and stores inner voice metadata`() {
        val assistant = assistantMessage(
            """
            我在。
            <lulupresence>
            status: 靠近屏幕
            description: 把手机拿近一点，等你继续说
            inner voice: 我有点担心，但不想把担心直接读出来。
            </lulupresence>
            """.trimIndent()
        )

        val result = splitLuluAssistantExpressionMessages(listOf(assistant))
        val annotation = result.single().annotations
            .filterIsInstance<UIMessageAnnotation.Metadata>()
            .single()

        assertEquals("我在。", result.single().toText())
        assertEquals("靠近屏幕", annotation.data["status"]?.jsonPrimitive?.content)
        assertEquals("把手机拿近一点，等你继续说", annotation.data["description"]?.jsonPrimitive?.content)
        assertEquals("我有点担心，但不想把担心直接读出来。", annotation.data["inner_voice"]?.jsonPrimitive?.content)
    }

    @Test
    fun `drops separate presence bubble and attaches metadata to visible assistant message`() {
        val first = assistantMessage("不是没人想跟你做好朋友，我在。")
        val presence = assistantMessage(
            """
            <lulu_presence>
            status: 认真倾听中
            description: 露露把屏幕亮度调暗，声音放得很轻很慢。
            inner_voice: 她问我这个问题的时候，我心里酸了一下。
            thought: 她想被当成朋友而不是猎物。
            </lulu_presence>
            """.trimIndent()
        )
        val action = assistantMessage("露露轻轻叹了口气，但还是打起精神撑着眼皮陪在屏幕前。")

        val result = splitLuluAssistantExpressionMessages(listOf(first, presence, action))
        val annotation = result.last().annotations
            .filterIsInstance<UIMessageAnnotation.Metadata>()
            .single()

        assertEquals(2, result.size)
        assertEquals("不是没人想跟你做好朋友，我在。", result.first().toText())
        assertEquals("露露轻轻叹了口气，但还是打起精神撑着眼皮陪在屏幕前。", result.last().toText())
        assertEquals("认真倾听中", annotation.data["status"]?.jsonPrimitive?.content)
        assertEquals("露露把屏幕亮度调暗，声音放得很轻很慢。", annotation.data["description"]?.jsonPrimitive?.content)
        assertEquals("她问我这个问题的时候，我心里酸了一下。", annotation.data["inner_voice"]?.jsonPrimitive?.content)
    }

    @Test
    fun `metadata annotation serializes without discriminator conflict`() {
        val json = Json { ignoreUnknownKeys = true }
        val annotation: UIMessageAnnotation = UIMessageAnnotation.Metadata(
            type = LULU_PRESENCE_METADATA_TYPE,
            data = buildJsonObject {
                put("status", "waiting")
            },
        )

        val encoded = json.encodeToString(annotation)
        val decoded = json.decodeFromString<UIMessageAnnotation>(encoded)

        val metadata = decoded as UIMessageAnnotation.Metadata
        assertEquals(LULU_PRESENCE_METADATA_TYPE, metadata.type)
        assertEquals("waiting", metadata.data["status"]?.jsonPrimitive?.content)
    }

    private fun assistantMessage(text: String) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text(text)),
        createdAt = LocalDateTime(2026, 6, 30, 12, 0, 0),
    )
}
