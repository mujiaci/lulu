package me.rerere.rikkahub.ui.pages.voicecall

import me.rerere.asr.ASRStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCallStateTest {
    @Test
    fun `voice call does not listen while assistant opening turn is pending`() {
        assertFalse(
            shouldStartVoiceCallListening(
                stageActive = true,
                isHistoryOnly = false,
                sleepMode = false,
                assistantTurnInProgress = true,
                isSpeaking = false,
                asrStatus = ASRStatus.Idle,
            )
        )
    }

    @Test
    fun `voice call can listen after assistant speech has finished`() {
        assertTrue(
            shouldStartVoiceCallListening(
                stageActive = true,
                isHistoryOnly = false,
                sleepMode = false,
                assistantTurnInProgress = false,
                isSpeaking = false,
                asrStatus = ASRStatus.Idle,
            )
        )
    }
    @Test
    fun `voice call rejects internal fallback as a spoken reply`() {
        assertFalse(isUsableVoiceCallReply("（本轮回复生成不完整，请重试）"))
        assertTrue(isUsableVoiceCallReply("嗯，我听着。"))
    }

    @Test
    fun `end of speech delay adapts to punctuation and short utterances`() {
        assertEquals(850L, voiceCallEndOfSpeechDelayMillis("你在吗？"))
        assertEquals(1_450L, voiceCallEndOfSpeechDelayMillis("喂"))
        assertEquals(1_000L, voiceCallEndOfSpeechDelayMillis("我今天想和你认真聊一件事情"))
    }

    @Test
    fun `opening prompt preserves persona and avoids recent wording`() {
        val prompt = buildVoiceCallOpeningPrompt(
            assistantName = "冷面侦探",
            recentOpenings = listOf("喂，我在。"),
            variationSeed = 7L,
        )

        assertTrue(prompt.contains("最高优先级"))
        assertTrue(prompt.contains("关系类型"))
        assertTrue(prompt.contains("喂，我在。"))
        assertTrue(prompt.contains("避免相同句式"))
    }
}
