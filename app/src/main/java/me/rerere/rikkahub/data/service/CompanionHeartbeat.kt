package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.companion.CompanionCommitmentStatus
import me.rerere.rikkahub.data.companion.CompanionConcernStatus
import me.rerere.rikkahub.data.companion.CompanionInteractionEvent
import me.rerere.rikkahub.data.companion.CompanionInteractionEventKind
import me.rerere.rikkahub.data.companion.CompanionOutboundStatus
import me.rerere.rikkahub.data.companion.CompanionSnapshot

data class CompanionHeartbeatDecision(
    val dueCommitmentIds: List<String>,
    val dueConcernIds: List<String>,
    val lifecycleEvents: List<CompanionInteractionEvent>,
    val minutesSinceUserActivity: Long,
    val shouldRunDeepPerception: Boolean,
    val reason: String,
)

/** Pure local heartbeat: checks clocks and due work without calling a model. */
object CompanionHeartbeatEvaluator {
    fun evaluate(snapshot: CompanionSnapshot, nowMillis: Long): CompanionHeartbeatDecision {
        val dueCommitmentIds = snapshot.commitments
            .filter { commitment ->
                commitment.status in setOf(
                    CompanionCommitmentStatus.ACTIVE,
                    CompanionCommitmentStatus.DUE,
                    CompanionCommitmentStatus.RETRY_SCHEDULED,
                ) && commitment.dueAt <= nowMillis
            }
            .map { it.id }
        val dueConcernIds = snapshot.concerns
            .filter { concern ->
                concern.status == CompanionConcernStatus.ACTIVE &&
                    concern.nextPerceptionAt?.let { it <= nowMillis } == true
            }
            .map { it.id }
        val lifecycleEvents = snapshot.interactionTimeline.outboundContacts
            .filter { contact ->
                contact.status in UNRESOLVED_OUTBOUND_STATUSES &&
                    nowMillis - contact.latestActivityAt() >= UNANSWERED_AFTER_MILLIS
            }
            .map { contact ->
                CompanionInteractionEvent(
                    kind = CompanionInteractionEventKind.OUTBOUND_UNANSWERED,
                    occurredAt = nowMillis,
                    contactId = contact.id,
                    conversationId = contact.conversationId,
                    sourceMessageId = contact.sourceMessageId,
                    detail = "no_user_feedback_yet",
                )
            }
        val idleMinutes = snapshot.interactionTimeline.lastUserActivityAt
            ?.let { ((nowMillis - it) / 60_000L).coerceAtLeast(0L) }
            ?: Long.MAX_VALUE
        val minutesSinceOutbound = snapshot.interactionTimeline.lastOutboundAt
            ?.let { ((nowMillis - it) / 60_000L).coerceAtLeast(0L) }
            ?: Long.MAX_VALUE
        val minutesSinceDeepState = snapshot.state.updatedAt
            .takeIf { it > 0L }
            ?.let { ((nowMillis - it) / 60_000L).coerceAtLeast(0L) }
            ?: Long.MAX_VALUE

        val reason = when {
            dueCommitmentIds.isNotEmpty() -> "due_commitment"
            dueConcernIds.isNotEmpty() -> "due_concern"
            idleMinutes >= 120L && minutesSinceOutbound >= 90L && minutesSinceDeepState >= 45L -> "meaningful_silence"
            else -> "local_heartbeat_only"
        }
        return CompanionHeartbeatDecision(
            dueCommitmentIds = dueCommitmentIds,
            dueConcernIds = dueConcernIds,
            lifecycleEvents = lifecycleEvents,
            minutesSinceUserActivity = idleMinutes,
            shouldRunDeepPerception = reason != "local_heartbeat_only",
            reason = reason,
        )
    }

    private fun me.rerere.rikkahub.data.companion.CompanionOutboundContact.latestActivityAt(): Long =
        listOfNotNull(openedAt, deliveredAt, sentAt, generatedAt).maxOrNull() ?: generatedAt

    private val UNRESOLVED_OUTBOUND_STATUSES = setOf(
        CompanionOutboundStatus.GENERATED,
        CompanionOutboundStatus.SENT,
        CompanionOutboundStatus.DELIVERED,
        CompanionOutboundStatus.OPENED,
    )
    private const val UNANSWERED_AFTER_MILLIS = 90L * 60L * 1_000L
}
