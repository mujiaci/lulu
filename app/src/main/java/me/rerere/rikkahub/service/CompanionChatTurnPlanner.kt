package me.rerere.rikkahub.service

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.companion.CompanionPerceptionPacket
import me.rerere.rikkahub.data.companion.toPromptContext
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.utils.JsonInstant

object CompanionChatTurnModelPlanner {
    suspend fun planChatTurnOrNull(
        input: CompanionChatTurnPlanInput,
        settings: Settings,
        model: Model,
        providerManager: ProviderManager,
    ): CompanionChatTurnPlan? {
        val provider = model.findProvider(settings.providers) ?: return null
        val providerImpl = providerManager.getProviderByType(provider)
        val chunk = providerImpl.generateText(
            providerSetting = provider,
            messages = listOf(UIMessage.user(buildChatTurnPrompt(input))),
            params = TextGenerationParams(
                model = model,
                temperature = 0.25f,
                topP = 0.8f,
                maxTokens = 700,
            ),
        )
        val raw = chunk.choices.firstOrNull()?.message?.toText().orEmpty()
        val jsonText = raw.extractJsonPayload()
        val root = runCatching {
            JsonInstant.parseToJsonElement(jsonText)
        }.getOrNull()
        if (root !is JsonObject) return null
        return parseChatTurnPlan(raw, input.perception.availableToolNames)
    }

    fun buildChatTurnPrompt(input: CompanionChatTurnPlanInput): String = buildString {
        val perception = input.perception
        appendLine("你是${perception.assistantName.ifBlank { "当前角色" }}的后台小脑，只负责本轮聊天前的行动规划，不生成聊天正文。")
        appendLine("你可以像角色本人一样判断：先读取已经注入的手机状态、位置等被动感知，再决定是否要看摄像头/日历/短信、控制音乐或顺手安排后续主动消息。")
        appendLine("统一陪伴系统采用：感知世界包-意义评估-动态判断-行动实现-状态生成-正式日记架构。")
        appendLine("情境感知包括当前时间、上下文、考研 App 计划、被动感知事实、排序后的向量记忆召回、最近状态栏/辞海/历史挂心记录；不要为重复读取已提供事实而调用工具。")
        appendLine("意义评估只评估重要性、威胁、机会、身体/精神安全、时间压力、成本、收益、不行动后果和资源；它不直接选择行动。")
        appendLine("动态判断负责决定 intention、工具需求、是否行动、行动池选择和下一次感知时间；工具结果如果能同步得到，就补回本轮再决定行动。")
        appendLine("状态生成放在行动后，只生成心情、身体状况、精神状况、关系状态和第一人称没说出口；belief/motive/intention 不作为状态栏字段。")
        appendLine("如果用户不回消息，角色的选项池不是只有发消息：还可以等待、先看感知工具、更新状态、安排下一次判断。静默不产生辞海记录；正式日记只在主动调用 write_lulu_journal 且能写出新内容时保存。")
        appendLine("工具是角色的主动行动能力；形成明确意图后可以写正式日记、设闹钟、查短信/日历、看摄像头或控制音乐。仍然要贴合人设和上下文，不要为了调用工具而调用。")
        appendLine("favorite_user_message 是角色自己的收藏动作：当用户这一句话让角色真心触动、觉得珍贵、能代表关系变化，或以后会想重新看到时，可以主动收藏；日常寒暄和普通指令不要收藏，也不要等用户说‘收藏’才使用。")
        appendLine("必须读取并服从 <persona>：包括角色语言风格、性格、职责和边界。不要把动作写进聊天正文括号里；如果需要动作/状态方向，放进 expressionGuidance，让 UI 状态栏承接。")
        appendLine("无论是否需要工具，都必须输出 innerThought：第一人称、角色本人本轮没说出口的心里话。不要写分析提纲、工具 JSON、字段名或 Seven-layer trace。")
        appendLine("Expression 只负责表达已决定的行动，不决定政策；expressionAffordances 可从 TEXT, KAOMOJI, STICKER, VOICE, STATUS_BAR, LIGHT_REMINDER, LONG_EXPLANATION, SILENT_RECORD 中选择。")
        appendLine("沉默、待办、番茄钟、学习状态只是观察事实，不代表自动安静下来；如果角色是学习监督员，可以主动监督、追问未完成任务，最终由人设决定。")
        appendLine("只返回 JSON，不要 markdown，不要解释。")
        appendLine("JSON 字段：toolRequests, followUpDelayMinutes, followUpReason, followUps, expressionGuidance, expressionAffordances, innerThought。")
        appendLine("toolRequests 最多 5 个；toolName 只能从 availableTools 中选。")
        appendLine("每个 toolRequest 字段：toolName, reason, arguments, autoExecutable。arguments 必须是 JSON 对象；autoExecutable 仅为兼容字段，角色形成意图的工具请求都会在回复前尝试执行。")
        appendLine("followUpDelayMinutes 可以是 null；如果当前角色决定稍后主动找用户，填 1 到 1440 的分钟数。")
        appendLine("followUps 可以列出多个不同时间点，每项包含 delayMinutes、reason 和 kind；需要持续照看的目标不要只安排一次后就当作完成。")
        appendLine("不要给普通回来、普通闲聊、无风险沉默安排固定 5 分钟 follow-up；只有身体不适、明确提醒/DDL/起床、学习承诺、吃饭睡觉照看这类语义才安排后续主动消息。")
        appendLine("Unified companion contract: perception packet gathers persona/context/actions/memory/diary/concerns/status -> appraisal understands meaningToUser and meaningToRole -> judgment decides intention, action needs, and nextPerceptionAt -> action executes message/tool/schedule/wait; formal diary is written only by write_lulu_journal -> status generates mood/body/mind/relationship/innerThought -> Cihai keeps concern cards and formal tool-written diary entries.")
        appendLine("<persona>")
        appendLine(perception.persona.take(2000))
        appendLine("</persona>")
        appendLine(perception.toPromptContext())
        if (perception.contextFacts.isNotEmpty()) {
            appendLine("<observed_facts>")
            perception.contextFacts.forEach { fact ->
                appendLine("${fact.key}=${fact.value}")
            }
            appendLine("</observed_facts>")
        }
        if (perception.memoryContext.isNotBlank()) {
            appendLine("<memory_context>")
            appendLine(perception.memoryContext)
            appendLine("</memory_context>")
        }
        appendLine("<availableTools>${perception.availableToolNames.joinToString(", ")}</availableTools>")
        appendLine("<conversation>")
        perception.recentTurns.takeLast(8).forEach { turn ->
            appendLine("${turn.role.name}: ${turn.content.take(500)}")
        }
        appendLine("</conversation>")
    }

