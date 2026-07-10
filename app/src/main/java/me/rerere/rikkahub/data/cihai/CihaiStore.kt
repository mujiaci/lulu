package me.rerere.rikkahub.data.cihai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.utils.JsonInstant

private val Context.cihaiDataStore: DataStore<Preferences> by preferencesDataStore(name = "cihai")

class CihaiStore(
    private val context: Context,
    scope: AppScope,
    private val json: Json = JsonInstant,
) {
    private val stateKey = stringPreferencesKey("state")

    val state: StateFlow<CihaiState> = context.cihaiDataStore.data
        .map { prefs ->
            prefs[stateKey]?.let { raw ->
                runCatching { json.decodeFromString<CihaiState>(raw) }.getOrDefault(CihaiState())
            } ?: CihaiState()
        }
        .catch { emit(CihaiState()) }
        .stateIn(scope, SharingStarted.Eagerly, CihaiState())

    suspend fun update(transform: (CihaiState) -> CihaiState) {
        context.cihaiDataStore.edit { prefs ->
            val current = prefs[stateKey]?.let { raw ->
                runCatching { json.decodeFromString<CihaiState>(raw) }.getOrDefault(CihaiState())
            } ?: CihaiState()
            prefs[stateKey] = json.encodeToString(transform(current).normalizedCihaiState())
        }
    }

    suspend fun snapshot(): CihaiState = context.cihaiDataStore.data.first()[stateKey]?.let { raw ->
        runCatching { json.decodeFromString<CihaiState>(raw) }.getOrDefault(CihaiState())
    } ?: CihaiState()

    suspend fun selectAssistant(assistantId: String) {
        update { it.copy(selectedAssistantId = assistantId) }
    }

    suspend fun addEntry(entry: CihaiEntry) {
        update { state -> state.addEntryToMemoryQueue(entry) }
    }

    suspend fun markEntryMemorySaved(entryId: String) {
        update { state ->
            state.completeMemorySettlement(
                reviewedEntryIds = setOf(entryId),
                savedEvidenceEntryIds = setOf(entryId),
            )
        }
    }

    suspend fun completeMemorySettlement(
        reviewedEntryIds: Set<String>,
        savedEvidenceEntryIds: Set<String>,
    ) {
        update { state ->
            state.completeMemorySettlement(
                reviewedEntryIds = reviewedEntryIds,
                savedEvidenceEntryIds = savedEvidenceEntryIds,
            )
        }
    }

    suspend fun retryMemorySettlement(
        entryIds: Set<String>,
        failedAt: Long,
        error: String,
    ) {
        update { state ->
            state.retryMemorySettlement(
                entryIds = entryIds,
                failedAt = failedAt,
                error = error,
            )
        }
    }

    suspend fun deleteEntry(entryId: String) {
        update { state -> state.removeCihaiEntry(entryId) }
    }

    suspend fun addBook(book: CihaiBook) {
        update { state ->
            state.copy(books = (listOf(book) + state.books).take(CIHAI_BOOK_LIMIT))
        }
    }

    suspend fun updateBook(book: CihaiBook) {
        update { state ->
            state.copy(books = state.books.map { current ->
                if (current.id == book.id) book else current
            })
        }
    }

    suspend fun deleteBook(bookId: String) {
        update { state ->
            state.copy(books = state.books.filterNot { it.id == bookId })
        }
    }

}

internal fun CihaiState.normalizedCihaiState(): CihaiState {
    val normalizedEntries = entries
        .filter { it.assistantId.isNotBlank() && it.content.isNotBlank() }
        .distinctBy { it.id }
        .take(CIHAI_ENTRY_LIMIT)
    val normalizedEntriesById = normalizedEntries.associateBy { it.id }
    return copy(
        entries = normalizedEntries,
        books = books
            .filter { it.assistantId.isNotBlank() && it.title.isNotBlank() && it.content.isNotBlank() }
            .distinctBy { it.id }
            .take(CIHAI_BOOK_LIMIT),
        memoryQueue = memoryQueue
            .asSequence()
            .filter { item ->
                val entry = normalizedEntriesById[item.entryId]
                item.entryId.isNotBlank() &&
                    item.assistantId.isNotBlank() &&
                    entry != null &&
                    entry.assistantId == item.assistantId &&
                    entry.resolvedMemoryDisposition == CihaiMemoryDisposition.PENDING
            }
            .distinctBy { it.entryId }
            .sortedWith(compareBy<CihaiMemoryQueueItem> { it.enqueuedAt }.thenBy { it.entryId })
            .take(CIHAI_MEMORY_QUEUE_LIMIT)
            .toList(),
    )
}

