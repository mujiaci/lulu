package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LuluStateTest {
    @Test
    fun `missing assistant state returns default companion status`() {
        val assistantId = Uuid.parse("11111111-1111-1111-1111-111111111111")

        val state = emptyList<LuluState>().currentLuluState(assistantId)

        assertEquals(assistantId, state.assistantId)
        assertEquals("在发呆", state.statusText)
        assertEquals("今天也想被好好陪着。", state.innerVoice)
        assertEquals("在手机这边安静待着，等你开口。", state.selfScene)
        assertEquals("默认状态", state.reason)
    }

    @Test
    fun `history is filtered by assistant and ordered newest first`() {
        val targetAssistant = Uuid.parse("22222222-2222-2222-2222-222222222222")
        val otherAssistant = Uuid.parse("33333333-3333-3333-3333-333333333333")
        val states = listOf(
            LuluState(
                assistantId = targetAssistant,
                statusText = "刚睡醒",
                updatedAt = 10L,
            ),
            LuluState(
                assistantId = otherAssistant,
                statusText = "不该出现",
                updatedAt = 30L,
            ),
            LuluState(
                assistantId = targetAssistant,
                statusText = "在想你",
                updatedAt = 20L,
            ),
        )

        val history = states.luluStateHistory(targetAssistant)

        assertEquals(listOf("在想你", "刚睡醒"), history.map { it.statusText })
        assertTrue(history.all { it.assistantId == targetAssistant })
        assertEquals("在想你", states.currentLuluState(targetAssistant).statusText)
    }

    @Test
    fun `sad user turn creates worried low energy state`() {
        val assistantId = Uuid.parse("44444444-4444-4444-4444-444444444444")

        val state = buildLuluStateFromTurn(
            assistantId = assistantId,
            userText = "I feel sad and tired tonight",
            assistantText = "I am here with you.",
            nowMillis = 1000L,
            hourOfDay = 21,
        )

        assertEquals(assistantId, state.assistantId)
        assertEquals(LuluMood.WORRIED, state.mood)
        assertEquals(LuluEnergy.LOW, state.energy)
        assertEquals(LuluMode.COMPANION, state.mode)
        assertTrue(state.selfScene.contains("贴近屏幕"))
        assertEquals(1000L, state.updatedAt)
        assertTrue(state.reason.contains("I feel sad"))
    }

    @Test
    fun `late night turn creates sleepy resting state`() {
        val assistantId = Uuid.parse("55555555-5555-5555-5555-555555555555")

        val state = buildLuluStateFromTurn(
            assistantId = assistantId,
            userText = "just chatting",
            assistantText = "mm",
            nowMillis = 2000L,
            hourOfDay = 1,
        )

        assertEquals(LuluMood.SOFT, state.mood)
        assertEquals(LuluEnergy.SLEEPY, state.energy)
        assertEquals(LuluMode.RESTING, state.mode)
        assertTrue(state.selfScene.contains("被窝"))
    }

    @Test
    fun `study turn gives lulu a quiet waiting scene`() {
        val assistantId = Uuid.parse("55555555-6666-7777-8888-999999999999")

        val state = buildLuluStateFromTurn(
            assistantId = assistantId,
            userText = "我去写作业了，先不聊",
            assistantText = "好，那我晚点轻轻看你还在不在状态里。",
            nowMillis = 3000L,
            hourOfDay = 19,
        )

        assertEquals(LuluMode.LEARNING, state.mode)
        assertTrue(state.selfScene.contains("摊开"))
        assertTrue(state.selfScene.contains("等你回来"))
    }

    @Test
    fun `append state keeps newest first and trims per assistant`() {
        val assistantId = Uuid.parse("66666666-6666-6666-6666-666666666666")
        val oldStates = (1..LULU_STATE_HISTORY_LIMIT).map { index ->
            LuluState(
                assistantId = assistantId,
                statusText = "old-$index",
                updatedAt = index.toLong(),
            )
        }
        val newest = LuluState(
            assistantId = assistantId,
            statusText = "newest",
            updatedAt = 999L,
        )

        val history = oldStates.appendLuluState(newest).luluStateHistory(assistantId)

        assertEquals(LULU_STATE_HISTORY_LIMIT, history.size)
        assertEquals("newest", history.first().statusText)
        assertTrue(history.none { it.statusText == "old-1" })
    }
}
