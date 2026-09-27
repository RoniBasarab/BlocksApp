package com.roni.blocks.navigation

import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.model.TemplateId
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class StartDestinationTest {
    @Test
    fun notStarted_isTheOnlyStateThatShowsThePicker() {
        assertEquals(StartDestination.TEMPLATE_PICKER, resolveStartDestination(DayState.NotStarted))
    }

    @Test
    fun startedOrFinishedDay_opensHome() {
        val now = Instant.parse("2026-09-27T09:40:00Z")
        val running = DayState.Running(TemplateId.SUN_TUE_WED, 1)
        listOf(
            running,
            DayState.Paused(running, now),
            DayState.Interrupted(running, now),
            DayState.KidTime,
            DayState.Evening(0),
            DayState.Evening(1),
            DayState.Ended,
            DayState.Off,
        ).forEach { state ->
            assertEquals("$state", StartDestination.HOME, resolveStartDestination(state))
        }
    }
}
