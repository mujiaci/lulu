package me.rerere.rikkahub.data.companion

import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.utils.JsonInstant
import java.nio.charset.StandardCharsets
import java.util.UUID

fun buildToolLifeEvent(
    assistantId: String,
    execution: CompanionToolExecution,
    source: CompanionLifeEventSource,
    nowMillis: Long,
): CompanionLifeEvent? {
    if (assistantId.isBlank() || execution.toolCallId.isBlank() || execution.toolName.isBlank()) return null
    val successful = execution.isSuccessfulToolExecution()
    if (!successful) return null
    val descriptor = execution.toMeaningfulLifeEventDescriptor() ?: return null
    return CompanionLifeEvent(
        id = stableLifeEventId(assistantId, descriptor.type, execution.toolCallId),
        assistantId = assistantId,
        type = descriptor.type,
        status = CompanionLifeEventStatus.COMPLETED,
        title = descriptor.completedTitle,
        summary = execution.toLifeEventSummary(descriptor),
        source = source,
        evidenceReference = execution.toolCallId,
        detailsJson = if (descriptor.type == CompanionLifeEventType.GAME) {
            execution.outputText.trim().takeIf { it.startsWith("{") }
        } ?: "",
        importance = descriptor.importance,
        startedAt = nowMillis,
        endedAt = nowMillis,
        createdAt = nowMillis,
    )
}

fun buildWaitingLifeEvent(
    assistantId: String,
    reason: String,
    evidenceReference: String?,
    nowMillis: Long,
): CompanionLifeEvent? {
    if (assistantId.isBlank()) return null
    return CompanionLifeEvent(
        id = stableLifeEventId(assistantId, CompanionLifeEventType.WAITING, evidenceReference),
        assistantId = assistantId,
        type = CompanionLifeEventType.WAITING,
        title = "决定先安静等一会儿",
        summary = reason.cleanLifeEventText(240),
        source = CompanionLifeEventSource.AGENT,
        evidenceReference = evidenceReference,
        importance = 1,
        startedAt = nowMillis,
        endedAt = nowMillis,
        createdAt = nowMillis,
    )
}

fun buildAutonomousReflectionLifeEvent(
    assistantId: String,
    reason: String,
    reviewedMemory: Boolean,
    evidenceReference: String,
    nowMillis: Long,
): CompanionLifeEvent? {
    if (assistantId.isBlank() || evidenceReference.isBlank()) return null
    val type = if (reviewedMemory) CompanionLifeEventType.MEMORY_REVIEW else CompanionLifeEventType.REFLECTION
    return CompanionLifeEvent(
        id = stableLifeEventId(assistantId, type, evidenceReference),
        assistantId = assistantId,
        type = type,
        title = if (reviewedMemory) "自主整理了一次记忆" else "自主整理了一次当前状态",
        summary = reason.cleanLifeEventText(240),
        source = CompanionLifeEventSource.AGENT,
        evidenceReference = evidenceReference,
        importance = 2,
        startedAt = nowMillis,
        endedAt = nowMillis,
        createdAt = nowMillis,
    )
}

private data class LifeEventDescriptor(
    val type: CompanionLifeEventType,
    val completedTitle: String,
    val importance: Int,
)

private fun CompanionToolExecution.toMeaningfulLifeEventDescriptor(): LifeEventDescriptor? = when (toolName) {
    "write_lulu_journal" -> LifeEventDescriptor(
        CompanionLifeEventType.JOURNAL,
        "写下了一篇辞海日记",
        4,
    )
    "control_music" -> {
        val action = inputJson.parseObjectOrNull()?.get("action")?.jsonPrimitive?.contentOrNull.orEmpty()
        if (action.isBlank() || action == "get_now_playing") null else LifeEventDescriptor(
            CompanionLifeEventType.MUSIC,
            "操作了手机里的音乐",
            2,
        )
    }
    "play_companion_game" -> LifeEventDescriptor(
        CompanionLifeEventType.GAME,
        "完成了一局信号寻踪",
        3,
    )
    "set_alarm" -> LifeEventDescriptor(CompanionLifeEventType.TOOL_ACTION, "设置了一次设备提醒", 3)
    "calendar_tool" -> {
        val action = inputJson.parseObjectOrNull()?.get("action")?.jsonPrimitive?.contentOrNull
        if (action == "create") LifeEventDescriptor(CompanionLifeEventType.TOOL_ACTION, "写入了一项日程", 3) else null
    }
    else -> null
}

