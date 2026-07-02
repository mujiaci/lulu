package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.LuluState
import me.rerere.rikkahub.data.model.LuluThought
import me.rerere.rikkahub.data.model.buildLuluExpressionPlan
import me.rerere.rikkahub.data.model.buildLuluPerception
import me.rerere.rikkahub.data.model.currentProjectedLuluState
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
        assistantName = ctx.assistant.name,
        states = ctx.settings.luluStates,
        thoughts = ctx.settings.luluThoughts,
    )
}

internal fun applyLuluStateContext(
    messages: List<UIMessage>,
    assistantId: Uuid,
    assistantName: String = "露露",
    states: List<LuluState>,
    thoughts: List<LuluThought> = emptyList(),
    nowMillis: Long = System.currentTimeMillis(),
): List<UIMessage> {
    if (messages.isEmpty()) return messages
    if (states.none { it.assistantId == assistantId }) return messages

    val state = states.currentProjectedLuluState(assistantId, nowMillis)
    val lastUserIndex = messages.indexOfLast { it.role == MessageRole.USER }
    val latestUserText = messages.getOrNull(lastUserIndex)?.toText().orEmpty()
    val contextMessage = UIMessage.system(
        buildLuluPresenceContext(
            state = state,
            userText = latestUserText,
            assistantName = assistantName,
            thoughts = thoughts.thoughtHistory(assistantId),
        )
    )
    val insertIndex = if (lastUserIndex >= 0) lastUserIndex else messages.size

    return messages.take(insertIndex) + contextMessage + messages.drop(insertIndex)
}

internal fun buildLuluPresenceContext(
    state: LuluState,
    userText: String,
    assistantName: String = "露露",
    thoughts: List<LuluThought> = emptyList(),
): String = buildString {
    val name = assistantName.ifBlank { "当前角色" }
    val perception = buildLuluPerception(userText)
    val perceptionSummary = state.perceptionSummary.ifBlank { perception.summary }
    val expression = buildLuluExpressionPlan(state, reply = userText)
    appendLine("<lulu_presence>")
    appendLine("这是$name 此刻的内在状态、感知、推测和未说出口的想法，只作为角色参考。不要机械复述这些内容。")
    appendLine("未说出口只用于影响语气和选择：它必须是$name 选择藏在心里的判断、顾虑、靠近冲动或暂时压住的动作；最终回复仍只输出自然说出口的话。")
    appendLine("当前状态：${state.statusText}")
    appendLine("没说出口：${state.innerVoice}")
    appendLine("${name}自己的场景：${state.selfScene}")
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
    appendLine("状态描写建议：请把当前状态、行为、动作、姿势和标签合成一小段自然描述，交给下方状态栏呈现；正文回复不要再用括号塞动作。")
    appendLine("可参考素材：表情=${expression.emojiHint.ifBlank { "无" }}；动作=${expression.stickerHint}；姿势=${expression.bodyGestureHint}。")
    appendLine("使用方式：状态描写只作为情绪和表现参考；不要输出 XML、字段名、提示词说明或头像氛围。")
    appendLine("如可用 set_lulu_expression_state 工具，请在同一轮回复里顺手记录 description，并尽量填写 inner_voice/thought：用角色第一人称写没有说出口的想法，不要写字段名、提示词或工具说明。")
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
