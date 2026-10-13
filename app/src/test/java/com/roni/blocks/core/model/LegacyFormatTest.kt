package com.roni.blocks.core.model

import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.daystate.DayStateSerializer
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Task-18 slice 1: the stored form of [TemplateId] must not change when it stops being an enum.
 * These strings are what 2.x wrote; they must still decode after 3.0. Written before the change.
 */
class LegacyFormatTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun runningDayState_keepsItsStoredForm() {
        val state = DayState.Running(TemplateId.MON_THU, 2)
        val encoded = json.encodeToString(DayStateSerializer, state)
        assertEquals(
            "{\"type\":\"com.roni.blocks.core.daystate.DayState.Running\",\"template\":\"MON_THU\",\"blockIndex\":2}",
            encoded,
        )
        assertEquals(state, json.decodeFromString(DayStateSerializer, encoded))
    }

    @Test
    fun dayRecord_keepsItsStoredForm() {
        val record = DayRecord(
            date = LocalDate(2026, 10, 12),
            template = TemplateId.SUN_TUE_WED,
            segments = emptyList(),
        )
        val encoded = json.encodeToString(DayRecord.serializer(), record)
        assertEquals(
            "{\"date\":\"2026-10-12\",\"template\":\"SUN_TUE_WED\",\"segments\":[],\"flags\":[],\"checkIn\":null,\"plannedBlocks\":null,\"demo\":false}",
            encoded,
        )
        assertEquals(record, json.decodeFromString(DayRecord.serializer(), encoded))
    }

    @Test
    fun todayPlan_keepsItsStoredForm() {
        val plan = TodayPlan(TemplateId.FRI_SAT, friSatBlocks)
        val encoded = json.encodeToString(TodayPlan.serializer(), plan)
        assertEquals(
            "{\"templateId\":\"FRI_SAT\",\"blocks\":[" +
                "{\"id\":\"fri_sat.kid_am\",\"type\":{\"type\":\"com.roni.blocks.core.model.BlockType.KidTime\"},\"start\":\"09:00\",\"end\":\"13:00\",\"pomodoro\":null,\"quiet\":false}," +
                "{\"id\":\"fri_sat.free\",\"type\":{\"type\":\"com.roni.blocks.core.model.BlockType.Free\"},\"start\":\"13:00\",\"end\":\"15:00\",\"pomodoro\":null,\"quiet\":false}," +
                "{\"id\":\"fri_sat.kid_pm\",\"type\":{\"type\":\"com.roni.blocks.core.model.BlockType.KidTime\"},\"start\":\"15:00\",\"end\":\"22:30\",\"pomodoro\":null,\"quiet\":false}]," +
                "\"edited\":false}",
            encoded,
        )
        assertEquals(plan, json.decodeFromString(TodayPlan.serializer(), encoded))
    }

    // FRI_SAT as 2.x shipped it (assets/templates.json).
    private val friSatBlocks = listOf(
        BlockSpec("fri_sat.kid_am", BlockType.KidTime, LocalTime(9, 0), LocalTime(13, 0)),
        BlockSpec("fri_sat.free", BlockType.Free, LocalTime(13, 0), LocalTime(15, 0)),
        BlockSpec("fri_sat.kid_pm", BlockType.KidTime, LocalTime(15, 0), LocalTime(22, 30)),
    )
}
