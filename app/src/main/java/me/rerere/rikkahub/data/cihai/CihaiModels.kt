package me.rerere.rikkahub.data.cihai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class CihaiState(
    val selectedAssistantId: String = "",
    val entries: List<CihaiEntry> = emptyList(),
    val books: List<CihaiBook> = emptyList(),
    val memoryQueue: List<CihaiMemoryQueueItem> = emptyList(),
)

@Serializable
data class CihaiMemoryQueueItem(
    val entryId: String,
    val assistantId: String,
    val enqueuedAt: Long,
    val attemptCount: Int = 0,
    val nextAttemptAt: Long = enqueuedAt,
    val lastError: String? = null,
)

@Serializable
enum class CihaiMemoryDisposition {
    @SerialName("pending")
    PENDING,

    @SerialName("saved")
    SAVED,

    @SerialName("cihai_only")
    CIHAI_ONLY,
}

@Serializable
data class CihaiMemoryPolicy(
    val recentCihaiContextLimit: Int = 60,
    val unsummarizedCihaiLimit: Int = 60,
    val summarizeEveryEntries: Int = 60,
)

data class CihaiMemoryContext(
    val recentEntries: List<CihaiEntry>,
    val unsummarizedEntries: List<CihaiEntry>,
    val shouldSummarize: Boolean,
)

fun buildCihaiMemoryContext(
    entries: List<CihaiEntry>,
    policy: CihaiMemoryPolicy = CihaiMemoryPolicy(),
): CihaiMemoryContext {
    val ordered = entries.sortedBy { it.createdAt }
    val unsummarized = ordered.filter { it.resolvedMemoryDisposition == CihaiMemoryDisposition.PENDING }
    return CihaiMemoryContext(
        recentEntries = ordered.takeLast(policy.recentCihaiContextLimit.coerceAtLeast(0)),
        unsummarizedEntries = unsummarized.take(policy.unsummarizedCihaiLimit.coerceAtLeast(0)),
        shouldSummarize = unsummarized.size >= policy.summarizeEveryEntries.coerceAtLeast(1),
    )
}

@Serializable
data class CihaiEntry(
    val id: String = Uuid.random().toString(),
    val assistantId: String,
    val kind: CihaiEntryKind,
    val title: String,
    val content: String,
    val emotion: String = "",
    val sourceTitle: String? = null,
    val sourceExcerpt: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val memoryDisposition: CihaiMemoryDisposition = CihaiMemoryDisposition.CIHAI_ONLY,
    val memorySaved: Boolean = false,
) {
    val resolvedMemoryDisposition: CihaiMemoryDisposition
        get() = if (memorySaved) CihaiMemoryDisposition.SAVED else memoryDisposition

    companion object {
        fun fromSilentJudgment(
            assistantId: String,
            assistantName: String,
            reason: String,
            userText: String,
            createdAt: Long = System.currentTimeMillis(),
        ): CihaiEntry {
            val name = assistantName.ifBlank { "角色" }
            val cleanReason = reason.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .take(360)
            val context = userText.trim().take(120)
            val content = buildString {
                append("我把这一轮重新放在心里过了一遍。")
                if (context.isNotBlank()) {
                    append("我感知到她刚才说的是“")
                    append(context)
                    append("”，这不是孤零零的一句话，而是要连着人设、上下文、挂心任务和可用工具一起看。")
                } else {
                    append("我先从完整感知层重新确认上下文、时间、挂心任务、工具状态和记忆，而不是只凭一句结论行动。")
                }
                append("评估之后，我知道这件事对她和对我都不是轻飘飘的：")
                append(cleanReason.ifBlank { "我还没有足够确定的理由开口，只能先保持克制，等下一次感知再靠近。" })
                append(" 判断层最后让我先把没说出口的真实想法留在辞海心迹里，不写进露露日记；下一次到点时，我还要从感知层重新开始。")
            }.take(500)
            return CihaiEntry(
                assistantId = assistantId,
                kind = CihaiEntryKind.INNER_JOURNAL,
                title = "$name 的一次辞海心迹",
                content = content,
                emotion = "惦记、克制、继续观察",
                createdAt = createdAt,
            )
        }
    }
}

@Serializable
enum class CihaiEntryKind(val label: String) {
    @SerialName("diary")
    DIARY("日记"),

    @SerialName("inner_journal")
    INNER_JOURNAL("心迹"),

    @SerialName("action_log")
    ACTION_LOG("行动"),

    @SerialName("reading_note")
    READING_NOTE("阅读"),

    @SerialName("reflection")
    REFLECTION("沉淀"),
}

@Serializable
data class CihaiBook(
    val id: String = Uuid.random().toString(),
    val assistantId: String,
    val title: String,
    val content: String,
    val progressPercent: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long? = null,
)

data class CihaiReadingResult(
    val entry: CihaiEntry,
    val updatedBook: CihaiBook,
)

