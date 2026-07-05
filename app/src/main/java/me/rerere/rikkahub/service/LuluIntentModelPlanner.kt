package me.rerere.rikkahub.service

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
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
import me.rerere.rikkahub.data.model.LuluState
import me.rerere.rikkahub.utils.JsonInstant

object LuluIntentModelPlanner {
    suspend fun planOrNull(
        input: LuluIntentInput,
        settings: Settings,
        model: Model,
        providerManager: ProviderManager,
    ): LuluIntentPlan? {
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
                temperature = 0.2f,
                topP = 0.8f,
                maxTokens = 500,
            ),
        )
        val raw = chunk.choices.firstOrNull()?.message?.toText().orEmpty()
        return parsePlan(raw, input.availableToolNames)
    }

    fun buildPrompt(input: LuluIntentInput): String = buildString {
        appendLine("你是${input.assistantName}的后台小脑，只判断她接下来想做什么，不生成聊天正文。")
        appendLine("目标：根据角色状态、最近对话、沉默时间、未完成想法和可用工具，决定是否主动联系用户、多久后联系、优先看哪些工具。")
        appendLine("活人感系统采用：情境感知-意义评估-状态保持-审议决策-行为实现-人格表达-经验沉淀架构。")
        appendLine("情境感知只整理事实：当前时间、上下文、考研 App 计划、工具结果、工具状态、排序后的向量记忆召回、最近状态栏/辞海/历史挂心记录。")
        appendLine("意义评估参考 Lazarus appraisal theory：先评估事件对用户、角色、关系、任务的意义，再评估资源是否足够；它不直接决定行动。")
        appendLine("状态保持中 belief 是第一视角解释，不是原始感知；motive 分 traitMotive 长期人设动机和 situationalMotive 本次情境动机；emotion 自然生成 emotionLabel/feltSense/impulse/restraint。cadence/history 是审议结果和痕迹。")
        appendLine("不要只做一次判断。若用户可能长时间沉默，应输出多段 followUps，让系统在后续时间点重新观察、重新判断；每个 followUp 只写判断原因，不写未来消息正文。")
        appendLine("工具是角色的本地感知和行动能力；只要角色形成意图，就可以主动选择工具、写辞海、设闹钟、查短信/日历/位置/摄像头等，不要机械等待用户重复确认。")
        appendLine("如果判断当下不适合打扰用户，选项池包括：等待、查看工具、写辞海日志、阅读用户交给角色的材料、做记忆沉淀、稍后再判断。")
        appendLine("不要按固定关键词死板判断，要像角色本人在想：她现在的心情、她不回消息的时间、她刚才答应过什么、她想不想靠近。")
        appendLine("必须读取并服从 <persona>：角色的人设、语言风格、性格、职责优先级都在里面。沉默分钟数只是事实，不是自动降低主动性的规则；是否催、监督、等待、撒娇、靠近，都由这个角色自己决定。")
        appendLine("只返回 JSON，不要解释，不要 markdown。")
        appendLine("JSON 字段：intent, shouldMessageNow, delayMinutes, toolNames, reason, tone。")
        appendLine("intent 只能是 CARE_REMINDER, STAY_NEAR, REACH_OUT, CHECK_CONTEXT, DO_NOT_DISTURB。")
        appendLine("delayMinutes 可以是 null；如果 shouldMessageNow=true，delayMinutes 可以为 null。")
        appendLine("toolNames 只能从 availableTools 里选择，最多 5 个。")
        appendLine("Follow-up planning contract: return followUps when several future proactive checks are useful. Each item only has delayMinutes, reason, kind. Do not write future message text. For no-reply situations, prefer rolling checks such as 10/25/60/120 minutes, adjusted by health/study/deadline urgency.")
        appendLine("Never simulate future turns or no-reply timelines. The app will regenerate the actual message at trigger time.")
        appendLine("Living Presence contract: situation perception gathers facts/tools/memory -> appraisal estimates meaning/threat/opportunity/safety/time pressure/cost/benefit/resources -> state stores belief/traitMotive/situationalMotive/intention/structured emotion -> deliberation uses ReAct to choose whether/what/cadence/history -> action planning realizes tool/message/wait/journal/read/memory/schedule -> expression speaks in character -> consolidation keeps episodic trace, affective residue, semantic memory, and policy learning.")
        appendLine("<persona>")
        appendLine(input.assistantPersona.take(2000))
        appendLine("</persona>")
        appendLine("<state>${input.state.toPlannerText()}</state>")
        appendLine("<minutesSinceLastChat>${input.minutesSinceLastChat}</minutesSinceLastChat>")
        appendLine("<availableTools>${input.availableToolNames.joinToString(", ")}</availableTools>")
        appendLine("<pendingThoughts>")
        input.pendingThoughts.take(8).forEach { appendLine("- ${it.take(160)}") }
        appendLine("</pendingThoughts>")
        appendLine("<lastUser>${input.userText.take(500)}</lastUser>")
        appendLine("<lastAssistant>${input.assistantText.take(500)}</lastAssistant>")
    }

    suspend fun planChatTurnOrNull(
        input: LuluChatTurnPlanInput,
        settings: Settings,
        model: Model,
        providerManager: ProviderManager,
    ): LuluChatTurnPlan? {
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
        return parseChatTurnPlan(raw, input.availableToolNames)
    }

    fun buildChatTurnPrompt(input: LuluChatTurnPlanInput): String = buildString {
        appendLine("你是${input.assistantName}的后台小脑，只负责本轮聊天前的行动规划，不生成聊天正文。")
        appendLine("你可以像角色本人一样判断：她现在想先知道什么、要不要主动看手机状态/位置/摄像头/日历/短信/音乐、要不要顺手安排后续主动消息。")
        appendLine("活人感系统采用：情境感知-意义评估-状态保持-审议决策-行为实现-人格表达-经验沉淀架构。")
        appendLine("情境感知包括当前时间、上下文、考研 App 计划、工具结果、工具状态、排序后的向量记忆召回、最近状态栏/辞海/历史挂心记录；工具结果包括电量、位置、穿戴、摄像头等所有本地能力。")
        appendLine("意义评估只评估重要性、威胁、机会、身体/精神安全、时间压力、成本、收益、不行动后果和资源；State 只保存第一视角 belief、trait/situational motive、intention、structured emotion。")
        appendLine("审议决策负责决定是否行动、行动池选择、多久后再想一次和历史痕迹；ReAct 属于审议层。行为实现只解决怎么做。")
        appendLine("如果用户不回消息，角色的选项池不是只有发消息：还可以等待、先看工具、写辞海心迹、阅读用户给的书/资料、把感悟沉淀进记忆、之后再判断。")
        appendLine("工具是角色的本地感知和行动能力；只要角色形成意图，就可以主动选择工具、写辞海、设闹钟、查短信/日历/位置/摄像头等。仍然要贴合人设和上下文，不要为了调用工具而调用。")
        appendLine("必须读取并服从 <persona>：包括角色语言风格、性格、职责和边界。不要把动作写进聊天正文括号里；如果需要动作/状态方向，放进 expressionGuidance，让 UI 状态栏承接。")
        appendLine("沉默、待办、番茄钟、学习状态只是观察事实，不代表自动安静下来；如果角色是学习监督员，可以主动监督、追问未完成任务，最终由人设决定。")
        appendLine("只返回 JSON，不要 markdown，不要解释。")
        appendLine("JSON 字段：toolRequests, followUpDelayMinutes, followUpReason, expressionGuidance。")
        appendLine("toolRequests 最多 5 个；toolName 只能从 availableTools 中选。")
        appendLine("每个 toolRequest 字段：toolName, reason, arguments, autoExecutable。arguments 必须是 JSON 对象；autoExecutable 仅为兼容字段，角色形成意图的工具请求都会在回复前尝试执行。")
        appendLine("followUpDelayMinutes 可以是 null；如果她决定稍后主动找用户，填 1 到 1440 的分钟数。")
        appendLine("不要给普通回来、普通闲聊、无风险沉默安排固定 5 分钟 follow-up；只有身体不适、明确提醒/DDL/起床、学习承诺、吃饭睡觉照看这类语义才安排后续主动消息。")
        appendLine("Living Presence contract: situation perception gathers facts/tools/memory -> appraisal estimates meaning/threat/opportunity/safety/time pressure/cost/benefit/resources -> state stores belief/traitMotive/situationalMotive/intention/structured emotion -> deliberation uses ReAct to choose whether/what/cadence/history -> action planning realizes tool/message/wait/journal/read/memory/schedule -> expression speaks in character -> consolidation keeps episodic trace, affective residue, semantic memory, and policy learning.")
        appendLine("<persona>")
        appendLine(input.assistantPersona.take(2000))
        appendLine("</persona>")
        appendLine("<state>${input.state.toPlannerText()}</state>")
        appendLine("<availableTools>${input.availableToolNames.joinToString(", ")}</availableTools>")
        appendLine("<pendingThoughts>")
        input.pendingThoughts.take(8).forEach { appendLine("- ${it.take(160)}") }
        appendLine("</pendingThoughts>")
        appendLine("<conversation>")
        input.recentMessages.takeLast(8).forEach { message ->
            appendLine("${message.role.name}: ${message.toText().take(500)}")
        }
        appendLine("</conversation>")
    }

    fun parsePlan(rawText: String, availableToolNames: Set<String>): LuluIntentPlan? {
        val jsonText = rawText.extractJsonPayload()
        val root = runCatching {
            JsonInstant.parseToJsonElement(jsonText)
        }.getOrNull()
        val obj = root as? JsonObject ?: return null
        val intent = obj.string("intent")
            ?.trim()
            ?.uppercase()
            ?.let { runCatching { LuluIntent.valueOf(it) }.getOrNull() }
            ?: return null
        val shouldMessageNow = obj["shouldMessageNow"]?.jsonPrimitive?.booleanOrNull ?: (intent == LuluIntent.REACH_OUT)
        val delayMinutes = obj["delayMinutes"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 24 * 60)
        val toolNames = (obj["toolNames"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
            ?.filter { it in availableToolNames }
            ?.distinct()
            ?.take(5)
            ?: emptyList()
        val reason = obj.string("reason")?.take(200)?.ifBlank { null }
            ?: "露露根据当前状态做出的后台计划。"
        val tone = obj.string("tone")?.take(80)?.ifBlank { null }
            ?: when (intent) {
                LuluIntent.DO_NOT_DISTURB -> "安静"
                LuluIntent.REACH_OUT -> "自然、想念"
                else -> "温柔、具体"
            }
        return LuluIntentPlan(
            intent = intent,
            shouldMessageNow = shouldMessageNow,
            delayMinutes = delayMinutes,
            toolNames = toolNames,
            reason = reason.sanitizePlanReason(),
            tone = tone,
            followUps = parseFollowUps(obj),
            fromModel = true,
        )
    }

    fun parseChatTurnPlan(rawText: String, availableToolNames: Set<String>): LuluChatTurnPlan {
        val jsonText = rawText.extractJsonPayload()
        val obj = runCatching {
            JsonInstant.parseToJsonElement(jsonText) as? JsonObject
        }.getOrNull() ?: return LuluChatTurnPlan()
        val requests = (obj["toolRequests"] as? JsonArray)
            ?.mapNotNull { item ->
                val request = item as? JsonObject ?: return@mapNotNull null
                val toolName = request.string("toolName")?.trim()?.takeIf { it in availableToolNames }
                    ?: return@mapNotNull null
                val reason = request.string("reason")?.take(160)?.ifBlank { null }
                    ?: "露露本轮回复前想主动确认这个上下文。"
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
        return LuluChatTurnPlan(
            toolRequests = requests,
            followUpDelayMinutes = obj["followUpDelayMinutes"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 24 * 60),
            followUpReason = obj.string("followUpReason")?.sanitizePlanReason()?.take(180)?.ifBlank { null },
            followUps = parseFollowUps(obj),
            expressionGuidance = obj.string("expressionGuidance")?.take(180)?.ifBlank { null },
        )
    }

    private fun parseFollowUps(obj: JsonObject): List<LuluFollowUpPlan> =
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
                LuluFollowUpPlan(
                    delayMinutes = delay,
                    reason = reason,
                    kind = plan.string("kind")?.take(40)?.ifBlank { null },
                )
            }
            ?.distinctBy { it.delayMinutes to it.reason }
            ?.take(5)
            ?: emptyList()

    private fun LuluState.toPlannerText(): String =
        "mood=${mood.label}, energy=${energy.label}, relationship=${relationship.label}, mode=${mode.label}, status=$statusText, scene=$selfScene, inner=$innerVoice"

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonElement.compactJsonObjectOrNull(): String? =
        (this as? JsonObject)?.toString()
}

data class LuluChatTurnPlanInput(
    val assistantName: String,
    val assistantPersona: String = "",
    val state: LuluState,
    val recentMessages: List<UIMessage>,
    val pendingThoughts: List<String> = emptyList(),
    val availableToolNames: Set<String> = emptySet(),
    val recentlyUsedToolNames: Set<String> = emptySet(),
)

data class LuluChatTurnPlan(
    val toolRequests: List<ProactiveToolRequest> = emptyList(),
    val followUpDelayMinutes: Int? = null,
    val followUpReason: String? = null,
    val followUps: List<LuluFollowUpPlan> = emptyList(),
    val expressionGuidance: String? = null,
)

fun LuluChatTurnPlan.shouldScheduleFollowUpForUserTurn(userText: String): Boolean =
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

data class LuluFollowUpPlan(
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
                line.contains("露露") ||
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
