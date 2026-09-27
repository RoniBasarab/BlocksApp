package com.roni.blocks.core.daystate

import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.TemplateId
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayStateMachineTest {
    private val now = Instant.parse("2026-09-21T06:00:00Z")

    @Test
    fun startDayAndOff() {
        assertEquals(
            DayState.Running(TemplateId.MON_THU, 0),
            DayStateMachine.transition(DayState.NotStarted, DayEvent.StartDay(TemplateId.MON_THU), now),
        )
        assertEquals(DayState.Off, DayStateMachine.transition(DayState.NotStarted, DayEvent.TakeOffDay, now))
        assertEquals(DayState.Off, DayStateMachine.transition(DayState.NotStarted, DayEvent.StartDay(TemplateId.OFF), now))
    }

    @Test
    fun runningTransitions() {
        val running = DayState.Running(TemplateId.SUN_TUE_WED, 1)
        assertEquals(running.copy(blockIndex = 2), DayStateMachine.transition(running, DayEvent.Next("any"), now))
        assertEquals(DayState.Paused(running, now), DayStateMachine.transition(running, DayEvent.Pause, now))
        assertEquals(DayState.KidTime, DayStateMachine.transition(running, DayEvent.KidTimeStarted, now))
        assertEquals(
            DayState.Ended,
            DayStateMachine.transition(running, DayEvent.EndDay(EndReason.NORMAL), now),
        )
    }

    @Test
    fun endReasonDoesNotChangeWhereTheDayEnds() {
        val running = DayState.Running(TemplateId.MON_THU, 0)
        assertEquals(
            DayState.Ended,
            DayStateMachine.transition(running, DayEvent.EndDay(EndReason.EMERGENCY), now),
        )
    }

    @Test
    fun resumePauseAndEvening() {
        val running = DayState.Running(TemplateId.MON_THU, 0)
        val paused = DayState.Paused(running, now)
        assertEquals(running, DayStateMachine.transition(paused, DayEvent.Resume, now))
        assertEquals(DayState.Evening(0), DayStateMachine.transition(DayState.KidTime, DayEvent.KidAsleep, now))
        assertEquals(
            DayState.Evening(1),
            DayStateMachine.transition(DayState.Evening(0), DayEvent.EveningChoice(Choice.READ), now),
        )
    }

    @Test
    fun endDayFromEveryActiveState() {
        val running = DayState.Running(TemplateId.FRI_SAT, 0)
        val active = listOf(
            running,
            DayState.Paused(running, now),
            DayState.KidTime,
            DayState.Evening(0),
        )
        active.forEach { state ->
            assertEquals(
                "$state",
                DayState.Ended,
                DayStateMachine.transition(state, DayEvent.EndDay(EndReason.NORMAL), now),
            )
        }
    }

    @Test
    fun illegalTransitionsReturnNull() {
        val running = DayState.Running(TemplateId.MON_THU, 0)
        assertNull(DayStateMachine.transition(DayState.NotStarted, DayEvent.Next("any"), now))
        assertNull(DayStateMachine.transition(DayState.NotStarted, DayEvent.EndDay(EndReason.NORMAL), now))
        assertNull(DayStateMachine.transition(DayState.Off, DayEvent.EndDay(EndReason.NORMAL), now))
        assertNull(DayStateMachine.transition(DayState.Off, DayEvent.Next("any"), now))
        assertNull(DayStateMachine.transition(DayState.Ended, DayEvent.EndDay(EndReason.NORMAL), now))
        assertNull(DayStateMachine.transition(DayState.Ended, DayEvent.StartDay(TemplateId.MON_THU), now))
        assertNull(DayStateMachine.transition(running, DayEvent.KidAsleep, now))
        assertNull(DayStateMachine.transition(DayState.KidTime, DayEvent.Pause, now))
        assertNull(DayStateMachine.transition(DayState.Evening(1), DayEvent.EveningChoice(Choice.CHILL), now))
        assertNull(DayStateMachine.transition(DayState.Paused(running, now), DayEvent.Next("any"), now))
    }

    @Test
    fun dateRolloverReturnsNotStarted() {
        val stored = LocalDate(2026, 9, 21)
        val today = LocalDate(2026, 9, 22)
        assertEquals(DayState.NotStarted, rollover(DayState.Ended, stored, today))
        assertEquals(DayState.NotStarted, rollover(DayState.Running(TemplateId.MON_THU, 2), stored, today))
        assertEquals(DayState.Off, rollover(DayState.Off, stored, stored))
    }
}