internal fun CihaiState.addEntryToMemoryQueue(entry: CihaiEntry): CihaiState {
    if (entry.id.isBlank() || entry.assistantId.isBlank() || entry.content.isBlank()) return this
    val pendingEntry = entry.copy(
        memoryDisposition = CihaiMemoryDisposition.PENDING,
        memorySaved = false,
    )
    val queueItem = CihaiMemoryQueueItem(
        entryId = pendingEntry.id,
        assistantId = pendingEntry.assistantId,
        enqueuedAt = pendingEntry.createdAt,
    )
    return copy(
        entries = listOf(pendingEntry) + entries.filterNot { it.id == pendingEntry.id },
        memoryQueue = memoryQueue.filterNot { it.entryId == pendingEntry.id } + queueItem,
    )
}

internal fun CihaiState.completeMemorySettlement(
    reviewedEntryIds: Set<String>,
    savedEvidenceEntryIds: Set<String>,
): CihaiState {
    val reviewed = reviewedEntryIds.filterTo(mutableSetOf()) { it.isNotBlank() }
    if (reviewed.isEmpty()) return this
    val saved = savedEvidenceEntryIds.intersect(reviewed)
    return copy(
        entries = entries.map { entry ->
            when (entry.id) {
                in saved -> entry.copy(
                    memoryDisposition = CihaiMemoryDisposition.SAVED,
                    memorySaved = true,
                )

                in reviewed -> entry.copy(
                    memoryDisposition = CihaiMemoryDisposition.CIHAI_ONLY,
                    memorySaved = false,
                )

                else -> entry
            }
        },
        memoryQueue = memoryQueue.filterNot { it.entryId in reviewed },
    )
}

internal fun CihaiState.retryMemorySettlement(
    entryIds: Set<String>,
    failedAt: Long,
    error: String,
): CihaiState {
    val failedIds = entryIds.filterTo(mutableSetOf()) { it.isNotBlank() }
    if (failedIds.isEmpty()) return this
    return copy(
        memoryQueue = memoryQueue.map { item ->
            if (item.entryId !in failedIds) return@map item
            val nextAttemptCount = if (item.attemptCount == Int.MAX_VALUE) {
                Int.MAX_VALUE
            } else {
                item.attemptCount + 1
            }
            val multiplier = 1L shl (nextAttemptCount - 1).coerceIn(0, 5)
            val delay = (CIHAI_RETRY_BASE_DELAY_MILLIS * multiplier)
                .coerceAtMost(CIHAI_RETRY_MAX_DELAY_MILLIS)
            item.copy(
                attemptCount = nextAttemptCount,
                nextAttemptAt = failedAt.coerceAtLeast(0L) + delay,
                lastError = error.trim().take(300).takeIf(String::isNotBlank),
            )
        },
    )
}

internal fun CihaiState.removeCihaiEntry(entryId: String): CihaiState = copy(
    entries = entries.filterNot { it.id == entryId },
    memoryQueue = memoryQueue.filterNot { it.entryId == entryId },
)

private const val CIHAI_RETRY_BASE_DELAY_MILLIS = 15L * 60 * 1_000
private const val CIHAI_RETRY_MAX_DELAY_MILLIS = 6L * 60 * 60 * 1_000
private const val CIHAI_ENTRY_LIMIT = 300
private const val CIHAI_BOOK_LIMIT = 60
private const val CIHAI_MEMORY_QUEUE_LIMIT = 300
