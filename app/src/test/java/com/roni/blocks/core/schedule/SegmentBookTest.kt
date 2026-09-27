package com.roni.blocks.core.schedule

import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.Template
import com.roni.blocks.core.model.TemplateId
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Test

class SegmentBookTest {
    private val zone = TimeZone.UTC
    private val date = LocalDate(2026, 9, 21)
    private val template = Template(
        TemplateId.MON_THU,
        "Mon/Thu",
        listOf(
            BlockSpec(BlockType.Cv, LocalTime(9, 0), LocalTime(9, 30)),
            BlockSpec(BlockType.Coding, LocalTime(9, 30), LocalTime(11, 30)),
            BlockSpec(BlockType.Free, LocalTime(11, 30), LocalTime(12, 0)),
            BlockSpec(BlockType.Gym, LocalTime(12, 0), LocalTime(14, 0)),
            BlockSpec(BlockType.KidTime, LocalTime(19, 0), LocalTime(22, 30), quiet = true),
        ),
    )

    @Test
    fun startDayConsumesEarlierBlocks() {
        val book = SegmentBook.startDay(template, date, zone, Instant.parse("2026-09-21T10:00:00Z"))
        assertEquals(SegmentKind.CONSUMED, book.segments.first { it.type == BlockType.Cv }.kind)
        val coding = book.segments.first { it.type == BlockType.Coding }
        assertEquals(SegmentKind.PLANNED, coding.kind)
        assertEquals(Instant.parse("2026-09-21T10:00:00Z"), coding.actualStart)
        assertEquals(null, coding.actualEnd)
    }

    @Test
    fun nextAfterEffectiveEndShrinksOrConsumes() {
        val started = SegmentBook.startDay(template, date, zone, Instant.parse("2026-09-21T09:30:00Z"))
        val leaked = SegmentBook.next(started, template, date, zone, Instant.parse("2026-09-21T11:45:00Z"))
        val free = leaked.segments.first { it.type == BlockType.Free && it.kind == SegmentKind.PLANNED }
        assertEquals(Instant.parse("2026-09-21T11:45:00Z"), free.actualStart)
        assertEquals(Instant.parse("2026-09-21T12:00:00Z"), free.plannedEnd)
        val consumed = SegmentBook.next(started, template, date, zone, Instant.parse("2026-09-21T12:05:00Z"))
        assertTrue(consumed.segments.any { it.type == BlockType.Free && it.kind == SegmentKind.CONSUMED })
    }

    @Test
    fun earlyNextOpensSyntheticFreeThatAutoEnds() {
        val started = SegmentBook.startDay(template, date, zone, Instant.parse("2026-09-21T09:30:00Z"))
        val early = SegmentBook.next(started, template, date, zone, Instant.parse("2026-09-21T11:00:00Z"))
        val free = early.segments.first { it.kind == SegmentKind.SYNTHETIC_FREE }
        assertEquals(Instant.parse("2026-09-21T11:00:00Z"), free.actualStart)
        assertEquals(Instant.parse("2026-09-21T11:30:00Z"), free.plannedEnd)
        val advanced = SegmentBook.next(early, template, date, zone, Instant.parse("2026-09-21T11:30:00Z"))
        assertEquals(Instant.parse("2026-09-21T11:30:00Z"), advanced.segments.first { it.kind == SegmentKind.SYNTHETIC_FREE }.actualEnd)
        assertEquals(BlockType.Free, advanced.segments.last().type)
    }

    @Test
    fun pauseAndResume() {
        val started = SegmentBook.startDay(template, date, zone, Instant.parse("2026-09-21T09:30:00Z"))
        val paused = SegmentBook.pause(started, Instant.parse("2026-09-21T10:00:00Z"))
        assertEquals(null, paused.segments.last { it.kind == SegmentKind.PAUSE }.actualEnd)
        val resumed = SegmentBook.resumePause(paused, Instant.parse("2026-09-21T10:20:00Z"))
        assertEquals(Instant.parse("2026-09-21T10:20:00Z"), resumed.segments.last { it.kind == SegmentKind.PAUSE }.actualEnd)
        assertEquals(Instant.parse("2026-09-21T11:50:00Z"), SegmentBook.effectiveEnd(resumed, Instant.parse("2026-09-21T10:20:00Z")))
    }

