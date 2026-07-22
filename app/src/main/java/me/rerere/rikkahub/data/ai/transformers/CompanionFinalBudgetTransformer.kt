package me.rerere.rikkahub.data.ai.transformers

import me.rerere.rikkahub.data.ai.ApiUsageSource
import me.rerere.rikkahub.data.ai.enforceFinalCompanionContextBudget
import me.rerere.ai.ui.UIMessage

/** Emergency ceiling after all source-level compaction and deduplication. */
object CompanionFinalBudgetTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> = enforceFinalCompanionContextBudget(
        messages = messages,
        source = ApiUsageSource.CHAT,
    ).messages
}