internal fun CompanionToolExecution.isSuccessfulToolExecution(): Boolean = outputText.toolExecutionSucceeded()

private fun String.toolExecutionSucceeded(): Boolean {
    val trimmed = trim()
    if (trimmed.isBlank()) return false
    val explicit = runCatching {
        JsonInstant.parseToJsonElement(trimmed).jsonObject["success"]?.jsonPrimitive?.booleanOrNull
    }.getOrNull()
    if (explicit != null) return explicit
    return listOf("error", "failed", "失败", "错误", "未成功")
        .none { marker -> contains(marker, ignoreCase = true) }
}

private fun CompanionToolExecution.toLifeEventSummary(descriptor: LifeEventDescriptor): String {
    val input = inputJson.parseObjectOrNull()
    val output = outputText.parseObjectOrNull()
    return when (descriptor.type) {
        CompanionLifeEventType.GAME -> {
            if (output == null) return "完成了一局信号寻踪"
            val score = output["score"]?.jsonPrimitive?.intOrNull
            val maxScore = output["max_score"]?.jsonPrimitive?.intOrNull
            val found = output["signals_found"]?.jsonPrimitive?.intOrNull
            val result = output["result"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            buildString {
                append("信号寻踪")
                if (score != null && maxScore != null) append("得分 $score/$maxScore")
                if (found != null) append("，找到 $found 个信号")
                if (result.isNotBlank()) append("。$result")
            }.cleanLifeEventText(320)
        }
        CompanionLifeEventType.JOURNAL -> {
            val title = input?.get("title")?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            if (title.isBlank()) "写下了一篇辞海日记" else "写下了日记：《${title.take(80)}》"
        }
        CompanionLifeEventType.MUSIC -> {
            val action = input?.get("action")?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            val label = when (action) {
                "play" -> "开始播放音乐"
                "pause" -> "暂停了音乐"
                "next" -> "切换到了下一首"
                "previous" -> "切换到了上一首"
                else -> "操作了手机里的音乐"
            }
            label
        }
        CompanionLifeEventType.TOOL_ACTION -> when (toolName) {
            "set_alarm" -> {
                val hour = input?.get("hour")?.jsonPrimitive?.intOrNull
                val minute = input?.get("minute")?.jsonPrimitive?.intOrNull
                val label = input?.get("label")?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                if (hour != null && minute != null) {
                    "设置了 ${"%02d:%02d".format(hour, minute)} 的设备提醒${label.takeIf(String::isNotBlank)?.let { "：${it.take(80)}" }.orEmpty()}"
                } else {
                    "设置了一次设备提醒"
                }
            }
            "calendar_tool" -> {
                val title = input?.get("title")?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                if (title.isBlank()) "写入了一项日程" else "写入了日程：${title.take(100)}"
            }
            else -> descriptor.completedTitle
        }
        else -> descriptor.completedTitle
    }.cleanLifeEventText(320)
}

private fun String.parseObjectOrNull() = runCatching {
    JsonInstant.parseToJsonElement(trim()).jsonObject
}.getOrNull()

fun CompanionLifeEvent.isMeaningfulDigitalLifeEvidence(): Boolean {
    if (status != CompanionLifeEventStatus.COMPLETED) return false
    return when (type) {
        CompanionLifeEventType.JOURNAL,
        CompanionLifeEventType.MUSIC,
        CompanionLifeEventType.GAME,
        CompanionLifeEventType.MEMORY_REVIEW -> true
        CompanionLifeEventType.TOOL_ACTION -> title.startsWith("设置了") || title.startsWith("写入了")
        else -> false
    }
}

private fun String.cleanLifeEventText(maxLength: Int): String = lineSequence()
    .map(String::trim)
    .filter(String::isNotBlank)
    .joinToString(" ")
    .replace(Regex("\\s+"), " ")
    .take(maxLength)
    .trim()

private fun stableLifeEventId(
    assistantId: String,
    type: CompanionLifeEventType,
    evidenceReference: String?,
): String {
    val evidence = evidenceReference?.trim().orEmpty()
    if (evidence.isBlank()) return UUID.randomUUID().toString()
    return UUID.nameUUIDFromBytes(
        "$assistantId|${type.name}|$evidence".toByteArray(StandardCharsets.UTF_8),
    ).toString()
}
