package com.roni.blocks.core.schedule

import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.Pomodoro
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.Template
import com.roni.blocks.core.model.TemplateId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Test

class ScheduleEngineTest {
    private val zone = TimeZone.UTC
    private val date = LocalDate(2026, 9, 21)
    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-09-21T09:00:00Z")
    }
    private val engine = ScheduleEngine(clock, zone)
    private val pomo = Pomodoro(50, 10)

    private val template = Template(
        TemplateId.MON_THU,
        "Mon/Thu",
        listOf(
            BlockSpec("cv", BlockType.Cv, LocalTime(9, 0), LocalTime(9, 30)),
            BlockSpec("coding", BlockType.Coding, LocalTime(9, 30), LocalTime(11, 30), pomo),
            BlockSpec("free", BlockType.Free, LocalTime(11, 30), LocalTime(12, 0)),
            BlockSpec("gym", BlockType.Gym, LocalTime(12, 0), LocalTime(14, 0)),
        ),
    )

    @Test
    fun overrunShrinksTheNextBlock() {
        val actualEnd = Instant.parse("2026-09-21T11:45:00Z")
        val segments = engine.overrun(template, date, blockIndex = 1, actualEnd)
        val next = segments.first { it.type == BlockType.Free }
        assertEquals(Instant.parse("2026-09-21T11:45:00Z"), next.actualStart)
        assertEquals(Instant.parse("2026-09-21T12:00:00Z"), next.plannedEnd)
        assertEquals(SegmentKind.PLANNED, next.kind)
    }

    @Test
    fun overrunPastNextEndIsConsumed() {
        val actualEnd = Instant.parse("2026-09-21T12:05:00Z")
        val segments = engine.overrun(template, date, blockIndex = 1, actualEnd)
        val free = segments.first { it.type == BlockType.Free }
        assertEquals(SegmentKind.CONSUMED, free.kind)
    }

    @Test
    fun earlyEndAddsSyntheticFree() {
        val actualEnd = Instant.parse("2026-09-21T11:00:00Z")
        val segments = engine.earlyEnd(template, date, blockIndex = 1, actualEnd)
        val free = segments.first { it.kind == SegmentKind.SYNTHETIC_FREE }
        assertEquals(BlockType.Free, free.type)
        assertEquals(actualEnd, free.actualStart)
        assertEquals(Instant.parse("2026-09-21T11:30:00Z"), free.actualEnd)
    }

    @Test
    fun emptyCvSlotBecomesFree() {
        val now = Instant.parse("2026-09-21T10:00:00Z")
        val segments = engine.emptyCvSlot(template, date, now)
        assertEquals(1, segments.size)
        assertEquals(BlockType.Free, segments.single().type)
        assertEquals(SegmentKind.SYNTHETIC_FREE, segments.single().kind)
    }

    @Test
    fun pomodoroPhaseAtAnyMinute() {
        val plannedStart = Instant.parse("2026-09-21T09:30:00Z")
        val end = Instant.parse("2026-09-21T11:30:00Z")
        val focus = engine.pomodoroPhase(plannedStart, end, Instant.parse("2026-09-21T09:40:00Z"), pomo)
        assertTrue(focus.focusing)
        assertEquals(1, focus.index)
        assertEquals(2, focus.total)
        val second = engine.pomodoroPhase(plannedStart, end, Instant.parse("2026-09-21T10:30:00Z"), pomo)
        assertTrue(second.focusing)
        assertEquals(2, second.index)
        val brk = engine.pomodoroPhase(plannedStart, end, Instant.parse("2026-09-21T10:25:00Z"), pomo)
        assertTrue(!brk.focusing)
    }

    @Test
    fun pomodoro_phasesFollowActualStart() {
        val start = engine.pomodoroPhase(
            Instant.parse("2026-09-21T09:20:00Z"),
            Instant.parse("2026-09-21T11:30:00Z"),
            Instant.parse("2026-09-21T11:25:00Z"),
            pomo,
        )
        assertTrue(start.focusing)
        assertEquals(3, start.index)
        assertEquals(3, start.total)
        assertEquals("the last phase is cut at the block's end", 5, start.minutesLeft)
    }

    @Test
    fun noKidAsleepTapAutoEndsWithEveningSkipped() {
        val now = Instant.parse("2026-09-21T22:30:00Z")
        val flags = engine.autoEndFlags(DayState.KidTime, now, date)
        assertEquals(setOf(DayFlag.AUTO_ENDED, DayFlag.EVENING_SKIPPED), flags)
    }

    @Test
    fun kidAsleepAfter2210LeavesOnlyDishes() {
        val plan = engine.eveningPlan(LocalTime(22, 11))
        assertEquals(1, plan.size)
        assertEquals(BlockType.Dishes, plan.single().type)
        assertTrue(plan.single().end < LocalTime(22, 30) || plan.single().end == LocalTime(22, 30))
        val earlier = engine.eveningPlan(LocalTime(22, 0))
        assertEquals(2, earlier.size)
    }

    @Test
    fun nextWakeUpRePromptsEvery15MinutesUntil10() {
        val prompts = engine.rePrompts(date)
        assertEquals(5, prompts.size)
        assertEquals(Instant.parse("2026-09-21T09:00:00Z"), prompts.first())
        assertEquals(Instant.parse("2026-09-21T09:15:00Z"), prompts[1])
        assertEquals(Instant.parse("2026-09-21T10:00:00Z"), prompts.last())
        val next = engine.nextWakeUp(DayState.NotStarted, date, Instant.parse("2026-09-21T09:00:00Z"))
        assertEquals(Instant.parse("2026-09-21T09:15:00Z"), next)
    }

    @Test
    fun rebootReplayRecordsDowntimeAsInterruption() {
        val segment = engine.replayDowntime(
            Instant.parse("2026-09-21T12:00:00Z"),
            Instant.parse("2026-09-21T12:40:00Z"),
        )
        assertEquals(SegmentKind.INTERRUPTION, segment.kind)
        assertEquals(null, segment.plannedStart)
    }
}
