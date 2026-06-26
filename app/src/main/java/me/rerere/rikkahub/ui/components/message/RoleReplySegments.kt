package me.rerere.rikkahub.ui.components.message

enum class RoleReplyKind(val label: String, val speakable: Boolean) {
    Speech("语言", true),
    Action("动作", false),
    Environment("环境", false),
    Thought("心理", false),
    Feeling("感受", false),
}

data class RoleReplySegment(
    val text: String,
    val kind: RoleReplyKind = RoleReplyKind.Speech,
)

fun String.parseRoleReplySegments(): List<RoleReplySegment> {
    val text = trim()
    if (text.isBlank()) return emptyList()
    if (text.contains("```") || text.contains("\n- ") || text.contains("\n1. ")) {
        return listOf(RoleReplySegment(text))
    }
    val tagged = text.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val match = Regex("^(语言|台词|动作|环境|心理|内心|感受)\\s*[:：]\\s*(.+)$").find(line)
            if (match != null) {
                RoleReplySegment(match.groupValues[2].trim(), match.groupValues[1].toRoleReplyKind())
            } else {
                null
            }
        }
    if (tagged.isNotEmpty()) return tagged

    val visualParts = splitIntoVisualBubbles()
    return visualParts.map { part ->
        val clean = part.trim()
        val kind = when {
            clean.isWrappedBy("（", "）") || clean.isWrappedBy("(", ")") -> RoleReplyKind.Action
            clean.isWrappedBy("*", "*") || clean.isWrappedBy("＊", "＊") -> RoleReplyKind.Action
            clean.startsWith("动作：") || clean.startsWith("动作:") -> RoleReplyKind.Action
            clean.startsWith("环境：") || clean.startsWith("环境:") -> RoleReplyKind.Environment
            clean.startsWith("心理：") || clean.startsWith("心理:") -> RoleReplyKind.Thought
            clean.startsWith("内心：") || clean.startsWith("内心:") -> RoleReplyKind.Thought
            clean.startsWith("感受：") || clean.startsWith("感受:") -> RoleReplyKind.Feeling
            else -> RoleReplyKind.Speech
        }
        RoleReplySegment(clean.trimNonSpeechMarks(), kind)
    }
}

fun String.extractSpeakableRoleText(): String =
    parseRoleReplySegments()
        .filter { it.kind.speakable }
        .joinToString("\n") { it.text }
        .trim()

fun String.splitIntoVisualBubbles(): List<String> {
    val text = trim()
    if (text.isBlank()) return listOf("")
    if (text.contains("```") || text.contains("\n- ") || text.contains("\n1. ")) return listOf(text)

    val paragraphSegments = text.split(Regex("\\n\\s*\\n+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (paragraphSegments.size > 1) return paragraphSegments

    val sentenceParts = text.split(Regex("(?<=[.!?~～。！？…])\\s*"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    val roughParts = if (sentenceParts.size > 1) {
        sentenceParts
    } else {
        text.split(Regex("(?<=[,，、;；:：])\\s*"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    if (roughParts.size <= 1 && text.length <= 56) return listOf(text)
    return roughParts
        .fold(mutableListOf<String>()) { acc, part ->
            val last = acc.lastOrNull()
            if (last == null || last.length + part.length > 44) {
                acc += part
            } else {
                acc[acc.lastIndex] = "$last$part"
            }
            acc
        }
        .ifEmpty { listOf(text) }
}

private fun String.toRoleReplyKind(): RoleReplyKind = when (this) {
    "动作" -> RoleReplyKind.Action
    "环境" -> RoleReplyKind.Environment
    "心理", "内心" -> RoleReplyKind.Thought
    "感受" -> RoleReplyKind.Feeling
    else -> RoleReplyKind.Speech
}

private fun String.isWrappedBy(prefix: String, suffix: String): Boolean =
    startsWith(prefix) && endsWith(suffix) && length > prefix.length + suffix.length

private fun String.trimNonSpeechMarks(): String =
    trim()
        .removePrefix("（").removeSuffix("）")
        .removePrefix("(").removeSuffix(")")
        .removePrefix("*").removeSuffix("*")
        .removePrefix("＊").removeSuffix("＊")
        .trim()
