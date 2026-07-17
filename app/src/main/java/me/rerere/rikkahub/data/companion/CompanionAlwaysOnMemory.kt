package me.rerere.rikkahub.data.companion

data class CompanionResponsibilityAnchorDraft(
    val stableKey: String,
    val kind: CompanionAlwaysOnAnchorKind = CompanionAlwaysOnAnchorKind.RESPONSIBILITY,
    val statement: String,
    val responsibility: String,
    val triggers: List<String> = emptyList(),
    val actions: List<String> = emptyList(),
    val avoid: List<String> = emptyList(),
    val importance: Int = 5,
)

fun CompanionResponsibilityAnchorDraft.toAlwaysOnAnchorOrNull(
    assistantId: String,
    sourceConversationId: String? = null,
    sourceMessageId: String? = null,
    nowMillis: Long = System.currentTimeMillis(),
): CompanionAlwaysOnAnchor? {
    val cleanAssistantId = assistantId.trim()
    val cleanStatement = statement.cleanResponsibilityText(360)
    val cleanResponsibility = responsibility.cleanResponsibilityText(520)
    val cleanActions = actions.cleanResponsibilityItems(8, 260)
    if (cleanAssistantId.isBlank() || cleanStatement.isBlank() || cleanResponsibility.isBlank() || cleanActions.isEmpty()) {
        return null
    }
    val normalizedKey = normalizeCompanionSubjectKey(
        stableKey.cleanResponsibilityText(120).ifBlank { cleanResponsibility },
    ).ifBlank { return null }
    val safeKind = kind.takeIf {
        it == CompanionAlwaysOnAnchorKind.HEALTH || it == CompanionAlwaysOnAnchorKind.RESPONSIBILITY
    } ?: CompanionAlwaysOnAnchorKind.RESPONSIBILITY
    val anchorId = when {
        safeKind == CompanionAlwaysOnAnchorKind.HEALTH && normalizedKey == "health" ->
            "$cleanAssistantId:health:body"
        normalizedKey == "wake" -> "$cleanAssistantId:responsibility:wake-alarm"
        else -> "$cleanAssistantId:responsibility:$normalizedKey"
    }
    return CompanionAlwaysOnAnchor(
        id = anchorId,
        assistantId = cleanAssistantId,
        kind = safeKind,
        statement = cleanStatement,
        responsibility = cleanResponsibility,
        triggers = triggers.cleanResponsibilityItems(8, 220),
        actions = cleanActions,
        avoid = avoid.cleanResponsibilityItems(8, 220),
        importance = importance.coerceIn(1, 5),
        sourceConversationId = sourceConversationId?.trim()?.takeIf(String::isNotBlank),
        sourceMessageId = sourceMessageId?.trim()?.takeIf(String::isNotBlank),
        createdAt = nowMillis,
        updatedAt = nowMillis,
    )
}

/**
 * Responsibility memory is deliberately narrower than recall memory. It contains duties the
 * character must actively remember until the user changes or cancels them.
 */
