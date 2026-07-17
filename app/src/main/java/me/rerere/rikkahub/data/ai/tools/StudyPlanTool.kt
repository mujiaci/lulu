package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.study.ExamStudyPlan
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.data.study.StudySleepHabit
import me.rerere.rikkahub.data.study.StudyState
import me.rerere.rikkahub.data.study.StudyStore
import me.rerere.rikkahub.data.study.StudyTaskSource
import org.koin.core.context.GlobalContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

fun createTodayStudyPlanTool(
    assistantId: String? = null,
    assistantName: String = "",
): Tool = Tool(
    name = "today_study_plan",
    description = """
        Read or update today's app-local study state. Use action=set_completion only after the user explicitly reports
        task completion. Use action=claim_sleep_reward only after the character has judged the user's early-sleep or
        early-rise report credible from the current time and conversation. Do not grant merely because the user asks;
        obtain the reported clock time or derive it from available sleep/app-usage evidence, ask a follow-up when it
        is missing, and refuse obvious contradictions. The user's personal baseline is sleep by about 01:30 and wake
        by about 09:30. Each reward is idempotent per day.
        Use this for 考研计划, 今日计划, 待办, 番茄钟, 作息, 早睡, 早起, 夸夸值 and rewards.
        Only existing tasks can change; this tool never creates or deletes tasks. Do not use calendar_tool.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("action") {
                    put("type", JsonPrimitive("string"))
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("read"))
                        add(JsonPrimitive("set_completion"))
                        add(JsonPrimitive("claim_sleep_reward"))
                    })
                    put(
                        "description",
                        JsonPrimitive("Read state, update completion, or grant a self-reported sleep reward."),
                    )
                }
                putJsonObject("sleep_habit") {
                    put("type", JsonPrimitive("string"))
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("early_sleep"))
                        add(JsonPrimitive("early_rise"))
                    })
                    put(
                        "description",
                        JsonPrimitive("early_sleep means last night's bedtime; early_rise means today's wake-up."),
                    )
                }
                putJsonObject("decision_reason") {
                    put("type", JsonPrimitive("string"))
                    put(
                        "description",
                        JsonPrimitive("A short in-character reason why the report is credible enough to reward."),
                    )
                }
                putJsonObject("reported_hour") {
                    put("type", JsonPrimitive("integer"))
                    put("minimum", JsonPrimitive(0))
                    put("maximum", JsonPrimitive(23))
                    put("description", JsonPrimitive("The reported local hour of sleeping or waking."))
                }
                putJsonObject("reported_minute") {
                    put("type", JsonPrimitive("integer"))
                    put("minimum", JsonPrimitive(0))
                    put("maximum", JsonPrimitive(59))
                    put("description", JsonPrimitive("The reported minute of sleeping or waking."))
                }
                putJsonObject("complete_task_ids") {
                    put("type", JsonPrimitive("array"))
                    putJsonObject("items") { put("type", JsonPrimitive("string")) }
                }
                putJsonObject("unfinished_task_ids") {
                    put("type", JsonPrimitive("array"))
                    putJsonObject("items") { put("type", JsonPrimitive("string")) }
                }
                putJsonObject("complete_all_except_titles") {
                    put("type", JsonPrimitive("array"))
                    putJsonObject("items") { put("type", JsonPrimitive("string")) }
                    put("description", JsonPrimitive("Complete all existing tasks except fuzzy title matches here."))
                }
            },
        )
    },
    execute = { args ->
        val store = GlobalContext.get().get<StudyStore>()
        val params = args.jsonObject
        val action = params["action"]?.jsonPrimitive?.contentOrNull ?: "read"
        if (action == "set_completion") {
            var changedIds = emptySet<String>()
            var updatedState: StudyState? = null
            store.update { current ->
                val result = updateTodayStudyTaskCompletion(
                    state = current,
                    completeTaskIds = params.stringSet("complete_task_ids"),
                    unfinishedTaskIds = params.stringSet("unfinished_task_ids"),
                    completeAllExceptTitles = params.stringSet("complete_all_except_titles"),
                )
                changedIds = result.changedTaskIds
                updatedState = result.state
                result.state
            }
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true)
                put("changed_count", changedIds.size)
                put("changed_task_ids", buildJsonArray { changedIds.forEach { add(it) } })
                put("state", buildTodayStudyPlanPayload(updatedState ?: store.state.first()))
            }.toString()))
        } else if (action == "claim_sleep_reward") {
            val habit = when (params["sleep_habit"]?.jsonPrimitive?.contentOrNull) {
                "early_sleep" -> StudySleepHabit.EarlySleep
                "early_rise" -> StudySleepHabit.EarlyRise
                else -> null
            }
            if (habit == null) {
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", false)
                    put("error", "sleep_habit must be early_sleep or early_rise")
                }.toString()))
            } else {
                var blockedByCompanion = false
                var alreadyClaimed = false
                var granted = false
                var rewardKudos = 0
                var rewardTenDrawTickets = 0
                var resultReason = ""
                var updatedState: StudyState? = null
                store.update { current ->
                    val selectedAssistantId = current.selectedAssistantId
                    if (
                        assistantId != null &&
                        selectedAssistantId != null &&
                        assistantId != selectedAssistantId
                    ) {
                        blockedByCompanion = true
                        updatedState = current
                        current
                    } else {
                        val result = StudyRules.claimSleepHabitReward(
                            state = current,
                            habit = habit,
                            assistantName = assistantName,
                            decisionReason = params["decision_reason"]
                                ?.jsonPrimitive
                                ?.contentOrNull
                                .orEmpty(),
                            reportedTime = params.reportedSleepClockTime(),
                        )
                        alreadyClaimed = result.alreadyClaimed
                        granted = result.granted
                        rewardKudos = result.reward.kudos
                        rewardTenDrawTickets = result.reward.tenDrawTickets
                        resultReason = result.reason
                        updatedState = result.state
                        result.state
                    }
                }
                val finalState = updatedState ?: store.state.first()
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", !blockedByCompanion)
                    put("granted", !blockedByCompanion && granted)
                    put("already_claimed_today", alreadyClaimed)
                    if (blockedByCompanion) {
                        put("error", "Only the character selected as today's study companion can grant this reward.")
                    }
                    put("reason", resultReason)
                    put("reward", buildJsonObject {
                        put("kudos", rewardKudos)
                        put("ten_draw_tickets", rewardTenDrawTickets)
                    })
                    put("state", buildTodayStudyPlanPayload(finalState))
                }.toString()))
            }
        } else {
            val state = store.state.first()
            listOf(UIMessagePart.Text(buildTodayStudyPlanPayload(state).toString()))
        }
    },
)

internal data class StudyTaskCompletionUpdate(
    val state: StudyState,
    val changedTaskIds: Set<String>,
)

internal fun updateTodayStudyTaskCompletion(
    state: StudyState,
    completeTaskIds: Set<String> = emptySet(),
    unfinishedTaskIds: Set<String> = emptySet(),
    completeAllExceptTitles: Set<String> = emptySet(),
    nowMillis: Long = System.currentTimeMillis(),
): StudyTaskCompletionUpdate {
    val normalizedExceptions = completeAllExceptTitles
        .map(String::normalizedTaskTitle)
        .filter { it.length >= 2 }
        .distinct()
    val exceptionTaskIds = matchTaskTitleQueries(state, normalizedExceptions.toSet())
    val completeAllExcept = normalizedExceptions.isNotEmpty() && exceptionTaskIds.size == normalizedExceptions.size
    var next = state
    val changed = linkedSetOf<String>()
    state.tasks.forEach { task ->
        val keepUnfinished = task.id in exceptionTaskIds
        val requestedDone = when {
            task.id in unfinishedTaskIds || keepUnfinished -> false
            task.id in completeTaskIds || completeAllExcept -> true
            else -> return@forEach
        }
        if (task.done == requestedDone) return@forEach
        next = StudyRules.toggleTask(next, task.id, requestedDone, nowMillis).state
        changed += task.id
    }
    return StudyTaskCompletionUpdate(next, changed)
}

private fun matchTaskTitleQueries(state: StudyState, queries: Set<String>): Set<String> = queries
    .map(String::normalizedTaskTitle)
    .filter { it.length >= 2 }
    .mapNotNull { query ->
        val exact = state.tasks.firstOrNull { it.title.normalizedTaskTitle() == query }
        if (exact != null) return@mapNotNull exact.id
        state.tasks
            .mapNotNull { task ->
                val title = task.title.normalizedTaskTitle()
                if (query !in title && title !in query) return@mapNotNull null
                task.id to kotlin.math.abs(title.length - query.length)
            }
            .sortedBy { it.second }
            .let { matches ->
                val best = matches.firstOrNull() ?: return@let null
                best.first.takeIf { matches.count { it.second == best.second } == 1 }
            }
    }
    .toSet()

private fun JsonObject.stringSet(key: String): Set<String> = this[key]
    ?.jsonArray
    ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
    ?.toSet()
    .orEmpty()

private fun JsonObject.reportedSleepClockTime(): LocalTime? {
    val hour = this["reported_hour"]?.jsonPrimitive?.intOrNull ?: return null
    val minute = this["reported_minute"]?.jsonPrimitive?.intOrNull ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return LocalTime.of(hour, minute)
}

private fun String.normalizedTaskTitle(): String = lowercase()
    .replace(Regex("[\\p{P}\\p{S}\\s]+"), "")

fun buildTodayStudyPlanPayload(
    state: StudyState,
    today: LocalDate = state.today.toLocalDateOrToday(),
): JsonObject {
    val planTasks = state.tasks.filter { it.source == StudyTaskSource.Plan }
    val manualTasks = state.tasks.filter { it.source == StudyTaskSource.Manual }
    val completedTasks = state.tasks.filter { it.done }
    val undoneTasks = state.tasks.filterNot { it.done }
    val schedule = state.generatedSchedules[state.today] ?: ExamStudyPlan.todaySchedule(today)
    val level = StudyRules.currentLevel(state)
    val timeOverview = StudyRules.studyTimeOverview(state, today)
    return buildJsonObject {
        put("success", true)
        put("source", "study_app_local_store")
        put("instruction", "This is the authoritative app-local study plan. Completed/checked-off tasks are already_done and must not be treated as unfinished.")
        put("date", state.today.ifBlank { today.toString() })
        put("plan_tasks_done", planTasks.count { it.done })
        put("plan_tasks_total", planTasks.size)
        put("manual_tasks_done", manualTasks.count { it.done })
        put("manual_tasks_total", manualTasks.size)
        put("pomodoros_today", timeOverview.todayPomodoros)
        put("study_minutes_today", timeOverview.todayMinutes)
        put("pomodoros_this_week", timeOverview.weekPomodoros)
        put("study_minutes_this_week", timeOverview.weekMinutes)
        put("pomodoros_total", state.stats.totalPomodoros)
        put("study_minutes_total", state.stats.totalStudyMinutes)
        put("kudos", state.wallet.kudos)
        put("total_kudos_earned", state.wallet.totalKudosEarned)
        put("level", level.level)
        put("level_title", level.title)
        put("sleep_habits", buildJsonObject {
            put(
                "early_sleep_claimed_today",
                StudyRules.hasClaimedSleepHabitReward(state, StudySleepHabit.EarlySleep, today),
            )
            put("early_sleep_reward_kudos", StudyRules.EARLY_SLEEP_KUDOS)
            put("early_sleep_personal_cutoff", "01:30")
            put(
                "early_rise_claimed_today",
                StudyRules.hasClaimedSleepHabitReward(state, StudySleepHabit.EarlyRise, today),
            )
            put("early_rise_reward_ten_draw_tickets", StudyRules.EARLY_RISE_TEN_DRAW_TICKETS)
            put("early_rise_personal_cutoff", "09:30")
            put(
                "instruction",
                "The character decides whether the report is credible from time and conversation. " +
                    "Do not grant just because the user asks; ask when ambiguous and refuse contradictions. " +
                    "Each habit can be granted once per day.",
            )
        })
        put("already_done", buildJsonArray {
            completedTasks.take(20).forEach { task ->
                add(buildJsonObject {
                    put("id", task.id)
                    put("title", task.title)
                    put("source", task.source.name)
                    task.completedAt?.let { put("completed_at", it) }
                })
            }
        })
        put("undone_tasks", buildJsonArray {
            undoneTasks.take(30).forEach { task ->
                add(buildJsonObject {
                    put("id", task.id)
                    put("title", task.title)
                    put("source", task.source.name)
                })
            }
        })
        put("today_schedule", buildJsonArray {
            schedule.take(16).forEach { block ->
                add(buildJsonObject {
                    put("time", block.time)
                    put("title", block.title)
                    put("detail", block.detail)
                })
            }
        })
        put("human_summary", buildString {
            append("今日学习状态：计划待办 ${planTasks.count { it.done }}/${planTasks.size}，")
            append("手动待办 ${manualTasks.count { it.done }}/${manualTasks.size}，")
            append("今日番茄 ${timeOverview.todayPomodoros} 个/${timeOverview.todayMinutes} 分钟，")
            append("本周番茄 ${timeOverview.weekPomodoros} 个/${timeOverview.weekMinutes} 分钟，")
            append("累计番茄 ${state.stats.totalPomodoros} 个，累计学习 ${state.stats.totalStudyMinutes} 分钟。")
            if (undoneTasks.isNotEmpty()) {
                append("未完成：")
                append(undoneTasks.take(6).joinToString("；") { it.title })
                append("。")
            } else if (state.tasks.isNotEmpty()) {
                append("今日待办已全部完成。")
            }
        })
    }
}

private fun String.toLocalDateOrToday(): LocalDate =
    try {
        takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: LocalDate.now()
    } catch (_: DateTimeParseException) {
        LocalDate.now()
    }
