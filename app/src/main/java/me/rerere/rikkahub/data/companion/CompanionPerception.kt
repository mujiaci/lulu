package me.rerere.rikkahub.data.companion

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
                    concern.assistantId == assistantId && concern.status == CompanionConcernStatus.ACTIVE
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
}
