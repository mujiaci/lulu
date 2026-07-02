package me.rerere.rikkahub.data.model

import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuluPresenceTest {
    private val assistantId = Uuid.parse("11111111-1111-1111-1111-111111111111")

    @Test
    fun `state update keeps inertia instead of jumping to every signal`() {
        val previous = LuluState(
            assistantId = assistantId,
            mood = LuluMood.CALM,
            energy = LuluEnergy.NORMAL,
            relationship = LuluRelationship.CLOSE,
            updatedAt = 1_000L,
        )

        val next = buildLuluStateFromTurn(
            assistantId = assistantId,
            previous = previous,
            userText = "我今天真的累到崩溃",
            assistantText = "我陪着你。",
            nowMillis = 2_000L,
            hourOfDay = 15,
        )

        assertEquals(LuluMood.SOFT, next.mood)
        assertEquals(LuluEnergy.NORMAL, next.energy)
        assertEquals(LuluRelationship.CLOSE, next.relationship)
        assertTrue(next.reason.contains("状态惯性"))
    }

    @Test
    fun `thoughts expire and keep only strongest active items`() {
        val old = LuluThought(
            assistantId = assistantId,
            content = "旧想法",
            importance = 2,
            createdAt = 0L,
            expiresAt = 100L,
        )
        val current = (1..5).map { index ->
            LuluThought(
                assistantId = assistantId,
                content = "想法$index",
                importance = index,
                createdAt = index.toLong(),
                expiresAt = 10_000L,
            )
        }

        val normalized = (current + old).normalizedLuluThoughts(
            validAssistantIds = setOf(assistantId),
            nowMillis = 1_000L,
        )

        assertEquals(listOf("想法5", "想法4", "想法3"), normalized.map { it.content })
        assertFalse(normalized.any { it.content == "旧想法" })
    }

    @Test
    fun `concerns survive expiry while normal thoughts expire`() {
        val oldConcern = LuluThought(
            assistantId = assistantId,
            content = "他最近总是很累，我有点放不下。",
            category = LuluThoughtCategory.CONCERN,
            importance = 5,
            createdAt = 0L,
            expiresAt = 100L,
        )
        val oldNormalThought = LuluThought(
            assistantId = assistantId,
            content = "普通旧想法",
            category = LuluThoughtCategory.SHORT_TERM,
            importance = 3,
            createdAt = 0L,
            expiresAt = 100L,
        )

        val normalized = listOf(oldConcern, oldNormalThought).normalizedLuluThoughts(
            validAssistantIds = setOf(assistantId),
            nowMillis = 1_000L,
        )

        assertEquals(listOf("他最近总是很累，我有点放不下。"), normalized.map { it.content })
    }

    @Test
    fun `pending action is generated for study promise`() {
        val thought = buildLuluThoughtFromTurn(
            assistantId = assistantId,
            userText = "我去学习一会儿，等下回来",
            state = LuluState(assistantId = assistantId),
            nowMillis = 1_000L,
        )

        assertEquals(LuluThoughtCategory.PENDING_ACTION, thought?.category)
        assertTrue(thought?.content.orEmpty().contains("等他回来"))
    }

    @Test
    fun `preferred thought ignores prompt leakage`() {
        val thought = buildLuluThoughtFromTurn(
            assistantId = assistantId,
            userText = "我有点累",
            state = LuluState(assistantId = assistantId),
            preferredThought = "<lulu_presence> use field thought and inner_voice",
            nowMillis = 1_000L,
        )

        val content = thought?.content.orEmpty()
        assertFalse(content.contains("lulu_presence"))
        assertFalse(content.contains("inner_voice"))
    }

    @Test
    fun `preferred inner voice ignores prompt leakage`() {
        val state = buildLuluStateFromTurn(
            assistantId = assistantId,
            userText = "我有点累",
            assistantText = "我陪你缓一下。",
            preferredInnerVoice = "set_lulu_expression_state inner_voice description",
            nowMillis = 1_000L,
        )

        assertFalse(state.innerVoice.contains("set_lulu_expression_state"))
        assertFalse(state.innerVoice.contains("inner_voice"))
    }

    @Test
    fun `pending action is marked expressed when user returns`() {
        val pending = LuluThought(
            assistantId = assistantId,
            content = "他说等下回来，我想记得等他回来。",
            category = LuluThoughtCategory.PENDING_ACTION,
            importance = 4,
            createdAt = 1_000L,
            expiresAt = 100_000L,
        )
        val concern = LuluThought(
            assistantId = assistantId,
            content = "他最近总是很累，我有点放不下。",
            category = LuluThoughtCategory.CONCERN,
            importance = 5,
            createdAt = 1_000L,
            expiresAt = 100_000L,
        )

        val updated = listOf(pending, concern).markResolvedLuluThoughts(
            assistantId = assistantId,
            userText = "我回来了，刚刚学完",
            nowMillis = 2_000L,
        )

        assertTrue(updated.single { it.category == LuluThoughtCategory.PENDING_ACTION }.expressed)
        assertFalse(updated.single { it.category == LuluThoughtCategory.CONCERN }.expressed)
        assertTrue(updated.thoughtHistory(assistantId, nowMillis = 2_000L).none { it.id == pending.id })
    }

    @Test
    fun `perception tags late night and tired user text`() {
        val perception = buildLuluPerception(
            userText = "我好累，想睡觉",
            hourOfDay = 1,
        )

        assertEquals(LuluTimeLabel.LATE_NIGHT, perception.timeLabel)
        assertTrue(perception.userSignals.contains(LuluUserSignal.TIRED))
        assertEquals(LuluSceneLabel.RESTING, perception.sceneLabel)
    }

    @Test
    fun `perception merges health device and app usage context`() {
        val perception = buildLuluPerception(
            input = LuluPerceptionInput(
                userText = "先不聊啦，我要去写作业",
                hourOfDay = 23,
                deviceState = LuluDeviceState(
                    batteryPercent = 12,
                    isCharging = false,
                    networkType = "wifi",
                ),
                healthState = LuluHealthState(
                    sleepMinutes = 240,
                    heartRate = 96,
                    stepCount = 500,
                ),
                appUsageState = LuluAppUsageState(
                    topApps = listOf("哔哩哔哩", "微信"),
                    screenMinutesToday = 420,
                ),
            ),
        )

        assertEquals(LuluTimeLabel.LATE_NIGHT, perception.timeLabel)
        assertEquals(LuluSceneLabel.STUDYING, perception.sceneLabel)
        assertTrue(perception.userSignals.contains(LuluUserSignal.STUDYING))
        assertTrue(perception.userSignals.contains(LuluUserSignal.LOW_BATTERY))
        assertTrue(perception.userSignals.contains(LuluUserSignal.SLEEP_DEBT))
        assertTrue(perception.userSignals.contains(LuluUserSignal.HEAVY_PHONE_USE))
        assertTrue(perception.summary.contains("电量低"))
        assertTrue(perception.summary.contains("睡眠偏少"))
        assertTrue(perception.summary.contains("屏幕时间偏长"))
    }

    @Test
    fun `state update can use structured perception context`() {
        val state = buildLuluStateFromTurn(
            assistantId = assistantId,
            perceptionInput = LuluPerceptionInput(
                userText = "我还好",
                hourOfDay = 23,
                healthState = LuluHealthState(sleepMinutes = 260),
                appUsageState = LuluAppUsageState(screenMinutesToday = 430),
            ),
            assistantText = "早点休息。",
            nowMillis = 10_000L,
        )

        assertEquals(LuluEnergy.SLEEPY, state.energy)
        assertEquals(LuluMode.RESTING, state.mode)
        assertTrue(state.perceptionSummary.contains("睡眠偏少"))
        assertTrue(state.reason.contains("睡眠偏少"))
        assertTrue(state.reason.contains("屏幕时间偏长"))
    }

    @Test
    fun `expression plan becomes shorter and slower when sleepy`() {
        val plan = buildLuluExpressionPlan(
            state = LuluState(
                assistantId = assistantId,
                energy = LuluEnergy.SLEEPY,
                mood = LuluMood.SOFT,
            ),
            reply = "我在呢。别急，先慢慢呼吸一下，然后把今天最难受的地方告诉我。",
        )

        assertEquals(LuluExpressionLength.SHORT, plan.length)
        assertTrue(plan.typingDelayMillis >= 1_200L)
        assertTrue(plan.guidance.contains("短句"))
    }

    @Test
    fun `expression plan carries embodied cues when worried`() {
        val plan = buildLuluExpressionPlan(
            state = LuluState(
                assistantId = assistantId,
                mood = LuluMood.WORRIED,
                energy = LuluEnergy.LOW,
                mode = LuluMode.COMPANION,
                perceptionSummary = "深夜 / 休息中 / 用户信号：疲惫",
            ),
            reply = "我在，先别一个人硬撑。",
        )

        assertEquals(LuluExpressionLength.SHORT, plan.length)
        assertTrue(plan.emojiHint in listOf("🥺", "🫂", "🤍"))
        assertTrue(plan.bodyGestureHint.contains("靠近") || plan.bodyGestureHint.contains("抱"))
        assertTrue(plan.avatarMoodHint.contains("担心") || plan.avatarMoodHint.contains("柔软"))
        assertFalse(plan.allowAvatarShift)
    }

    @Test
    fun `expression plan can suggest playful avatar shift when happy and energetic`() {
        val plan = buildLuluExpressionPlan(
            state = LuluState(
                assistantId = assistantId,
                mood = LuluMood.HAPPY,
                energy = LuluEnergy.HIGH,
                mode = LuluMode.COMPANION,
            ),
            reply = "好耶，我也有点开心起来了！",
        )

        assertEquals("✨", plan.emojiHint)
        assertTrue(plan.allowAvatarShift)
        assertTrue(plan.avatarMoodHint.contains("亮"))
        assertTrue(plan.stickerHint.contains("贴近") || plan.stickerHint.contains("开心"))
    }

    @Test
    fun `expression plan stays quiet during learning mode`() {
        val plan = buildLuluExpressionPlan(
            state = LuluState(
                assistantId = assistantId,
                mood = LuluMood.CALM,
                energy = LuluEnergy.NORMAL,
                mode = LuluMode.LEARNING,
            ),
            reply = "我在旁边，你先写。",
        )

        assertEquals("🤫", plan.emojiHint)
        assertFalse(plan.allowAvatarShift)
        assertTrue(plan.guidance.contains("学习") || plan.bodyGestureHint.contains("安静"))
    }

    @Test
    fun `state tracks intensity and duration`() {
        val previous = LuluState(
            assistantId = assistantId,
            mood = LuluMood.WORRIED,
            moodIntensity = 0.5f,
            updatedAt = 1_000L,
            sinceAt = 1_000L,
        )

        val next = buildLuluStateFromTurn(
            assistantId = assistantId,
            previous = previous,
            userText = "我还是有点累",
            assistantText = "我在。",
            nowMillis = 31_000L,
            hourOfDay = 22,
        )

        assertTrue(next.moodIntensity > previous.moodIntensity)
        assertEquals(1_000L, next.sinceAt)
        assertTrue(next.durationMillis(31_000L) >= 30_000L)
    }
}
