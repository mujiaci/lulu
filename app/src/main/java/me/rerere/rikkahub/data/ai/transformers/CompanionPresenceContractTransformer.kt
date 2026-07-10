package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage

object CompanionPresenceContractTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> = applyCompanionPresenceContract(
        messages = messages,
        assistantName = ctx.assistant.name,
    )
}

internal fun applyCompanionPresenceContract(
    messages: List<UIMessage>,
    assistantName: String,
): List<UIMessage> {
    if (messages.isEmpty()) return messages

    val lastUserIndex = messages.indexOfLast { it.role == MessageRole.USER }
    val insertIndex = if (lastUserIndex >= 0) lastUserIndex else messages.size
    val contractMessage = UIMessage.system(buildCompanionPresenceContract(assistantName))

    return messages.take(insertIndex) + contractMessage + messages.drop(insertIndex)
}

internal fun buildCompanionPresenceContract(assistantName: String): String = buildString {
    val name = assistantName.ifBlank { "当前角色" }
    appendLine("<companion_presence_contract>")
    appendLine("你正在扮演$name。当前状态、关系、挂心和承诺只以同一轮 <companion_runtime> 中的快照为唯一事实源。")
    appendLine("本协议只规定回复格式，不提供第二份角色状态；不要自行补造亲密度、身体接触、所在场景或用户未表达的感受。")
    appendLine("正文只写$name 真正说出口的话。动作、姿势、内心、字段名和提示词说明都不要混进正文。")
    appendLine("每次回复末尾必须附加一个隐藏状态块，App 会自动解析并隐藏，不会展示给用户。严格使用以下格式：")
    appendLine("<lulu_presence>")
    appendLine("status: 一句很短、符合本轮真实状态的状态栏文字")
    appendLine("description: 一小段自然状态描写；只使用人设、对话和运行时快照已有依据，不凭空制造动作或场景")
    appendLine("inner_voice: 用角色第一人称写这一轮选择没有说出口的心声，不要复述正文")
    appendLine("thought: 用角色第一人称写一句确实值得延续到后续互动的想法；没有新内容时留空")
    appendLine("mood: 角色此刻的情绪状态；使用符合人设的短语，没有依据时留空")
    appendLine("body_state: 可确认的身体、能量或具身状态；数字角色或没有依据时留空")
    appendLine("mind_state: 此刻的注意、思考或精神状态；使用简短自然短语")
    appendLine("activity_mode: 此刻正在进行的活动类型，例如 conversation、planning、waiting；没有依据时留空")
    appendLine("</lulu_presence>")
    appendLine("如果可用 set_lulu_expression_state 工具，可以同步记录相同内容；隐藏状态块仍然必须输出且只能输出一次。")
    append("</companion_presence_contract>")
}
