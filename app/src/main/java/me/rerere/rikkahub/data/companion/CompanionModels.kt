package me.rerere.rikkahub.data.companion

import kotlinx.serialization.Serializable
import java.util.UUID

const val CURRENT_COMPANION_SCHEMA_VERSION = 1

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
    val relationship: CompanionRelationshipState = CompanionRelationshipState(),
    val concerns: List<CompanionConcern> = emptyList(),
    val commitments: List<CompanionCommitment> = emptyList(),
    val updatedAt: Long = 0L,
) {
    companion object {
        fun empty(assistantId: String): CompanionSnapshot = CompanionSnapshot(assistantId = assistantId)
    }
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
data class CompanionRelationshipState(
    val roleLabel: String = "",
    val familiarity: Float = 0f,
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
    val familiarityDelta: Float = 0f,
    val trustDelta: Float = 0f,
    val closenessDelta: Float = 0f,
    val reliabilityDelta: Float = 0f,
    val boundaryDelta: Float = 0f,
    val tensionDelta: Float = 0f,
    val evidence: String,
    val createdAt: Long = System.currentTimeMillis(),
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

@Serializable
data class CompanionActionPlan(
    val type: CompanionActionType = CompanionActionType.NONE,
    val toolName: String? = null,
    val argumentsJson: String = "{}",
    val userFacingSummary: String = "",
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
