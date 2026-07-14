package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.companion.CompanionPrivateImpression
import org.junit.Assert.assertEquals
import org.junit.Test

class AffectivePrivateImpressionTest {
    @Test
    fun `durable memory candidates become private impression dimensions`() {
        val impression = buildCompanionPrivateImpression(
            previous = CompanionPrivateImpression(),
            candidates = listOf(
                AffectiveMemoryCandidate(
                    type = "user_fact",
                    content = "用户在压力大时仍会坚持完成最低任务",
                    userSignal = "很有韧性",
                    importance = 4,
                    confidence = 0.9,
                ),
                AffectiveMemoryCandidate(
                    type = "user_preference",
                    content = "不喜欢空泛打鸡血",
                    importance = 4,
                    confidence = 0.9,
                ),
                AffectiveMemoryCandidate(
                    type = "user_boundary",
                    content = "不能编造已经发生的角色生活",
                    importance = 5,
                    confidence = 1.0,
                ),
                AffectiveMemoryCandidate(
                    type = "relationship",
                    content = "开始更信任有证据的回应",
                    relationshipEffect = "信任来自说到做到和真实证据",
                    importance = 5,
                    confidence = 0.95,
                ),
            ),
            nowMillis = 500L,
        )

        assertEquals("信任来自说到做到和真实证据", impression.summary)
        assertEquals(listOf("很有韧性"), impression.observedTraits)
        assertEquals(listOf("不喜欢空泛打鸡血"), impression.preferences)
        assertEquals(listOf("不能编造已经发生的角色生活"), impression.boundaries)
        assertEquals(500L, impression.updatedAt)
    }

    @Test
    fun `unchanged impression candidates do not rewrite the timestamp`() {
        val previous = CompanionPrivateImpression(
            preferences = listOf("不喜欢空泛打鸡血"),
            updatedAt = 100L,
        )

        val impression = buildCompanionPrivateImpression(
            previous = previous,
            candidates = listOf(
                AffectiveMemoryCandidate(
                    type = "user_preference",
                    content = "不喜欢空泛打鸡血",
                    importance = 4,
                    confidence = 0.9,
                ),
            ),
            nowMillis = 500L,
        )

        assertEquals(previous, impression)
    }
}
