package me.rerere.rikkahub.ui.pages.cihai

import me.rerere.rikkahub.data.companion.CompanionActionType
import me.rerere.rikkahub.data.companion.CompanionCommitment
import me.rerere.rikkahub.data.companion.CompanionCommitmentStatus
import me.rerere.rikkahub.data.companion.CompanionConcern
import me.rerere.rikkahub.data.companion.CompanionConcernStatus
import me.rerere.rikkahub.data.companion.CompanionSnapshot
import me.rerere.rikkahub.data.companion.normalizeCompanionSubjectKey
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

data class CompanionConcernCardModel(
    val id: String,
    val subjectKeys: Set<String>,
    val title: String,
    val nextPerceptionText: String,
    val statusText: String,
    val eventText: String,
    val goalText: String,
    val commitmentText: String?,
    val overdue: Boolean,
)

fun buildCompanionConcernCards(
    snapshot: CompanionSnapshot,
    nowMillis: Long = System.currentTimeMillis(),
): List<CompanionConcernCardModel> {
    val concerns = snapshot.concerns.filter { concern ->
        concern.assistantId == snapshot.assistantId &&
            concern.status in setOf(CompanionConcernStatus.ACTIVE, CompanionConcernStatus.PAUSED)
    }.collapseLegacyConcerns()
    val commitments = snapshot.commitments.filter { commitment ->
        commitment.assistantId == snapshot.assistantId && commitment.status.isVisibleConcernCommitment()
    }.collapseLegacyCommitments()
    val representedCommitmentIds = mutableSetOf<String>()
    val concernCards = concerns.map { concern ->
        val commitment = commitments
            .asSequence()
            .filterNot { it.id in representedCommitmentIds }
            .firstOrNull { it.normalizedSubjectKey() == concern.normalizedSubjectKey() }
            ?: commitments
                .asSequence()
                .filterNot { it.id in representedCommitmentIds }
                .firstOrNull { it.sharesSourceMessageWith(concern) }
            ?: commitments
                .asSequence()
                .filterNot { it.id in representedCommitmentIds }
                .firstOrNull { it.semanticallyMatches(concern) }
        commitment
            ?.also { representedCommitmentIds += it.id }
        concern.toCardModel(commitment, nowMillis)
    }
    val commitmentOnlyCards = commitments
        .filterNot { it.id in representedCommitmentIds }
        .map { it.toCardModel(nowMillis) }

    return (concernCards + commitmentOnlyCards)
        .sortedWith(compareBy<SortableConcernCard> { it.sortAt }.thenByDescending { it.importance })
        .take(12)
        .map { it.card }
}

private fun List<CompanionConcern>.collapseLegacyConcerns(): List<CompanionConcern> =
    groupBy { it.normalizedSubjectKey() }
        .map { (subjectKey, duplicates) ->
            val selected = duplicates.maxWith(
                compareBy<CompanionConcern> { it.status == CompanionConcernStatus.ACTIVE }
                    .thenBy { it.lastUpdatedAt }
                    .thenBy { it.createdAt },
            )
            selected.copy(
                subjectKey = subjectKey,
                status = if (duplicates.any { it.status == CompanionConcernStatus.ACTIVE }) {
                    CompanionConcernStatus.ACTIVE
                } else {
                    CompanionConcernStatus.PAUSED
                },
                nextPerceptionAt = duplicates.mapNotNull { it.nextPerceptionAt }.minOrNull(),
                sourceMessageIds = duplicates
                    .flatMap { it.sourceMessageIds }
                    .filter(String::isNotBlank)
                    .distinct(),
                createdAt = duplicates.minOf { it.createdAt },
                lastUpdatedAt = duplicates.maxOf { it.lastUpdatedAt },
            )
        }

private fun List<CompanionCommitment>.collapseLegacyCommitments(): List<CompanionCommitment> =
    groupBy { it.normalizedSubjectKey() }
        .map { (subjectKey, duplicates) ->
            duplicates.minWith(
                compareBy<CompanionCommitment> { it.status.visiblePriority() }
                    .thenBy { it.dueAt }
                    .thenByDescending { it.updatedAt },
            ).copy(subjectKey = subjectKey)
        }

private fun CompanionConcern.normalizedSubjectKey(): String = normalizeCompanionSubjectKey(subjectKey)

private fun CompanionCommitment.normalizedSubjectKey(): String = normalizeCompanionSubjectKey(subjectKey)

private fun CompanionCommitment.sharesSourceMessageWith(concern: CompanionConcern): Boolean =
    sourceMessageId?.takeIf(String::isNotBlank)?.let { it in concern.sourceMessageIds } == true

private fun CompanionCommitment.semanticallyMatches(concern: CompanionConcern): Boolean {
    val concernAt = concern.nextPerceptionAt ?: return false
    val concernFamily = listOf(concern.subjectKey, concern.event, concern.goal).companionConcernFamily()
        ?: return false
    val commitmentFamily = listOf(
        subjectKey,
        promise,
        actionPlan.category,
        actionPlan.contextText,
    ).companionConcernFamily() ?: return false
    if (concernFamily != commitmentFamily) return false
    val toleranceMillis = if (concernFamily == "time") 60_000L else 5 * 60_000L
    return abs(dueAt - concernAt) <= toleranceMillis
}

