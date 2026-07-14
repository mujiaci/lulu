package me.rerere.rikkahub.data.ai.transformers

import me.rerere.rikkahub.data.companion.CompanionLifeEvent
import me.rerere.rikkahub.data.companion.CompanionLifeEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class CompanionLifeClaimOutputTransformerTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `unsupported completed game claim is corrected`() {
        val result = sanitizeUnsupportedDigitalLifeClaims(
            text = "我今天玩了一个游戏，还挺有意思的。",
            lifeEvents = emptyList(),
            nowMillis = 1_720_000_000_000L,
            zoneId = zone,
        )

        assertTrue("真实发生" in result.text)
        assertEquals(setOf(CompanionLifeEventType.GAME), result.unsupportedTypes)
    }

    @Test
    fun `completed game event supports a same day claim`() {
        val now = 1_720_000_000_000L
        val result = sanitizeUnsupportedDigitalLifeClaims(
            text = "我今天玩了一局游戏，还挺有意思的。",
            lifeEvents = listOf(
                CompanionLifeEvent(
                    assistantId = "assistant-a",
                    type = CompanionLifeEventType.GAME,
                    title = "完成了一局记忆配对",
                    startedAt = now - 1_000L,
                    endedAt = now - 1_000L,
                    createdAt = now - 1_000L,
                ),
            ),
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals("我今天玩了一局游戏，还挺有意思的。", result.text)
        assertTrue(result.unsupportedTypes.isEmpty())
    }

    @Test
    fun `plans and negated statements are not treated as completed claims`() {
        val planned = sanitizeUnsupportedDigitalLifeClaims(
            text = "我想玩一个游戏，但现在还没玩。",
            lifeEvents = emptyList(),
            nowMillis = 1_720_000_000_000L,
            zoneId = zone,
        )

        assertEquals("我想玩一个游戏，但现在还没玩。", planned.text)
        assertTrue(planned.unsupportedTypes.isEmpty())
    }
}
