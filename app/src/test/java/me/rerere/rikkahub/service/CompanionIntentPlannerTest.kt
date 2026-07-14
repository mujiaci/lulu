package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.companion.CompanionContextFact
import me.rerere.rikkahub.data.companion.CompanionConversationTurn
import me.rerere.rikkahub.data.companion.CompanionPerceptionAssembler
import me.rerere.rikkahub.data.companion.CompanionPerceptionInput
import me.rerere.rikkahub.data.companion.CompanionRelationshipState
import me.rerere.rikkahub.data.companion.CompanionSnapshot
import me.rerere.rikkahub.data.companion.CompanionState
import me.rerere.rikkahub.data.companion.CompanionTurnRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionIntentPlannerTest {
    @Test
    fun `foreground fallback waits without inventing role behavior`() {
        val decision = CompanionIntentFallbackPlanner.plan(
            CompanionIntentInput(
                perception = perception(),
                mode = CompanionDecisionMode.FOREGROUND,
                minutesSinceLastChat = 0L,
            ),
        )

        assertEquals(CompanionIntent.WAIT, decision.intent)
        assertFalse(decision.shouldMessageNow)
        assertEquals(null, decision.delayMinutes)
        assertFalse(decision.reason.contains("露露"))
    }

    @Test
    fun `background fallback may reach out after long silence without persona keyword matching`() {
        val decision = CompanionIntentFallbackPlanner.plan(
            CompanionIntentInput(
                perception = perception(availableTools = setOf("get_battery_info", "get_app_usage")),
                mode = CompanionDecisionMode.BACKGROUND,
                minutesSinceLastChat = 180L,
            ),
        )

        assertEquals(CompanionIntent.REACH_OUT, decision.intent)
        assertTrue(decision.shouldMessageNow)
        assertEquals(listOf("get_battery_info", "get_app_usage"), decision.toolNames)
    }

    @Test
    fun `background fallback may choose a real private activity during quiet time`() {
        val decision = CompanionIntentFallbackPlanner.plan(
            CompanionIntentInput(
                perception = perception(availableTools = setOf("play_companion_game")),
                mode = CompanionDecisionMode.BACKGROUND,
                minutesSinceLastChat = 60L,
            ),
        )

        assertEquals(CompanionIntent.SELF_ACTIVITY, decision.intent)
        assertFalse(decision.shouldMessageNow)
        assertEquals("play_companion_game", decision.actionToolName)
        assertTrue(decision.actionArgumentsJson.contains("curious"))
    }

    @Test
    fun `background fallback respects unresolved relationship tension`() {
        val tenseSnapshot = CompanionSnapshot.empty("assistant-a").copy(
            relationship = CompanionRelationshipState(unresolvedTension = 0.8f),
        )
        val packet = CompanionPerceptionAssembler.assemble(
            input = CompanionPerceptionInput(
                assistantId = "assistant-a",
                assistantName = "Role A",
                persona = "Any role persona",
                nowMillis = 1_000L,
            ),
            snapshot = tenseSnapshot,
        )

        val decision = CompanionIntentFallbackPlanner.plan(
            CompanionIntentInput(
                perception = packet,
                mode = CompanionDecisionMode.BACKGROUND,
                minutesSinceLastChat = 180L,
            ),
        )

        assertEquals(CompanionIntent.WAIT, decision.intent)
    }

    @Test
    fun `model parser accepts legacy labels but returns role neutral intent`() {
        val decision = CompanionIntentModelPlanner.parsePlan(
            rawText = """
                {
                  "intent": "check_context",
                  "shouldMessageNow": false,
                  "delayMinutes": 99999,
                  "toolNames": ["camera_capture", "unknown", "camera_capture"],
                  "reason": "Recheck current context before deciding.",
                  "tone": "follow persona",
                  "innerThought": "I should look again before speaking."
                }
            """.trimIndent(),
            availableToolNames = setOf("camera_capture"),
        )

        assertEquals(CompanionIntent.OBSERVE, decision?.intent)
        assertEquals(24 * 60, decision?.delayMinutes)
        assertEquals(listOf("camera_capture"), decision?.toolNames)
        assertTrue(decision?.fromModel == true)
    }

    @Test
    fun `model prompt contains persona and unified runtime truth`() {
        val prompt = CompanionIntentModelPlanner.buildPrompt(
            CompanionIntentInput(
                perception = perception(
                    persona = "A restrained housekeeper persona",
                    memoryContext = "用户曾明确说过早晨很难醒。",
                ),
                mode = CompanionDecisionMode.BACKGROUND,
                minutesSinceLastChat = 45L,
            ),
        )

        assertTrue(prompt.contains("A restrained housekeeper persona"))
        assertTrue(prompt.contains("<companion_runtime"))
        assertTrue(prompt.contains("FOLLOW_UP, STAY_AVAILABLE, REACH_OUT, OBSERVE, SELF_ACTIVITY, WAIT"))
        assertTrue(prompt.contains("用户曾明确说过早晨很难醒。"))
        assertFalse(prompt.contains("study-supervisor"))
    }

    @Test
    fun `model self activity requires a real available action tool`() {
        val accepted = CompanionIntentModelPlanner.parsePlan(
            rawText = """{"intent":"SELF_ACTIVITY","actionToolName":"play_companion_game","actionArguments":{"strategy":"careful"}}""",
            availableToolNames = setOf("play_companion_game"),
        )
        val rejected = CompanionIntentModelPlanner.parsePlan(
            rawText = """{"intent":"SELF_ACTIVITY","actionToolName":"imaginary_game"}""",
            availableToolNames = setOf("play_companion_game"),
        )

        assertEquals(CompanionIntent.SELF_ACTIVITY, accepted?.intent)
        assertEquals("play_companion_game", accepted?.actionToolName)
        assertTrue(accepted?.actionArgumentsJson?.contains("careful") == true)
        assertEquals(null, rejected)
    }

    @Test
    fun `chat turn prompt uses unified perception without legacy thought state`() {
        val packet = CompanionPerceptionAssembler.assemble(
            input = CompanionPerceptionInput(
                assistantId = "assistant-a",
                assistantName = "阿澈",
                persona = "沉稳的男性朋友，尊重边界。",
                recentTurns = listOf(
                    CompanionConversationTurn(
                        role = CompanionTurnRole.USER,
                        content = "今天有点累",
                        createdAt = 900L,
                    ),
                ),
                contextFacts = listOf(
                    CompanionContextFact(
                        key = "minutes_since_previous_interaction",
                        value = "45",
                        observedAt = 1_000L,
                    ),
                ),
                availableToolNames = setOf("get_battery_info"),
                memoryContext = "用户不喜欢被连续追问。",
                nowMillis = 1_000L,
            ),
            snapshot = CompanionSnapshot.empty("assistant-a").copy(
                state = CompanionState(statusText = "安静留意", mindState = "专注"),
            ),
        )
        val prompt = CompanionChatTurnModelPlanner.buildChatTurnPrompt(
            CompanionChatTurnPlanInput(perception = packet),
        )

        assertTrue(prompt.contains("当前角色现在想先知道什么"))
        assertTrue(prompt.contains("如果当前角色决定稍后主动找用户"))
        assertTrue(prompt.contains("<companion_runtime"))
        assertTrue(prompt.contains("安静留意"))
        assertTrue(prompt.contains("minutes_since_previous_interaction=45"))
        assertTrue(prompt.contains("用户不喜欢被连续追问。"))
        assertTrue(prompt.contains("USER: 今天有点累"))
        assertTrue(prompt.contains("get_battery_info"))
        assertFalse(prompt.contains("<pendingThoughts>"))
        assertFalse(prompt.contains("她现在"))
        assertFalse(prompt.contains("如果她决定"))
        assertFalse(prompt.contains("露露"))
    }

    private fun perception(
        persona: String = "Any role persona",
        availableTools: Set<String> = emptySet(),
        memoryContext: String = "",
    ) = CompanionPerceptionAssembler.assemble(
        input = CompanionPerceptionInput(
            assistantId = "assistant-a",
            assistantName = "Role A",
            persona = persona,
            availableToolNames = availableTools,
            memoryContext = memoryContext,
            nowMillis = 1_000L,
        ),
        snapshot = CompanionSnapshot.empty("assistant-a"),
    )
}
