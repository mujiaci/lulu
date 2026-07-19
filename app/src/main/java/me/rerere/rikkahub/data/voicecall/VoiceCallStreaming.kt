package me.rerere.rikkahub.data.voicecall

import me.rerere.rikkahub.data.ai.transformers.COMPANION_INCOMPLETE_REPLY_MARKER
import me.rerere.rikkahub.data.ai.transformers.sanitizeLuluVisibleExpression

/**
 * Converts an accumulating model reply into stable, speakable segments.
 *
 * The segmenter never emits unfinished internal tags and only advances when the
 * newly observed text still begins with the already emitted prefix. This avoids
 * speaking provider rewrites or private runtime blocks during streaming.
 */
internal class VoiceCallStreamSegmenter {
    private var emittedPrefix: String = ""

    fun offer(accumulatedText: String): List<String> =
        drain(accumulatedText = accumulatedText, flushRemainder = false)

    fun finish(finalText: String): List<String> =
        drain(accumulatedText = finalText, flushRemainder = true)

    private fun drain(
        accumulatedText: String,
        flushRemainder: Boolean,
    ): List<String> {
        val visible = accumulatedText.safeVoiceCallStreamText()
        if (visible.isBlank() || visible == COMPANION_INCOMPLETE_REPLY_MARKER) return emptyList()
        if (!visible.startsWith(emittedPrefix)) return emptyList()

        val pending = visible.substring(emittedPrefix.length)
        if (pending.isBlank()) return emptyList()
        val cutOffsets = mutableListOf<Int>()
        var segmentStart = 0
        pending.forEachIndexed { index, char ->
            val candidateLength = pending
                .substring(segmentStart, index + 1)
                .count { !it.isWhitespace() }
            val isHardBoundary = char in HARD_SPEECH_BOUNDARIES
            val isSoftBoundary = char in SOFT_SPEECH_BOUNDARIES &&
                candidateLength >= MIN_SOFT_SEGMENT_CHARACTERS
            if (isHardBoundary || isSoftBoundary) {
                cutOffsets += index + 1
                segmentStart = index + 1
            }
        }
        if (flushRemainder && segmentStart < pending.length) {
            cutOffsets += pending.length
        }
        if (cutOffsets.isEmpty()) return emptyList()

        val segments = mutableListOf<String>()
        var previousOffset = 0
        cutOffsets.distinct().sorted().forEach { offset ->
            pending.substring(previousOffset, offset)
                .trim()
                .takeIf(String::isNotBlank)
                ?.let(segments::add)
            previousOffset = offset
        }
        val consumed = cutOffsets.maxOrNull() ?: return emptyList()
        emittedPrefix += pending.take(consumed)
        return segments
    }
}

private fun String.safeVoiceCallStreamText(): String {
    val firstInternalTag = INTERNAL_STREAM_TAGS
        .map { tag -> indexOf(tag, ignoreCase = true) }
        .filter { it >= 0 }
        .minOrNull()
    val speakablePrefix = if (firstInternalTag == null) this else take(firstInternalTag)
    return sanitizeLuluVisibleExpression(speakablePrefix).trim()
}

private val INTERNAL_STREAM_TAGS: List<String> = listOf(
    "<companion",
    "<private",
    "<lulu",
    "<runtime",
)

private val HARD_SPEECH_BOUNDARIES = setOf('。', '！', '？', '.', '!', '?', '…', '~', '～', '\n')
private val SOFT_SPEECH_BOUNDARIES = setOf('，', ',', '；', ';')
private const val MIN_SOFT_SEGMENT_CHARACTERS = 18
