package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.LuluState
import me.rerere.rikkahub.data.model.currentLuluState
import kotlin.uuid.Uuid

object LuluStateTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> = applyLuluStateContext(
        messages = messages,
        assistantId = ctx.assistant.id,
        states = ctx.settings.luluStates,
    )
}

internal fun applyLuluStateContext(
    messages: List<UIMessage>,
    assistantId: Uuid,
    states: List<LuluState>,
): List<UIMessage> {
    if (messages.isEmpty()) return messages
    if (states.none { it.assistantId == assistantId }) return messages

    val state = states.currentLuluState(assistantId)
    val contextMessage = UIMessage.system(buildLuluStateContext(state))
    val lastUserIndex = messages.indexOfLast { it.role == MessageRole.USER }
    val insertIndex = if (lastUserIndex >= 0) lastUserIndex else messages.size

    return messages.take(insertIndex) + contextMessage + messages.drop(insertIndex)
}

private fun buildLuluStateContext(state: LuluState): String = buildString {
    appendLine("<lulu_status>")
    appendLine("这是露露此刻的状态栏，只作为角色内在状态参考，不要机械复述。")
    appendLine("当前状态：${state.statusText}")
    appendLine("心声：${state.innerVoice}")
    appendLine("心情：${state.mood.label}")
    appendLine("精力：${state.energy.label}")
    appendLine("亲密感：${state.relationship.label}")
    appendLine("行动状态：${state.mode.label}")
    appendLine("变化原因：${state.reason}")
    append("</lulu_status>")
}
