package me.rerere.rikkahub.data.service

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.math.sqrt

internal fun cosineSimilarity(a: List<Float>, b: List<Float>): Double {
    if (a.isEmpty() || a.size != b.size) return 0.0

    var dot = 0.0
    var normA = 0.0
    var normB = 0.0
    a.indices.forEach { index ->
        val av = a[index].toDouble()
        val bv = b[index].toDouble()
        dot += av * bv
        normA += av * av
        normB += bv * bv
    }
    if (normA == 0.0 || normB == 0.0) return 0.0
    return dot / (sqrt(normA) * sqrt(normB))
}

internal fun encodeMemoryVector(vector: List<Float>): String =
    JsonInstant.encodeToString(ListSerializer(Float.serializer()), vector)

internal fun decodeMemoryVector(raw: String?): List<Float> =
    if (raw.isNullOrBlank()) {
        emptyList()
    } else {
        runCatching {
            JsonInstant.decodeFromString(ListSerializer(Float.serializer()), raw)
        }.getOrDefault(emptyList())
    }
