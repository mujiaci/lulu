package me.rerere.rikkahub.data.companion

import me.rerere.rikkahub.data.model.LuluEnergy
import me.rerere.rikkahub.data.model.LuluMood
import me.rerere.rikkahub.data.model.LuluRelationship
import me.rerere.rikkahub.data.model.LuluState
import me.rerere.rikkahub.service.RollingJudgmentLoop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.uuid.Uuid

class CompanionLegacyAdapterTest {
    @Test
    fun `legacy state maps without introducing a fixed character name`() {
        val legacy = legacyState()

        val state = legacy.toCompanionState()

        assertEquals("在整理日程", state.statusText)
        assertEquals("平静", state.mood)
        assertEquals("刚刚好", state.bodyState)
        assertFalse(state.selfScene.contains("露露"))
    }

    @Test
    fun `legacy relationship maps to approximate numeric state`() {
        val relationship = legacyState().copy(
            relationship = LuluRelationship.CLOSE,
            relationshipIntensity = 0.76f,
        ).toCompanionRelationshipState()

        assertEquals("很亲近", relationship.roleLabel)
        assertEquals(0.76f, relationship.closeness)
        assertEquals(0.76f, relationship.familiarity)
    }

    @Test
    fun `legacy intent maps to assistant scoped concern`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantId = ASSISTANT_ID,
            assistantName = "任意角色",
            userText = "明天八点叫我起床",
            assistantText = "好。",
            nowMillis = 100L,
            targetAtMillis = 1_000L,
        )

        val concern = intent.toCompanionConcern()

        assertEquals(ASSISTANT_ID, concern.assistantId)
        assertEquals("wake:1000", concern.subjectKey)
        assertEquals(listOf(intent.id), concern.sourceMessageIds)
    }

    @Test
    fun `importing same legacy data twice is idempotent`() {
        val legacy = legacyState()
        val intent = RollingJudgmentLoop.createIntent(
            assistantId = ASSISTANT_ID,
            assistantName = "任意角色",
            userText = "我去学习了",
            assistantText = "好。",
            nowMillis = 100L,
        )

        val first = importLegacyCompanionSnapshot(
            assistantId = ASSISTANT_ID,
            current = CompanionSnapshot.empty(ASSISTANT_ID),
            legacyStates = listOf(legacy),
            legacyIntents = listOf(intent),
        )
        val second = importLegacyCompanionSnapshot(
            assistantId = ASSISTANT_ID,
            current = first,
            legacyStates = listOf(legacy),
            legacyIntents = listOf(intent),
        )

        assertEquals(first, second)
        assertEquals(1, second.concerns.size)
    }

    private fun legacyState(): LuluState = LuluState(
        assistantId = Uuid.parse(ASSISTANT_ID),
        statusText = "在整理日程",
        innerVoice = "我记着今天的安排。",
        mood = LuluMood.CALM,
        energy = LuluEnergy.NORMAL,
        relationship = LuluRelationship.FAMILIAR,
        relationshipIntensity = 0.45f,
        selfScene = "角色正在桌边整理今天的日程。",
        updatedAt = 200L,
        sinceAt = 150L,
    )

    private companion object {
        const val ASSISTANT_ID = "11111111-1111-1111-1111-111111111111"
    }
}