    @Test
    fun interruptResumeWhereIWasShifts() {
        val started = SegmentBook.startDay(template, date, zone, Instant.parse("2026-09-21T09:30:00Z"))
        val interrupted = SegmentBook.interrupt(started, Instant.parse("2026-09-21T10:00:00Z"))
        val resumed = SegmentBook.resumeWhereIWas(interrupted, Instant.parse("2026-09-21T10:30:00Z"))
        assertEquals(Instant.parse("2026-09-21T10:30:00Z"), resumed.segments.last { it.kind == SegmentKind.INTERRUPTION }.actualEnd)
        assertEquals(30.minutes, resumed.shift)
    }

    @Test
    fun jumpToNowMarksSkippedInterruptions() {
        val started = SegmentBook.startDay(template, date, zone, Instant.parse("2026-09-21T09:30:00Z"))
        val interrupted = SegmentBook.interrupt(started, Instant.parse("2026-09-21T11:00:00Z"))
        val jumped = SegmentBook.jumpToNow(interrupted, template, date, zone, Instant.parse("2026-09-21T12:30:00Z"))
        assertTrue(jumped.segments.any { it.kind == SegmentKind.INTERRUPTION && it.type == BlockType.Free })
        assertEquals(BlockType.Gym, jumped.segments.last().type)
    }

    @Test
    fun kidTimeStartsAutomaticallyWithoutLeak() {
        val started = SegmentBook.startDay(template, date, zone, Instant.parse("2026-09-21T18:00:00Z"))
        val kid = SegmentBook.enterKidTime(started, template, date, zone)
        val previous = kid.segments.first { it.type != BlockType.KidTime && it.actualEnd != null }
        assertEquals(Instant.parse("2026-09-21T19:00:00Z"), kid.segments.last().actualStart)
        assertTrue(previous.actualEnd == Instant.parse("2026-09-21T19:00:00Z") || kid.segments.last().type == BlockType.KidTime)
    }

    @Test
    fun kidAsleepOpensDishesAndChoiceRunsTo2230() {
        val kid = SegmentBook.enterKidTime(SegmentBook.startDay(template, date, zone, Instant.parse("2026-09-21T19:00:00Z")), template, date, zone)
        val dishes = SegmentBook.kidAsleep(kid, date, zone, Instant.parse("2026-09-21T20:00:00Z"))
        assertEquals(BlockType.Dishes, dishes.segments.last().type)
        assertEquals(Instant.parse("2026-09-21T20:20:00Z"), dishes.segments.last().plannedEnd)
        val evening = SegmentBook.eveningChoice(dishes, Choice.GAME, date, zone, Instant.parse("2026-09-21T20:20:00Z"))
        assertEquals(BlockType.EveningChoice(Choice.GAME), evening.segments.last().type)
        assertEquals(Instant.parse("2026-09-21T22:30:00Z"), evening.segments.last().plannedEnd)
    }

    @Test
    fun endDayClosesOpenSegmentsAndFlagsSkippedEvening() {
        val kid = SegmentBook.enterKidTime(SegmentBook.startDay(template, date, zone, Instant.parse("2026-09-21T19:00:00Z")), template, date, zone)
        val ended = SegmentBook.endDay(kid, Instant.parse("2026-09-21T22:30:00Z"), fromKidTime = true)
        assertTrue(ended.segments.all { it.actualEnd != null })
        assertTrue(DayFlag.EVENING_SKIPPED in ended.flags)
    }

    @Test
    fun gymMissedWhenConsumedOrShort() {
        val missed = SegmentBook.startDay(template, date, zone, Instant.parse("2026-09-21T14:00:00Z"))
        assertTrue(SegmentBook.gymMissed(missed))
    }
}
