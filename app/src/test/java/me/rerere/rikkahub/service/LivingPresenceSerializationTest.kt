package me.rerere.rikkahub.service

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.living.LivingPresenceState
import org.junit.Assert.assertEquals
import org.junit.Test

class LivingPresenceSerializationTest {
    @Test
    fun `living presence state serializes active intents`() {
        val json = Json { ignoreUnknownKeys = true }
        val intent = RollingJudgmentLoop.createIntent(
            assistantId = "assistant-1",
            assistantName = "露露",
            userText = "我先忙一下",
            assistantText = "好，我在这里等你。",
            nowMillis = 1_700_000_000_000L,
        )

        val encoded = json.encodeToString(LivingPresenceState(activeIntents = listOf(intent)))
        val decoded = json.decodeFromString<LivingPresenceState>(encoded)

        assertEquals(1, decoded.activeIntents.size)
        assertEquals("assistant-1", decoded.activeIntents.single().assistantId)
        assertEquals(intent.kind, decoded.activeIntents.single().kind)
        assertEquals(intent.evaluationCadence.delaysMinutes, decoded.activeIntents.single().evaluationCadence.delaysMinutes)
    }
}
