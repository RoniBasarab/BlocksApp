package com.roni.blocks.core.storage

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.model.EVENING_CHOICE_ID
import com.roni.blocks.core.model.EVENING_DISHES_ID
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.schedule.ScheduleEngine
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Block ids identify a block in notification actions and in the calendar UI, so they must be
 * stable across app versions rather than derived from a position that editing would move.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TemplateBlockIdsTest {
    private val templates = TemplateManager(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun templateBlockIds_areUniqueAndStable() {
        val expected = mapOf(
            TemplateId.MON_THU to listOf(
                "mon_thu.cv",
                "mon_thu.coding_am",
                "mon_thu.free_pre_gym",
                "mon_thu.gym",
                "mon_thu.free_post_gym",
                "mon_thu.coding_pm",
                "mon_thu.free_pm",
                "mon_thu.kid_time",
            ),
            TemplateId.SUN_TUE_WED to listOf(
                "sun_tue_wed.cv",
                "sun_tue_wed.coding_am",
                "sun_tue_wed.free_midday",
                "sun_tue_wed.coding_pm",
                "sun_tue_wed.free_pm",
                "sun_tue_wed.kid_time",
            ),
            TemplateId.FRI_SAT to listOf(
                "fri_sat.kid_time_am",
                "fri_sat.free",
                "fri_sat.kid_time_pm",
            ),
            TemplateId.OFF to emptyList(),
        )

        expected.forEach { (template, ids) ->
            val blocks = requireNotNull(templates.find(template)).blocks
            assertEquals("$template block ids", ids, blocks.map { it.id })
            assertEquals("$template is in time order", blocks.map { it.start }, blocks.map { it.start }.sorted())
        }

        val allIds = TemplateId.builtIns.flatMap { template -> requireNotNull(templates.find(template)).blocks.map { it.id } }
        assertEquals(
            "block ids must not repeat",
            allIds.size,
            allIds.toSet().size,
        )
    }

    @Test
    fun eveningBlocks_useTheRuntimeIds() {
        val engine = ScheduleEngine(
            object : Clock {
                override fun now(): Instant = Instant.parse("2026-09-27T09:00:00Z")
            },
            TimeZone.UTC,
        )

        assertEquals(
            listOf(EVENING_DISHES_ID, EVENING_CHOICE_ID),
            engine.eveningPlan(LocalTime(20, 5)).map { it.id },
        )
    }
}
