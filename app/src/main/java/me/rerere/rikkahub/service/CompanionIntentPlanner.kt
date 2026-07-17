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
    val actionToolName: String? = null,
    val actionArgumentsJson: String = "{}",
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
    SELF_ACTIVITY,
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

        val recentlyPlayed = input.perception.recentLifeEvents.any { event ->
            event.type == me.rerere.rikkahub.data.companion.CompanionLifeEventType.GAME &&
                input.perception.nowMillis - (event.endedAt ?: event.startedAt) < SELF_ACTIVITY_COOLDOWN_MILLIS
        }
        if (
            "play_companion_game" in input.perception.availableToolNames &&
            input.minutesSinceLastChat in SELF_ACTIVITY_MIN_IDLE_MINUTES until DEFAULT_REACH_OUT_MINUTES &&
            !recentlyPlayed
        ) {
            return CompanionIntentDecision(
                intent = CompanionIntent.SELF_ACTIVITY,
                shouldMessageNow = false,
                delayMinutes = null,
                toolNames = emptyList(),
                reason = "The character has quiet time and chose one real app-local activity without interrupting the user.",
                tone = "No user-facing message is needed for this private activity.",
                innerThought = "现在不用打扰你，我想自己去玩一小局，再把真实结果留下来。",
                category = "digital_life",
                actionToolName = "play_companion_game",
                actionArgumentsJson = """{"strategy":"curious"}""",
            )
        }

        return waitDecision(
            if (input.minutesSinceLastChat >= DEFAULT_REACH_OUT_MINUTES) {
                "Silence alone is not an event. There is no fresh evidence or due responsibility that justifies interruption."
            } else {
                "There is not enough current evidence to interrupt the user."
            },
        )
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
    private const val SELF_ACTIVITY_MIN_IDLE_MINUTES = 45L
    private const val SELF_ACTIVITY_COOLDOWN_MILLIS = 6L * 60L * 60L * 1_000L
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
            ?.enforceRelationshipPolicy(input.perception.snapshot.relationship)
    }

    fun buildPrompt(input: CompanionIntentInput): String = buildString {
        appendLine("You are the background decision layer for ${input.perception.assistantName.ifBlank { "the current role" }}.")
        appendLine("Decide only the next intention and perception point. Do not generate user-facing message text.")
        appendLine("The role persona is authoritative. The runtime core must not assume housekeeper, lover, friend, or supervisor behavior.")
        appendLine("Use evidence from the unified runtime snapshot. Existing commitments are durable and ordinary chat never cancels them.")
        appendLine("responsibility_anchors are duties the character must actively remember, separate from private_impression and optional recall. Check their triggers on every decision and either execute a real available action, schedule the next evidence check, or consciously wait; never merely repeat the anchor text.")
        appendLine("A model may propose changes, but deterministic reducers validate identity, time, transitions, and relationship deltas.")
        appendLine("Return JSON only with: intent, shouldMessageNow, delayMinutes, toolNames, reason, tone, innerThought, category, followUps, actionToolName, actionArguments.")
        appendLine("intent must be one of FOLLOW_UP, STAY_AVAILABLE, REACH_OUT, OBSERVE, SELF_ACTIVITY, WAIT.")
        appendLine("SELF_ACTIVITY means silently doing one real App-local action. It requires an available actionToolName and JSON object actionArguments; never invent completion and do not send a user-facing message merely to narrate it.")
        appendLine("Do not repeat the same SELF_ACTIVITY when recent_digital_life already contains it within roughly six hours.")
        appendLine("followUps contains objects with delayMinutes, reason, and optional category. Schedule only useful next perception points.")
        appendLine("Elapsed silence is context, not an event. Do not REACH_OUT merely because minutes_since_last_chat is large; require a due concern, active responsibility trigger, meaningful device/context change, or a new real digital-life event.")
        appendLine("The user decides the relationship boundary through what they say. If recent conversation, memory, or private impression says they are busy, unavailable, need space, cannot chat for a while, resting, or asleep, treat that as an active preference to lower interruption. Prefer WAIT, STAY_AVAILABLE, a later evidence check, or silent SELF_ACTIVITY; do not make a guilt-inducing check-in.")
        appendLine("When the user is temporarily busy or unavailable, prefer STAY_AVAILABLE with shouldMessageNow=false and a thoughtful delayMinutes for the next perception rather than a near-term generic pulse. Let the duration follow what the user said and the relationship context; do not fabricate an exact schedule.")
        appendLine("Use the local time, recent conversation, sleep/health facts, routine, and commitments as context. Do not impose a fixed night curfew or a fixed daily message quota: decide whether contact is considerate in this specific situation. At night, silence alone is especially weak evidence for interruption; only reach out when a real commitment, safety concern, meaningful change, or the role's established relationship makes it genuinely appropriate.")
        appendLine("The role owns its rhythm. Your job is to make a reasoned decision from current evidence, not to obey a mechanical frequency rule.")
        appendLine("Never prewrite a future message. Never increase closeness merely because time passed.")
        appendLine("Formal diary writing requires the explicit write_lulu_journal tool and new concrete content.")
        appendLine("<decision_mode>${input.mode.name}</decision_mode>")
        appendLine("<minutes_since_last_chat>${input.minutesSinceLastChat}</minutes_since_last_chat>")
        appendLine("<persona>")
        appendLine(input.perception.persona)
        appendLine("</persona>")
        appendLine(input.perception.toPromptContext())
        if (input.perception.memoryContext.isNotBlank()) {
            appendLine("<memory_context>")
            appendLine(input.perception.memoryContext)
            appendLine("</memory_context>")
        }
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
        val actionToolName = root.string("actionToolName")
            ?.trim()
            ?.takeIf { it in availableToolNames }
        if (intent == CompanionIntent.SELF_ACTIVITY && actionToolName == null) return null
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
            actionToolName = actionToolName,
            actionArgumentsJson = (root["actionArguments"] as? JsonObject)?.toString()
                ?: root.string("actionArgumentsJson")?.take(1_200)
                ?: "{}",
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
    "SELF_ACTIVITY", "PRIVATE_ACTIVITY", "PLAY" -> CompanionIntent.SELF_ACTIVITY
    "WAIT", "DO_NOT_DISTURB" -> CompanionIntent.WAIT
    else -> null
}

private fun fallbackCompanionInnerThought(intent: CompanionIntent): String = when (intent) {
    CompanionIntent.FOLLOW_UP -> "我把这件明确的后续记住，到点会重新看当时的真实情况。"
    CompanionIntent.STAY_AVAILABLE -> "我先不打断，把注意留在这里，等下一次有意义的变化。"
    CompanionIntent.REACH_OUT -> "安静了一阵，我想按自己的人设自然确认一下现在是否适合开口。"
    CompanionIntent.OBSERVE -> "我先重新看清上下文，再决定行动和表达。"
    CompanionIntent.SELF_ACTIVITY -> "现在不用打扰你，我想自己做一件真实的小事。"
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
