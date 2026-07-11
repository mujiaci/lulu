package me.rerere.rikkahub.data.ai.transformers

internal val companionInputTransformers: List<InputMessageTransformer> = listOf(
    TimeReminderTransformer,
    PromptInjectionTransformer,
    CompanionPresenceContractTransformer,
    StudyStateTransformer,
    PlaceholderTransformer,
    DocumentAsPromptTransformer,
    OcrTransformer,
    VoiceMessageTransformer,
)

internal val companionOutputTransformers: List<OutputMessageTransformer> = listOf(
    ThinkTagTransformer,
    Base64ImageToLocalFileTransformer,
    RegexOutputTransformer,
    LuluExpressionOutputTransformer,
)
