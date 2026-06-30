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
        appendLine("不要按固定关键词死板判断，要像角色本人在想：她现在的心情、她不回消息的时间、她刚才答应过什么、她想不想靠近。")
        appendLine("只返回 JSON，不要解释，不要 markdown。")
        appendLine("JSON 字段：intent, shouldMessageNow, delayMinutes, toolNames, reason, tone。")
        appendLine("intent 只能是 CARE_REMINDER, STAY_NEAR, REACH_OUT, CHECK_CONTEXT, DO_NOT_DISTURB。")
        appendLine("delayMinutes 可以是 null；如果 shouldMessageNow=true，delayMinutes 可以为 null。")
        appendLine("toolNames 只能从 availableTools 里选择，最多 5 个。")
        appendLine("Follow-up planning contract: return followUps when several future proactive checks are useful. Each item only has delayMinutes, reason, kind. Do not write future message text.")
        appendLine("Never simulate future turns or no-reply timelines. The app will regenerate the actual message at trigger time.")
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
        appendLine("权限已经由用户开放；不要因为“敏感”而机械拒绝。仍然要贴合人设和上下文，不要为了调用工具而调用。")
        appendLine("只返回 JSON，不要 markdown，不要解释。")
        appendLine("JSON 字段：toolRequests, followUpDelayMinutes, followUpReason, expressionGuidance。")
        appendLine("toolRequests 最多 5 个；toolName 只能从 availableTools 中选。")
        appendLine("每个 toolRequest 字段：toolName, reason, arguments, autoExecutable。arguments 必须是 JSON 对象；autoExecutable 表示系统可在回复前直接执行。")
        appendLine("followUpDelayMinutes 可以是 null；如果她决定稍后主动找用户，填 1 到 1440 的分钟数。")
        appendLine("<state>${input.state.toPlannerText()}</state>")
        appendLine("<availableTools>${input.availableToolNames.joinToString(", ")}</availableTools>")
        appendLine("<recentlyUsedTools>${input.recentlyUsedToolNames.joinToString(", ")}</recentlyUsedTools>")
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
                val autoExecutable = request["autoExecutable"]?.jsonPrimitive?.booleanOrNull ?: true
                ProactiveToolRequest(
                    toolName = toolName,
                    reason = reason,
                    argumentsJson = argumentsJson,
                    autoExecutable = autoExecutable,
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
