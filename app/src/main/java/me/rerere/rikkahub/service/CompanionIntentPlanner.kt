package me.rerere.rikkahub.service

import kotlinx.serialization.json.JsonArray
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
import me.rerere.rikkahub.data.companion.CompanionPerceptionPacket
import me.rerere.rikkahub.data.companion.toPromptContext
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.utils.JsonInstant

data class CompanionIntentInput(
    val perception: CompanionPerceptionPacket,
    val mode: CompanionDecisionMode,
    val minutesSinceLastChat: Long,
)

data class CompanionIntentDecision(
    val intent: CompanionIntent,
    val shouldMessageNow: Boolean,
    val delayMinutes: Int?,
    val toolNames: List<String>,
    val reason: String,
    val tone: String,
    val innerThought: String = "",
    val followUps: List<CompanionFollowUpPlan> = emptyList(),
    val category: String? = null,
    val fromModel: Boolean = false,
)

data class CompanionFollowUpPlan(
    val delayMinutes: Int,
    val reason: String,
    val category: String? = null,
)

enum class CompanionIntent {
    FOLLOW_UP,
    STAY_AVAILABLE,
    REACH_OUT,
    OBSERVE,
    WAIT,
}

enum class CompanionDecisionMode {
    FOREGROUND,
    BACKGROUND,
}

object CompanionIntentFallbackPlanner {
    fun plan(input: CompanionIntentInput): CompanionIntentDecision {
        if (input.mode == CompanionDecisionMode.FOREGROUND) {
            return waitDecision("The current turn has no validated follow-up decision; keep existing commitments unchanged.")
        }

        val dueConcern = input.perception.activeConcerns.firstOrNull { concern ->
            concern.nextPerceptionAt != null && concern.nextPerceptionAt <= input.perception.nowMillis
        }
        if (dueConcern != null) {
            return CompanionIntentDecision(
                intent = CompanionIntent.OBSERVE,
                shouldMessageNow = false,
                delayMinutes = 1,
                toolNames = chooseObservationTools(input.perception.availableToolNames),
                reason = "An active concern is due for fresh perception before any expression.",
                tone = "Follow the persona and current relationship boundaries.",
                innerThought = "我先重新看清现在的情况，再决定要不要开口。",
                category = "concern",
            )
        }

        if (
            input.minutesSinceLastChat >= DEFAULT_REACH_OUT_MINUTES &&
            input.perception.snapshot.relationship.unresolvedTension < MAX_FALLBACK_REACH_OUT_TENSION
        ) {
            return CompanionIntentDecision(
                intent = CompanionIntent.REACH_OUT,
                shouldMessageNow = true,
                delayMinutes = null,
                toolNames = chooseObservationTools(input.perception.availableToolNames),
                reason = "A long quiet interval makes one fresh persona-led check-in reasonable.",
                tone = "Use the configured persona; do not sound like a timer or generic reminder.",
                innerThought = "安静了一阵，我想按自己的方式确认一下现在是否适合联系或行动。",
                category = "reach_out",
            )
        }

        return waitDecision("There is not enough current evidence to interrupt the user.")
    }

    private fun waitDecision(reason: String): CompanionIntentDecision = CompanionIntentDecision(
        intent = CompanionIntent.WAIT,
        shouldMessageNow = false,
        delayMinutes = null,
        toolNames = emptyList(),
        reason = reason,
        tone = "Follow the configured persona without inventing intimacy.",
        innerThought = "现在没有足够理由打扰，我先保留这份注意，等下一次真实变化。",
    )

    private fun chooseObservationTools(available: Set<String>): List<String> = listOf(
        "get_battery_info",
        "get_app_usage",
        "get_gadgetbridge_data",
    ).filter { it in available }

    private const val DEFAULT_REACH_OUT_MINUTES = 120L
    private const val MAX_FALLBACK_REACH_OUT_TENSION = 0.5f
}

