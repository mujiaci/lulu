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
        appendLine("最终架构名：感知世界包-意义评估-动态判断-行动实现-状态生成-辞海记忆。")
        appendLine("感知 Perception 是本轮完整世界包，不是摘要：角色人设、角色规则、当前时间、对话上下文、未总结上下文、活跃挂心、最近辞海、未总结辞海、向量记忆召回、上一轮状态栏、工具可用状态、工具结果都必须先进入感知。")
        appendLine("后续每一层都必须能回看完整感知包；不要只吃上一层压缩后的几行结论。角色的一切判断、行动、沉默、回复和状态栏都必须按照人设和上下文来。")
        appendLine("意义评估 Appraisal 只理解重量：对用户意味着什么、对角色自己意味着什么、重要性、紧急性、风险、情绪读法、关系意味、缺失信息和把握度。它不直接选择行动。")
        appendLine("动态判断 Judgment 决定 intention、是否需要同步查询工具、实际动作、是否沉默、是否更新挂心、nextPerceptionDelayMinutes。下一轮到点后必须重新从感知开始。")
        appendLine("工具链路分清：同步查询工具的结果补入本轮上下文后重新评估/判断；执行型工具失败要回到判断层补救；异步工具结果才触发下一轮感知。")
        appendLine("行动实现 Action 只执行判断结果：MESSAGE, WAIT, TOOL_USE, SET_ALARM, WRITE_DIARY, SCHEDULE_NEXT_PERCEPTION, READ, ASK_USER, PASS。WRITE_DIARY 是兼容旧字段名，只表示写辞海心迹，不写入露露日记；记忆提取不作为模型动作，辞海和聊天达到阈值后自动总结入向量记忆。")
        appendLine("状态生成在行动后发生，只生成 mood/bodyState/mindState/relationship/innerThought。innerThought 必须是角色第一人称没说出口的心理活动。")
        appendLine("辞海保存挂心任务、第一人称心迹和沉淀；辞海心迹/行动不计入露露日记。挂心任务展示事件、目标、下次感知；不要把 belief、traitMotive、situationalMotive 当成用户可见状态栏。")
        appendLine("行动池只能从 MESSAGE, WAIT, TOOL_USE, SET_ALARM, WRITE_DIARY, SCHEDULE_NEXT_PERCEPTION, READ, ASK_USER, PASS 中选；旧名 TOOL_CHECK 等同 TOOL_USE，旧名 JOURNAL_WRITE 等同 WRITE_DIARY，旧名 SCHEDULE_NEXT_TICK 等同 SCHEDULE_NEXT_PERCEPTION，MEMORY_UPDATE 不要再输出。")
        appendLine("下一次感知时间不能写死。你必须根据这一次意义评估和动态判断决定 nextPerceptionDelayMinutes；这表示多久后重新从感知层开始，不等于多久后发消息。")
        appendLine("普通无风险沉默不要机械 5 分钟；身体安全/起床/DDL 可以更短，学习或忙碌应更克制。")
        appendLine("thought 必须是第一人称、角色本人没有说出口的一小段心声，会进入状态栏“没说出口”；不要写 Seven-layer trace、Perception=、工具 JSON、字段名或分析提纲。")
        appendLine("只返回 JSON，不要 markdown，不要解释。")
        appendLine("JSON 字段：belief, motive, emotion, intention, thought, action, observation, decision, nextPerceptionDelayMinutes, appraisal, consolidation, historyNote。")
        appendLine("emotion 字段：emotionLabel, feltSense, impulse, restraint, intensity。")
        appendLine("appraisal 字段：meaning, value, risk, cost, consequence, resources。meaning 里必须包含对用户和对角色自己的意义；consolidation 字段：episodicTrace, affectiveResidue, semanticMemory, policyLearning。")
        appendLine("nextPerceptionDelayMinutes 为 1 到 1440 的整数分钟；由角色判断，不要照抄固定表。")
        appendLine("<persona>")
        appendLine(input.persona.take(2400))
        appendLine("</persona>")
        appendLine("<intent>")
        appendLine("kind=${input.intent.kind}")
        appendLine("belief=${input.intent.belief}")
        appendLine("motive=${input.intent.motive}")
        appendLine("intention=${input.intent.intention}")
        appendLine("hypotheses=${input.intent.hypotheses.joinToString(" / ")}")
        appendLine("appraisal=${input.intent.appraisal}")
        appendLine("consolidation=${input.intent.consolidation}")
        appendLine("spokenCount=${input.intent.spokenCount}")
        appendLine("silentEvaluationCount=${input.intent.silentEvaluationCount}")
        appendLine("urgency=${input.intent.urgency}")
        appendLine("restraint=${input.intent.restraint}")
        appendLine("emotion=${input.intent.emotion.emotionLabel}, felt=${input.intent.emotion.feltSense}, impulse=${input.intent.emotion.impulse}, restraint=${input.intent.emotion.restraintText}, intensity=${input.intent.emotion.intensity ?: input.intent.emotion.concern}")
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
            desire = obj.string("motive")?.take(500)?.ifBlank { null }
                ?: obj.string("desire")?.take(500)?.ifBlank { null }
                ?: input.intent.motive,
            intention = obj.string("intention")?.take(500)?.ifBlank { null } ?: input.intent.intention,
            thought = obj.string("thought")?.take(900)?.ifBlank { null } ?: "主 API 已读取 observation，但没有给出可用 thought。",
            action = obj.string("action")?.normalizeActionNames()?.take(240)?.ifBlank { null } ?: "SCHEDULE_NEXT_PERCEPTION",
            observation = obj.string("observation")?.take(900)?.ifBlank { null } ?: input.observation.summary,
            decision = obj.string("decision")?.take(700)?.ifBlank { null } ?: "保留下一轮判断。",
            nextEvaluateDelayMinutes = obj["nextEvaluateDelayMinutes"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 24 * 60),
            nextPerceptionDelayMinutes = obj["nextPerceptionDelayMinutes"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 24 * 60),
            motiveText = obj.string("motive")?.take(500)?.ifBlank { null }
                ?: obj.string("desire")?.take(500)?.ifBlank { null }
                ?: input.intent.motive,
            traitMotive = obj.string("traitMotive")?.take(500)?.ifBlank { null } ?: input.intent.traitMotive,
            situationalMotive = obj.string("situationalMotive")?.take(500)?.ifBlank { null } ?: input.intent.situationalMotive,
            emotion = obj.emotion("emotion", input.intent.emotion),
            appraisal = obj.appraisal("appraisal") ?: input.intent.appraisal,
            consolidation = obj.consolidation("consolidation") ?: input.intent.consolidation,
            historyNote = obj.string("historyNote")?.take(500).orEmpty(),
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

    private fun String.normalizeActionNames(): String =
        replace(Regex("\\bTOOL_CHECK\\b", RegexOption.IGNORE_CASE), "TOOL_USE")
            .replace(Regex("\\bASK_CAPABILITY\\b", RegexOption.IGNORE_CASE), "ASK_USER")
            .replace(Regex("\\bJOURNAL_WRITE\\b", RegexOption.IGNORE_CASE), "WRITE_DIARY")
            .replace(Regex("\\bSCHEDULE_NEXT_TICK\\b", RegexOption.IGNORE_CASE), "SCHEDULE_NEXT_PERCEPTION")
            .replace(Regex("\\bMEMORY_UPDATE\\b", RegexOption.IGNORE_CASE), "WRITE_DIARY")

    private fun JsonObject.appraisal(key: String): MeaningAppraisal? {
        val obj = this[key] as? JsonObject ?: return null
        return MeaningAppraisal(
            meaning = obj.string("meaning")?.take(300).orEmpty(),
            value = obj.string("value")?.take(300).orEmpty(),
            risk = obj.string("risk")?.take(300).orEmpty(),
            cost = obj.string("cost")?.take(300).orEmpty(),
            consequence = obj.string("consequence")?.take(300).orEmpty(),
            resources = obj.string("resources")?.take(300).orEmpty(),
        )
    }

    private fun JsonObject.consolidation(key: String): ConsolidationPlan? {
        val obj = this[key] as? JsonObject ?: return null
        return ConsolidationPlan(
            episodicTrace = obj.string("episodicTrace")?.take(300).orEmpty(),
            affectiveResidue = obj.string("affectiveResidue")?.take(300).orEmpty(),
            semanticMemory = obj.string("semanticMemory")?.take(300).orEmpty(),
            policyLearning = obj.string("policyLearning")?.take(300).orEmpty(),
        )
    }

    private fun JsonObject.emotion(key: String, fallback: EmotionSnapshot): EmotionSnapshot? {
        val obj = this[key] as? JsonObject ?: return null
        val label = obj.string("emotionLabel")?.take(240)?.ifBlank { null } ?: fallback.emotionLabel
        return fallback.copy(
            label = label,
            emotionLabel = label,
            feltSense = obj.string("feltSense")?.take(300).orEmpty().ifBlank { fallback.feltSense },
            impulse = obj.string("impulse")?.take(300).orEmpty().ifBlank { fallback.impulse },
            restraintText = obj.string("restraint")?.take(300).orEmpty().ifBlank { fallback.restraintText },
            intensity = obj["intensity"]?.jsonPrimitive?.intOrNull?.coerceIn(0, 10) ?: fallback.intensity,
        )
    }
}

data class LivingJudgmentModelInput(
    val assistantName: String,
    val persona: String,
    val intent: LivingIntent,
    val observation: LivingObservation,
    val recentConversation: List<String>,
)
