package me.rerere.rikkahub.data.cihai

import me.rerere.rikkahub.data.service.MemoryBankService

class CihaiService(
    private val store: CihaiStore,
    private val memoryBankService: MemoryBankService,
) {
    suspend fun addEntryAndRemember(entry: CihaiEntry) {
        store.addEntry(entry)
        memoryBankService.saveExtractedMemories(
            candidates = listOf(entry.toMemoryCandidate()),
            assistantId = entry.assistantId,
            conversationId = null,
            createdAt = entry.createdAt,
        )
        store.markEntryMemorySaved(entry.id)
    }

    suspend fun addBook(book: CihaiBook) {
        store.addBook(book)
    }

    suspend fun recordSilentJudgment(
        assistantId: String,
        assistantName: String,
        reason: String,
        userText: String,
        createdAt: Long = System.currentTimeMillis(),
    ) {
        addEntryAndRemember(
            CihaiEntry.fromSilentJudgment(
                assistantId = assistantId,
                assistantName = assistantName,
                reason = reason,
                userText = userText,
                createdAt = createdAt,
            )
        )
    }

    suspend fun recordSilentPresenceAction(
        assistantId: String,
        assistantName: String,
        reason: String,
        userText: String,
        actionHintNames: List<String> = emptyList(),
        createdAt: Long = System.currentTimeMillis(),
    ) {
        val result = planCihaiSilentPresence(
            CihaiSilentPresenceInput(
                assistantId = assistantId,
                assistantName = assistantName,
                reason = reason,
                userText = userText,
                actionHintNames = actionHintNames,
                books = store.state.value.books,
                createdAt = createdAt,
            )
        )
        result.updatedBook?.let { store.updateBook(it) }
        result.entries.forEach { entry ->
            addEntryAndRemember(entry)
        }
    }

    suspend fun readBookAndRemember(book: CihaiBook) {
        val result = book.readNextReflection()
        store.updateBook(result.updatedBook)
        addEntryAndRemember(result.entry)
    }
}
