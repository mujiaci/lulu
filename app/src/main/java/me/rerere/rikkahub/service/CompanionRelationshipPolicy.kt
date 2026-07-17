package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.companion.CompanionRelationshipState

/**
 * Deterministic relationship gates applied after model planning. Prompt guidance shapes tone;
 * these rules prevent low-trust or tense states from silently authorizing intrusive actions.
 */
internal fun CompanionIntentDecision.enforceRelationshipPolicy(
    relationship: CompanionRelationshipState,
): CompanionIntentDecision {
    if (relationship.unresolvedTension >= HIGH_TENSION && intent == CompanionIntent.REACH_OUT) {
        return copy(
            intent = CompanionIntent.WAIT,
            shouldMessageNow = false,
            delayMinutes = null,
            toolNames = emptyList(),
            reason = "Relationship tension is high, so repair waits for relevant user contact instead of an unsolicited reach-out.",
            followUps = emptyList(),
            actionToolName = null,
            actionArgumentsJson = "{}",
        )
    }

    val filteredTools = toolNames.filter { relationship.allowsUnpromptedTool(it) }
    val filteredAction = actionToolName?.takeIf { relationship.allowsAutonomousAction(it) }
    val validIntent = if (intent == CompanionIntent.SELF_ACTIVITY && filteredAction == null) {
        CompanionIntent.WAIT
    } else {
        intent
    }
    return copy(
        intent = validIntent,
        shouldMessageNow = shouldMessageNow && validIntent != CompanionIntent.WAIT,
        delayMinutes = delayMinutes.takeIf { validIntent != CompanionIntent.WAIT },
        toolNames = filteredTools,
        followUps = followUps.takeUnless {
            relationship.unresolvedTension >= HIGH_TENSION || relationship.reliability < LOW_RELIABILITY
        }.orEmpty(),
        actionToolName = filteredAction,
        actionArgumentsJson = actionArgumentsJson.takeIf { filteredAction != null } ?: "{}",
    )
}

internal fun CompanionChatTurnPlan.enforceRelationshipPolicy(
    relationship: CompanionRelationshipState,
    latestUserText: String,
): CompanionChatTurnPlan {
    val userExplicitlyRequestedFollowUp = latestUserText.hasAny(
        "提醒", "叫我", "闹钟", "等会", "过会", "稍后", "明天", "以后", "跟进", "remind", "alarm",
    )
    val filteredAffordances = if (relationship.unresolvedTension >= HIGH_TENSION) {
        expressionAffordances.filter { affordance ->
            when (affordance) {
                CompanionExpressionAffordance.STICKER -> latestUserText.hasAny("表情", "贴纸", "sticker")
                CompanionExpressionAffordance.VOICE -> latestUserText.hasAny("语音", "说给我听", "voice")
                else -> true
            }
        }
    } else {
        expressionAffordances
    }
    val keepModelFollowUps = userExplicitlyRequestedFollowUp || (
        relationship.unresolvedTension < HIGH_TENSION && relationship.reliability >= LOW_RELIABILITY
    )
    return copy(
        toolRequests = toolRequests.filter { request ->
            relationship.allowsUnpromptedTool(request.toolName) ||
                latestUserText.explicitlyRequestsTool(request.toolName)
        },
        followUpDelayMinutes = followUpDelayMinutes.takeIf { keepModelFollowUps },
        followUpReason = followUpReason.takeIf { keepModelFollowUps },
        followUps = followUps.takeIf { keepModelFollowUps }.orEmpty(),
        expressionAffordances = filteredAffordances,
    )
}

private fun CompanionRelationshipState.allowsUnpromptedTool(toolName: String): Boolean {
    if (unresolvedTension >= HIGH_TENSION && toolName in USER_AFFECTING_TOOLS) return false
    if (trust < LOW_TRUST && toolName in USER_AFFECTING_TOOLS) return false
    if (boundaryConfidence < LOW_BOUNDARY_CONFIDENCE && toolName in INTRUSIVE_OBSERVATION_TOOLS) return false
    return true
}

private fun CompanionRelationshipState.allowsAutonomousAction(toolName: String): Boolean {
    if (toolName in SELF_CONTAINED_DIGITAL_LIFE_TOOLS) return true
    return allowsUnpromptedTool(toolName)
}

private fun String.explicitlyRequestsTool(toolName: String): Boolean = when (toolName) {
    "set_alarm" -> hasAny("闹钟", "提醒我", "叫我", "alarm", "remind")
    "calendar_tool" -> hasAny("日历", "日程", "行程", "calendar")
    "camera_capture" -> hasAny("摄像头", "相机", "拍照", "看看周围", "camera")
    "read_sms" -> hasAny("短信", "sms")
    "get_notifications" -> hasAny("通知", "notification")
    "clipboard_tool" -> hasAny("剪贴板", "clipboard")
    "get_location", "explore_nearby" -> hasAny("我在哪", "位置", "地址", "定位", "附近", "location")
    "get_app_usage" -> hasAny("应用使用", "屏幕使用", "使用时长", "screen time", "app usage")
    "get_gadgetbridge_data" -> hasAny("睡眠", "健康", "步数", "心率", "运动", "sleep", "health")
    "control_music" -> hasAny("音乐", "播放", "暂停", "下一首", "music")
    "write_lulu_journal" -> hasAny("日记", "辞海", "journal")
    "write_files" -> hasAny("写文件", "保存文件", "文件", "file")
    "eval_javascript" -> hasAny("javascript", "js", "执行代码")
    else -> false
}

private fun String.hasAny(vararg markers: String): Boolean {
    val normalized = lowercase()
    return markers.any { marker -> marker.lowercase() in normalized }
}

private val INTRUSIVE_OBSERVATION_TOOLS = setOf(
    "camera_capture",
    "read_sms",
    "get_notifications",
    "clipboard_tool",
    "get_location",
    "explore_nearby",
    "get_app_usage",
    "get_gadgetbridge_data",
)

private val USER_AFFECTING_TOOLS = INTRUSIVE_OBSERVATION_TOOLS + setOf(
    "set_alarm",
    "calendar_tool",
    "control_music",
    "write_files",
    "eval_javascript",
)

private val SELF_CONTAINED_DIGITAL_LIFE_TOOLS = setOf(
    "play_companion_game",
    "write_lulu_journal",
)

private const val HIGH_TENSION = 0.6f
private const val LOW_TRUST = 0.4f
private const val LOW_RELIABILITY = 0.5f
private const val LOW_BOUNDARY_CONFIDENCE = 0.45f
