package me.rerere.ai.provider.providers

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAIImageTransportTest {
    @Test
    fun `dall e image requests prefer base64 response to avoid extra image url download`() {
        val body = buildJsonObject {
            put("model", kotlinx.serialization.json.JsonPrimitive("dall-e-3"))
        }.withImageTransportDefaults("dall-e-3")

        assertEquals("b64_json", body["response_format"]?.jsonPrimitive?.content)
    }

    @Test
    fun `custom response format is preserved`() {
        val body = buildJsonObject {
            put("model", kotlinx.serialization.json.JsonPrimitive("dall-e-3"))
            put("response_format", kotlinx.serialization.json.JsonPrimitive("url"))
        }.withImageTransportDefaults("dall-e-3")

        assertEquals("url", body["response_format"]?.jsonPrimitive?.content)
    }

    @Test
    fun `gpt image requests do not add unsupported response format`() {
        val body = buildJsonObject {
            put("model", kotlinx.serialization.json.JsonPrimitive("gpt-image-1"))
        }.withImageTransportDefaults("gpt-image-1")

        assertNull(body["response_format"])
    }
}
