package me.rerere.rikkahub.data.companion

internal fun CompanionState.sanitizedCompanionState(): CompanionState = copy(
    statusText = statusText.cleanTechnicalStateText(fallback = "安静留意"),
    innerThought = innerThought.cleanTechnicalStateText(fallback = ""),
    mindState = mindState.cleanTechnicalStateText(fallback = "安静留意着现在的变化"),
    selfScene = selfScene.cleanTechnicalStateText(
        fallback = "暂时没有开口，只把注意力留在接下来的变化上。",
    ),
)

private fun String.cleanTechnicalStateText(fallback: String): String {
    val normalized = trim().replace(Regex("\\s+"), " ")
    if (normalized.isBlank()) return ""
    val lower = normalized.lowercase()
    return if (TECHNICAL_STATE_MARKERS.any { marker -> marker in lower }) fallback else normalized
}

private val TECHNICAL_STATE_MARKERS = listOf(
    "后台判断",
    "后台判定",
    "副 api",
    "副api",
    "api 判断",
    "api判断",
    "本地规划",
    "规划兜底",
    "模型兜底",
    "状态栏留下",
    "planner",
    "fallback",
    "local planning",
    "sub api",
)
