package me.rerere.rikkahub.data.ai.transformers

internal val companionInputTransformers: List<InputMessageTransformer> = listOf(
    // First pass removes transient snapshots accidentally retained by previous turns.
    CompanionContextDedupTransformer,
    TimeReminderTransformer,
    PromptInjectionTransformer,
    CompanionPresenceContractTransformer,
    StudyStateTransformer,
    PlaceholderTransformer,
    DocumentAsPromptTransformer,
    OcrTransformer,
    VoiceMessageTransformer,
    // Final pass makes the freshly generated snapshot win and removes exact
    // duplicate system/world-book blocks introduced through multiple paths.
    CompanionContextDedupTransformer,
)

internal val companionOutputTransformers: List<OutputMessageTransformer> = listOf(
    ThinkTagTransformer,
    Base64ImageToLocalFileTransformer,
    RegexOutputTransformer,
    LuluExpressionOutputTransformer,
    CompanionLifeClaimOutputTransformer,
)

internal fun List<InputMessageTransformer>.withRequiredAssistantPromptContext(): List<InputMessageTransformer> =
    if (PromptInjectionTransformer in this) this else listOf(PromptInjectionTransformer) + this
