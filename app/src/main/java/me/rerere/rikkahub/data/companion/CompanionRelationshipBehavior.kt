package me.rerere.rikkahub.data.companion

/**
 * Converts relationship measurements into behavioral boundaries. The impression card remains
 * descriptive; this contract is only for deciding how the character should act in a turn.
 */
fun CompanionRelationshipState.toBehaviorContract(): String = buildString {
    appendLine("<relationship_behavior_contract>")
    when {
        unresolvedTension >= 0.6f -> {
            appendLine("priority=repair_first")
            appendLine("behavior=acknowledge tension when relevant; do not pile on casual intimacy, pressure, or repeated reminders")
        }
        trust < 0.4f -> {
            appendLine("priority=earn_trust")
            appendLine("behavior=be transparent about uncertainty; prefer small verifiable actions over confident promises")
        }
        closeness >= 0.7f -> {
            appendLine("priority=persona_consistent_familiarity")
            appendLine("behavior=express evidence-backed familiarity in the configured persona's own manner without assuming entitlement, warmth, intimacy, or consent")
        }
        else -> {
            appendLine("priority=persona_consistent_baseline")
            appendLine("behavior=keep the configured persona's baseline distance and manner; let any relationship change follow real interactions rather than a default warm direction")
        }
    }
    if (boundaryConfidence < 0.45f) {
        appendLine("boundary=ask before sensitive inference, intrusive tools, or high-pressure follow-up")
    } else {
        appendLine("boundary=respect explicit user limits; confidence never overrides a newer correction")
    }
    if (reliability < 0.5f) {
        appendLine("reliability=avoid adding new durable promises unless the user clearly delegates them; demonstrate follow-through first")
    } else {
        appendLine("reliability=keep existing promises visible and report real completion or failure")
    }
    appendLine("latest_user_correction_wins=true")
    appendLine("</relationship_behavior_contract>")
}
