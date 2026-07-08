package me.rerere.rikkahub.data.ai.transformers

import kotlinx.coroutines.flow.first
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.study.ExamStudyPlan
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.data.study.StudyState
import me.rerere.rikkahub.data.study.StudyStore
import me.rerere.rikkahub.data.study.StudyTaskSource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDate
import java.time.format.DateTimeParseException

object StudyStateTransformer : InputMessageTransformer, KoinComponent {
    private val studyStore: StudyStore by inject()

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val state = studyStore.state.first()
        if (state.selectedAssistantId != ctx.assistant.id.toString()) return messages

        val today = state.today.toLocalDateOrToday()
        val schedule = state.generatedSchedules[state.today] ?: ExamStudyPlan.todaySchedule(today)
        if (
            state.tasks.isEmpty() &&
            state.stats.totalPomodoros <= 0 &&
            state.stats.totalStudyMinutes <= 0 &&
            schedule.isEmpty()
        ) {
            return messages
        }

        return messages + UIMessage.system(buildStudyCompanionContext(state, today))
    }
}

internal fun buildStudyCompanionContext(
    state: StudyState,
    today: LocalDate = state.today.toLocalDateOrToday(),
): String {
    val planTasks = state.tasks.filter { it.source == StudyTaskSource.Plan }
    val manualTasks = state.tasks.filter { it.source == StudyTaskSource.Manual }
    val completedTasks = state.tasks.filter { it.done }
    val undoneTasks = state.tasks.filterNot { it.done }
    val schedule = state.generatedSchedules[state.today] ?: ExamStudyPlan.todaySchedule(today)
    val level = StudyRules.currentLevel(state)
    val timeOverview = StudyRules.studyTimeOverview(state, today)

    return buildString {
        appendLine("<study_companion_state>")
        appendLine("你是用户在考研 App 今日页选择的学习陪伴角色。下面是固定学习状态板块，不是聊天记录；不要说你查了日历、工具或系统。")
        appendLine("日期：${state.today.ifBlank { today.toString() }}")
        appendLine("计划待办：${planTasks.count { it.done }}/${planTasks.size}")
        appendLine("手动待办：${manualTasks.count { it.done }}/${manualTasks.size}")
        appendLine("今日学习：${timeOverview.todayPomodoros} 个番茄，${timeOverview.todayMinutes} 分钟")
        appendLine("本周学习：${timeOverview.weekPomodoros} 个番茄，${timeOverview.weekMinutes} 分钟")
        appendLine("累计番茄钟：${state.stats.totalPomodoros} 个；累计学习：${state.stats.totalStudyMinutes} 分钟")
        appendLine("夸夸值：当前 ${state.wallet.kudos}；累计 ${state.wallet.totalKudosEarned}；等级 Lv${level.level} ${level.title}")
        if (completedTasks.isNotEmpty()) {
            appendLine("已完成/已划掉待办：")
            completedTasks.take(8).forEach { task ->
                appendLine("- ${task.title}")
            }
            appendLine("这些任务代表用户已经点了打钩并划掉了；不要再把已完成/已划掉的任务当作未完成任务提醒。")
        }
        if (undoneTasks.isNotEmpty()) {
            appendLine("未完成待办：")
            undoneTasks.take(8).forEach { task ->
                appendLine("- ${task.title}")
            }
            appendLine("如果用户问今天还有什么没做，直接根据这里回答；如果主动提醒，要像陪伴而不是系统催办。")
        } else if (state.tasks.isNotEmpty()) {
            appendLine("今日待办已全部完成。")
        }
        if (schedule.isNotEmpty()) {
            appendLine("今日时间表：")
            schedule.take(12).forEach { block ->
                appendLine("${block.time}｜${block.title}｜${block.detail}")
            }
            appendLine("如果用户问今天怎么安排、现在该学什么或计划表是什么，直接参考这张时间表回答。")
        }
        append("</study_companion_state>")
    }
}

private fun String.toLocalDateOrToday(): LocalDate {
    return try {
        takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: LocalDate.now()
    } catch (_: DateTimeParseException) {
        LocalDate.now()
    }
}
