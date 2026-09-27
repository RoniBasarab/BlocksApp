package com.roni.blocks.core.schedule

import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.Template
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.storage.debtMinutes
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
            BlockSpec("cv", BlockType.Cv, LocalTime(9, 0), LocalTime(9, 30)),
            BlockSpec("coding", BlockType.Coding, LocalTime(9, 30), LocalTime(11, 30)),
            BlockSpec("free", BlockType.Free, LocalTime(11, 30), LocalTime(12, 0)),
            BlockSpec("gym", BlockType.Gym, LocalTime(12, 0), LocalTime(14, 0)),
            BlockSpec("kid_time", BlockType.KidTime, LocalTime(19, 0), LocalTime(22, 30), quiet = true),
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
    }

    @Test
    fun nextSlot_earlyOnWorkBlock_restBecomesFree() {
        val started = SegmentBook.startDay(template, date, zone, Instant.parse("2026-09-21T09:30:00Z"))
        val next = SegmentBook.next(started, template, date, zone, Instant.parse("2026-09-21T10:00:00Z"))

        val coding = next.segments.first { it.type == BlockType.Coding && it.kind == SegmentKind.PLANNED }
        assertEquals("the block the clock was in ends when the user moves on", Instant.parse("2026-09-21T10:00:00Z"), coding.actualEnd)
        val free = next.segments.first { it.kind == SegmentKind.SYNTHETIC_FREE }
        assertEquals(Instant.parse("2026-09-21T10:00:00Z"), free.actualStart)
        assertEquals("the rest of the block is free, and it never leaks", Instant.parse("2026-09-21T11:30:00Z"), free.plannedEnd)
    }

    @Test
    fun nextSlot_afterSyntheticFreeStartsTheNextBlockOnSchedule() {
        val started = SegmentBook.startDay(template, date, zone, Instant.parse("2026-09-21T09:30:00Z"))
        val early = SegmentBook.next(started, template, date, zone, Instant.parse("2026-09-21T10:00:00Z"))
        val autoEnded = SegmentBook.next(early, template, date, zone, Instant.parse("2026-09-21T11:30:00Z"))

        assertEquals(Instant.parse("2026-09-21T11:30:00Z"), autoEnded.segments.first { it.kind == SegmentKind.SYNTHETIC_FREE }.actualEnd)
        val free = autoEnded.segments.first { it.type == BlockType.Free && it.kind == SegmentKind.PLANNED }
        assertEquals("the next block starts when the free time ran out", Instant.parse("2026-09-21T11:30:00Z"), free.actualStart)
    }

    @Test
    fun nextSlot_duringSyntheticFree_startsNextNow() {
        val started = SegmentBook.startDay(template, date, zone, Instant.parse("2026-09-21T09:30:00Z"))
        val early = SegmentBook.next(started, template, date, zone, Instant.parse("2026-09-21T10:00:00Z"))
        val next = SegmentBook.next(early, template, date, zone, Instant.parse("2026-09-21T10:05:00Z"))

        assertEquals(Instant.parse("2026-09-21T10:05:00Z"), next.segments.first { it.kind == SegmentKind.SYNTHETIC_FREE }.actualEnd)
        val free = next.segments.first { it.type == BlockType.Free && it.kind == SegmentKind.PLANNED }
        assertEquals(Instant.parse("2026-09-21T10:05:00Z"), free.actualStart)
        assertEquals("it keeps its own planned end", Instant.parse("2026-09-21T12:00:00Z"), free.plannedEnd)
    }

    @Test
    fun nextSlot_duringFree_startsNextNow_keepsItsEnd() {
        val started = SegmentBook.startDay(template, date, zone, Instant.parse("2026-09-21T11:30:00Z"))
        val next = SegmentBook.next(started, template, date, zone, Instant.parse("2026-09-21T12:30:00Z"))

        val free = next.segments.first { it.type == BlockType.Free && it.kind == SegmentKind.PLANNED }
        assertEquals(Instant.parse("2026-09-21T12:30:00Z"), free.actualEnd)
        val gym = next.segments.first { it.type == BlockType.Gym && it.kind == SegmentKind.PLANNED }
        assertEquals(Instant.parse("2026-09-21T12:30:00Z"), gym.actualStart)
        assertEquals(Instant.parse("2026-09-21T14:00:00Z"), gym.plannedEnd)
    }

    @Test
    fun nextSlot_late_countsLeak() {
        val started = SegmentBook.startDay(template, date, zone, Instant.parse("2026-09-21T09:30:00Z"))
        val next = SegmentBook.next(started, template, date, zone, Instant.parse("2026-09-21T11:45:00Z"))

        val coding = next.segments.first { it.type == BlockType.Coding && it.kind == SegmentKind.PLANNED }
        assertEquals(Instant.parse("2026-09-21T11:45:00Z"), coding.actualEnd)
        assertEquals("15 minutes past the planned end is debt", 15, debtMinutes(next.segments))
    }

    @Test
    fun nextSlot_pastNextBlockEnd_landsWhereTheClockIs() {
        val started = SegmentBook.startDay(template, date, zone, Instant.parse("2026-09-21T09:30:00Z"))
        val next = SegmentBook.next(started, template, date, zone, Instant.parse("2026-09-21T14:30:00Z"))

        assertTrue("the skipped free block is consumed", next.segments.any { it.type == BlockType.Free && it.kind == SegmentKind.CONSUMED })
        assertTrue("gym ended at 14:00, so it is consumed too", next.segments.any { it.type == BlockType.Gym && it.kind == SegmentKind.CONSUMED })
        val landed = next.segments.last()
        assertEquals("the day lands in the block that contains the clock", BlockType.KidTime, landed.type)
        assertEquals(Instant.parse("2026-09-21T14:30:00Z"), landed.actualStart)
    }

    @Test
    fun pause_movesNoEnds() {
        val started = SegmentBook.startDay(template, date, zone, Instant.parse("2026-09-21T09:30:00Z"))
        val paused = SegmentBook.pause(started, Instant.parse("2026-09-21T10:00:00Z"))
        val resumed = SegmentBook.resumePause(paused, Instant.parse("2026-09-21T10:20:00Z"))

        val coding = resumed.segments.first { it.type == BlockType.Coding && it.kind == SegmentKind.PLANNED }
        assertEquals("a pause shortens the block, it does not push the end", Instant.parse("2026-09-21T11:30:00Z"), SegmentBook.effectiveEnd(resumed, Instant.parse("2026-09-21T10:20:00Z")))
        assertEquals(Instant.parse("2026-09-21T11:30:00Z"), coding.plannedEnd)
    }

    @Test
    fun pause_autoClosesAtBlockEnd() {
        val started = SegmentBook.startDay(template, date, zone, Instant.parse("2026-09-21T09:30:00Z"))
        val paused = SegmentBook.pause(started, Instant.parse("2026-09-21T10:00:00Z"))
        val autoClosed = SegmentBook.resumePause(paused, Instant.parse("2026-09-21T11:45:00Z"))

        assertEquals("a late alarm must not stretch the pause past the block", Instant.parse("2026-09-21T11:30:00Z"), autoClosed.segments.last { it.kind == SegmentKind.PAUSE }.actualEnd)
    }

    @Test
    fun pause_isNotDebt_butIsLoggedAsPauseTime() {
        val started = SegmentBook.startDay(template, date, zone, Instant.parse("2026-09-21T09:30:00Z"))
        val paused = SegmentBook.pause(started, Instant.parse("2026-09-21T10:00:00Z"))
        val resumed = SegmentBook.resumePause(paused, Instant.parse("2026-09-21T10:20:00Z"))

        assertEquals(0, debtMinutes(resumed.segments))
        assertEquals(20, resumed.segments.filter { it.kind == SegmentKind.PAUSE }.sumOf { (it.actualEnd!! - it.actualStart).inWholeMinutes })
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
