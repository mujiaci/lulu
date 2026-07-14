package me.rerere.rikkahub.data.companion

import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.utils.JsonInstant
import java.nio.charset.StandardCharsets
import java.util.UUID

fun buildConversationLifeEvent(
    assistantId: String,
    assistantText: String,
    source: CompanionLifeEventSource,
    evidenceReference: String?,
    nowMillis: Long,
): CompanionLifeEvent? {
    val summary = assistantText.cleanLifeEventText(280)
    if (assistantId.isBlank() || summary.isBlank()) return null
    val type = if (source == CompanionLifeEventSource.PROACTIVE) {
        CompanionLifeEventType.PROACTIVE_MESSAGE
    } else {
        CompanionLifeEventType.CONVERSATION
    }
    return CompanionLifeEvent(
        id = stableLifeEventId(assistantId, type, evidenceReference),
        assistantId = assistantId,
        type = type,
        title = if (type == CompanionLifeEventType.PROACTIVE_MESSAGE) "主动联系了你" else "和你聊了一会儿",
        summary = summary,
        source = source,
        evidenceReference = evidenceReference,
        importance = if (type == CompanionLifeEventType.PROACTIVE_MESSAGE) 3 else 2,
        startedAt = nowMillis,
        endedAt = nowMillis,
        createdAt = nowMillis,
    )
}

fun buildToolLifeEvent(
    assistantId: String,
    execution: CompanionToolExecution,
    source: CompanionLifeEventSource,
    nowMillis: Long,
): CompanionLifeEvent? {
    if (assistantId.isBlank() || execution.toolCallId.isBlank() || execution.toolName.isBlank()) return null
    val successful = execution.outputText.toolExecutionSucceeded()
    val details = execution.outputText.toLifeEventSummary(execution.toolName)
    val descriptor = execution.toolName.toLifeEventDescriptor()
    return CompanionLifeEvent(
        id = stableLifeEventId(assistantId, descriptor.type, execution.toolCallId),
        assistantId = assistantId,
        type = descriptor.type,
        status = if (successful) CompanionLifeEventStatus.COMPLETED else CompanionLifeEventStatus.FAILED,
        title = if (successful) descriptor.completedTitle else descriptor.failedTitle,
        summary = details,
        source = source,
        evidenceReference = execution.toolCallId,
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
    val failedTitle: String,
    val importance: Int,
)

private fun String.toLifeEventDescriptor(): LifeEventDescriptor = when (this) {
    "write_lulu_journal" -> LifeEventDescriptor(
        CompanionLifeEventType.JOURNAL,
        "写下了一篇辞海日记",
        "想写日记，但没有保存成功",
        4,
    )
    "control_music" -> LifeEventDescriptor(
        CompanionLifeEventType.MUSIC,
        "操作了手机里的音乐",
        "尝试操作音乐，但没有成功",
        2,
    )
    "play_companion_game" -> LifeEventDescriptor(
        CompanionLifeEventType.GAME,
        "完成了一局信号寻踪",
        "想玩一局信号寻踪，但游戏没有完成",
        3,
    )
    "today_study_plan" -> LifeEventDescriptor(
        CompanionLifeEventType.STUDY_REVIEW,
        "查看或整理了考研计划",
        "尝试查看考研计划，但没有成功",
        3,
    )
    "favorite_user_message" -> LifeEventDescriptor(
        CompanionLifeEventType.MEMORY_REVIEW,
        "收藏了一句想留下来的话",
        "想收藏这句话，但没有成功",
        4,
    )
    else -> LifeEventDescriptor(
        CompanionLifeEventType.TOOL_ACTION,
        "完成了一次数字行动",
        "尝试了一次数字行动，但没有成功",
        2,
    )
}

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

private fun String.toLifeEventSummary(toolName: String): String {
    if (toolName != "play_companion_game") return cleanLifeEventText(320)
    val output = runCatching { JsonInstant.parseToJsonElement(trim()).jsonObject }.getOrNull()
        ?: return cleanLifeEventText(320)
    val score = output["score"]?.jsonPrimitive?.intOrNull
    val maxScore = output["max_score"]?.jsonPrimitive?.intOrNull
    val found = output["signals_found"]?.jsonPrimitive?.intOrNull
    val result = output["result"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
    return buildString {
        append("信号寻踪")
        if (score != null && maxScore != null) append("得分 $score/$maxScore")
        if (found != null) append("，找到 $found 个信号")
        if (result.isNotBlank()) append("。$result")
    }.cleanLifeEventText(320)
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
