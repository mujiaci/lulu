package me.rerere.tts.provider.providers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.common.http.SseEvent
import me.rerere.common.http.sseFlow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "MiniMaxTTSProvider"
private const val MINIMAX_SAMPLE_RATE = 32000
private val JSON_MEDIA_TYPE = "application/json".toMediaType()
private val miniMaxJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
private data class MiniMaxResponseData(
    val audio: String? = null,
    val status: Int? = null,
    val ced: String? = null
)

@Serializable
private data class MiniMaxResponse(
    val data: MiniMaxResponseData? = null,
    @SerialName("base_resp")
    val baseResp: MiniMaxBaseResponse? = null,
    @SerialName("trace_id")
    val traceId: String? = null
)

@Serializable
private data class MiniMaxBaseResponse(
    @SerialName("status_code")
    val statusCode: Int = 0,
    @SerialName("status_msg")
    val statusMsg: String = ""
)

internal class MiniMaxSseProcessor(
    private val model: String,
    private val voiceId: String
) {
    private var hasAudio = false

    fun process(event: SseEvent): AudioChunk? {
        return when (event) {
            is SseEvent.Open -> {
                Log.i(TAG, "SSE connection opened")
                null
            }

            is SseEvent.Event -> processData(event.data)

            is SseEvent.Closed -> {
                Log.i(TAG, "SSE connection closed")
                if (!hasAudio) {
                    throw IllegalStateException(
                        "MiniMax TTS returned no audio. Check API Key, Group ID, model, and voice ID."
                    )
                }
                AudioChunk(
                    data = byteArrayOf(),
                    format = AudioFormat.MP3,
                    sampleRate = MINIMAX_SAMPLE_RATE,
                    isLast = true,
                    metadata = mapOf("provider" to "minimax")
                )
            }

            is SseEvent.Failure -> {
                val throwable = event.throwable
                Log.e(TAG, "SSE connection failed", throwable)
                val response = event.response
                val errorBody = response?.body?.string().orEmpty()
                throw Exception(
                    buildString {
                        append("MiniMax TTS request failed")
                        if (response != null) append(": ${response.code} ${response.message}")
                        if (errorBody.isNotBlank()) append(" - $errorBody")
                        if (throwable?.message?.isNotBlank() == true) {
                            append(" - ${throwable.message}")
                        }
                    },
                    throwable
                )
            }
        }
    }

    private fun processData(rawData: String): AudioChunk? {
        if (rawData == "[DONE]") return null

        val response = try {
            miniMaxJson.decodeFromString<MiniMaxResponse>(rawData)
        } catch (e: Exception) {
            Log.w(TAG, "Ignoring malformed MiniMax SSE chunk: $rawData", e)
            return null
        }

        response.baseResp?.takeIf { it.statusCode != 0 }?.let {
            throw Exception("MiniMax TTS error ${it.statusCode}: ${it.statusMsg.ifBlank { "unknown error" }}")
        }

        val responseData = response.data ?: return null
        val audioHex = responseData.audio.orEmpty()
        if (audioHex.isBlank()) return null

        val audioBytes = try {
            hexStringToBytes(audioHex)
        } catch (e: Exception) {
            throw Exception("MiniMax TTS returned invalid audio data", e)
        }

        hasAudio = true
        return AudioChunk(
            data = audioBytes,
            format = AudioFormat.MP3,
            sampleRate = MINIMAX_SAMPLE_RATE,
            isLast = false,
            metadata = mapOf(
                "provider" to "minimax",
                "model" to model,
                "voice" to voiceId,
                "status" to responseData.status?.toString().orEmpty(),
                "ced" to responseData.ced.orEmpty(),
                "traceId" to response.traceId.orEmpty()
            )
        )
    }
}

class MiniMaxTTSProvider : TTSProvider<TTSProviderSetting.MiniMax> {
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.MiniMax,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val apiKey = providerSetting.apiKey.trim()
        val groupId = providerSetting.groupId.trim()
        val model = TTSProviderSetting.MiniMax.normalizeModel(providerSetting.model)

        require(apiKey.isNotBlank()) { "MiniMax API Key is required" }
        require(groupId.isNotBlank()) { "MiniMax Group ID is required" }

        val requestBody = buildJsonObject {
            put("model", model)
            put("text", request.text)
            put("stream", true)
            put("output_format", "hex")
            put("stream_options", buildJsonObject {
                put("exclude_aggregated_audio", true)
            })
            put("voice_setting", buildJsonObject {
                put("voice_id", providerSetting.voiceId)
                put("emotion", providerSetting.emotion)
                put("speed", providerSetting.speed)
            })
            put("audio_setting", buildJsonObject {
                put("sample_rate", 32000)
                put("bitrate", 128000)
                put("format", "mp3")
                put("channel", 1)
            })
        }

        Log.i(TAG, "generateSpeech: $requestBody")

        val httpRequest = Request.Builder()
            .url(buildT2aUrl(providerSetting.baseUrl, groupId))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val processor = MiniMaxSseProcessor(
            model = model,
            voiceId = providerSetting.voiceId
        )

        httpClient.sseFlow(httpRequest).collect { event ->
            processor.process(event)?.let { emit(it) }
        }
    }
}

private fun buildT2aUrl(baseUrl: String, groupId: String): HttpUrl {
    val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
    val endpoint = if (normalizedBaseUrl.endsWith("/t2a_v2")) {
        normalizedBaseUrl
    } else {
        "$normalizedBaseUrl/t2a_v2"
    }

    return endpoint.toHttpUrl()
        .newBuilder()
        .removeAllQueryParameters("GroupId")
        .removeAllQueryParameters("Groupid")
        .addQueryParameter("GroupId", groupId)
        .build()
}

private fun hexStringToBytes(hexString: String): ByteArray {
    val cleanHex = hexString.replace("\\s+".toRegex(), "")
    val length = cleanHex.length

    // Check for even number of characters
    if (length % 2 != 0) {
        throw IllegalArgumentException("Hex string must have even number of characters")
    }

    val bytes = ByteArray(length / 2)
    for (i in 0 until length step 2) {
        val hexByte = cleanHex.substring(i, i + 2)
        bytes[i / 2] = hexByte.toInt(16).toByte()
    }
    return bytes
}
