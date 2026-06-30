package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.LuluState
import me.rerere.rikkahub.data.model.LuluThought
import me.rerere.rikkahub.data.model.buildLuluExpressionPlan
import me.rerere.rikkahub.data.model.buildLuluPerception
import me.rerere.rikkahub.data.model.currentLuluState
import me.rerere.rikkahub.data.model.durationMillis
import me.rerere.rikkahub.data.model.thoughtHistory
import kotlin.uuid.Uuid

object LuluStateTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> = applyLuluStateContext(
        messages = messages,
        assistantId = ctx.assistant.id,
        states = ctx.settings.luluStates,
        thoughts = ctx.settings.luluThoughts,
    )
}

internal fun applyLuluStateContext(
    messages: List<UIMessage>,
    assistantId: Uuid,
    states: List<LuluState>,
    thoughts: List<LuluThought> = emptyList(),
): List<UIMessage> {
    if (messages.isEmpty()) return messages
    if (states.none { it.assistantId == assistantId }) return messages

    val state = states.currentLuluState(assistantId)
    val lastUserIndex = messages.indexOfLast { it.role == MessageRole.USER }
    val latestUserText = messages.getOrNull(lastUserIndex)?.toText().orEmpty()
    val contextMessage = UIMessage.system(
        buildLuluPresenceContext(
            state = state,
            userText = latestUserText,
            thoughts = thoughts.thoughtHistory(assistantId),
        )
    )
    val insertIndex = if (lastUserIndex >= 0) lastUserIndex else messages.size

    return messages.take(insertIndex) + contextMessage + messages.drop(insertIndex)
}

internal fun buildLuluPresenceContext(
    state: LuluState,
    userText: String,
    thoughts: List<LuluThought> = emptyList(),
): String = buildString {
    val perception = buildLuluPerception(userText)
    val perceptionSummary = state.perceptionSummary.ifBlank { perception.summary }
    val expression = buildLuluExpressionPlan(state, reply = userText)
    appendLine("<lulu_presence>")
    appendLine("这是露露此刻的内在状态、感知、推测和未说出口的想法，只作为角色参考。不要机械复述这些字段。")
    appendLine("心声用于影响语气和选择：可以体现露露的猜测、顾虑、想靠近但没说出口的话、想做但暂时压住的动作。最终回复仍只输出自然说出口的话。")
    appendLine("当前状态：${state.statusText}")
    appendLine("心声：${state.innerVoice}")
    appendLine("露露自己的场景：${state.selfScene}")
    appendLine("心情：${state.mood.label}（强度 ${state.moodIntensity.formatPresenceIntensity()}）")
    appendLine("精力：${state.energy.label}（强度 ${state.energyIntensity.formatPresenceIntensity()}）")
    appendLine("亲密感：${state.relationship.label}（强度 ${state.relationshipIntensity.formatPresenceIntensity()}）")
    appendLine("行动状态：${state.mode.label}")
    appendLine("状态持续：约 ${state.durationMillis().formatPresenceDuration()}")
    state.reason.takeIf { it.isNotBlank() }?.let {
        appendLine("变化原因：$it")
    }
    appendLine("当前感知：$perceptionSummary")
    if (thoughts.isNotEmpty()) {
        appendLine("未说出口的想法：")
        thoughts.forEach { thought ->
            appendLine("- [${thought.category.label}] ${thought.content}")
        }
    }
    appendLine("表达建议：${expression.guidance}")
    append("</lulu_presence>")
}

private fun Float.formatPresenceIntensity(): String =
    "%.2f".format(coerceIn(0f, 1f))

private fun Long.formatPresenceDuration(): String {
    val minutes = this / 60_000L
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟"
        minutes < 60 * 24 -> "${minutes / 60} 小时"
        else -> "${minutes / (60 * 24)} 天"
    }
}
