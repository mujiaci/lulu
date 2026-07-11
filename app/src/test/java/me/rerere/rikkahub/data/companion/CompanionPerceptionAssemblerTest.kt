package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionPerceptionAssemblerTest {
    @Test(expected = IllegalArgumentException::class)
    fun `assembler rejects a snapshot owned by another assistant`() {
        CompanionPerceptionAssembler.assemble(
            input = input(assistantId = "assistant-a"),
            snapshot = CompanionSnapshot.empty("assistant-b"),
        )
    }

    @Test
    fun `assembler exposes only active concerns and actionable commitments`() {
        val packet = CompanionPerceptionAssembler.assemble(
            input = input(),
            snapshot = CompanionSnapshot(
                assistantId = "assistant-a",
                concerns = listOf(
                    concern(id = "active", status = CompanionConcernStatus.ACTIVE),
                    concern(id = "paused", status = CompanionConcernStatus.PAUSED),
                    concern(id = "done", status = CompanionConcernStatus.COMPLETED),
                ),
                commitments = listOf(
                    commitment(id = "active", status = CompanionCommitmentStatus.ACTIVE),
                    commitment(id = "retry", status = CompanionCommitmentStatus.RETRY_SCHEDULED),
                    commitment(id = "done", status = CompanionCommitmentStatus.FULFILLED),
                ),
            ),
        )

        assertEquals(listOf("active"), packet.activeConcerns.map { it.id })
        assertEquals(listOf("active", "retry"), packet.actionableCommitments.map { it.id })
    }

    @Test
    fun `assembler lets stale unscheduled concerns fade out but keeps scheduled follow ups`() {
        val now = 100L * 24L * 60L * 60L * 1_000L
        val packet = CompanionPerceptionAssembler.assemble(
            input = input().copy(nowMillis = now),
            snapshot = CompanionSnapshot(
                assistantId = "assistant-a",
                concerns = listOf(
                    concern(id = "stale", importance = 1, nextPerceptionAt = null).copy(lastUpdatedAt = 0L),
                    concern(id = "scheduled", importance = 1, nextPerceptionAt = now + 1_000L)
                        .copy(lastUpdatedAt = 0L),
                    concern(id = "important", importance = 5, nextPerceptionAt = null)
                        .copy(lastUpdatedAt = now - 30L * 24L * 60L * 60L * 1_000L),
                ),
            ),
        )

        assertEquals(listOf("important", "scheduled"), packet.activeConcerns.map { it.id })
    }

    @Test
    fun `assembler orders due work before later work`() {
        val packet = CompanionPerceptionAssembler.assemble(
            input = input(),
            snapshot = CompanionSnapshot(
                assistantId = "assistant-a",
                concerns = listOf(
                    concern(id = "low", importance = 2, nextPerceptionAt = 10L),
                    concern(id = "important-later", importance = 5, nextPerceptionAt = 300L),
                    concern(id = "important-now", importance = 5, nextPerceptionAt = 100L),
                ),
                commitments = listOf(
                    commitment(id = "later", dueAt = 300L),
                    commitment(id = "now", dueAt = 100L),
                ),
            ),
        )

        assertEquals(listOf("important-now", "important-later", "low"), packet.activeConcerns.map { it.id })
        assertEquals(listOf("now", "later"), packet.actionableCommitments.map { it.id })
    }

    @Test
    fun `assembler bounds persona context facts and recent turns`() {
        val packet = CompanionPerceptionAssembler.assemble(
            input = input().copy(
                persona = " p ".repeat(3_000),
                recentTurns = (1..20).map { index ->
                    CompanionConversationTurn(
                        role = CompanionTurnRole.USER,
                        content = " message-$index ".repeat(100),
                        createdAt = index.toLong(),
                    )
                },
                contextFacts = (1..40).map { index ->
                    CompanionContextFact(
                        key = " fact-$index ",
                        value = " value-$index ".repeat(100),
                        observedAt = index.toLong(),
                    )
                },
                availableToolNames = (1..80).map { " tool-$it " }.toSet(),
                memoryContext = " memory ".repeat(2_000),
            ),
            snapshot = CompanionSnapshot.empty("assistant-a"),
        )

        assertEquals(12, packet.recentTurns.size)
        assertTrue(packet.recentTurns.first().content.startsWith("message-9"))
        assertEquals(32, packet.contextFacts.size)
        assertEquals(64, packet.availableToolNames.size)
        assertTrue(packet.persona.length <= 4_000)
        assertTrue(packet.memoryContext.length <= 6_000)
        assertTrue(packet.recentTurns.all { it.content.length <= 1_000 })
        assertTrue(packet.contextFacts.all { it.key.length <= 80 && it.value.length <= 500 })
    }

    @Test
    fun `prompt context includes active work but excludes completed work`() {
        val packet = CompanionPerceptionAssembler.assemble(
            input = input(),
            snapshot = CompanionSnapshot(
                assistantId = "assistant-a",
                concerns = listOf(
                    concern(id = "active"),
                    concern(id = "done", status = CompanionConcernStatus.COMPLETED),
                ),
                commitments = listOf(
                    commitment(id = "active"),
                    commitment(id = "done", status = CompanionCommitmentStatus.FULFILLED),
                ),
            ),
        )

        val prompt = packet.toPromptContext()

        assertTrue(prompt.contains("subject:active"))
        assertTrue(prompt.contains("promise active"))
        assertEquals(false, prompt.contains("subject:done"))
        assertEquals(false, prompt.contains("promise done"))
    }

    @Test
    fun `prompt context exposes absolute time and overdue state`() {
        val packet = CompanionPerceptionAssembler.assemble(
            input = input().copy(nowMillis = 1_783_616_400_000L),
            snapshot = CompanionSnapshot(
                assistantId = "assistant-a",
                concerns = listOf(concern(id = "overdue", nextPerceptionAt = 100L)),
                commitments = listOf(commitment(id = "overdue", dueAt = 100L)),
            ),
        )

        val prompt = packet.toPromptContext()

        assertTrue(prompt.contains("current_time="))
        assertTrue(prompt.contains("timezone="))
        assertTrue(prompt.contains("overdue=true"))
    }

    @Test
    fun `prompt context includes structured passive perception facts`() {
        val packet = CompanionPerceptionAssembler.assemble(
            input = input().copy(
                contextFacts = listOf(
                    CompanionContextFact("battery", "{\"level\":42}", 123L),
                    CompanionContextFact("app_usage", "{\"minutes\":90}", 124L),
                ),
            ),
            snapshot = CompanionSnapshot.empty("assistant-a"),
        )

        val prompt = packet.toPromptContext()

        assertTrue(prompt.contains("perception_facts:"))
        assertTrue(prompt.contains("battery"))
        assertTrue(prompt.contains("app_usage"))
    }

    private fun input(assistantId: String = "assistant-a") = CompanionPerceptionInput(
        assistantId = assistantId,
        assistantName = "角色 A",
        persona = "保持自然",
        nowMillis = 200L,
    )

    private fun concern(
        id: String,
        status: CompanionConcernStatus = CompanionConcernStatus.ACTIVE,
        importance: Int = 3,
        nextPerceptionAt: Long? = 100L,
    ) = CompanionConcern(
        id = id,
        assistantId = "assistant-a",
        subjectKey = "subject:$id",
        event = "event $id",
        goal = "goal $id",
        status = status,
        importance = importance,
        nextPerceptionAt = nextPerceptionAt,
    )

    private fun commitment(
        id: String,
        status: CompanionCommitmentStatus = CompanionCommitmentStatus.ACTIVE,
        dueAt: Long = 100L,
    ) = CompanionCommitment(
        id = id,
        assistantId = "assistant-a",
        subjectKey = "subject:$id",
        promise = "promise $id",
        dueAt = dueAt,
        status = status,
    )
}
