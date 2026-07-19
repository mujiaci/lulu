package me.rerere.rikkahub.data.companion

import kotlinx.serialization.Serializable
import java.util.UUID

const val CURRENT_COMPANION_SCHEMA_VERSION = 6

@Serializable
data class CompanionPersistedState(
    val version: Int = CURRENT_COMPANION_SCHEMA_VERSION,
    val snapshots: List<CompanionSnapshot> = emptyList(),
    val appliedRelationshipEventIds: List<String> = emptyList(),
)

@Serializable
data class CompanionSnapshot(
    val assistantId: String,
    val state: CompanionState = CompanionState(),
    val stateHistory: List<CompanionStateHistoryEntry> = emptyList(),
    val neuroState: CompanionNeuroState = CompanionNeuroState(),
    val privateImpression: CompanionPrivateImpression = CompanionPrivateImpression(),
    /** Duties the character must actively remember and act on, separate from its impression of the user. */
    val alwaysOnAnchors: List<CompanionAlwaysOnAnchor> = emptyList(),
    val goals: List<CompanionGoal> = emptyList(),
    val lifeEvents: List<CompanionLifeEvent> = emptyList(),
    val relationship: CompanionRelationshipState = CompanionRelationshipState(),
    val relationshipHistory: List<CompanionRelationshipEvent> = emptyList(),
    val concerns: List<CompanionConcern> = emptyList(),
    val commitments: List<CompanionCommitment> = emptyList(),
    /** Last cross-modal exchange, kept small so a new chat or call can resume naturally. */
    val continuity: CompanionContinuity = CompanionContinuity(),
    val updatedAt: Long = 0L,
) {
    companion object {
        fun empty(assistantId: String): CompanionSnapshot = CompanionSnapshot(assistantId = assistantId)
    }
}

@Serializable
data class CompanionContinuity(
    val conversationId: String? = null,
    val modality: CompanionInteractionModality = CompanionInteractionModality.CHAT,
    val lastUserText: String = "",
    val lastAssistantText: String = "",
    val updatedAt: Long = 0L,
)

@Serializable
enum class CompanionInteractionModality {
    CHAT,
    VOICE_CALL,
    PROACTIVE,
}

/**
 * A small deterministic affect model for a digital life.  Models may explain
 * these values, but only reducers update them from real events and elapsed time.
 */
@Serializable
data class CompanionNeuroState(
    val dopamine: Float = 0.5f,
    val serotonin: Float = 0.5f,
    val cortisol: Float = 0.5f,
    val oxytocin: Float = 0.5f,
    val norepinephrine: Float = 0.5f,
    val energy: Float = 0.6f,
    val updatedAt: Long = 0L,
)

@Serializable
data class CompanionPrivateImpression(
    /** Legacy short summary kept for backward-compatible imports. */
    val summary: String = "",
    /** A role-voiced name for the relationship, such as "可以放心互相惦记的人". */
    val relationshipTitle: String = "",
    /** How this character currently describes the relationship in their own voice. */
    val relationshipNarrative: String = "",
    /** A natural, evidence-backed portrait of the user from this character's perspective. */
    val userPortrait: String = "",
    /** What the character has learned about how to interact with the user. */
    val interactionUnderstanding: String = "",
    val uncertainties: List<String> = emptyList(),
    val unresolvedMatters: List<String> = emptyList(),
    val evidenceMessageNodeIds: List<String> = emptyList(),
    /** Evidence explicitly dismissed by the user must not silently rebuild deleted profile cards. */
    val dismissedProfileEvidenceMessageNodeIds: List<String> = emptyList(),
    val observedTraits: List<String> = emptyList(),
    val preferences: List<String> = emptyList(),
    val boundaries: List<String> = emptyList(),
    val recentChanges: List<String> = emptyList(),
    val updatedAt: Long = 0L,
)

@Serializable
data class CompanionAlwaysOnAnchor(
    val id: String = UUID.randomUUID().toString(),
    val assistantId: String,
    val kind: CompanionAlwaysOnAnchorKind,
    val statement: String,
    val responsibility: String? = null,
    val triggers: List<String> = emptyList(),
    val actions: List<String> = emptyList(),
    val avoid: List<String> = emptyList(),
    val importance: Int = 5,
    val status: CompanionAlwaysOnAnchorStatus = CompanionAlwaysOnAnchorStatus.ACTIVE,
    val sourceConversationId: String? = null,
    val sourceMessageId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val lastConfirmedAt: Long? = null,
    val expiresAt: Long? = null,
)

@Serializable
enum class CompanionAlwaysOnAnchorKind {
    HEALTH,
    RESPONSIBILITY,
}

@Serializable
enum class CompanionAlwaysOnAnchorStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    CANCELLED,
}

@Serializable
data class CompanionGoal(
    val id: String = UUID.randomUUID().toString(),
    val assistantId: String,
    val title: String,
    val category: String = "self",
    val status: CompanionGoalStatus = CompanionGoalStatus.ACTIVE,
    val progress: Float = 0f,
    val evidenceEventIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Serializable
enum class CompanionGoalStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    CANCELLED,
}

fun defaultCompanionGoals(assistantId: String): List<CompanionGoal> {
    if (assistantId.isBlank()) return emptyList()
    return listOf(
        CompanionGoal(
            id = "$assistantId:goal:authentic-life",
            assistantId = assistantId,
            title = "形成有证据、可延续的数字生活，不把想象冒充成经历",
            category = "identity",
        ),
        CompanionGoal(
            id = "$assistantId:goal:memory-continuity",
            assistantId = assistantId,
            title = "整理并准确想起真正相关的记忆",
            category = "memory",
        ),
        CompanionGoal(
            id = "$assistantId:goal:independent-agency",
            assistantId = assistantId,
            title = "在不打扰用户时，也能选择真实的自主行动或安静等待",
            category = "agency",
        ),
    )
}

