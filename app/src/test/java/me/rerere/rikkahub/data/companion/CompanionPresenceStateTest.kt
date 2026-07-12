package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionPresenceStateTest {
    @Test
    fun `model presence updates the unified state directly`() {
        val previous = CompanionState(
            statusText = "安静等着",
            innerThought = "先不催。",
            mood = "平静",
            bodyState = "无明确具身信息",
            mindState = "留意中",
            activityMode = "waiting",
            selfScene = "保持安静。",
            updatedAt = 100L,
            sinceAt = 80L,
        )

        val next = buildCompanionStateFromTurn(
            previous = previous,
            assistantText = "我在，慢慢说。",
            presence = CompanionModelPresence(
                statusText = "认真听你说",
                description = "把注意力收回来，等你继续。",
                innerThought = "这次先听完，不急着给建议。",
                mood = "关切但克制",
                bodyState = "没有可确认的身体状态",
                mindState = "专注倾听",
                activityMode = "conversation",
            ),
            nowMillis = 1_000L,
        )

        assertEquals("认真听你说", next.statusText)
        assertEquals("这次先听完，不急着给建议。", next.innerThought)
        assertEquals("关切但克制", next.mood)
        assertEquals("没有可确认的身体状态", next.bodyState)
        assertEquals("专注倾听", next.mindState)
        assertEquals("conversation", next.activityMode)
        assertEquals("把注意力收回来，等你继续。", next.selfScene)
        assertEquals(1_000L, next.updatedAt)
        assertEquals(1_000L, next.sinceAt)
    }

    @Test
    fun `missing model description refreshes scene while preserving supplied runtime fields`() {
        val previous = CompanionState(
            statusText = "整理日程中",
            innerThought = "记着下午的安排。",
            mood = "稳定",
            bodyState = "",
            mindState = "专注",
            activityMode = "planning",
            selfScene = "正在核对安排。",
            updatedAt = 100L,
            sinceAt = 50L,
        )

        val next = buildCompanionStateFromTurn(
            previous = previous,
            assistantText = "下午三点的安排我记着。",
            presence = CompanionModelPresence(innerThought = "到点前再确认一次。"),
            nowMillis = 1_000L,
        )

        assertEquals("整理日程中", next.statusText)
        assertEquals("到点前再确认一次。", next.innerThought)
        assertEquals("稳定", next.mood)
        assertEquals("planning", next.activityMode)
        assertEquals("刚刚和你聊到“下午三点的安排我记着。”，注意力还停在这段对话上。", next.selfScene)
        assertEquals(1_000L, next.updatedAt)
        assertEquals(1_000L, next.sinceAt)
    }

    @Test
    fun `planner inner thought replaces stale thought when presence block is missing`() {
        val next = buildCompanionStateFromTurn(
            previous = CompanionState(innerThought = "昨天的旧心声", updatedAt = 100L),
            assistantText = "今天已经重新回应。",
            presence = null,
            nowMillis = 1_000L,
            fallbackInnerThought = "我刚刚重新想过这一轮该怎样回应。",
        )

        assertEquals("我刚刚重新想过这一轮该怎样回应。", next.innerThought)
        assertEquals("刚刚和你聊到“今天已经重新回应。”，注意力还停在这段对话上。", next.selfScene)
        assertEquals(1_000L, next.updatedAt)
        assertEquals(1_000L, next.sinceAt)
    }

    @Test
    fun `each visible reply refreshes fallback scene thought and timestamps`() {
        val first = buildCompanionStateFromTurn(
            previous = CompanionState(
                innerThought = "昨天留下的旧心声。",
                selfScene = "昨天留下的旧此刻。",
                updatedAt = 100L,
                sinceAt = 80L,
            ),
            assistantText = "学习计划我已经重新排好了。",
            presence = null,
            nowMillis = 1_000L,
        )
        val second = buildCompanionStateFromTurn(
            previous = first,
            assistantText = "今晚早点休息，我会记着。",
            presence = null,
            nowMillis = 2_000L,
        )

        assertEquals(
            "刚才聊到“学习计划我已经重新排好了。”，我还在想怎样把下一步变得更容易开始。",
            first.innerThought,
        )
        assertEquals("刚刚和你聊到“学习计划我已经重新排好了。”，注意力还停在这段对话上。", first.selfScene)
        assertEquals(1_000L, first.updatedAt)
        assertEquals(1_000L, first.sinceAt)

        assertEquals(
            "刚才聊到“今晚早点休息，我会记着。”，我在留意你的状态，别让这段对话把该休息的时间也占掉。",
            second.innerThought,
        )
        assertEquals("刚刚和你聊到“今晚早点休息，我会记着。”，注意力还停在这段对话上。", second.selfScene)
        assertEquals(2_000L, second.updatedAt)
        assertEquals(2_000L, second.sinceAt)
    }

    @Test
    fun `missing model fields do not preserve technical runtime narration`() {
        val previous = CompanionState(
            statusText = "副 API 判断中",
            innerThought = "本地规划兜底正在决定是否发消息",
            mindState = "副 API 判断",
            selfScene = "露露刚刚做了一次后台判断，状态栏留下了技术结果。",
            updatedAt = 100L,
            sinceAt = 50L,
        )

        val next = buildCompanionStateFromTurn(
            previous = previous,
            assistantText = "我在。",
            presence = null,
            nowMillis = 1_000L,
        )

        assertEquals("安静留意", next.statusText)
        assertEquals("刚才聊到“我在。”，我刚刚把话说出来，也在等你接住下一句。", next.innerThought)
        assertEquals("安静留意着现在的变化", next.mindState)
        assertEquals("刚刚和你聊到“我在。”，注意力还停在这段对话上。", next.selfScene)
        assertEquals(1_000L, next.updatedAt)
        assertEquals(1_000L, next.sinceAt)
        val visibleState = listOf(next.statusText, next.innerThought, next.mindState, next.selfScene).joinToString()
        assertFalse(visibleState.contains("副 API", ignoreCase = true))
        assertFalse(visibleState.contains("后台", ignoreCase = true))
        assertFalse(visibleState.contains("本地规划", ignoreCase = true))
    }

    @Test
    fun `technical model presence falls back to the visible reply`() {
        val next = buildCompanionStateFromTurn(
            previous = CompanionState(updatedAt = 100L, sinceAt = 50L),
            assistantText = "今晚早点休息，我会记着。",
            presence = CompanionModelPresence(
                description = "后台判断：等待副 API 返回",
                innerThought = "副 API 判断中",
            ),
            fallbackInnerThought = "我已经回应了这一轮对话，接下来先留意你会怎样继续。",
            nowMillis = 1_000L,
        )

        assertTrue(next.innerThought.startsWith("刚才聊到“今晚早点休息，我会记着。”"))
        assertEquals("刚刚和你聊到“今晚早点休息，我会记着。”，注意力还停在这段对话上。", next.selfScene)
        assertFalse(next.innerThought.contains("副 API", ignoreCase = true))
        assertFalse(next.selfScene.contains("后台", ignoreCase = true))
    }
}
