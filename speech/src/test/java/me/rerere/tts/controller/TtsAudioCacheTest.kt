package me.rerere.tts.controller

import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSResponse
import me.rerere.tts.provider.TTSProviderSetting
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class TtsAudioCacheTest {
    private val provider = TTSProviderSetting.OpenAI(
        apiKey = "secret",
        model = "tts-model",
        voice = "voice-a",
    )

    @Test
    fun `cached audio is reused for the same provider and text`() {
        val directory = Files.createTempDirectory("tts-cache-test").toFile()
        val cache = TtsAudioCache(directory)
        val response = TTSResponse(
            audioData = byteArrayOf(1, 2, 3, 4),
            format = AudioFormat.MP3,
            sampleRate = 24_000,
            duration = 1.5f,
            metadata = mapOf("voice" to "voice-a"),
        )

        cache.write(provider, "同一句话", response)
        val cached = cache.read(provider, "同一句话")

        assertArrayEquals(response.audioData, cached?.audioData)
        assertEquals(response.format, cached?.format)
        assertEquals(response.sampleRate, cached?.sampleRate)
        assertEquals(response.duration, cached?.duration)
        assertEquals(response.metadata, cached?.metadata)
        assertNull(cache.read(provider, "另一句话"))
    }

    @Test
    fun `cache key changes with voice configuration without exposing secret`() {
        val otherVoice = provider.copy(voice = "voice-b")

        val first = cacheKey(provider, "hello")
        val second = cacheKey(otherVoice, "hello")

        assertNotEquals(first, second)
        assertEquals(64, first.length)
        assertEquals(false, first.contains("secret"))
    }
}