private fun List<String>.companionConcernFamily(): String? {
    val text = joinToString(" ").lowercase()
    return when {
        listOf("wake", "起床", "叫醒").any { marker -> marker in text } -> "wake"
        listOf("sleep", "休息", "睡觉", "入睡").any { marker -> marker in text } -> "sleep"
        listOf("study", "学习", "复习", "背书").any { marker -> marker in text } -> "study"
        listOf("health", "身体", "健康", "不舒服", "吃药").any { marker -> marker in text } -> "health"
        listOf("meal", "吃饭", "早餐", "午饭", "晚饭").any { marker -> marker in text } -> "meal"
        listOf("schedule", "deadline", "reminder", "time", "时间", "提醒", "约定", "到点")
            .any { marker -> marker in text } -> "time"
        else -> null
    }
}

private fun CompanionCommitmentStatus.visiblePriority(): Int = when (this) {
    CompanionCommitmentStatus.EXECUTING -> 0
    CompanionCommitmentStatus.DUE -> 1
    CompanionCommitmentStatus.RETRY_SCHEDULED -> 2
    CompanionCommitmentStatus.ACTIVE -> 3
    CompanionCommitmentStatus.PROPOSED -> 4
    else -> 5
}

private data class SortableConcernCard(
    val card: CompanionConcernCardModel,
    val sortAt: Long,
    val importance: Int,
)

private fun CompanionConcern.toCardModel(
    commitment: CompanionCommitment?,
    nowMillis: Long,
): SortableConcernCard {
    val targetAt = nextPerceptionAt ?: commitment?.dueAt
    val isOverdue = targetAt != null && targetAt <= nowMillis
    return SortableConcernCard(
        card = CompanionConcernCardModel(
            id = "concern:$id",
            subjectKeys = listOfNotNull(subjectKey, commitment?.subjectKey).toSet(),
            title = commitment.concernTitle(),
            nextPerceptionText = targetAt.toPerceptionText(nowMillis),
            statusText = when {
                commitment?.status == CompanionCommitmentStatus.EXECUTING -> "正在处理"
                commitment?.status == CompanionCommitmentStatus.RETRY_SCHEDULED -> "等待重试"
                isOverdue -> "已经到点"
                status == CompanionConcernStatus.PAUSED -> "暂缓留意"
                else -> "挂心中"
            },
            eventText = event.trim().ifBlank { commitment?.promise.orEmpty().trim() },
            goalText = goal.trim(),
            commitmentText = commitment?.promise
                ?.trim()
                ?.takeIf { it.isNotBlank() && it != event.trim() && it != goal.trim() },
            overdue = isOverdue,
        ),
        sortAt = targetAt ?: Long.MAX_VALUE,
        importance = importance,
    )
}

private fun CompanionCommitment.toCardModel(nowMillis: Long): SortableConcernCard {
    val isOverdue = dueAt <= nowMillis
    return SortableConcernCard(
        card = CompanionConcernCardModel(
            id = "commitment:$id",
            subjectKeys = setOf(subjectKey),
            title = concernTitle(),
            nextPerceptionText = dueAt.toPerceptionText(nowMillis),
            statusText = when (status) {
                CompanionCommitmentStatus.EXECUTING -> "正在处理"
                CompanionCommitmentStatus.RETRY_SCHEDULED -> "等待重试"
                else -> if (isOverdue) "已经到点" else "挂心中"
            },
            eventText = promise.trim(),
            goalText = actionPlan.userFacingSummary.trim().takeIf { it != promise.trim() }.orEmpty(),
            commitmentText = null,
            overdue = isOverdue,
        ),
        sortAt = dueAt,
        importance = 3,
    )
}

private fun CompanionCommitment?.concernTitle(): String {
    val commitment = this ?: return "挂心事项"
    return when {
        commitment.actionPlan.category.contains("wake", ignoreCase = true) -> "起床提醒"
        commitment.actionPlan.category.contains("study", ignoreCase = true) -> "学习节奏"
        commitment.actionPlan.category.contains("health", ignoreCase = true) -> "身体与安全"
        commitment.actionPlan.category.contains("deadline", ignoreCase = true) -> "时间提醒"
        commitment.actionPlan.category.contains("schedule", ignoreCase = true) -> "时间提醒"
        commitment.actionPlan.type == CompanionActionType.ALARM -> "起床提醒"
        commitment.actionPlan.type == CompanionActionType.CALENDAR -> "日程安排"
        commitment.actionPlan.type == CompanionActionType.REMINDER -> "时间提醒"
        commitment.actionPlan.type == CompanionActionType.CHECK_IN -> "持续关心"
        else -> "挂心事项"
    }
}

private fun CompanionCommitmentStatus.isVisibleConcernCommitment(): Boolean = this in setOf(
    CompanionCommitmentStatus.PROPOSED,
    CompanionCommitmentStatus.ACTIVE,
    CompanionCommitmentStatus.DUE,
    CompanionCommitmentStatus.EXECUTING,
    CompanionCommitmentStatus.RETRY_SCHEDULED,
)

private fun Long?.toPerceptionText(nowMillis: Long): String {
    if (this == null || this <= 0L) return "持续留意，没有固定时间"
    val absoluteTime = Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
    return if (this <= nowMillis) {
        "原定留意时间：$absoluteTime"
    } else {
        "计划留意时间：$absoluteTime"
    }
}
