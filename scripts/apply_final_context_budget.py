from pathlib import Path

path = Path("app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt")
text = path.read_text(encoding="utf-8")
old = '''        val internalMessages = preTransformMessages.transforms(
            transformers = transformers,
            context = context,
            model = model,
            assistant = assistant,
            settings = settings,
            processingStatus = processingStatus,
        )
        val breakdown = buildGenerationTokenBreakdown(
'''
new = '''        val transformedMessages = preTransformMessages.transforms(
            transformers = transformers,
            context = context,
            model = model,
            assistant = assistant,
            settings = settings,
            processingStatus = processingStatus,
        )
        val finalContext = enforceFinalCompanionContextBudget(
            messages = transformedMessages,
            source = apiUsageSource,
        )
        val internalMessages = finalContext.messages
        Log.i(
            TAG,
            "final companion context: ${finalContext.estimatedTokens} tokens, " +
                "dropped=${finalContext.droppedMessages}, compactedSystem=${finalContext.compactedSystemMessages}",
        )
        val breakdown = buildGenerationTokenBreakdown(
'''
if old not in text:
    if "val finalContext = enforceFinalCompanionContextBudget" in text:
        print("already applied")
        raise SystemExit(0)
    raise SystemExit("target block not found")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("patched", path)
