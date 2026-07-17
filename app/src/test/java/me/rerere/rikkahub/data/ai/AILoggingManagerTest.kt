package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AILoggingManagerTest {
    @Test
    fun `updates token usage on the same generation log`() {
        val manager = AILoggingManager()
        val log = AILogging.Generation(
            params = TextGenerationParams(model = Model(modelId = "test-model")),
            messages = emptyList(),
            providerSetting = ProviderSetting.OpenAI(name = "Test Provider"),
            stream = true,
        )

        manager.addLog(log)
        manager.updateGenerationUsage(
            id = log.id,
            usage = TokenUsage(promptTokens = 12, completionTokens = 5, cachedTokens = 3),
        )
        manager.finishGeneration(id = log.id)

        val updated = manager.getLogs().value.single() as AILogging.Generation
        assertEquals(log.id, updated.id)
        assertEquals(12, updated.usage?.promptTokens)
        assertEquals(5, updated.usage?.completionTokens)
        assertEquals(3, updated.usage?.cachedTokens)
        assertEquals(17, updated.usage?.totalTokens)
        assertNull(updated.error)
    }
}