object CompanionIntentModelPlanner {
    suspend fun planOrNull(
        input: CompanionIntentInput,
        settings: Settings,
        model: Model,
        providerManager: ProviderManager,
    ): CompanionIntentDecision? {
        val provider = model.findProvider(settings.providers) ?: return null
        val providerImpl = providerManager.getProviderByType(provider)
        val chunk = providerImpl.generateText(
            providerSetting = provider,
            messages = listOf(
                UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text(buildPrompt(input))),
                ),
            ),
            params = TextGenerationParams(
                model = model,
                temperature = 0.2f,
                topP = 0.8f,
                maxTokens = 600,
            ),
        )
        val raw = chunk.choices.firstOrNull()?.message?.toText().orEmpty()
        return parsePlan(raw, input.perception.availableToolNames)
    }

    fun buildPrompt(input: CompanionIntentInput): String = buildString {
        appendLine("You are the background decision layer for ${input.perception.assistantName.ifBlank { "the current role" }}.")
        appendLine("Decide only the next intention and perception point. Do not generate user-facing message text.")
        appendLine("The role persona is authoritative. The runtime core must not assume housekeeper, lover, friend, or supervisor behavior.")
        appendLine("Use evidence from the unified runtime snapshot. Existing commitments are durable and ordinary chat never cancels them.")
        appendLine("A model may propose changes, but deterministic reducers validate identity, time, transitions, and relationship deltas.")
        appendLine("Return JSON only with: intent, shouldMessageNow, delayMinutes, toolNames, reason, tone, innerThought, category, followUps.")
        appendLine("intent must be one of FOLLOW_UP, STAY_AVAILABLE, REACH_OUT, OBSERVE, WAIT.")
        appendLine("followUps contains objects with delayMinutes, reason, and optional category. Schedule only useful next perception points.")
        appendLine("Never prewrite a future message. Never increase closeness merely because time passed.")
        appendLine("Formal diary writing requires the explicit write_lulu_journal tool and new concrete content.")
        appendLine("<decision_mode>${input.mode.name}</decision_mode>")
        appendLine("<minutes_since_last_chat>${input.minutesSinceLastChat}</minutes_since_last_chat>")
        appendLine("<persona>")
        appendLine(input.perception.persona)
        appendLine("</persona>")
        appendLine(input.perception.toPromptContext())
        appendLine("<recent_conversation>")
        input.perception.recentTurns.takeLast(8).forEach { turn ->
            appendLine("${turn.role.name}: ${turn.content.take(500)}")
        }
        appendLine("</recent_conversation>")
        appendLine("<available_tools>${input.perception.availableToolNames.joinToString(", ")}</available_tools>")
    }

    fun parsePlan(
        rawText: String,
        availableToolNames: Set<String>,
    ): CompanionIntentDecision? {
        val root = runCatching {
            JsonInstant.parseToJsonElement(rawText.extractCompanionJsonPayload())
        }.getOrNull() as? JsonObject ?: return null
        val intent = root.string("intent")?.toCompanionIntent() ?: return null
        val shouldMessageNow = root["shouldMessageNow"]?.jsonPrimitive?.booleanOrNull
            ?: (intent == CompanionIntent.REACH_OUT)
        val delayMinutes = root["delayMinutes"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 24 * 60)
        val toolNames = (root["toolNames"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
            ?.filter { it in availableToolNames }
            ?.distinct()
            ?.take(5)
            .orEmpty()
        return CompanionIntentDecision(
            intent = intent,
            shouldMessageNow = shouldMessageNow,
            delayMinutes = delayMinutes,
            toolNames = toolNames,
            reason = root.string("reason")
                ?.sanitizeCompanionPlanText(240)
                ?: "The model selected the next companion action from current evidence.",
            tone = root.string("tone")
                ?.sanitizeCompanionPlanText(100)
                ?: "Follow the configured persona and relationship boundaries.",
            innerThought = root.string("innerThought")
                ?.cleanCompanionInnerThought()
                ?: root.string("inner_thought")?.cleanCompanionInnerThought()
                ?: fallbackCompanionInnerThought(intent),
            followUps = parseFollowUps(root),
            category = root.string("category")?.trim()?.take(60)?.takeIf(String::isNotBlank),
            fromModel = true,
        )
    }

    private fun parseFollowUps(root: JsonObject): List<CompanionFollowUpPlan> =
        (root["followUps"] as? JsonArray)
            ?.mapNotNull { item ->
                val plan = item as? JsonObject ?: return@mapNotNull null
                val delay = plan["delayMinutes"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 24 * 60)
                    ?: return@mapNotNull null
                val reason = plan.string("reason")
                    ?.sanitizeCompanionPlanText(180)
                    ?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                CompanionFollowUpPlan(
                    delayMinutes = delay,
                    reason = reason,
                    category = plan.string("category")
                        ?: plan.string("kind"),
                )
            }
            ?.distinctBy { it.delayMinutes to it.reason }
            ?.take(5)
            .orEmpty()

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull
}

private fun String.toCompanionIntent(): CompanionIntent? = when (trim().uppercase()) {
    "FOLLOW_UP", "CARE_REMINDER" -> CompanionIntent.FOLLOW_UP
    "STAY_AVAILABLE", "STAY_NEAR" -> CompanionIntent.STAY_AVAILABLE
    "REACH_OUT" -> CompanionIntent.REACH_OUT
    "OBSERVE", "CHECK_CONTEXT" -> CompanionIntent.OBSERVE
    "WAIT", "DO_NOT_DISTURB" -> CompanionIntent.WAIT
    else -> null
}

private fun fallbackCompanionInnerThought(intent: CompanionIntent): String = when (intent) {
    CompanionIntent.FOLLOW_UP -> "我把这件明确的后续记住，到点会重新看当时的真实情况。"
    CompanionIntent.STAY_AVAILABLE -> "我先不打断，把注意留在这里，等下一次有意义的变化。"
    CompanionIntent.REACH_OUT -> "安静了一阵，我想按自己的人设自然确认一下现在是否适合开口。"
    CompanionIntent.OBSERVE -> "我先重新看清上下文，再决定行动和表达。"
    CompanionIntent.WAIT -> "现在没有足够理由打扰，我先保持安静。"
}

private fun String.cleanCompanionInnerThought(): String? {
    val compact = trim()
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .take(180)
    if (compact.isBlank()) return null
    val forbidden = listOf("Seven-layer trace", "tool_result", "requested_tools=", "{", "}")
    return compact.takeUnless { text -> forbidden.any { text.contains(it, ignoreCase = true) } }
}

private fun String.sanitizeCompanionPlanText(maxLength: Int): String = lineSequence()
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .joinToString(" ")
    .take(maxLength)

private fun String.extractCompanionJsonPayload(): String {
    val trimmed = trim()
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed
    val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
        .find(trimmed)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    if (!fenced.isNullOrBlank()) return fenced
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    return if (start >= 0 && end >= start) trimmed.substring(start, end + 1) else trimmed
}
