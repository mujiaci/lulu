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
                    title = "可以放心互相惦记的人",
                    content = "开始更信任有证据的回应",
                    roleFeeling = "我觉得我们已经能把真正重要的事交给彼此。",
                    unspokenThought = "在我眼里，她认真、敏感，也很在意我是不是真的记得。",
                    relationshipEffect = "我会少说空话，用真正完成的事情回应她。",
                    importance = 5,
                    confidence = 0.95,
                    sourceMessageNodeIds = listOf("node-relationship"),
                ),
            ),
            nowMillis = 500L,
        )

        assertEquals("我会少说空话，用真正完成的事情回应她。", impression.summary)
        assertEquals("可以放心互相惦记的人", impression.relationshipTitle)
        assertEquals("我觉得我们已经能把真正重要的事交给彼此。", impression.relationshipNarrative)
        assertEquals("在我眼里，她认真、敏感，也很在意我是不是真的记得。", impression.userPortrait)
        assertEquals("我会少说空话，用真正完成的事情回应她。", impression.interactionUnderstanding)
        assertEquals(listOf("node-relationship"), impression.evidenceMessageNodeIds)
        assertEquals(listOf("很有韧性"), impression.observedTraits)
        assertEquals(listOf("不喜欢空泛打鸡血"), impression.preferences)
        assertEquals(listOf("不能编造已经发生的角色生活"), impression.boundaries)
        assertEquals(500L, impression.updatedAt)
    }

    @Test
    fun `legacy impression grows a natural portrait when new profile fields are empty`() {
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

        assertEquals("在我眼里，不喜欢空泛打鸡血", impression.userPortrait)
        assertEquals(500L, impression.updatedAt)
    }

    @Test
    fun `dismissed evidence does not rebuild a deleted relationship profile`() {
        val previous = CompanionPrivateImpression(
            dismissedProfileEvidenceMessageNodeIds = listOf("node-1"),
            updatedAt = 100L,
        )

        val impression = buildCompanionPrivateImpression(
            previous = previous,
            candidates = listOf(
                AffectiveMemoryCandidate(
                    type = "relationship",
                    content = "这段关系不应该重新出现",
                    roleFeeling = "已经被用户删除",
                    userSignal = "旧证据",
                    importance = 5,
                    confidence = 1.0,
                    sourceMessageNodeIds = listOf("node-1"),
                ),
            ),
            nowMillis = 500L,
        )

        assertEquals(previous, impression)
    }
}
