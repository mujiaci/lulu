package me.rerere.tts.controller

import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSResponse
import me.rerere.tts.provider.TTSProviderSetting
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal class TtsAudioCache(
    private val directory: File,
) {
    fun read(provider: TTSProviderSetting, text: String): TTSResponse? {
        val file = cacheFile(provider, text)
        if (!file.isFile) return null
        return runCatching {
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                check(input.readInt() == CACHE_MAGIC)
                check(input.readInt() == CACHE_VERSION)
                val format = AudioFormat.valueOf(input.readUTF())
                val sampleRate = input.readInt().takeIf { it >= 0 }
                val duration = input.readFloat().takeIf { it >= 0f }
                val metadataCount = input.readInt().coerceIn(0, MAX_METADATA_ITEMS)
                val metadata = buildMap {
                    repeat(metadataCount) {
                        put(input.readUTF(), input.readUTF())
                    }
                }
                val audioSize = input.readInt()
                check(audioSize in 1..MAX_AUDIO_BYTES)
                TTSResponse(
                    audioData = ByteArray(audioSize).also(input::readFully),
                    format = format,
                    sampleRate = sampleRate,
                    duration = duration,
                    metadata = metadata,
                )
            }
        }.onSuccess {
            file.setLastModified(System.currentTimeMillis())
        }.onFailure {
            file.delete()
        }.getOrNull()
    }

    fun write(provider: TTSProviderSetting, text: String, response: TTSResponse) {
        if (response.audioData.isEmpty() || response.audioData.size > MAX_AUDIO_BYTES) return
        runCatching {
            directory.mkdirs()
            val target = cacheFile(provider, text)
            val temp = File(directory, "${target.name}.${UUID.randomUUID()}.tmp")
            DataOutputStream(BufferedOutputStream(temp.outputStream())).use { output ->
                output.writeInt(CACHE_MAGIC)
                output.writeInt(CACHE_VERSION)
                output.writeUTF(response.format.name)
                output.writeInt(response.sampleRate ?: -1)
                output.writeFloat(response.duration ?: -1f)
                val metadata = response.metadata.entries.take(MAX_METADATA_ITEMS)
                output.writeInt(metadata.size)
                metadata.forEach { (key, value) ->
                    output.writeUTF(key.take(MAX_METADATA_TEXT_LENGTH))
                    output.writeUTF(value.take(MAX_METADATA_TEXT_LENGTH))
                }
                output.writeInt(response.audioData.size)
                output.write(response.audioData)
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            prune()
        }
    }

    private fun cacheFile(provider: TTSProviderSetting, text: String): File =
        File(directory, "${cacheKey(provider, text)}.tts")

    private fun prune() {
        val files = directory.listFiles { file -> file.isFile && file.extension == "tts" }
            ?.sortedByDescending(File::lastModified)
            .orEmpty()
        var retainedBytes = 0L
        files.forEachIndexed { index, file ->
            retainedBytes += file.length()
            if (index >= MAX_CACHE_FILES || retainedBytes > MAX_CACHE_BYTES) {
                file.delete()
            }
        }
    }
}

internal fun cacheKey(provider: TTSProviderSetting, text: String): String {
    val material = buildString {
        append(provider::class.qualifiedName)
        append('|')
        append(provider)
        append('|')
        append(text.trim())
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(material.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private const val CACHE_MAGIC = 0x4C545453
private const val CACHE_VERSION = 1
private const val MAX_CACHE_FILES = 160
private const val MAX_CACHE_BYTES = 192L * 1024L * 1024L
private const val MAX_AUDIO_BYTES = 24 * 1024 * 1024
private const val MAX_METADATA_ITEMS = 32
private const val MAX_METADATA_TEXT_LENGTH = 4_096
