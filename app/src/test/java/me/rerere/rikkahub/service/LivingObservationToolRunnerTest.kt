package me.rerere.rikkahub.service

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivingObservationToolRunnerTest {
    @Test
    fun `safe observation tool args are selected by intent kind`() {
        val appUsageArgs = LivingObservationToolRunner.argumentsFor(
            toolName = "get_app_usage",
            intentKind = LivingIntentKind.STUDY_FOCUS,
        )
        val healthArgs = LivingObservationToolRunner.argumentsFor(
            toolName = "get_gadgetbridge_data",
            intentKind = LivingIntentKind.HEALTH_SAFETY,
        )
        val calendarArgs = LivingObservationToolRunner.argumentsFor(
            toolName = "calendar_tool",
            intentKind = LivingIntentKind.DEADLINE,
        )

        assertTrue(appUsageArgs.toString().contains("\"limit\":5"))
        assertTrue(healthArgs.toString().contains("\"data_type\":\"all\""))
        assertTrue(calendarArgs.toString().contains("\"action\":\"read\""))
        assertTrue(calendarArgs.toString().contains("\"limit\":5"))
    }

    @Test
    fun `unsafe or side effect tools are not auto executed before judgment`() {
        assertFalse(LivingObservationToolRunner.canAutoObserve("set_alarm"))
        assertFalse(LivingObservationToolRunner.canAutoObserve("camera_capture"))
        assertFalse(LivingObservationToolRunner.canAutoObserve("write_lulu_journal"))
        assertTrue(LivingObservationToolRunner.canAutoObserve("get_battery_info"))
        assertTrue(LivingObservationToolRunner.canAutoObserve("today_study_plan"))
    }

    @Test
    fun `tool output is compacted for observation prompt`() {
        val text = LivingObservationToolRunner.formatResult(
            toolName = "get_battery_info",
            outputs = listOf(
                """{"success":true,"level":81,"is_charging":false,"message":"Battery: 81%, Not charging"}""",
            ),
        )

        assertEquals(
            "tool_result[get_battery_info]={\"success\":true,\"level\":81,\"is_charging\":false,\"message\":\"Battery: 81%, Not charging\"}",
            text,
        )
    }

    @Test
    fun `blank output becomes unavailable signal`() {
        val text = LivingObservationToolRunner.formatResult(
            toolName = "today_study_plan",
            outputs = emptyList(),
        )

        assertEquals("tool_result[today_study_plan]=empty", text)
    }

    @Test
    fun `unknown tool uses empty json arguments`() {
        assertEquals(JsonObject(emptyMap()), LivingObservationToolRunner.argumentsFor("unknown", LivingIntentKind.ORDINARY_SILENCE))
    }
}
