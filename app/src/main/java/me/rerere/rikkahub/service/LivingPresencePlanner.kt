package me.rerere.rikkahub.service

import java.util.concurrent.TimeUnit

data class LivingPresenceInput(
    val assistantName: String,
    val userText: String,
    val assistantText: String,
    val preferredToolNames: List<String> = emptyList(),
)

enum class LivingPresenceAction {
    MESSAGE,
    WAIT,
    TOOL_USE,
    SET_ALARM,
    WRITE_DIARY,
    SCHEDULE_NEXT_PERCEPTION,
    READ,
    ASK_USER,
    PASS,
}

enum class LivingPresenceConsolidationHint {
    WRITE_JOURNAL,
    READ_BOOK,
    MEMORY_REFLECT,
}

enum class LivingPresenceExpressionAffordance {
    TEXT,
    KAOMOJI,
    STICKER,
    VOICE,
    STATUS_BAR,
    LIGHT_REMINDER,
    LONG_EXPLANATION,
    SILENT_RECORD,
}

object LivingPresencePlanner {
    fun planRollingJudgments(
        input: LivingPresenceInput,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<ProactiveReminderPlan> {
        if (input.userText.isBlank() && input.assistantText.isBlank()) return emptyList()
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = input.assistantName,
            userText = input.userText,
            assistantText = input.assistantText,
            nowMillis = nowMillis,
        )
        if (intent.kind == LivingIntentKind.ORDINARY_SILENCE) {
            return emptyList()
        }
        return intent.perceptionCadence.delaysMinutes.take(1).mapIndexed { index, delay ->
            ProactiveReminderPlan(
                triggerAtMillis = nowMillis + TimeUnit.MINUTES.toMillis(delay),
                kind = intent.kind.toReminderKind(),
                reason = buildReason(intent, index),
                userText = input.userText.take(160),
                preferredToolNames = preferredTools(intent.kind, input.preferredToolNames),
                actionHints = buildActionHints(intent.kind),
            )
        }.take(5)
    }

    private fun buildReason(intent: LivingIntent, index: Int): String = buildString {
        append("活人感动态感知#${index + 1}：感知世界包-意义评估-动态判断-行动实现-状态生成-辞海记忆。")
        append("事件=${intent.concernEvent}。目标=${intent.concernGoal}。")
        append("感知层必须先装入角色人设、上下文、未总结聊天、辞海、挂心任务、工具状态、工具结果、向量记忆和上一轮状态栏。")
        append("意义评估 Appraisal=${intent.appraisal.meaning}；风险=${intent.appraisal.risk}；价值=${intent.appraisal.value}；成本=${intent.appraisal.cost}；资源=${intent.appraisal.resources}.")
        append("判断层 intention=${intent.intention}；是否开口、是否查工具、是否等待、是否写辞海、下一次什么时候感知，都必须根据本轮感知和人设重新决定。")
        append("行动池 includes MESSAGE, WAIT, TOOL_USE, SET_ALARM, WRITE_DIARY, SCHEDULE_NEXT_PERCEPTION, READ, ASK_USER, PASS；记忆沉淀由辞海/聊天阈值自动触发，不作为模型动作。")
        append("状态栏只生成心情、身体、精神、亲密和第一人称没说出口；不要把 belief/motive/intention 当成状态栏展示。")
        append("Consolidation=${intent.consolidation.episodicTrace} / ${intent.consolidation.policyLearning}.")
        append("Hypotheses: ${intent.hypotheses.joinToString(" / ")}.")
        append("到点后必须重新从感知开始，不要把这段 reason 当成预写消息。")
    }

    private fun buildActionHints(kind: LivingIntentKind): List<ProactiveActionHint> = buildList {
        add(
            ProactiveActionHint(
                toolName = LivingPresenceConsolidationHint.WRITE_JOURNAL.name,
                reason = "如果不适合打扰用户，把这次未说出口的担心、克制和判断写入辞海心迹，并进入向量记忆。",
            )
        )
        add(
            ProactiveActionHint(
                toolName = LivingPresenceConsolidationHint.READ_BOOK.name,
                reason = "如果用户持续沉默且没有紧急风险，可以阅读辞海里用户交给角色的材料，留下阅读感悟并进入记忆。",
            )
        )
        add(
            ProactiveActionHint(
                toolName = LivingPresenceConsolidationHint.MEMORY_REFLECT.name,
                reason = "把本轮感知、意义评估、审议判断、辞海记录、向量记忆和图谱记忆整理成沉淀，供下一轮判断复用。",
            )
        )
        if (kind == LivingIntentKind.HEALTH_SAFETY) {
            add(
                ProactiveActionHint(
                toolName = LivingPresenceAction.TOOL_USE.name,
                    reason = "身体不适场景优先查看健康、电量、应用或位置线索，再决定是否发消息。",
                )
            )
        }
    }

    private fun LivingIntentKind.toReminderKind(): ProactiveReminderKind = when (this) {
        LivingIntentKind.STUDY_FOCUS -> ProactiveReminderKind.STUDY
        LivingIntentKind.DEADLINE, LivingIntentKind.WAKE_UP -> ProactiveReminderKind.SCHEDULE
        LivingIntentKind.HEALTH_SAFETY, LivingIntentKind.ORDINARY_SILENCE -> ProactiveReminderKind.GENERAL
    }

    private fun preferredTools(kind: LivingIntentKind, seed: List<String>): List<String> {
        val base = when (kind) {
            LivingIntentKind.HEALTH_SAFETY -> listOf("get_gadgetbridge_data", "get_battery_info", "get_app_usage", "get_location")
            LivingIntentKind.STUDY_FOCUS -> listOf("get_app_usage", "control_music", "get_battery_info")
            LivingIntentKind.DEADLINE -> listOf("get_time_info", "calendar_tool", "get_app_usage", "get_battery_info")
            LivingIntentKind.WAKE_UP -> listOf("get_time_info", "set_alarm", "get_gadgetbridge_data", "get_app_usage", "get_battery_info")
            LivingIntentKind.ORDINARY_SILENCE -> listOf("get_app_usage", "get_battery_info")
        }
        return (seed + base).distinct().take(5)
    }
}
