package me.rerere.rikkahub.data.companion

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CompanionPerceptionInput(
    val assistantId: String,
    val assistantName: String,
    val persona: String,
    val conversationId: String? = null,
    val recentTurns: List<CompanionConversationTurn> = emptyList(),
    val contextFacts: List<CompanionContextFact> = emptyList(),
    val availableToolNames: Set<String> = emptySet(),
    val memoryContext: String = "",
    val nowMillis: Long,
)

data class CompanionPerceptionPacket(
    val assistantId: String,
    val assistantName: String,
    val persona: String,
    val conversationId: String?,
    val snapshot: CompanionSnapshot,
    val recentTurns: List<CompanionConversationTurn>,
    val contextFacts: List<CompanionContextFact>,
    val activeConcerns: List<CompanionConcern>,
    val actionableCommitments: List<CompanionCommitment>,
    val activeGoals: List<CompanionGoal>,
    val recentLifeEvents: List<CompanionLifeEvent>,
    val availableToolNames: Set<String>,
    val memoryContext: String,
    val nowMillis: Long,
)

data class CompanionConversationTurn(
    val role: CompanionTurnRole,
    val content: String,
    val createdAt: Long,
    val sourceId: String? = null,
)

enum class CompanionTurnRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL,
}

data class CompanionContextFact(
    val key: String,
    val value: String,
    val observedAt: Long,
)

object CompanionPerceptionAssembler {
    fun assemble(
        input: CompanionPerceptionInput,
        snapshot: CompanionSnapshot,
    ): CompanionPerceptionPacket {
        val assistantId = input.assistantId.trim()
        require(assistantId.isNotBlank()) { "Companion perception requires an assistant ID" }
        require(snapshot.assistantId == assistantId) {
            "Companion snapshot belongs to a different assistant"
        }

        return CompanionPerceptionPacket(
            assistantId = assistantId,
            assistantName = input.assistantName.clean(MAX_ASSISTANT_NAME_LENGTH),
            persona = input.persona.clean(MAX_PERSONA_LENGTH),
            conversationId = input.conversationId?.trim()?.takeIf(String::isNotBlank),
            snapshot = snapshot,
            recentTurns = input.recentTurns
                .takeLast(MAX_RECENT_TURNS)
                .mapNotNull { turn -> turn.normalized() },
            contextFacts = input.contextFacts
                .takeLast(MAX_CONTEXT_FACTS)
                .mapNotNull { fact -> fact.normalized() },
            activeConcerns = snapshot.concerns
                .asSequence()
                .filter { concern ->
                    concern.assistantId == assistantId &&
                        concern.status == CompanionConcernStatus.ACTIVE &&
                        concern.isStillRelevant(input.nowMillis)
                }
                .sortedWith(
                    compareByDescending<CompanionConcern> { it.importance }
                        .thenBy { it.nextPerceptionAt ?: Long.MAX_VALUE }
                        .thenByDescending { it.lastUpdatedAt },
                )
                .take(MAX_ACTIVE_CONCERNS)
                .toList(),
            actionableCommitments = snapshot.commitments
                .asSequence()
                .filter { commitment ->
                    commitment.assistantId == assistantId && commitment.status.isActionable()
                }
                .sortedWith(
                    compareBy<CompanionCommitment> { it.dueAt }
                        .thenBy { it.createdAt }
                        .thenBy { it.id },
                )
                .take(MAX_ACTIONABLE_COMMITMENTS)
                .toList(),
            activeGoals = snapshot.goals.ifEmpty { defaultCompanionGoals(assistantId) }
                .asSequence()
                .filter { goal -> goal.assistantId == assistantId && goal.status == CompanionGoalStatus.ACTIVE }
                .sortedByDescending { it.updatedAt }
                .take(MAX_ACTIVE_GOALS)
                .toList(),
            recentLifeEvents = snapshot.lifeEvents
                .asSequence()
                .filter { event ->
                    event.assistantId == assistantId &&
                        event.isMeaningfulDigitalLifeEvidence()
                }
                .sortedByDescending { event -> event.endedAt ?: event.startedAt }
                .take(MAX_RECENT_LIFE_EVENTS)
                .toList(),
            availableToolNames = input.availableToolNames
                .asSequence()
                .map { it.trim() }
                .filter(String::isNotBlank)
                .distinct()
                .sorted()
                .take(MAX_TOOL_NAMES)
                .toCollection(linkedSetOf()),
            memoryContext = input.memoryContext.clean(MAX_MEMORY_CONTEXT_LENGTH),
            nowMillis = input.nowMillis,
        )
    }

    private fun CompanionConversationTurn.normalized(): CompanionConversationTurn? {
        val cleanContent = content.clean(MAX_TURN_CONTENT_LENGTH)
        if (cleanContent.isBlank()) return null
        return copy(
            content = cleanContent,
            sourceId = sourceId?.trim()?.takeIf(String::isNotBlank),
        )
    }

