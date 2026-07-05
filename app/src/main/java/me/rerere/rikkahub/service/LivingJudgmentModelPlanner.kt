package me.rerere.rikkahub.service

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.utils.JsonInstant

object LivingJudgmentModelPlanner {
    suspend fun planOrNull(
        input: LivingJudgmentModelInput,
        settings: Settings,
        model: Model,
        providerManager: ProviderManager,
    ): LivingJudgmentTrace? {
        val provider = model.findProvider(settings.providers) ?: return null
        val providerImpl = providerManager.getProviderByType(provider)
        val chunk = providerImpl.generateText(
            providerSetting = provider,
            messages = listOf(
                UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text(buildPrompt(input))),
                )
            ),
            params = TextGenerationParams(
                model = model,
                temperature = 0.25f,
                topP = 0.8f,
                maxTokens = 700,
            ),
        )
        return parseTrace(chunk.choices.firstOrNull()?.message?.toText().orEmpty(), input)
    }

    fun buildPrompt(input: LivingJudgmentModelInput): String = buildString {
        appendLine("你是${input.assistantName}的 Living Presence OS 后台主 API 判断器。")
        appendLine("你只做结构化活人感判断，不生成聊天正文。")
        appendLine("判断流程：Perception 先读 observation/工具/记忆事实；Appraisal 评估意义、风险、成本、价值和资源；State 用第一视角更新 belief/desire/intention/emotion；Deliberation 决定现在要不要行动以及做什么；Action Realization 只输出行动类型和下一轮时间。")
        appendLine("必须贴合角色人设：同一事件可以多次判断；每次判断不等于每次发消息。")
        appendLine("先读 observation，再决定 action。工具不可用也是 observation，不能假装知道；工具可用时可以把它当作角色本地感知/行动能力。")
        appendLine("行动池只能从 MESSAGE, TOOL_CHECK, WAIT, INNER_THOUGHT, JOURNAL_WRITE, READ, MEMORY_UPDATE, SCHEDULE_NEXT_TICK, SET_ALARM, ASK_CAPABILITY 中选。")
        appendLine("下一轮判断时间不能写死。你必须根据这一次意义评估和审议判断决定 nextEvaluateDelayMinutes；这表示多久后再次想这件事，不等于多久后发消息。")
        appendLine("普通无风险沉默不要机械 5 分钟；身体安全/起床/DDL 可以更短，学习或忙碌应更克制。")
        appendLine("只返回 JSON，不要 markdown，不要解释。")
        appendLine("JSON 字段：belief, desire, intention, thought, action, observation, decision, nextEvaluateDelayMinutes。")
        appendLine("nextEvaluateDelayMinutes 为 1 到 1440 的整数分钟；由角色判断，不要照抄固定表。")
        appendLine("<persona>")
        appendLine(input.persona.take(2400))
        appendLine("</persona>")
        appendLine("<intent>")
        appendLine("kind=${input.intent.kind}")
        appendLine("belief=${input.intent.belief}")
        appendLine("desire=${input.intent.desire}")
        appendLine("intention=${input.intent.intention}")
        appendLine("hypotheses=${input.intent.hypotheses.joinToString(" / ")}")
        appendLine("spokenCount=${input.intent.spokenCount}")
        appendLine("silentEvaluationCount=${input.intent.silentEvaluationCount}")
        appendLine("urgency=${input.intent.urgency}")
        appendLine("restraint=${input.intent.restraint}")
        appendLine("emotion=${input.intent.emotion.label}, concern=${input.intent.emotion.concern}, attachment=${input.intent.emotion.attachment}")
        appendLine("</intent>")
        appendLine("<observation>")
        appendLine(input.observation.summary.take(1800))
        appendLine("signals=${input.observation.signals.joinToString(", ").take(1000)}")
        appendLine("</observation>")
        appendLine("<recentConversation>")
        input.recentConversation.takeLast(6).forEach { appendLine(it.take(600)) }
        appendLine("</recentConversation>")
    }

    fun parseTrace(rawText: String, input: LivingJudgmentModelInput): LivingJudgmentTrace? {
        val jsonText = rawText.extractJsonPayload()
        val obj = runCatching { JsonInstant.parseToJsonElement(jsonText) as? JsonObject }.getOrNull() ?: return null
        return LivingJudgmentTrace(
            source = LivingJudgmentSource.MAIN_API_STRUCTURED_JUDGMENT,
            belief = obj.string("belief")?.take(500)?.ifBlank { null } ?: input.intent.belief,
            desire = obj.string("desire")?.take(500)?.ifBlank { null } ?: input.intent.desire,
            intention = obj.string("intention")?.take(500)?.ifBlank { null } ?: input.intent.intention,
            thought = obj.string("thought")?.take(900)?.ifBlank { null } ?: "主 API 已读取 observation，但没有给出可用 thought。",
            action = obj.string("action")?.take(240)?.ifBlank { null } ?: "SCHEDULE_NEXT_TICK",
            observation = obj.string("observation")?.take(900)?.ifBlank { null } ?: input.observation.summary,
            decision = obj.string("decision")?.take(700)?.ifBlank { null } ?: "保留下一轮判断。",
            nextEvaluateDelayMinutes = obj["nextEvaluateDelayMinutes"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 24 * 60),
        )
    }

    private fun String.extractJsonPayload(): String {
        val trimmed = trim()
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(trimmed)?.groupValues?.getOrNull(1)
        val source = fenced ?: trimmed
        val start = source.indexOf('{')
        val end = source.lastIndexOf('}')
        return if (start >= 0 && end >= start) source.substring(start, end + 1) else source
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull
}

data class LivingJudgmentModelInput(
    val assistantName: String,
    val persona: String,
    val intent: LivingIntent,
    val observation: LivingObservation,
    val recentConversation: List<String>,
)
