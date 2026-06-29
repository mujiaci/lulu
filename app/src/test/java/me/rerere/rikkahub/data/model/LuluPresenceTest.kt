package me.rerere.rikkahub.data.model

import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuluPresenceTest {
    private val assistantId = Uuid.parse("11111111-1111-1111-1111-111111111111")

    @Test
    fun `state update keeps inertia instead of jumping to every signal`() {
        val previous = LuluState(
            assistantId = assistantId,
            mood = LuluMood.CALM,
            energy = LuluEnergy.NORMAL,
            relationship = LuluRelationship.CLOSE,
            updatedAt = 1_000L,
        )

        val next = buildLuluStateFromTurn(
            assistantId = assistantId,
            previous = previous,
            userText = "我今天真的累到崩溃",
            assistantText = "我陪着你。",
            nowMillis = 2_000L,
            hourOfDay = 15,
        )

        assertEquals(LuluMood.SOFT, next.mood)
        assertEquals(LuluEnergy.NORMAL, next.energy)
        assertEquals(LuluRelationship.CLOSE, next.relationship)
        assertTrue(next.reason.contains("状态惯性"))
    }

    @Test
    fun `thoughts expire and keep only strongest active items`() {
        val old = LuluThought(
            assistantId = assistantId,
            content = "旧想法",
            importance = 2,
            createdAt = 0L,
            expiresAt = 100L,
        )
        val current = (1..5).map { index ->
            LuluThought(
                assistantId = assistantId,
                content = "想法$index",
                importance = index,
                createdAt = index.toLong(),
                expiresAt = 10_000L,
            )
        }

        val normalized = (current + old).normalizedLuluThoughts(
            validAssistantIds = setOf(assistantId),
            nowMillis = 1_000L,
        )

        assertEquals(listOf("想法5", "想法4", "想法3"), normalized.map { it.content })
        assertFalse(normalized.any { it.content == "旧想法" })
    }

    @Test
    fun `concerns survive expiry while normal thoughts expire`() {
        val oldConcern = LuluThought(
            assistantId = assistantId,
            content = "他最近总是很累，我有点放不下。",
            category = LuluThoughtCategory.CONCERN,
            importance = 5,
            createdAt = 0L,
            expiresAt = 100L,
        )
        val oldNormalThought = LuluThought(
            assistantId = assistantId,
            content = "普通旧想法",
            category = LuluThoughtCategory.SHORT_TERM,
            importance = 3,
            createdAt = 0L,
            expiresAt = 100L,
        )

        val normalized = listOf(oldConcern, oldNormalThought).normalizedLuluThoughts(
            validAssistantIds = setOf(assistantId),
            nowMillis = 1_000L,
        )

        assertEquals(listOf("他最近总是很累，我有点放不下。"), normalized.map { it.content })
    }

    @Test
    fun `pending action is generated for study promise`() {
        val thought = buildLuluThoughtFromTurn(
            assistantId = assistantId,
            userText = "我去学习一会儿，等下回来",
            state = LuluState(assistantId = assistantId),
            nowMillis = 1_000L,
        )

        assertEquals(LuluThoughtCategory.PENDING_ACTION, thought?.category)
        assertTrue(thought?.content.orEmpty().contains("等他回来"))
    }

    @Test
    fun `perception tags late night and tired user text`() {
        val perception = buildLuluPerception(
            userText = "我好累，想睡觉",
            hourOfDay = 1,
        )

        assertEquals(LuluTimeLabel.LATE_NIGHT, perception.timeLabel)
        assertTrue(perception.userSignals.contains(LuluUserSignal.TIRED))
        assertEquals(LuluSceneLabel.RESTING, perception.sceneLabel)
    }

    @Test
    fun `expression plan becomes shorter and slower when sleepy`() {
        val plan = buildLuluExpressionPlan(
            state = LuluState(
                assistantId = assistantId,
                energy = LuluEnergy.SLEEPY,
                mood = LuluMood.SOFT,
            ),
            reply = "我在呢。别急，先慢慢呼吸一下，然后把今天最难受的地方告诉我。",
        )

        assertEquals(LuluExpressionLength.SHORT, plan.length)
        assertTrue(plan.typingDelayMillis >= 1_200L)
        assertTrue(plan.guidance.contains("短句"))
    }

    @Test
    fun `state tracks intensity and duration`() {
        val previous = LuluState(
            assistantId = assistantId,
            mood = LuluMood.WORRIED,
            moodIntensity = 0.5f,
            updatedAt = 1_000L,
            sinceAt = 1_000L,
        )

        val next = buildLuluStateFromTurn(
            assistantId = assistantId,
            previous = previous,
            userText = "我还是有点累",
            assistantText = "我在。",
            nowMillis = 31_000L,
            hourOfDay = 22,
        )

        assertTrue(next.moodIntensity > previous.moodIntensity)
        assertEquals(1_000L, next.sinceAt)
        assertTrue(next.durationMillis(31_000L) >= 30_000L)
    }
}