    private fun CompanionContextFact.normalized(): CompanionContextFact? {
        val cleanKey = key.clean(MAX_CONTEXT_KEY_LENGTH)
        val cleanValue = value.clean(MAX_CONTEXT_VALUE_LENGTH)
        if (cleanKey.isBlank() || cleanValue.isBlank()) return null
        return copy(key = cleanKey, value = cleanValue)
    }

    private fun CompanionCommitmentStatus.isActionable(): Boolean = this in setOf(
        CompanionCommitmentStatus.ACTIVE,
        CompanionCommitmentStatus.DUE,
        CompanionCommitmentStatus.EXECUTING,
        CompanionCommitmentStatus.RETRY_SCHEDULED,
    )

    /**
     * Concerns without fresh evidence should not stay in the character's foreground forever.
     * Explicitly scheduled follow-ups remain visible, while unscheduled concerns naturally
     * fade according to their importance.
     */
    private fun CompanionConcern.isStillRelevant(nowMillis: Long): Boolean {
        if (nextPerceptionAt != null) return true
        val ageMillis = (nowMillis - lastUpdatedAt).coerceAtLeast(0L)
        val retentionMillis = when (importance.coerceIn(1, 5)) {
            5 -> 90L * MILLIS_PER_DAY
            4 -> 45L * MILLIS_PER_DAY
            3 -> 21L * MILLIS_PER_DAY
            2 -> 10L * MILLIS_PER_DAY
            else -> 5L * MILLIS_PER_DAY
        }
        return ageMillis <= retentionMillis
    }

    private fun String.clean(maxLength: Int): String = trim()
        .replace(Regex("\\s+"), " ")
        .take(maxLength)

    private const val MAX_ASSISTANT_NAME_LENGTH = 120
    // Keep the full selected role/world-book context available to planner calls.
    private const val MAX_PERSONA_LENGTH = 16_000
    private const val MAX_MEMORY_CONTEXT_LENGTH = 6_000
    private const val MAX_RECENT_TURNS = 12
    private const val MAX_TURN_CONTENT_LENGTH = 1_000
    private const val MAX_CONTEXT_FACTS = 32
    private const val MAX_CONTEXT_KEY_LENGTH = 80
    private const val MAX_CONTEXT_VALUE_LENGTH = 500
    private const val MAX_ACTIVE_CONCERNS = 50
    private const val MAX_ACTIONABLE_COMMITMENTS = 50
    private const val MAX_ACTIVE_GOALS = 12
    private const val MAX_RECENT_LIFE_EVENTS = 20
    private const val MAX_TOOL_NAMES = 64
    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L
}

