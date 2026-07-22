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

        assertEquals(existing, result)
    }
}