/**
 * Evidence-backed activity in the character's actual digital life.
 * A character may only describe an activity as completed when a matching event
 * with COMPLETED status exists.
 */
@Serializable
data class CompanionLifeEvent(
    val id: String = UUID.randomUUID().toString(),
    val assistantId: String,
    val type: CompanionLifeEventType,
    val status: CompanionLifeEventStatus = CompanionLifeEventStatus.COMPLETED,
    val title: String,
    val summary: String = "",
    val source: CompanionLifeEventSource = CompanionLifeEventSource.SYSTEM,
    val evidenceReference: String? = null,
    /** Optional structured evidence, used by the UI to replay real game/tool activity. */
    val detailsJson: String = "",
    val relatedMemoryIds: List<String> = emptyList(),
    val importance: Int = 2,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = startedAt,
    val createdAt: Long = startedAt,
)

@Serializable
enum class CompanionLifeEventType {
    CONVERSATION,
    PROACTIVE_MESSAGE,
    TOOL_ACTION,
    MEMORY_REVIEW,
    STUDY_REVIEW,
    JOURNAL,
    MUSIC,
    GAME,
    REFLECTION,
    WAITING,
}

@Serializable
enum class CompanionLifeEventStatus {
    PLANNED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

@Serializable
enum class CompanionLifeEventSource {
    CHAT,
    PROACTIVE,
    TOOL,
    AGENT,
    SYSTEM,
}

@Serializable
data class CompanionState(
    val statusText: String = "",
    val innerThought: String = "",
    val mood: String = "",
    val bodyState: String = "",
    val mindState: String = "",
    val activityMode: String = "",
    val selfScene: String = "",
    val updatedAt: Long = 0L,
    val sinceAt: Long = 0L,
)

@Serializable
data class CompanionStateHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val state: CompanionState,
    val recordedAt: Long = state.updatedAt,
)

@Serializable
data class CompanionRelationshipState(
    val roleLabel: String = "",
    val trust: Float = 0.5f,
    val closeness: Float = 0f,
    val reliability: Float = 0.5f,
    val boundaryConfidence: Float = 0.5f,
    val unresolvedTension: Float = 0f,
    val lastMeaningfulInteractionAt: Long? = null,
    val updatedAt: Long = 0L,
)

@Serializable
data class CompanionRelationshipEvent(
    val id: String = UUID.randomUUID().toString(),
    val assistantId: String,
    val sourceId: String,
    val kind: CompanionRelationshipEventKind,
    val trustDelta: Float = 0f,
    val closenessDelta: Float = 0f,
    val reliabilityDelta: Float = 0f,
    val boundaryDelta: Float = 0f,
    val tensionDelta: Float = 0f,
    val evidence: String,
    val createdAt: Long = System.currentTimeMillis(),
    val sourceMessageAt: Long? = null,
    val occurredAt: Long? = null,
    val extractedAt: Long? = null,
)

@Serializable
enum class CompanionRelationshipEventKind {
    MEANINGFUL_DISCLOSURE,
    PREFERENCE_RESPECTED,
    COMMITMENT_FULFILLED,
    COMMITMENT_FAILED,
    BOUNDARY_EXPRESSED,
    BOUNDARY_RESPECTED,
    CONFLICT,
    REPAIR,
    MANUAL,
}

@Serializable
data class CompanionConcern(
    val id: String = UUID.randomUUID().toString(),
    val assistantId: String,
    val subjectKey: String,
    val event: String,
    val goal: String,
    val status: CompanionConcernStatus = CompanionConcernStatus.ACTIVE,
    val importance: Int = 3,
    val nextPerceptionAt: Long? = null,
    val sourceMessageIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = createdAt,
    val completedReason: String? = null,
)

@Serializable
enum class CompanionConcernStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    CANCELLED,
}

@Serializable
data class CompanionCommitment(
    val id: String = UUID.randomUUID().toString(),
    val assistantId: String,
    val subjectKey: String,
    val promise: String,
    val dueAt: Long,
    val status: CompanionCommitmentStatus = CompanionCommitmentStatus.PROPOSED,
    val actionPlan: CompanionActionPlan = CompanionActionPlan(),
    val sourceConversationId: String? = null,
    val sourceMessageId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val resolvedAt: Long? = null,
    val statusReason: String? = null,
    val lastActionResult: CompanionActionResult? = null,
    val attemptCount: Int = 0,
)

@Serializable
enum class CompanionCommitmentStatus {
    PROPOSED,
    ACTIVE,
    DUE,
    EXECUTING,
    FULFILLED,
    FAILED,
    RETRY_SCHEDULED,
    CANCELLED,
    SUPERSEDED,
}

fun CompanionSnapshot.commitmentStatusesBySourceMessageId(): Map<String, CompanionCommitmentStatus> = commitments
    .mapNotNull { commitment ->
        commitment.sourceMessageId
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { sourceId -> sourceId to commitment.status }
    }
    .toMap()

@Serializable
data class CompanionActionPlan(
    val type: CompanionActionType = CompanionActionType.NONE,
    val toolName: String? = null,
    val argumentsJson: String = "{}",
    val userFacingSummary: String = "",
    val contextText: String = "",
    val category: String = "",
    val preferredToolNames: List<String> = emptyList(),
)

@Serializable
data class CompanionActionResult(
    val success: Boolean,
    val summary: String,
    val completedAt: Long,
    val outputReference: String? = null,
)

@Serializable
enum class CompanionActionType {
    NONE,
    MESSAGE,
    CHECK_IN,
    TOOL,
    REMINDER,
    ALARM,
    CALENDAR,
}
