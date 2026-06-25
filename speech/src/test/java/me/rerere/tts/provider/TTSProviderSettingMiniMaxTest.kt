package me.rerere.tts.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TTSProviderSettingMiniMaxTest {
    @Test
    fun minimax_defaults_use_supported_model_and_require_group_id() {
        val setting = TTSProviderSetting.MiniMax()

        assertEquals("MiniMax TTS", setting.name)
        assertEquals("https://api.minimaxi.com/v1", setting.baseUrl)
        assertEquals("", setting.apiKey)
        assertEquals("", setting.groupId)
        assertEquals(TTSProviderSetting.MiniMax.DEFAULT_MODEL, setting.model)
        assertTrue(TTSProviderSetting.MiniMax.SUPPORTED_MODELS.contains(setting.model))
    }

    @Test
    fun minimax_normalizes_old_unknown_default_model() {
        assertEquals(
            TTSProviderSetting.MiniMax.DEFAULT_MODEL,
            TTSProviderSetting.MiniMax.normalizeModel("speech-2.6-turbo")
        )
    }

    @Test
    fun minimax_is_registered_in_provider_types() {
        assertTrue(TTSProviderSetting.Types.contains(TTSProviderSetting.MiniMax::class))
    }
}
