package me.rerere.rikkahub.data.companion

import me.rerere.rikkahub.data.model.LuluMode
import me.rerere.rikkahub.data.model.LuluMood
import me.rerere.rikkahub.data.model.LuluRelationship
import me.rerere.rikkahub.data.model.LuluState
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

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
    fun `missing model fields preserve runtime continuity`() {
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
        assertEquals("正在核对安排。", next.selfScene)
        assertEquals(50L, next.sinceAt)
    }

    @Test
    fun `legacy ui projection preserves legacy enum fields`() {
        val assistantId = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val previous = LuluState(
            assistantId = assistantId,
            mood = LuluMood.CALM,
            mode = LuluMode.RESTING,
            relationship = LuluRelationship.FAMILIAR,
        )
        val state = CompanionState(
            statusText = "刚刚聊完",
            innerThought = "这件事要继续记着。",
            selfScene = "把刚才的重点整理好了。",
            updatedAt = 2_000L,
            sinceAt = 1_500L,
        )

        val projected = state.toLegacyLuluState(assistantId, previous)

        assertEquals("刚刚聊完", projected.statusText)
        assertEquals("这件事要继续记着。", projected.innerVoice)
        assertEquals("把刚才的重点整理好了。", projected.selfScene)
        assertEquals(LuluMood.CALM, projected.mood)
        assertEquals(LuluMode.RESTING, projected.mode)
        assertEquals(LuluRelationship.FAMILIAR, projected.relationship)
    }
}
