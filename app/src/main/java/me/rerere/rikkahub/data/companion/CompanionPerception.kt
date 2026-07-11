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
    private const val MAX_PERSONA_LENGTH = 4_000
    private const val MAX_MEMORY_CONTEXT_LENGTH = 6_000
    private const val MAX_RECENT_TURNS = 12
    private const val MAX_TURN_CONTENT_LENGTH = 1_000
    private const val MAX_CONTEXT_FACTS = 32
    private const val MAX_CONTEXT_KEY_LENGTH = 80
    private const val MAX_CONTEXT_VALUE_LENGTH = 500
    private const val MAX_ACTIVE_CONCERNS = 50
    private const val MAX_ACTIONABLE_COMMITMENTS = 50
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
    appendLine(
        "relationship trust=${snapshot.relationship.trust} closeness=${snapshot.relationship.closeness} " +
            "reliability=${snapshot.relationship.reliability} tension=${snapshot.relationship.unresolvedTension}",
    )
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
    if (contextFacts.isNotEmpty()) {
        appendLine("perception_facts:")
        contextFacts.take(16).forEach { fact ->
            appendLine("- ${fact.key} observed_at=${fact.observedAt} value=${fact.value.take(500)}")
        }
    }
    appendLine("</companion_runtime>")
    append("Treat this runtime snapshot as current business truth. Ordinary chat never cancels unrelated commitments.")
}.trim()
