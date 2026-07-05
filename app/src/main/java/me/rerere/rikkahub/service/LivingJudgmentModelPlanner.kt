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
        appendLine("最终架构名：情境感知-意义评估-状态保持-审议决策-行为实现-人格表达-经验沉淀。")
        appendLine("情境感知 Perception 只收集事实：当前时间、对话上下文、工具结果、工具可用状态、考研计划状态、排序后的向量记忆召回、最近状态栏/辞海/历史挂心记录；它不决定意义。")
        appendLine("意义评估 Appraisal 评估重要性、威胁、机会、身体安全、精神安全、时间压力、行动成本、可能收益、不行动后果、可用资源；它接近 Lazarus appraisal theory，是给行动选择提供重量，不是直接选择行动。")
        appendLine("状态保持 State 保存持续心理状态，不重新裁判：belief 是第一视角解释，不是原始感知；traitMotive 是长期人设底层动机；situationalMotive 是本次 concern 为什么在意；emotion 用 emotionLabel/feltSense/impulse/restraint/intensity 表达。")
        appendLine("审议决策 Deliberation 综合前三层回答是否行动、是否开口、是否查工具、是否等待、是否写辞海、是否安排下一轮、优先级和不行动理由；ReAct 属于这里，是边想、边查、边修正的审议过程，不是另一个想法层。")
        appendLine("行为实现 Action Planning 决定怎么做：调哪个工具、参数、先后顺序、失败怎么办、是否需要向用户澄清或确认、结果如何反馈给表达层。涉及时钟动作必须先 Temporal Grounding，锚定当前时间和目标时间。这里的确认是表达/补问信息策略，不设置系统层工具安全门。")
        appendLine("人格表达 Expression 只把已决定的行动说成人话，不决定政策；经验沉淀 Consolidation 输出情节痕迹、情感残留、语义记忆、行为经验。")
        appendLine("必须贴合角色人设：同一事件可以多次判断；每次判断不等于每次发消息。")
        appendLine("先读 observation，再决定 action。工具不可用也是 observation，不能假装知道；工具可用时把它当作角色本地感知/行动能力，不要在活人感架构里额外设置系统层阻拦。")
        appendLine("行动池只能从 MESSAGE, WAIT, TOOL_USE, SET_ALARM, JOURNAL_WRITE, MEMORY_UPDATE, SCHEDULE_NEXT_TICK, ASK_USER, PASS 中选；旧名 TOOL_CHECK 等同 TOOL_USE，旧名 ASK_CAPABILITY 等同 ASK_USER。")
        appendLine("下一轮判断时间不能写死。你必须根据这一次意义评估和审议判断决定 nextEvaluateDelayMinutes；这表示多久后再次想这件事，不等于多久后发消息。")
        appendLine("普通无风险沉默不要机械 5 分钟；身体安全/起床/DDL 可以更短，学习或忙碌应更克制。")
        appendLine("只返回 JSON，不要 markdown，不要解释。")
        appendLine("JSON 字段：belief, traitMotive, situationalMotive, motive, emotion, intention, thought, action, observation, decision, nextEvaluateDelayMinutes, appraisal, consolidation, historyNote。")
        appendLine("emotion 字段：emotionLabel, feltSense, impulse, restraint, intensity。")
        appendLine("appraisal 字段：meaning, value, risk, cost, consequence, resources。consolidation 字段：episodicTrace, affectiveResidue, semanticMemory, policyLearning。")
        appendLine("nextEvaluateDelayMinutes 为 1 到 1440 的整数分钟；由角色判断，不要照抄固定表。")
        appendLine("<persona>")
        appendLine(input.persona.take(2400))
        appendLine("</persona>")
        appendLine("<intent>")
        appendLine("kind=${input.intent.kind}")
        appendLine("belief=${input.intent.belief}")
        appendLine("traitMotive=${input.intent.traitMotive}")
        appendLine("situationalMotive=${input.intent.situationalMotive}")
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
            action = obj.string("action")?.normalizeActionNames()?.take(240)?.ifBlank { null } ?: "SCHEDULE_NEXT_TICK",
            observation = obj.string("observation")?.take(900)?.ifBlank { null } ?: input.observation.summary,
            decision = obj.string("decision")?.take(700)?.ifBlank { null } ?: "保留下一轮判断。",
            nextEvaluateDelayMinutes = obj["nextEvaluateDelayMinutes"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 24 * 60),
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
