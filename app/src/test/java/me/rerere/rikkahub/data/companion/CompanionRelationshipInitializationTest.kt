package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionRelationshipInitializationTest {
    @Test
    fun `declared lover relationship does not start from zero`() {
        val result = initializeCompanionRelationshipFromCharacterCard(
            current = CompanionRelationshipState(),
            characterCard = "关系：恋人。性格冷淡、克制，不使用亲昵称呼。",
            nowMillis = 100L,
        )

        assertEquals("恋人", result.roleLabel)
        assertTrue(result.closeness >= 0.70f)
        assertTrue(result.trust >= 0.65f)
        assertEquals(100L, result.updatedAt)
    }

    @Test
    fun `enemy declaration keeps an adversarial baseline`() {
        val result = initializeCompanionRelationshipFromCharacterCard(
            current = CompanionRelationshipState(),
            characterCard = "身份：宿敌；说话直接，不会安慰用户。",
            nowMillis = 100L,
        )

        assertEquals("敌对关系", result.roleLabel)
        assertTrue(result.trust <= 0.50f)
        assertTrue(result.unresolvedTension >= 0.65f)
    }

    @Test
    fun `ordinary friend stays distinct from intimate and hostile roles`() {
        val result = initializeCompanionRelationshipFromCharacterCard(
            current = CompanionRelationshipState(),
            characterCard = "关系：朋友。说话平常，尊重距离。",
            nowMillis = 100L,
        )

        assertEquals("朋友", result.roleLabel)
        assertTrue(result.closeness in 0.30f..0.50f)
        assertTrue(result.unresolvedTension < 0.20f)
    }

    @Test
    fun `nonhuman identity does not receive an invented intimate relationship`() {
        val original = CompanionRelationshipState()
        val result = initializeCompanionRelationshipFromCharacterCard(
            current = original,
            characterCard = "身份：非人观测体。表达方式冷静，不模拟恋爱或人类依恋。",
            nowMillis = 100L,
        )

        assertEquals(original, result)
    }

    @Test
    fun `negated relationship never creates intimacy`() {
        val original = CompanionRelationshipState()

        val result = initializeCompanionRelationshipFromCharacterCard(
            current = original,
            characterCard = "两人不是恋人，只是刚认识的陌生人。",
            nowMillis = 100L,
        )

        assertEquals(original, result)
    }

    @Test
    fun `complete character card initializes structured relationship facts once`() {
        val result = initializeCompanionRelationshipFromCharacterCard(
            current = CompanionRelationshipState(),
            characterCard = """
                关系：恋人
                认识时长：三年
                共同经历：一起准备考试、共同旅行
                关系阶段：稳定交往
                安全感：遇到冲突仍会说明原因
                依恋表达：克制但会用行动照顾
                互动习惯：每天互道晚安、重要决定先商量
                边界：不使用羞辱性称呼
                潜在矛盾：忙碌时容易误解彼此
            """.trimIndent(),
            nowMillis = 100L,
        )
        assertEquals("三年", result.knownDuration)
        assertEquals(listOf("一起准备考试", "共同旅行"), result.sharedExperiences)
        assertEquals("稳定交往", result.stage)
        assertEquals(listOf("不使用羞辱性称呼"), result.declaredBoundaries)
        assertEquals(listOf("character_card"), result.lastEvidenceIds)
    }

    @Test
    fun `real interaction state always wins over card initialization`() {
        val existing = CompanionRelationshipState(
            roleLabel = "关系冷却中",
            trust = 0.20f,
            closeness = 0.10f,
            unresolvedTension = 0.80f,
            updatedAt = 90L,
        )

        val result = initializeCompanionRelationshipFromCharacterCard(
            current = existing,
            characterCard = "关系：恋人",
            nowMillis = 100L,
        )

        assertEquals(existing.roleLabel, result.roleLabel)
        assertEquals(existing.trust, result.trust)
        assertEquals(existing.closeness, result.closeness)
        assertEquals(existing.unresolvedTension, result.unresolvedTension)
        assertEquals("character_card", result.initializationEvidence)
    }
}
