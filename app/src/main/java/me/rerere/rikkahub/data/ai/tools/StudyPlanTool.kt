package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.study.ExamStudyPlan
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.data.study.StudyState
import me.rerere.rikkahub.data.study.StudyStore
import me.rerere.rikkahub.data.study.StudyTaskSource
import org.koin.core.context.GlobalContext
import java.time.LocalDate
import java.time.format.DateTimeParseException

fun createTodayStudyPlanTool(): Tool = Tool(
    name = "today_study_plan",
    description = """
        Read the app's local study plan state for today, including real study-app todos, completed/checked-off todos,
        generated schedule, pomodoro stats, kudos and rewards. Use this for 考研计划, 今日计划, 待办, 番茄钟,
        completed study tasks, and study progress. Do not use calendar_tool for these app-local study records.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(properties = buildJsonObject { })
    },
    execute = {
        val store = GlobalContext.get().get<StudyStore>()
        val state = runBlocking { store.state.first() }
        listOf(UIMessagePart.Text(buildTodayStudyPlanPayload(state).toString()))
    },
)

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
