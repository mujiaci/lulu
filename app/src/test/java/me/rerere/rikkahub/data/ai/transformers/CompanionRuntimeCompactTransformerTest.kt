package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionRuntimeCompactTransformerTest {
    @Test
    fun `keeps only bounded semantic runtime fields`() {
        val runtime = buildString {
            appendLine("<companion_runtime>")
            appendLine("active_commitments:")
            repeat(9) { index ->
                appendLine("- id=c$index due=$index status=ACTIVE promise=promise-$index last_result=very long old result")
            }
            appendLine("recent_digital_life:")
            repeat(8) { index ->
                appendLine("- title=life-$index summary=summary-$index evidence=internal-$index")
            }
            appendLine("perception_facts:")
            repeat(12) { index -> appendLine("- fact-$index value=value-$index") }
            appendLine("</companion_runtime>")
        }

        val result = compactCompanionRuntimeMessages(
            listOf(UIMessage.system(runtime), UIMessage.user("hello")),
        ).first().toText()

        assertEquals(4, result.lineSequence().count { it.trim().startsWith("- due=") })
        assertEquals(3, result.lineSequence().count { it.trim().startsWith("- title=life-") })
        assertEquals(6, result.lineSequence().count { it.trim().startsWith("- fact-") })
        assertFalse(result.contains("last_result="))
        assertFalse(result.contains("evidence="))
        assertTrue(result.contains("<companion_runtime>"))
    }

    @Test
    fun `drops continuity copy already present in recent conversation`() {
        val runtime = """
            <companion_runtime>
            cross_modal_continuity modality=CHAT at=1
            - previous_user=这一句已经在最近消息里出现
            - previous_assistant=好的，我记得
            - instruction=long instruction
            state status=ok
            </companion_runtime>
        """.trimIndent()

        val result = compactCompanionRuntimeMessages(
            listOf(
                UIMessage.system(runtime),
                UIMessage.user("这一句已经在最近消息里出现"),
            ),
        ).first().toText()

        assertFalse(result.contains("previous_user="))
        assertFalse(result.contains("previous_assistant="))
        assertTrue(result.contains("state status=ok"))
    }
}
