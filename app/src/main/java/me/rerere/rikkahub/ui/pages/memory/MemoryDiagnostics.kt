package me.rerere.rikkahub.ui.pages.memory

import me.rerere.rikkahub.data.db.entity.MemoryExtractionBatchEntity
import me.rerere.rikkahub.data.db.entity.MemoryExtractionBatchStatus

/**
 * User-facing explanation of why memory extraction did or did not run.
 *
 * Keep this calculation deterministic and independent from Compose so the same
 * truth can be shown in the memory page, logs, and tests.
 */
data class MemoryTriggerDiagnostic(
    val totalMessageCount: Int,
    val protectedRecentCount: Int,
    val eligibleMessageCount: Int,
    val batchSize: Int,
    val stableRegionEnd: Int,
    val successfulThrough: Int,
    val pendingMessageCount: Int,
    val messagesUntilNextBatch: Int,
    val state: MemoryTriggerState,
    val explanation: String,
    val successfulBatchCount: Int,
    val emptyBatchCount: Int,
    val retryableFailureCount: Int,
    val manualReviewCount: Int,
)

enum class MemoryTriggerState {
    WAITING_FOR_MESSAGES,
    READY,
    PROCESSING_OR_RETRYING,
    UP_TO_DATE,
}

fun buildMemoryTriggerDiagnostic(
    totalMessageCount: Int,
    protectedRecentCount: Int,
    batchSize: Int,
    successfulThrough: Int,
    batches: List<MemoryExtractionBatchEntity>,
): MemoryTriggerDiagnostic {
    val safeTotal = totalMessageCount.coerceAtLeast(0)
    val safeProtected = protectedRecentCount.coerceAtLeast(0)
    val safeBatchSize = batchSize.coerceAtLeast(1)
    val eligible = (safeTotal - safeProtected).coerceAtLeast(0)
    val stableEnd = (eligible / safeBatchSize) * safeBatchSize
    val safeSuccessfulThrough = successfulThrough.coerceIn(0, stableEnd)
    val pending = (stableEnd - safeSuccessfulThrough).coerceAtLeast(0)
    val remainder = eligible % safeBatchSize
    val untilNext = if (pending > 0) 0 else (safeBatchSize - remainder).let { needed ->
        if (needed == safeBatchSize) safeBatchSize else needed
    }

    val retryable = batches.count { it.status == MemoryExtractionBatchStatus.FAILED_RETRYABLE.name }
    val manualReview = batches.count { it.status == MemoryExtractionBatchStatus.FAILED_MANUAL_REVIEW.name }
    val successful = batches.count { it.status == MemoryExtractionBatchStatus.SUCCESS_WITH_MEMORIES.name }
    val empty = batches.count { it.status == MemoryExtractionBatchStatus.SUCCESS_EMPTY.name }

    val state = when {
        retryable > 0 || manualReview > 0 -> MemoryTriggerState.PROCESSING_OR_RETRYING
        pending > 0 -> MemoryTriggerState.READY
        eligible < safeBatchSize -> MemoryTriggerState.WAITING_FOR_MESSAGES
        else -> MemoryTriggerState.UP_TO_DATE
    }

    val explanation = when (state) {
        MemoryTriggerState.WAITING_FOR_MESSAGES ->
            "当前共 $safeTotal 条消息，最近 $safeProtected 条受保护，可整理 $eligible 条；还差 ${safeBatchSize - eligible} 条才形成下一批。"
        MemoryTriggerState.READY ->
            "当前稳定区到 $stableEnd，已成功处理到 $safeSuccessfulThrough，还有 $pending 条等待整理。"
        MemoryTriggerState.PROCESSING_OR_RETRYING ->
            "存在失败或待人工复核批次：可重试 $retryable 批，需人工复核 $manualReview 批。"
        MemoryTriggerState.UP_TO_DATE ->
            "当前稳定区已全部处理；继续聊天后再积累 $untilNext 条可形成下一批。"
    }

    return MemoryTriggerDiagnostic(
        totalMessageCount = safeTotal,
        protectedRecentCount = safeProtected,
        eligibleMessageCount = eligible,
        batchSize = safeBatchSize,
        stableRegionEnd = stableEnd,
        successfulThrough = safeSuccessfulThrough,
        pendingMessageCount = pending,
        messagesUntilNextBatch = untilNext,
        state = state,
        explanation = explanation,
        successfulBatchCount = successful,
        emptyBatchCount = empty,
        retryableFailureCount = retryable,
        manualReviewCount = manualReview,
    )
}
