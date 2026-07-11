package me.rerere.rikkahub.data.ai.transformers

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionTransformerPipelineTest {
    @Test
    fun `shared companion input pipeline includes study worldbook and voice context`() {
        assertEquals(
            listOf(
                TimeReminderTransformer,
                PromptInjectionTransformer,
                CompanionPresenceContractTransformer,
                StudyStateTransformer,
                PlaceholderTransformer,
                DocumentAsPromptTransformer,
                OcrTransformer,
                VoiceMessageTransformer,
            ),
            companionInputTransformers,
        )
    }

    @Test
    fun `shared companion output pipeline keeps expression and cleanup stages`() {
        assertEquals(
            listOf(
                ThinkTagTransformer,
                Base64ImageToLocalFileTransformer,
                RegexOutputTransformer,
                LuluExpressionOutputTransformer,
            ),
            companionOutputTransformers,
        )
    }
}
