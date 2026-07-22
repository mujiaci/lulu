package me.rerere.rikkahub.data.service

/**
 * Deterministic second-pass guard for semantic memory extraction.
 *
 * The model decides what felt meaningful in context; this gate only rejects output that is
 * structurally unsupported, obviously temporary, generic, or contaminated by prompts/tools.
 * It deliberately does not require every memory to affect future behaviour: a shared event may
 * pass because it was emotionally important or memorable in its own right.
 */
internal object MemoryCandidateQualityGate {
    private val allowedTypes = setOf(
        "user_fact",
        "user_preference",
        "user_boundary",
        "promise",
        "relationship",
        "shared_event",
        "correction",
    )

    private val contaminationMarkers = listOf(
        "system prompt",
        "developer message",
        "assistant persona",
        "conversation_turns",
        "sourceMessageNodeIds",
        "evidenceMessageNodeIds",
        "tool_result",
        "requested_tools",
        "available_requested_tools",
        "missing_requested_tools",
        "seven-layer trace",
        "感知世界包",
        "意义评估",
        "动态判断",
        "状态生成",
        "辞海记忆架构",
        "记忆整理器",
        "作为一个ai",
        "作为语言模型",
    )

    private val genericTemplateMarkers = listOf(
        "我记得这件事，当时感觉很深刻",
        "这是一段值得记住的经历",
        "这件事让我感到很开心",
        "这件事让我感到很难过",
        "我会一直记住这件事",
        "以后可以参考",
        "等待下一次",
        "复盘、收束、准备下一轮",
    )

    private val trivialDailyPatterns = listOf(
        Regex("^(我记得)?(?:她|他|用户)?今天(?:吃了|喝了|买了|看了|去了|天气|下雨|晴天).{0,24}[。.!！]?$"),
        Regex("^(我记得)?(?:她|他|用户)?(?:早上|中午|下午|晚上)(?:吃了|喝了).{0,20}[。.!！]?$"),
        Regex("^(我记得)?(?:她|他|用户)?今天(?:几点|[0-9一二三四五六七八九十]+点)(?:起床|睡觉|出门).{0,16}[。.!！]?$"),
    )

    fun accepts(candidate: AffectiveMemoryCandidate): Boolean {
        val memory = candidate.normalized()
        if (memory.type !in allowedTypes) return false
        if (memory.content.length !in 8..800) return false
        if (memory.userSignal.isNullOrBlank()) return false
        if (memory.sourceMessageNodeIds.isEmpty() && memory.evidenceMessageNodeIds.isEmpty()) return false
        if (memory.confidence < 0.55) return false

        val combined = listOfNotNull(
            memory.title,
            memory.content,
            memory.roleFeeling,
            memory.bodySense,
            memory.unspokenThought,
            memory.userSignal,
            memory.relationshipEffect,
        ).joinToString("\n")

        if (contaminationMarkers.any { combined.contains(it, ignoreCase = true) }) return false
        if (genericTemplateMarkers.any { combined.contains(it, ignoreCase = true) }) return false
        if (looksLikeJsonOrTraceDump(combined)) return false

        if (memory.type == "shared_event" || memory.type == "relationship") {
            if (!hasEventOrAffectiveWeight(memory)) return false
        }

        if (trivialDailyPatterns.any { it.matches(memory.content.trim()) } && !hasEventOrAffectiveWeight(memory)) {
            return false
        }

        return true
    }

    private fun hasEventOrAffectiveWeight(memory: AffectiveMemoryCandidate): Boolean {
        val affectiveDetails = listOfNotNull(
            memory.roleFeeling,
            memory.bodySense,
            memory.unspokenThought,
            memory.relationshipEffect,
        ).count { it.length >= 6 }

        val significanceMarkers = listOf(
            "第一次", "终于", "一直", "重要", "难忘", "害怕", "担心", "委屈", "难过",
            "开心", "惊喜", "感动", "生气", "争吵", "吵架", "和好", "道歉", "哭",
            "承认", "拒绝", "告白", "离开", "失去", "陪伴", "一起", "我们",
        )
        val combined = listOfNotNull(
            memory.content,
            memory.roleFeeling,
            memory.unspokenThought,
            memory.relationshipEffect,
            memory.userSignal,
        ).joinToString("\n")

        return affectiveDetails >= 1 ||
            memory.importance >= 4 ||
            significanceMarkers.any { combined.contains(it, ignoreCase = true) }
    }

    private fun looksLikeJsonOrTraceDump(text: String): Boolean {
        val trimmed = text.trim()
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
        ) return true
        val structuralTokens = listOf("\"success\":", "\"path\":", "\"status\":", "stacktrace", "exception:")
        return structuralTokens.any { text.contains(it, ignoreCase = true) }
    }
}

internal fun AffectiveMemoryCandidate.passesStrictMemoryQualityGate(): Boolean =
    MemoryCandidateQualityGate.accepts(this)
