package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionRelationshipBehaviorTest {
    @Test
    fun `baseline relationship contract does not prescribe warmth`() {
        val context = CompanionRelationshipState().toBehaviorContract()

        assertTrue(context.contains("persona_consistent_baseline"))
        assertFalse(context.contains("be warm"))
        assertFalse(context.contains("warm_familiarity"))
    }

    @Test
    fun `high closeness preserves persona instead of assuming intimacy`() {
        val context = CompanionRelationshipState(closeness = 0.9f).toBehaviorContract()

        assertTrue(context.contains("persona_consistent_familiarity"))
        assertTrue(context.contains("without assuming entitlement, warmth, intimacy, or consent"))
    }
}
