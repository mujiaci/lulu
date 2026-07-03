package me.rerere.rikkahub.data.ai.transformers

import kotlinx.coroutines.flow.first
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.study.StudyStore
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.data.study.StudyTaskSource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object StudyStateTransformer : InputMessageTransformer, KoinComponent {
    private val studyStore: StudyStore by inject()

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val state = studyStore.state.first()
        if (state.selectedAssistantId != ctx.assistant.id.toString()) return messages

        val planTasks = state.tasks.filter { it.source == StudyTaskSource.Plan }
        val manualTasks = state.tasks.filter { it.source == StudyTaskSource.Manual }
        val undoneTasks = state.tasks.filterNot { it.done }
        if (state.tasks.isEmpty() && state.stats.totalPomodoros <= 0 && state.stats.totalStudyMinutes <= 0) {
            return messages
        }
        val level = StudyRules.currentLevel(state)

        val context = buildString {
            appendLine("<study_companion_state>")
            appendLine("你是用户在考研 App 今日页选择的学习陪伴角色。下面是固定学习状态板块，不是聊天记录；不要说你查了日历、工具或系统。")
            appendLine("日期：${state.today.ifBlank { "今天" }}")
            appendLine("计划待办：${planTasks.count { it.done }}/${planTasks.size}")
            appendLine("手动待办：${manualTasks.count { it.done }}/${manualTasks.size}")
            appendLine("累计番茄钟：${state.stats.totalPomodoros} 个；累计学习：${state.stats.totalStudyMinutes} 分钟")
            appendLine("夸夸值：当前 ${state.wallet.kudos}；累计 ${state.wallet.totalKudosEarned}；等级 Lv${level.level} ${level.title}")
            if (undoneTasks.isNotEmpty()) {
                appendLine("未完成待办：")
                undoneTasks.take(8).forEach { task ->
                    appendLine("- ${task.title}")
                }
                appendLine("如果用户问今天还有什么没做，直接根据这里回答；如果主动提醒，要像陪伴而不是系统催办。")
            } else if (state.tasks.isNotEmpty()) {
                appendLine("今日待办已全部完成。")
            }
            append("</study_companion_state>")
        }

        return messages + UIMessage.system(context)
    }
}
