package me.rerere.tts.provider

import me.rerere.common.http.SseEvent
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.provider.providers.MiniMaxSseProcessor
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

    @Test
    fun minimax_sse_processor_decodes_hex_audio_chunks() {
        val processor = MiniMaxSseProcessor(
            model = TTSProviderSetting.MiniMax.DEFAULT_MODEL,
            voiceId = "female-shaonv"
        )

        val chunk = processor.process(
            SseEvent.Event(
                id = null,
                type = null,
                data = """
                    {
                      "data": {
                        "audio": "000102ff",
                        "status": 2,
                        "ced": "done"
                      },
                      "trace_id": "trace-1",
                      "base_resp": {
                        "status_code": 0,
                        "status_msg": ""
                      }
                    }
                """.trimIndent()
            )
        )
        val terminal = processor.process(SseEvent.Closed)

        assertEquals(AudioFormat.MP3, chunk?.format)
        assertEquals(32000, chunk?.sampleRate)
        assertTrue(byteArrayOf(0, 1, 2, -1).contentEquals(chunk?.data))
        assertEquals(false, chunk?.isLast)
        assertEquals(true, terminal?.isLast)
    }
}