fun detectAlwaysOnAnchors(
    assistantId: String,
    userText: String,
    sourceConversationId: String? = null,
    sourceMessageId: String? = null,
    nowMillis: Long = System.currentTimeMillis(),
): List<CompanionAlwaysOnAnchor> {
    if (assistantId.isBlank() || userText.isBlank()) return emptyList()
    val normalized = userText.lowercase()
    return buildList {
        if (normalized.containsAny(setOf("身体不好", "身体不太好", "身体差", "健康不好", "体质不好", "有病", "慢性病"))) {
            add(
                CompanionAlwaysOnAnchor(
                    id = "$assistantId:health:body",
                    assistantId = assistantId,
                    kind = CompanionAlwaysOnAnchorKind.HEALTH,
                    statement = "用户的身体状况不太好，需要在安排学习、运动、作息和提醒时优先考虑负担与恢复。",
                    responsibility = "先关心身体状态，再给出低负担、可执行的建议。",
                    triggers = listOf("用户表达疲惫或身体不适", "安排运动、学习、睡眠或饮食", "观察到睡眠不足或长时间使用手机"),
                    actions = listOf("先询问当前身体状态", "减少高负担活动", "自然提醒睡眠、起床、饮食和必要的就医常识"),
                    avoid = listOf("不要自称医生或擅自诊断", "不要强迫高强度运动", "不要只重复身体不好这句话"),
                    sourceConversationId = sourceConversationId,
                    sourceMessageId = sourceMessageId,
                    createdAt = nowMillis,
                    updatedAt = nowMillis,
                ),
            )
        }

        val wakeDuty = normalized.containsAny(setOf("定闹钟", "叫我起床", "叫我起来", "提醒我起床", "闹钟交给你")) &&
            normalized.containsAny(setOf("以后", "每天", "交给你", "负责", "都这么做", "记住"))
        if (wakeDuty) {
            add(
                CompanionAlwaysOnAnchor(
                    id = "$assistantId:responsibility:wake-alarm",
                    assistantId = assistantId,
                    kind = CompanionAlwaysOnAnchorKind.RESPONSIBILITY,
                    statement = "用户把每天的起床闹钟交给角色负责。",
                    responsibility = "每天晚上根据最近真实睡眠、起床时间、屏幕使用和健康数据，判断次日合适的起床时间并设置闹钟；没有足够证据时先询问，不要编造。",
                    triggers = listOf("每天夜间准备次日作息", "用户准备睡觉", "睡眠或屏幕使用数据发生明显变化"),
                    actions = listOf("读取可用睡眠和应用使用证据", "决定次日叫醒时间并真实设置闹钟", "早晨根据设备活动确认是否已经醒来"),
                    avoid = listOf("不要只口头答应", "不要在没有证据时虚构睡眠时间", "用户取消后停止执行"),
                    sourceConversationId = sourceConversationId,
                    sourceMessageId = sourceMessageId,
                    createdAt = nowMillis,
                    updatedAt = nowMillis,
                ),
            )
        }
    }
}

fun detectAlwaysOnAnchorCancellations(
    assistantId: String,
    userText: String,
): List<String> {
    if (assistantId.isBlank() || userText.isBlank()) return emptyList()
    val normalized = userText.lowercase()
    val cancellation = normalized.containsAny(setOf("取消", "不用你", "不用再", "别再", "不需要你", "已经好了", "恢复了"))
    if (!cancellation) return emptyList()
    return buildList {
        if (normalized.containsAny(setOf("闹钟", "起床", "叫我"))) add("$assistantId:responsibility:wake-alarm")
        if (normalized.containsAny(setOf("身体", "健康", "体质"))) add("$assistantId:health:body")
    }
}

fun buildCompanionResponsibilityContext(
    anchors: List<CompanionAlwaysOnAnchor>,
    nowMillis: Long,
): String = buildString {
    val active = anchors
        .asSequence()
        .filter { anchor ->
            anchor.status == CompanionAlwaysOnAnchorStatus.ACTIVE &&
                (anchor.expiresAt == null || anchor.expiresAt > nowMillis)
        }
        .sortedWith(compareByDescending<CompanionAlwaysOnAnchor> { it.importance }.thenByDescending { it.updatedAt })
        .take(12)
        .toList()
    if (active.isEmpty()) return@buildString
    appendLine("responsibility_anchors:")
    appendLine("These are duties the character must actively remember and perform. They are separate from private_impression and are not optional recall candidates.")
    active.forEach { anchor ->
        appendLine(
            "- id=${anchor.id} kind=${anchor.kind.name} importance=${anchor.importance} " +
                "statement=${anchor.statement.take(300)} responsibility=${anchor.responsibility.orEmpty().take(420)}",
        )
        anchor.triggers.take(5).forEach { trigger -> appendLine("  trigger=${trigger.take(200)}") }
        anchor.actions.take(6).forEach { action -> appendLine("  required_action=${action.take(240)}") }
        anchor.avoid.take(5).forEach { avoid -> appendLine("  avoid=${avoid.take(200)}") }
    }
    appendLine("Do not move these duties into private_impression. Do not silently forget, downgrade, or contradict an active anchor. If the user corrects or cancels one, update it explicitly.")
}.trim()

private fun String.containsAny(words: Set<String>): Boolean = words.any(::contains)

private fun String.cleanResponsibilityText(maxLength: Int): String = trim()
    .replace(Regex("\\s+"), " ")
    .take(maxLength)

private fun List<String>.cleanResponsibilityItems(maxItems: Int, maxLength: Int): List<String> = asSequence()
    .map { it.cleanResponsibilityText(maxLength) }
    .filter(String::isNotBlank)
    .distinct()
    .take(maxItems)
    .toList()
