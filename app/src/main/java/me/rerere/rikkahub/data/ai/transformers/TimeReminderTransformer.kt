package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.utils.toLocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant

private const val TIME_GAP_THRESHOLD_SECONDS = 3600L

/**
 * Injects time reminders so the model can distinguish historical message time from the current request time.
 */
object TimeReminderTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return applyTimeReminder(
            messages = messages,
            requestInstant = Clock.System.now(),
            includeHistoricalGaps = ctx.assistant.enableTimeReminder
        )
    }
}

internal fun applyTimeReminder(messages: List<UIMessage>): List<UIMessage> {
    return applyTimeReminder(
        messages = messages,
        requestInstant = Clock.System.now(),
        includeHistoricalGaps = true,
    )
}

internal fun applyTimeReminder(
    messages: List<UIMessage>,
    requestInstant: Instant,
    includeHistoricalGaps: Boolean = true,
): List<UIMessage> {
    if (messages.isEmpty()) return emptyList()

    val result = mutableListOf<UIMessage>()
    val messageTimeContext = buildMessageTimeContextMessage(messages)
    if (messageTimeContext != null) {
        result.add(messageTimeContext)
    }
    val timeZone = TimeZone.currentSystemDefault()
    val lastUserIndex = messages.indexOfLast { it.role == MessageRole.USER }
    var previousUserInstant: Instant? = null

    for (i in messages.indices) {
        val current = messages[i]
        if (current.role == MessageRole.USER) {
            val currentInstant = current.createdAt.toInstant(timeZone)
            val gapSeconds = previousUserInstant?.let { (currentInstant - it).inWholeSeconds }

            if (i == lastUserIndex) {
                result.add(
                    buildTimeReminderMessage(
                        gapSeconds = gapSeconds,
                        instant = requestInstant,
                        isCurrentRequest = true
                    )
                )
            } else if (includeHistoricalGaps && gapSeconds != null && gapSeconds >= TIME_GAP_THRESHOLD_SECONDS) {
                result.add(
                    buildTimeReminderMessage(
                        gapSeconds = gapSeconds,
                        instant = currentInstant,
                        isCurrentRequest = false
                    )
                )
            }

            previousUserInstant = currentInstant
        }
        result.add(current)
    }

    return result
}

private fun buildMessageTimeContextMessage(messages: List<UIMessage>): UIMessage? {
    val chatMessages = messages.filter { it.role != MessageRole.SYSTEM }
    if (chatMessages.isEmpty()) return null

    val zoneId = ZoneId.systemDefault()
    val content = buildString {
        appendLine("<message_time_context>")
        appendLine("These timestamps belong to the current chat context in this request. Rows match the non-system messages in order; use them to understand when each visible chat message was sent.")
        chatMessages.forEachIndexed { index, message ->
            val sentAt = message.createdAt.toInstant(TimeZone.currentSystemDefault())
                .toJavaInstant()
                .atZone(zoneId)
                .toLocalDateTime()
            val finishedAt = message.finishedAt?.toInstant(TimeZone.currentSystemDefault())
                ?.toJavaInstant()
                ?.atZone(zoneId)
                ?.toLocalDateTime()
            append("[${index + 1}] ${message.role.name} sent_at=$sentAt")
            if (finishedAt != null) append(" finished_at=$finishedAt")
            appendLine()
        }
        append("</message_time_context>")
    }
    return UIMessage.system(content)
}

private fun buildTimeReminderMessage(
    gapSeconds: Long?,
    instant: Instant,
    isCurrentRequest: Boolean,
): UIMessage {
    val javaInstant = instant.toJavaInstant()
    val dayOfWeek = javaInstant.atZone(ZoneId.systemDefault()).dayOfWeek
        .getDisplayName(TextStyle.FULL, Locale.getDefault())
    val timeStr = javaInstant.toLocalDateTime()
    val gapText = gapSeconds?.let { "${formatGap(it)} since last user message" }

    val content = if (isCurrentRequest) {
        buildString {
            append("<time_reminder>Current real time for this request: $dayOfWeek, $timeStr")
            if (gapText != null) append(" ($gapText)")
            append(". Treat this as the current time, not the time of earlier chat history.</time_reminder>")
        }
    } else {
        buildString {
            append("<time_reminder>Current time: $dayOfWeek, $timeStr")
            if (gapText != null) append(" ($gapText)")
            append("</time_reminder>")
        }
    }
    return UIMessage.user(content)
}

private fun formatGap(seconds: Long): String {
    return when {
        seconds < 3600 -> "${seconds / 60} min"
        seconds < 86400 -> "${seconds / 3600} h"
        else -> "${seconds / 86400} d"
    }
}