fun CompanionPerceptionPacket.toPromptContext(): String = buildString {
    appendLine("<companion_runtime assistant_id=\"$assistantId\">")
    val zoneId = ZoneId.systemDefault()
    val absoluteTime = Instant.ofEpochMilli(nowMillis)
        .atZone(zoneId)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX", Locale.ROOT))
    appendLine("current_time=$absoluteTime timezone=${zoneId.id} epoch_ms=$nowMillis")
    appendLine("persona_priority=The user-defined core persona, relationship type, worldview, speaking style, and explicit boundaries are immutable highest-priority constraints. Runtime mood and relationship values may only modulate behavior inside that persona; they must never replace it with a generic affectionate companion.")
    snapshot.continuity.takeIf { continuity ->
        continuity.updatedAt > 0L &&
            (continuity.lastUserText.isNotBlank() || continuity.lastAssistantText.isNotBlank())
    }?.let { continuity ->
        appendLine("cross_modal_continuity conversation=${continuity.conversationId.orEmpty()} modality=${continuity.modality.name} at=${continuity.updatedAt}")
        continuity.lastUserText.takeIf(String::isNotBlank)?.let { appendLine("- previous_user=${it.take(800)}") }
        continuity.lastAssistantText.takeIf(String::isNotBlank)?.let { appendLine("- previous_assistant=${it.take(800)}") }
        appendLine("- instruction=Continue naturally when the latest input follows this exchange. Preserve prior stance, promises, unresolved threads, and forms of address unless the user corrects them. Never recite this record.")
    }
    appendLine(
        "relationship trust=${snapshot.relationship.trust} closeness=${snapshot.relationship.closeness} " +
            "reliability=${snapshot.relationship.reliability} tension=${snapshot.relationship.unresolvedTension}",
    )
    appendLine(snapshot.relationship.toBehaviorContract())
    appendLine(
        "digital_neuro dopamine=${snapshot.neuroState.dopamine} serotonin=${snapshot.neuroState.serotonin} " +
            "cortisol=${snapshot.neuroState.cortisol} oxytocin=${snapshot.neuroState.oxytocin} " +
            "focus=${snapshot.neuroState.norepinephrine} energy=${snapshot.neuroState.energy}",
    )
    buildCompanionCurrentLifeThread(snapshot, nowMillis)
        .takeIf(String::isNotBlank)
        ?.let(::appendLine)
    snapshot.privateImpression.takeIf { impression ->
        impression.summary.isNotBlank() ||
            impression.relationshipNarrative.isNotBlank() ||
            impression.userPortrait.isNotBlank() ||
            impression.interactionUnderstanding.isNotBlank() ||
            impression.unresolvedMatters.isNotEmpty() ||
            impression.observedTraits.isNotEmpty() ||
            impression.preferences.isNotEmpty() ||
            impression.boundaries.isNotEmpty() ||
            impression.recentChanges.isNotEmpty()
    }?.let { impression ->
        appendLine("private_impression:")
        impression.relationshipTitle.takeIf(String::isNotBlank)?.let { appendLine("- relationship_title=${it.take(160)}") }
        impression.relationshipNarrative.takeIf(String::isNotBlank)?.let { appendLine("- relationship_narrative=${it.take(600)}") }
        impression.userPortrait.takeIf(String::isNotBlank)?.let { appendLine("- user_portrait=${it.take(600)}") }
        impression.interactionUnderstanding.takeIf(String::isNotBlank)?.let { appendLine("- interaction_understanding=${it.take(600)}") }
        impression.unresolvedMatters.takeLast(3).forEach { appendLine("- unresolved_matter=${it.take(240)}") }
        impression.summary.takeIf(String::isNotBlank)?.let { appendLine("- legacy_summary=${it.take(300)}") }
        impression.observedTraits.take(4).forEach { appendLine("- observed_trait=${it.take(180)}") }
        impression.preferences.take(4).forEach { appendLine("- preference=${it.take(180)}") }
        impression.boundaries.take(4).forEach { appendLine("- boundary=${it.take(180)}") }
        impression.recentChanges.takeLast(3).forEach { appendLine("- recent_change=${it.take(180)}") }
    }
    buildCompanionResponsibilityContext(snapshot.alwaysOnAnchors, nowMillis)
        .takeIf(String::isNotBlank)
        ?.let(::appendLine)
    if (snapshot.state.statusText.isNotBlank() || snapshot.state.innerThought.isNotBlank()) {
        appendLine(
            "state status=${snapshot.state.statusText.take(120)} " +
                "mood=${snapshot.state.mood.take(80)} body=${snapshot.state.bodyState.take(80)} " +
                "mind=${snapshot.state.mindState.take(80)}",
        )
        snapshot.state.innerThought.takeIf { it.isNotBlank() }?.let { thought ->
            appendLine("unspoken=${thought.take(300)}")
        }
    }
    if (activeConcerns.isNotEmpty()) {
        appendLine("active_concerns:")
        activeConcerns.take(8).forEach { concern ->
            appendLine(
                "- subject=${concern.subjectKey} importance=${concern.importance} " +
                    "next=${concern.nextPerceptionAt ?: "none"} " +
                    "overdue=${concern.nextPerceptionAt?.let { it <= nowMillis } == true} " +
                    "goal=${concern.goal.take(180)}",
            )
        }
    }
    if (actionableCommitments.isNotEmpty()) {
        appendLine("active_commitments:")
        actionableCommitments.take(8).forEach { commitment ->
            appendLine(
                "- id=${commitment.id} due=${commitment.dueAt} status=${commitment.status.name} " +
                    "overdue=${commitment.dueAt <= nowMillis} promise=${commitment.promise.take(180)} " +
                    "last_result=${commitment.lastActionResult?.summary?.take(300).orEmpty()}",
            )
        }
    }
    if (activeGoals.isNotEmpty()) {
        appendLine("active_self_goals:")
        activeGoals.take(6).forEach { goal ->
            appendLine("- id=${goal.id} category=${goal.category} progress=${goal.progress} goal=${goal.title.take(180)}")
        }
    }
    if (recentLifeEvents.isNotEmpty()) {
        appendLine("recent_digital_life:")
        recentLifeEvents.take(8).forEach { event ->
            appendLine(
                "- at=${event.endedAt ?: event.startedAt} type=${event.type.name} status=${event.status.name} " +
                    "title=${event.title.take(120)} summary=${event.summary.take(240)} evidence=${event.evidenceReference.orEmpty().take(120)}",
            )
        }
        appendLine("Only describe an activity as actually completed when it appears here with COMPLETED status or is backed by a current tool result.")
    }
    if (contextFacts.isNotEmpty()) {
        appendLine("perception_facts:")
        contextFacts.take(16).forEach { fact ->
            appendLine("- ${fact.key} observed_at=${fact.observedAt} value=${fact.value.take(500)}")
        }
    }
    appendLine("</companion_runtime>")
    append("Treat this runtime snapshot as current business truth. Ordinary chat never cancels unrelated commitments. It supplies continuity and evidence only; it does not authorize changing the configured persona or inventing intimacy.")
}.trim()