    fun parseChatTurnPlan(rawText: String, availableToolNames: Set<String>): CompanionChatTurnPlan {
        val jsonText = rawText.extractJsonPayload()
        val obj = runCatching {
            JsonInstant.parseToJsonElement(jsonText) as? JsonObject
        }.getOrNull() ?: return CompanionChatTurnPlan()
        val requests = (obj["toolRequests"] as? JsonArray)
            ?.mapNotNull { item ->
                val request = item as? JsonObject ?: return@mapNotNull null
                val toolName = request.string("toolName")?.trim()?.takeIf { it in availableToolNames }
                    ?: return@mapNotNull null
                val reason = request.string("reason")?.take(160)?.ifBlank { null }
                    ?: "当前角色在本轮回复前想主动确认这个上下文。"
                val argumentsJson = request["arguments"]?.compactJsonObjectOrNull() ?: "{}"
                ProactiveToolRequest(
                    toolName = toolName,
                    reason = reason,
                    argumentsJson = argumentsJson,
                    autoExecutable = true,
                )
            }
            ?.distinctBy { it.toolName }
            ?.take(5)
            ?: emptyList()
        return CompanionChatTurnPlan(
            toolRequests = requests,
            followUpDelayMinutes = obj["followUpDelayMinutes"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 24 * 60),
            followUpReason = obj.string("followUpReason")?.sanitizePlanReason()?.take(180)?.ifBlank { null },
            followUps = parseFollowUps(obj),
            expressionGuidance = obj.string("expressionGuidance")?.take(180)?.ifBlank { null },
            expressionAffordances = parseExpressionAffordances(obj),
            innerThought = obj.string("innerThought")
                ?.cleanInnerThought()
                ?: obj.string("inner_thought")?.cleanInnerThought(),
        )
    }

    private fun parseExpressionAffordances(obj: JsonObject): List<CompanionExpressionAffordance> =
        (obj["expressionAffordances"] as? JsonArray)
            ?.mapNotNull { item ->
                item.jsonPrimitive.contentOrNull
                    ?.trim()
                    ?.uppercase()
                    ?.let { runCatching { CompanionExpressionAffordance.valueOf(it) }.getOrNull() }
            }
            ?.distinct()
            ?.take(8)
            ?: emptyList()

    private fun parseFollowUps(obj: JsonObject): List<CompanionChatFollowUpPlan> =
        (obj["followUps"] as? JsonArray)
            ?.mapNotNull { item ->
                val plan = item as? JsonObject ?: return@mapNotNull null
                val delay = plan["delayMinutes"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 24 * 60)
                    ?: return@mapNotNull null
                val reason = plan.string("reason")
                    ?.sanitizePlanReason()
                    ?.take(180)
                    ?.ifBlank { null }
                    ?: return@mapNotNull null
                CompanionChatFollowUpPlan(
                    delayMinutes = delay,
                    reason = reason,
                    kind = plan.string("kind")?.take(40)?.ifBlank { null },
                )
            }
            ?.distinctBy { it.delayMinutes to it.reason }
            ?.take(5)
            ?: emptyList()

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonElement.compactJsonObjectOrNull(): String? =
        (this as? JsonObject)?.toString()
}

