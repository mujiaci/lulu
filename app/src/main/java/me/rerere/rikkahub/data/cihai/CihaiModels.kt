package me.rerere.rikkahub.data.cihai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.service.AffectiveMemoryCandidate
import kotlin.uuid.Uuid

@Serializable
data class CihaiState(
    val selectedAssistantId: String = "",
    val entries: List<CihaiEntry> = emptyList(),
    val books: List<CihaiBook> = emptyList(),
)

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
    val unsummarized = ordered.filterNot { it.memorySaved }
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
    val memorySaved: Boolean = false,
) {
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
                .take(420)
            val context = userText.takeIf { it.isNotBlank() }?.let { "用户刚才说过：$it\n" }.orEmpty()
            return CihaiEntry(
                assistantId = assistantId,
                kind = CihaiEntryKind.INNER_JOURNAL,
                title = "$name 的一次沉默判断",
                content = context +
                    "我刚刚重新想了一次这件事：$cleanReason\n" +
                    "如果现在不适合开口，我会先把这份担心、克制和判断写进辞海，等下一次更合适的时候再靠近。",
                emotion = "惦记、克制、继续观察",
                createdAt = createdAt,
            )
        }
    }
}

@Serializable
enum class CihaiEntryKind(val label: String) {
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
        .ifEmpty { listOf(CIHAI_ACTION_WRITE_JOURNAL, CIHAI_ACTION_READ_BOOK) }
    val journal = CihaiEntry.fromSilentJudgment(
        assistantId = input.assistantId,
        assistantName = input.assistantName,
        reason = input.reason,
        userText = input.userText,
        createdAt = input.createdAt,
    )
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
    val actionLog = CihaiEntry(
        assistantId = input.assistantId,
        kind = CihaiEntryKind.ACTION_LOG,
        title = "${input.assistantName.ifBlank { "角色" }} 的静默行动",
        content = buildString {
            append("这次我重新判断后，没有立刻打扰用户。\n")
            append("判断理由：")
            append(input.reason.trim().ifBlank { "当前没有足够自然或必要的开口理由。" })
            append("\n")
            if (reading != null) {
                append("我的选择：先把没说出口的担心写进心迹，再读《")
                append(reading.entry.sourceTitle ?: "未命名材料")
                append("》的一段，给之后的陪伴多留一点理解。")
            } else if (CIHAI_ACTION_READ_BOOK in normalizedHints) {
                append("我的选择：本来想读一段用户给我的材料，但现在没有可读材料；所以先写日志、等待、保留下一次判断。")
            } else {
                append("我的选择：先写日志并等待下一次判断，不把关心变成机械催促。")
            }
        },
        emotion = "克制、照看、继续判断",
        createdAt = input.createdAt + 1,
    )
    val reflection = if (CIHAI_ACTION_MEMORY_REFLECT in normalizedHints) {
        CihaiEntry(
            assistantId = input.assistantId,
            kind = CihaiEntryKind.REFLECTION,
            title = "${input.assistantName.ifBlank { "角色" }} 的下一轮判断沉淀",
            content = buildString {
                append("本轮感知世界包-意义评估-动态判断-行动实现-状态生成-辞海记忆架构的结果：我先整理时间、上下文、工具结果、工具状态、考研计划、召回记忆和历史挂心记录。\n")
                append("意义评估只给这件事加重量：重要性、威胁、机会、身心安全、时间压力、成本、收益、不行动后果和可用资源；状态保持把它压成第一视角信念、长期/情境动机、意图和情绪。\n")
                append("审议层用 ReAct 边想边查边修正，决定本轮不立刻开口，而是保留行动池和下一次审议时间；沉淀层把这次经历压缩成情节痕迹、情感残留、语义记忆和行为经验。")
            },
            emotion = "复盘、收束、准备下一轮",
            createdAt = input.createdAt + 3,
        )
    } else {
        null
    }
    return CihaiSilentPresenceResult(
        entries = buildList {
            add(journal)
            add(actionLog)
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
private const val CIHAI_ACTION_WRITE_JOURNAL = "WRITE_JOURNAL"
private const val CIHAI_ACTION_READ_BOOK = "READ_BOOK"
private const val CIHAI_ACTION_MEMORY_REFLECT = "MEMORY_REFLECT"

fun CihaiEntry.toMemoryCandidate(): AffectiveMemoryCandidate {
    val kindName = when (kind) {
        CihaiEntryKind.INNER_JOURNAL -> "cihai_journal"
        CihaiEntryKind.ACTION_LOG -> "cihai_action"
        CihaiEntryKind.READING_NOTE -> "cihai_reading"
        CihaiEntryKind.REFLECTION -> "cihai_reflection"
    }
    val source = sourceTitle?.takeIf { it.isNotBlank() }?.let { "来源：《$it》\n" }.orEmpty()
    val feeling = emotion.takeIf { it.isNotBlank() }
    val fullContent = buildString {
        append(source)
        append(content.trim())
        if (!feeling.isNullOrBlank()) {
            append("\n当时情绪：")
            append(feeling)
        }
    }.trim()
    return AffectiveMemoryCandidate(
        type = kindName,
        title = title.ifBlank { kind.label },
        content = fullContent,
        roleFeeling = feeling,
        unspokenThought = if (kind == CihaiEntryKind.INNER_JOURNAL) content.trim() else null,
        relationshipEffect = when (kind) {
            CihaiEntryKind.INNER_JOURNAL -> "角色在用户沉默时产生了内心判断，并把未说出口的想法沉淀下来。"
            CihaiEntryKind.ACTION_LOG -> "角色记录了自己等待、克制、观察或照看的行动选择。"
            CihaiEntryKind.READING_NOTE -> "角色通过阅读形成了新的理解，可能改变之后陪伴用户的方式。"
            CihaiEntryKind.REFLECTION -> "角色把多次判断后的经验整理成后续可复用的长期记忆。"
        },
        importance = when (kind) {
            CihaiEntryKind.REFLECTION -> 5
            CihaiEntryKind.READING_NOTE -> 4
            else -> 3
        },
        confidence = 1.0,
        tags = listOf("辞海", kind.label) + sourceTitle?.takeIf { it.isNotBlank() }.let { listOfNotNull(it) },
        embeddingText = buildString {
            append(title)
            append("\n")
            append(fullContent)
            append("\n记忆用途：之后遇到相似沉默、学习、身体状态或关系情境时，角色应参考这次判断。")
        },
        people = listOf("用户", "角色"),
        topics = listOf("活人感", "内心日志", kind.label),
    )
}