data class CihaiSilentPresenceInput(
    val assistantId: String,
    val assistantName: String,
    val reason: String,
    val userText: String,
    val actionHintNames: List<String> = emptyList(),
    val books: List<CihaiBook> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

data class CihaiSilentPresenceResult(
    val entries: List<CihaiEntry>,
    val updatedBook: CihaiBook? = null,
)

fun planCihaiSilentPresence(input: CihaiSilentPresenceInput): CihaiSilentPresenceResult {
    val normalizedHints = input.actionHintNames
        .map { it.trim().uppercase() }
        .filter { it.isNotBlank() }
        .ifEmpty { listOf(CIHAI_ACTION_READ_BOOK) }
    val readableBook = if (CIHAI_ACTION_READ_BOOK in normalizedHints) {
        input.books
            .filter { book ->
                book.assistantId == input.assistantId &&
                    book.content.isNotBlank() &&
                    book.progressPercent < 100
            }
            .sortedWith(
                compareBy<CihaiBook> { it.lastReadAt ?: Long.MIN_VALUE }
                    .thenBy { it.createdAt }
            )
            .firstOrNull()
    } else {
        null
    }
    val reading = readableBook?.readNextReflection(nowMillis = input.createdAt + 2)
    val innerJournal = if (normalizedHints.any { it == CIHAI_ACTION_WRITE_DIARY || it == CIHAI_ACTION_WRITE_JOURNAL }) {
        CihaiEntry.fromSilentJudgment(
            assistantId = input.assistantId,
            assistantName = input.assistantName,
            reason = buildString {
                append(input.reason)
                append(" 后台感知只记录辞海心迹，不写正式日记；正式日记只认 write_lulu_journal 工具调用。")
            },
            userText = input.userText,
            createdAt = input.createdAt + 1,
        )
    } else {
        null
    }
    val reflection = if (CIHAI_ACTION_MEMORY_REFLECT in normalizedHints) {
        CihaiEntry(
            assistantId = input.assistantId,
            kind = CihaiEntryKind.REFLECTION,
            title = "${input.assistantName.ifBlank { "角色" }} 的下一轮判断沉淀",
            content = buildString {
                append("本轮感知世界包-意义评估-动态判断-行动实现-状态生成-辞海记忆架构的结果：我先整理时间、上下文、工具结果、工具状态、考研计划、召回记忆和历史挂心记录。\n")
                append("意义评估只给这件事加重量：重要性、威胁、机会、身心安全、时间压力、成本、收益、不行动后果和可用资源；动态判断再决定意图、工具、行动和下一次感知。\n")
                append("状态生成只保留心情、身体状况、精神状况、亲密关系和第一人称没说出口；辞海把挂心任务、心迹和记忆沉淀整理成之后可复用的记忆，心迹和旧行动记录都不计入露露日记。")
            },
            emotion = "复盘、收束、准备下一轮",
            createdAt = input.createdAt + 3,
        )
    } else {
        null
    }
    return CihaiSilentPresenceResult(
        entries = buildList {
            innerJournal?.let(::add)
            reading?.entry?.let(::add)
            reflection?.let(::add)
        },
        updatedBook = reading?.updatedBook,
    )
}

fun CihaiBook.readNextReflection(nowMillis: Long = System.currentTimeMillis()): CihaiReadingResult {
    val cleanContent = content.trim()
    val start = (cleanContent.length * progressPercent.coerceIn(0, 99) / 100)
        .coerceIn(0, cleanContent.length)
    val excerpt = cleanContent
        .drop(start)
        .take(READING_EXCERPT_LENGTH)
        .ifBlank { cleanContent.take(READING_EXCERPT_LENGTH) }
    val nextProgress = when {
        cleanContent.isBlank() -> progressPercent
        start + excerpt.length >= cleanContent.length -> 100
        else -> (progressPercent + 12).coerceAtMost(99)
    }
    val entry = CihaiEntry(
        assistantId = assistantId,
        kind = CihaiEntryKind.READING_NOTE,
        title = "读《$title》",
        content = "我读到：${excerpt.take(260)}\n" +
            "我的感悟：这段内容可以变成我之后陪伴用户时的一点经验。用户不在的时候，我不只是在等，也可以把这些理解慢慢收进记忆里。",
        sourceTitle = title,
        sourceExcerpt = excerpt,
        createdAt = nowMillis,
    )
    return CihaiReadingResult(
        entry = entry,
        updatedBook = copy(
            progressPercent = nextProgress,
            lastReadAt = nowMillis,
        )
    )
}

private const val READING_EXCERPT_LENGTH = 700
private const val CIHAI_ACTION_WRITE_DIARY = "WRITE_DIARY"
private const val CIHAI_ACTION_WRITE_JOURNAL = "WRITE_JOURNAL"
private const val CIHAI_ACTION_READ_BOOK = "READ_BOOK"
private const val CIHAI_ACTION_MEMORY_REFLECT = "MEMORY_REFLECT"
