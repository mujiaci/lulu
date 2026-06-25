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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

private const val TAG = "MiniMaxTTSProvider"

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

class MiniMaxTTSProvider : TTSProvider<TTSProviderSetting.MiniMax> {
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

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
            .post(json.encodeToString(requestBody).toRequestBody("application/json".toMediaType()))
            .build()

        var hasEmittedAudio = false

        httpClient.sseFlow(httpRequest).collect {
            when (it) {
                is SseEvent.Open -> Log.i(TAG, "SSE connection opened")
                is SseEvent.Event -> {
                    if (it.data == "[DONE]") return@collect

                    val data = try {
                        json.decodeFromString<MiniMaxResponse>(it.data)
                    } catch (e: Exception) {
                        Log.w(TAG, "Ignoring malformed MiniMax SSE chunk: ${it.data}", e)
                        return@collect
                    }

                    data.baseResp?.takeIf { response -> response.statusCode != 0 }?.let { response ->
                        throw Exception(
                            "MiniMax TTS error ${response.statusCode}: ${response.statusMsg.ifBlank { "unknown error" }}"
                        )
                    }

                    val responseData = data.data ?: return@collect
                    val audioHex = responseData.audio.orEmpty()
                    if (audioHex.isBlank()) return@collect

                    // Convert hex string to bytes
                    val audioBytes = try {
                        hexStringToBytes(audioHex)
                    } catch (e: Exception) {
                        throw Exception("MiniMax TTS returned invalid audio data", e)
                    }

                    emit(
                        AudioChunk(
                            data = audioBytes,
                            format = AudioFormat.MP3, // MiniMax returns MP3 format
                            sampleRate = 32000, // Default sample rate from MiniMax
                            isLast = false, // Will be set to true on last chunk
                            metadata = mapOf(
                                "provider" to "minimax",
                                "model" to model,
                                "voice" to providerSetting.voiceId,
                                "status" to responseData.status.toString(),
                                "ced" to responseData.ced.orEmpty(),
                                "traceId" to data.traceId.orEmpty()
                            )
                        )
                    )
                    hasEmittedAudio = true
                }

                is SseEvent.Closed -> {
                    Log.i(TAG, "SSE connection closed")
                    // Emit final chunk if we haven't already
                    if (hasEmittedAudio) {
                        emit(
                            AudioChunk(
                                data = byteArrayOf(), // Empty data for last chunk
                                format = AudioFormat.MP3,
                                sampleRate = 32000,
                                isLast = true,
                                metadata = mapOf("provider" to "minimax")
                            )
                        )
                    } else {
                        throw Exception("MiniMax TTS returned no audio. Check API Key, Group ID, model, and voice ID.")
                    }
                }

                is SseEvent.Failure -> {
                    Log.e(TAG, "SSE connection failed", it.throwable)
                    val response = it.response
                    val errorBody = response?.body?.string().orEmpty()
                    throw Exception(
                        buildString {
                            append("MiniMax TTS request failed")
                            if (response != null) append(": ${response.code} ${response.message}")
                            if (errorBody.isNotBlank()) append(" - $errorBody")
                            if (it.throwable?.message?.isNotBlank() == true) append(" - ${it.throwable.message}")
                        },
                        it.throwable
                    )
                }
            }
        }
    }
}

private fun buildT2aUrl(baseUrl: String, groupId: String): okhttp3.HttpUrl {
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