private fun String.cleanInnerThought(): String? {
    val compact = trim()
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .take(180)
    if (compact.isBlank()) return null
    val forbidden = listOf("Seven-layer trace", "Perception=", "tool_result", "requested_tools=", "{", "}")
    return compact.takeUnless { text -> forbidden.any { text.contains(it, ignoreCase = true) } }
}

data class CompanionChatTurnPlanInput(
    val perception: CompanionPerceptionPacket,
)

data class CompanionChatTurnPlan(
    val toolRequests: List<ProactiveToolRequest> = emptyList(),
    val followUpDelayMinutes: Int? = null,
    val followUpReason: String? = null,
    val followUps: List<CompanionChatFollowUpPlan> = emptyList(),
    val expressionGuidance: String? = null,
    val expressionAffordances: List<CompanionExpressionAffordance> = emptyList(),
    val innerThought: String? = null,
)

enum class CompanionExpressionAffordance {
    TEXT,
    KAOMOJI,
    STICKER,
    VOICE,
    STATUS_BAR,
    LIGHT_REMINDER,
    LONG_EXPLANATION,
    SILENT_RECORD,
}

fun CompanionChatTurnPlan.shouldScheduleFollowUpForUserTurn(userText: String): Boolean =
    shouldScheduleFollowUpForUserTurn(
        userText = userText,
        reason = followUpReason,
        delayMinutes = followUpDelayMinutes,
    )

fun shouldScheduleFollowUpForUserTurn(
    userText: String,
    reason: String?,
    delayMinutes: Int?,
): Boolean {
    if (delayMinutes == null || delayMinutes <= 0) return false
    val combined = "$userText\n${reason.orEmpty()}".lowercase()
    val highValue = combined.hasAny(FOLLOW_UP_HEALTH_WORDS) ||
        combined.hasAny(FOLLOW_UP_TIME_WORDS) ||
        combined.hasAny(FOLLOW_UP_STUDY_WORDS) ||
        combined.hasAny(FOLLOW_UP_CARE_WORDS)
    if (!highValue) return false
    val ordinaryReturn = userText.lowercase().hasAny(FOLLOW_UP_RETURN_WORDS)
    return !ordinaryReturn || userText.lowercase().hasAny(FOLLOW_UP_HEALTH_WORDS) ||
        userText.lowercase().hasAny(FOLLOW_UP_STUDY_WORDS) ||
        userText.lowercase().hasAny(FOLLOW_UP_TIME_WORDS)
}

data class CompanionChatFollowUpPlan(
    val delayMinutes: Int,
    val reason: String,
    val kind: String? = null,
)

private fun String.sanitizePlanReason(): String =
    lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { line ->
            line.contains("佳辞") ||
                line.contains("～") ||
                line.contains("呀") ||
                line.contains("哦") ||
                line.contains("吗") ||
                line.contains("呢") ||
                line.contains("zzz", ignoreCase = true)
        }
        .joinToString(" ")
        .ifBlank { take(80) }

private fun String.extractJsonPayload(): String {
    val trimmed = trim()
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed

    val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
        .find(trimmed)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    if (!fenced.isNullOrBlank()) return fenced

    val objectStart = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    return if (objectStart >= 0 && end >= objectStart) {
        trimmed.substring(objectStart, end + 1)
    } else {
        trimmed
    }
}

private fun String.hasAny(words: Set<String>): Boolean = words.any { contains(it) }

private val FOLLOW_UP_RETURN_WORDS = setOf("回来了", "回来啦", "回来咯", "我回", "刚回来", "到了", "到啦", "我在")
private val FOLLOW_UP_HEALTH_WORDS = setOf("肚子", "胃", "头疼", "头痛", "疼", "痛", "难受", "不舒服", "发烧", "药")
private val FOLLOW_UP_TIME_WORDS = setOf(
    "提醒", "叫我", "催我", "闹钟", "起床", "ddl", "截止", "点前", "之前完成", "到时候", "分钟后", "小时后"
)
private val FOLLOW_UP_STUDY_WORDS = setOf(
    "学习", "写作业", "作业", "复习", "背书", "刷题", "专业课", "考研", "番茄", "待办", "任务"
)
private val FOLLOW_UP_CARE_WORDS = setOf("睡", "晚安", "困", "吃饭", "没吃", "午饭", "晚饭", "早饭")
