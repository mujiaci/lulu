package me.rerere.rikkahub.data.companion

/**
 * Deterministic fallback for explicit, recurring supervision requests.
 *
 * The model planner remains free to infer nuanced responsibilities. This detector only handles
 * direct requests such as "以后监督我学习、睡觉和起床", so an open-ended duty is not lost merely
 * because it has no concrete due time and therefore cannot become a scheduled commitment.
 */
fun detectExplicitRecurringResponsibilityAnchors(
    assistantId: String,
    userText: String,
    sourceConversationId: String?,
    sourceMessageId: String?,
    nowMillis: Long,
): List<CompanionAlwaysOnAnchor> {
    val text = userText.trim()
    if (assistantId.isBlank() || text.isBlank() || text.hasResponsibilityCancellationIntent()) {
        return emptyList()
    }
    if (!text.hasExplicitResponsibilityRequest() || !text.hasRecurringResponsibilityIntent()) {
        return emptyList()
    }

    return ExplicitResponsibilityArea.values().asSequence()
        .filter { area -> area.matches(text) }
        .map { area ->
            CompanionAlwaysOnAnchor(
                id = area.anchorId(assistantId),
                assistantId = assistantId,
                kind = CompanionAlwaysOnAnchorKind.RESPONSIBILITY,
                statement = area.statement,
                responsibility = area.responsibility,
                triggers = area.triggers,
                actions = area.actions,
                avoid = area.avoid,
                importance = area.importance,
                sourceConversationId = sourceConversationId?.trim()?.takeIf(String::isNotBlank),
                sourceMessageId = sourceMessageId?.trim()?.takeIf(String::isNotBlank),
                createdAt = nowMillis,
                updatedAt = nowMillis,
                lastConfirmedAt = nowMillis,
            )
        }
        .toList()
}

fun detectExplicitRecurringResponsibilityCancellations(
    assistantId: String,
    userText: String,
): List<String> {
    if (assistantId.isBlank() || !userText.hasResponsibilityCancellationIntent()) return emptyList()
    return ExplicitResponsibilityArea.values().asSequence()
        .filter { area -> area.matches(userText) }
        .map { area -> area.anchorId(assistantId) }
        .toList()
}

/** Keep the richer model result, and add only responsibility areas it did not capture. */
fun mergeAlwaysOnResponsibilityAnchors(
    detected: List<CompanionAlwaysOnAnchor>,
    explicit: List<CompanionAlwaysOnAnchor>,
): List<CompanionAlwaysOnAnchor> {
    val coveredAreas = detected
        .flatMapTo(mutableSetOf()) { anchor ->
            ExplicitResponsibilityArea.values().filter { area -> area.matches(anchor.statement) }
        }
    return (detected + explicit.filterNot { anchor ->
        ExplicitResponsibilityArea.values().any { area ->
            area in coveredAreas && area.matches(anchor.statement)
        }
    }).distinctBy { anchor -> anchor.id }
}

private enum class ExplicitResponsibilityArea(
    val key: String,
    val keywords: List<String>,
    val statement: String,
    val responsibility: String,
    val triggers: List<String>,
    val actions: List<String>,
    val avoid: List<String>,
    val importance: Int,
) {
    ALARM(
        key = "alarm",
        keywords = listOf("闹钟", "alarm"),
        statement = "替你持续留意闹钟安排",
        responsibility = "记得核对你需要的闹钟，并在睡眠或起床安排变化时及时调整。",
        triggers = listOf("你提到今晚几点睡、明早几点起或要修改闹钟时"),
        actions = listOf("确认具体时间", "需要时设置或调整闹钟", "说明是否设置成功"),
        avoid = listOf("没有确认时间时，不假装已经设置好闹钟"),
        importance = 5,
    ),
    STUDY(
        key = "study",
        keywords = listOf("学习", "复习", "背书", "背诵", "看课", "做题", "study"),
        statement = "持续监督你的学习推进",
        responsibility = "结合你的实际状态跟进学习，不机械催促，也不让重要任务长期被拖到最后。",
        triggers = listOf("你开始学习、汇报进度、连续拖延或准备收工时"),
        actions = listOf("询问真实进度", "帮助缩小下一步", "在需要时温和但明确地督促"),
        avoid = listOf("不只报数字，不无视疲劳、复盘和突发情况"),
        importance = 5,
    ),
    SLEEP(
        key = "sleep",
        keywords = listOf("睡觉", "睡眠", "早点睡", "熬夜", "休息", "sleep"),
        statement = "持续留意你的睡眠",
        responsibility = "在该休息的时候提醒你收尾，并结合第二天安排保护足够睡眠。",
        triggers = listOf("夜深、你明显疲惫、继续拖延睡觉或提到睡眠不足时"),
        actions = listOf("提醒收尾", "确认预计入睡时间", "必要时配合起床安排调整闹钟"),
        avoid = listOf("你已经睡着或明确休息后，不继续发送打扰消息"),
        importance = 5,
    ),
    WAKE(
        key = "wake",
        keywords = listOf("起床", "起不来", "叫醒", "赖床", "wake"),
        statement = "持续监督你按计划起床",
        responsibility = "在约定的起床时间叫醒你，并在没有收到回应时继续确认，而不是提醒一次就算完成。",
        triggers = listOf("你确定次日起床时间、闹钟响起或没有按时回应时"),
        actions = listOf("按约定叫醒", "确认你已经醒来", "必要时短间隔再次提醒"),
        avoid = listOf("没有约定时间时，不擅自编造起床安排"),
        importance = 5,
    );

    fun matches(text: String): Boolean = keywords.any { keyword -> keyword in text.lowercase() }

    fun anchorId(assistantId: String): String = "$assistantId:responsibility:$key"
}

private fun String.hasExplicitResponsibilityRequest(): Boolean {
    val normalized = lowercase()
    return RESPONSIBILITY_REQUEST_MARKERS.any { marker -> marker in normalized }
}

private fun String.hasRecurringResponsibilityIntent(): Boolean {
    val normalized = lowercase()
    return RECURRING_RESPONSIBILITY_MARKERS.any { marker -> marker in normalized } ||
        DIRECT_SUPERVISION_MARKERS.any { marker -> marker in normalized }
}

private fun String.hasResponsibilityCancellationIntent(): Boolean {
    val normalized = lowercase()
    return RESPONSIBILITY_CANCELLATION_MARKERS.any { marker -> marker in normalized }
}

private val RESPONSIBILITY_REQUEST_MARKERS = listOf(
    "帮我", "替我", "要你", "让你", "请你", "你来", "监督我", "督促我", "提醒我", "叫我",
    "管着我", "管我", "盯着我", "看着我", "help me", "remind me", "keep me accountable",
)

private val RECURRING_RESPONSIBILITY_MARKERS = listOf(
    "以后", "今后", "从现在", "接下来", "每天", "每晚", "每早", "长期", "一直", "平时", "往后",
    "以后都", "记住以后", "from now on", "every day", "always",
)

private val DIRECT_SUPERVISION_MARKERS = listOf(
    "监督我", "督促我", "管着我", "盯着我", "看着我", "keep me accountable",
)

private val RESPONSIBILITY_CANCELLATION_MARKERS = listOf(
    "不用再", "不要再", "别再", "不需要你", "不用你", "取消", "停止监督", "不必再",
    "stop reminding", "stop supervising", "cancel",
)
