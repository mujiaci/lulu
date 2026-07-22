package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionPerceptionLegacyStateTest {
    @Test
    fun `legacy neurotransmitter values never enter role prompts`() {
        val packet = CompanionPerceptionAssembler.assemble(
            input = CompanionPerceptionInput(
                assistantId = "assistant-a",
                assistantName = "角色",
                persona = "冷淡但可靠",
                nowMillis = 1_000L,
            ),
            snapshot = CompanionSnapshot(
                assistantId = "assistant-a",
                relationship = CompanionRelationshipState(roleLabel = "恋人"),
                neuroState = CompanionNeuroState(
                    dopamine = 0.99f,
                    serotonin = 0.01f,
                    cortisol = 0.88f,
                    oxytocin = 0.77f,
                    norepinephrine = 0.66f,
                    energy = 0.55f,
                ),
            ),
        )

        val prompt = packet.toPromptContext()

        assertFalse(prompt.contains("digital_neuro"))
        assertFalse(prompt.contains("dopamine"))
        assertFalse(prompt.contains("oxytocin"))
        assertTrue(prompt.contains("relationship"))
    }
}
