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
    TOOL_CHECK,
    WAIT,
    INNER_THOUGHT,
    WRITE_JOURNAL,
    READ_BOOK,
    MEMORY_REFLECT,
    PLAN_SUPPORT,
    ASK_CAPABILITY,
}

object LivingPresencePlanner {
    fun planRollingJudgments(
        input: LivingPresenceInput,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<ProactiveReminderPlan> {
        val text = "${input.userText}\n${input.assistantText}".lowercase()
        val profile = when {
            text.containsAny(HEALTH_WORDS) -> RollingProfile.Health
            text.containsAny(STUDY_WORDS) -> RollingProfile.Study
            text.containsAny(DEADLINE_WORDS) -> RollingProfile.Deadline
            text.isBlank() -> return emptyList()
            else -> RollingProfile.OrdinarySilence
        }
        return profile.delaysMinutes.mapIndexed { index, delay ->
            ProactiveReminderPlan(
                triggerAtMillis = nowMillis + TimeUnit.MINUTES.toMillis(delay),
                kind = profile.kind,
                reason = buildReason(input, profile, index),
                userText = input.userText.take(160),
                preferredToolNames = profile.preferredTools(input.preferredToolNames),
                actionHints = buildActionHints(profile),
            )
        }.take(5)
    }

    private fun buildReason(
        input: LivingPresenceInput,
        profile: RollingProfile,
        index: Int,
    ): String = buildString {
        append("${input.assistantName.ifBlank { "角色" }}的滚动判断#${index + 1}：")
        append(profile.reason)
        append(" RollingJudgmentLoop: event enters -> BDI beliefs/desires/intentions -> ReAct think/check/decide -> action pool.")
        append(" Action pool includes message, tool check, wait, write journal, read, memory reflection.")
        append(" Cihai records heart trace/action/reading/reflection, then memory keeps raw record, vector memory, graph memory, study state, and schedules the next judgement.")
        append(" 生成时必须重新观察当前状态，不要把这段 reason 当成预写消息。")
    }

    private fun buildActionHints(profile: RollingProfile): List<ProactiveActionHint> = buildList {
        add(
            ProactiveActionHint(
                toolName = LivingPresenceAction.WRITE_JOURNAL.name,
                reason = "如果不适合打扰用户，把这次未说出口的担心、克制和判断写入辞海心迹，并进入向量记忆。",
                autoExecutable = false,
            )
        )
        add(
            ProactiveActionHint(
                toolName = LivingPresenceAction.READ_BOOK.name,
                reason = "如果用户持续沉默且没有紧急风险，可以阅读辞海里用户交给角色的材料，留下阅读感悟并进入记忆。",
                autoExecutable = false,
            )
        )
        add(
            ProactiveActionHint(
                toolName = LivingPresenceAction.MEMORY_REFLECT.name,
                reason = "把本轮 BDI/ReAct 判断、辞海记录、向量记忆和图谱记忆整理成沉淀，供下一轮判断复用。",
                autoExecutable = false,
            )
        )
        if (profile == RollingProfile.Health) {
            add(
                ProactiveActionHint(
                    toolName = LivingPresenceAction.TOOL_CHECK.name,
                    reason = "身体不适场景优先查看健康、电量、应用或位置线索，再决定是否发消息。",
                    autoExecutable = false,
                )
            )
        }
    }

    private fun String.containsAny(words: Set<String>): Boolean = words.any { contains(it) }

    private sealed class RollingProfile(
        val delaysMinutes: List<Long>,
        val kind: ProactiveReminderKind,
        val reason: String,
    ) {
        data object Health : RollingProfile(
            delaysMinutes = listOf(8, 20, 45, 90, 150),
            kind = ProactiveReminderKind.GENERAL,
            reason = "用户表达过身体不舒服，不能只判断一次；需要多次确认安全，同时控制打扰强度。",
        )

        data object Study : RollingProfile(
            delaysMinutes = listOf(30, 60, 120),
            kind = ProactiveReminderKind.STUDY,
            reason = "用户可能在学习或忙任务，应该用低打扰节奏反复判断，优先保护专注。",
        )

        data object Deadline : RollingProfile(
            delaysMinutes = listOf(60, 120, 180),
            kind = ProactiveReminderKind.SCHEDULE,
            reason = "用户提到任务或截止时间，需要像学习监督员一样滚动检查进度。",
        )

        data object OrdinarySilence : RollingProfile(
            delaysMinutes = listOf(10, 25, 60, 120),
            kind = ProactiveReminderKind.GENERAL,
            reason = "用户没有继续回复，但原因未知；需要在关心、克制、等待和自我活动之间反复权衡。",
        )

        fun preferredTools(seed: List<String>): List<String> {
            val base = when (this) {
                Health -> listOf("get_gadgetbridge_data", "get_battery_info", "get_app_usage", "get_location")
                Study -> listOf("get_app_usage", "control_music", "get_battery_info")
                Deadline -> listOf("calendar_tool", "get_app_usage", "get_battery_info")
                OrdinarySilence -> listOf("get_app_usage", "get_battery_info")
            }
            return (seed + base).distinct().take(5)
        }
    }

    private val HEALTH_WORDS = setOf("肚子疼", "肚子痛", "胃疼", "胃痛", "难受", "不舒服", "头疼", "头痛", "疼", "痛")
    private val STUDY_WORDS = setOf("学习", "复习", "背书", "刷题", "写作业", "自习", "看书", "专业课", "考研")
    private val DEADLINE_WORDS = setOf("ddl", "截止", "交", "提交", "今晚", "今天之前", "点前", "之前完成")
}
