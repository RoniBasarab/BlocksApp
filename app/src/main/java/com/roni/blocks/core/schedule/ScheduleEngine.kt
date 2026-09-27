package com.roni.blocks.core.schedule

import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.EVENING_CHOICE_ID
import com.roni.blocks.core.model.EVENING_DISHES_ID
import com.roni.blocks.core.model.Pomodoro
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.Template
import com.roni.blocks.core.time.at
import com.roni.blocks.core.time.localInstant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

data class PomodoroPhase(
    val focusing: Boolean,
    val index: Int,
    val total: Int,
    val minutesLeft: Int,
)

data class ResolvedBlock(
    val spec: BlockSpec,
    val kind: SegmentKind,
    val plannedStart: Instant,
    val plannedEnd: Instant,
    val effectiveStart: Instant,
    val effectiveEnd: Instant,
)

@Singleton
class ScheduleEngine @Inject constructor(
    private val clock: Clock,
    private val zone: TimeZone,
) {
    fun now(): Instant = clock.now()

    fun planned(template: Template, date: LocalDate): List<ResolvedBlock> =
        template.blocks.map { spec ->
            val start = spec.start.at(date, zone)
            val end = spec.end.at(date, zone)
            ResolvedBlock(spec, SegmentKind.PLANNED, start, end, start, end)
        }

    fun overrun(template: Template, date: LocalDate, blockIndex: Int, actualEnd: Instant): List<Segment> {
        val blocks = planned(template, date)
        val current = blocks[blockIndex]
        val segments = mutableListOf(
            segment(current.spec.type, SegmentKind.PLANNED, current.plannedStart, current.plannedEnd, current.plannedStart, actualEnd),
        )
        var cursor = actualEnd
        for (next in blocks.drop(blockIndex + 1)) {
            if (cursor >= next.plannedEnd) {
                segments += segment(next.spec.type, SegmentKind.CONSUMED, next.plannedStart, next.plannedEnd, next.plannedStart, next.plannedEnd)
            } else {
                val start = maxOf(cursor, next.plannedStart)
                segments += segment(next.spec.type, SegmentKind.PLANNED, next.plannedStart, next.plannedEnd, start, next.plannedEnd)
                break
            }
        }
        return segments
    }

    fun earlyEnd(template: Template, date: LocalDate, blockIndex: Int, actualEnd: Instant): List<Segment> {
        val block = planned(template, date)[blockIndex]
        return listOf(
            segment(block.spec.type, SegmentKind.PLANNED, block.plannedStart, block.plannedEnd, block.plannedStart, actualEnd),
            segment(BlockType.Free, SegmentKind.SYNTHETIC_FREE, block.plannedStart, block.plannedEnd, actualEnd, block.plannedEnd),
        )
    }

    fun emptyCvSlot(template: Template, date: LocalDate, now: Instant): List<Segment> {
        val blocks = planned(template, date)
        return blocks.mapNotNull { block ->
            if (block.spec.type != BlockType.Cv || now < block.plannedEnd) return@mapNotNull null
            segment(BlockType.Free, SegmentKind.SYNTHETIC_FREE, block.plannedStart, block.plannedEnd, block.plannedStart, block.plannedEnd)
        }
    }

    fun effectiveEnd(plannedEnd: Instant, paused: kotlin.time.Duration): Instant = plannedEnd + paused

    fun pause(template: Template, date: LocalDate, blockIndex: Int, pauseStart: Instant, pauseEnd: Instant): List<Segment> {
        val blocks = planned(template, date)
        val current = blocks[blockIndex]
        val paused = pauseEnd - pauseStart
        val extended = effectiveEnd(current.plannedEnd, paused)
        val following = blocks.getOrNull(blockIndex + 1)
        val eaten = following?.let { next ->
            if (extended >= next.plannedEnd) {
                segment(next.spec.type, SegmentKind.CONSUMED, next.plannedStart, next.plannedEnd, next.plannedStart, next.plannedEnd)
            } else {
                segment(next.spec.type, SegmentKind.PLANNED, next.plannedStart, next.plannedEnd, maxOf(next.plannedStart, extended), next.plannedEnd)
            }
        }
        return listOfNotNull(
            segment(current.spec.type, SegmentKind.PLANNED, current.plannedStart, current.plannedEnd, current.plannedStart, null),
            segment(current.spec.type, SegmentKind.PAUSE, null, null, pauseStart, pauseEnd),
            eaten,
        )
    }

    fun pomodoroPhase(start: LocalTime, at: LocalTime, pomodoro: Pomodoro, blockMinutes: Int): PomodoroPhase {
        val elapsed = (at.toSecondOfDay() - start.toSecondOfDay()) / 60
        val cycle = pomodoro.focusMinutes + pomodoro.breakMinutes
        val into = elapsed.mod(cycle)
        val focusing = into < pomodoro.focusMinutes
        val left = if (focusing) pomodoro.focusMinutes - into else pomodoro.breakMinutes - (into - pomodoro.focusMinutes)
        val total = (blockMinutes + cycle - 1) / cycle
        return PomodoroPhase(focusing, elapsed / cycle + 1, total.coerceAtLeast(1), left)
    }

    fun eveningPlan(asleepAt: LocalTime): List<BlockSpec> {
        val dayEnd = LocalTime(22, 30)
        if (asleepAt >= dayEnd) return emptyList()
        val dishesEnd = minOf(asleepAt.plusMinutes(20), dayEnd)
        val dishes = BlockSpec(EVENING_DISHES_ID, BlockType.Dishes, asleepAt, dishesEnd)
        if (dishesEnd >= dayEnd) return listOf(dishes)
        return listOf(dishes, BlockSpec(EVENING_CHOICE_ID, BlockType.Free, dishesEnd, dayEnd))
    }

    fun autoEndFlags(state: DayState, now: Instant, date: LocalDate): Set<DayFlag>? {
        val end = localInstant(date, 22, 30, zone)
        if (now < end) return null
        if (state == DayState.Ended || state == DayState.Off || state == DayState.NotStarted) return null
        val flags = mutableSetOf(DayFlag.AUTO_ENDED)
        if (state !is DayState.Evening) flags += DayFlag.EVENING_SKIPPED
        return flags
    }

    fun nextWakeUp(
        state: DayState,
        date: LocalDate,
        now: Instant,
        template: Template? = null,
        blockIndex: Int = 0,
    ): Instant? {
        val twenty = localInstant(date, 20, 0, zone)
        val end = localInstant(date, 22, 30, zone)
        val midnight = localInstant(date.plus(1, DateTimeUnit.DAY), 0, 0, zone)
        val runningWake = template?.let { nextBoundary(it, date, blockIndex, now) }
        val candidates = when (state) {
            DayState.NotStarted -> rePrompts(date) + midnight
            DayState.Off, DayState.Ended -> listOf(midnight)
            DayState.KidTime -> listOf(twenty, end, midnight)
            is DayState.Evening -> listOf(end, midnight)
            is DayState.Running -> listOfNotNull(runningWake, twenty, end, midnight)
            is DayState.Paused -> listOf(twenty, end, midnight)
        }
        return candidates.filter { it > now }.minOrNull()
    }

    fun rePrompts(date: LocalDate): List<Instant> {
        val ten = localInstant(date, 10, 0, zone)
        return buildList {
            var cursor = localInstant(date, 9, 0, zone)
            while (cursor <= ten) {
                add(cursor)
                cursor = cursor.plus(15.minutes)
            }
        }
    }

    private fun nextBoundary(template: Template, date: LocalDate, blockIndex: Int, now: Instant): Instant? {
        val block = planned(template, date).getOrNull(blockIndex) ?: return null
        val pomodoro = block.spec.pomodoro
        if (pomodoro == null) return block.plannedEnd.takeIf { it > now }
        val cycle = (pomodoro.focusMinutes + pomodoro.breakMinutes).minutes
        var cursor = block.plannedStart
        while (cursor <= block.plannedEnd) {
            if (cursor > now) return cursor
            cursor = cursor.plus(pomodoro.focusMinutes.minutes)
            if (cursor > now && cursor <= block.plannedEnd) return cursor
            cursor = cursor.plus(pomodoro.breakMinutes.minutes)
            if (cycle.isPositive()) continue
        }
        return block.plannedEnd.takeIf { it > now }
    }

    fun replayDowntime(from: Instant, until: Instant, type: BlockType = BlockType.Free): Segment =
        segment(type, SegmentKind.INTERRUPTION, null, null, from, until)

    private fun segment(
        type: BlockType,
        kind: SegmentKind,
        plannedStart: Instant?,
        plannedEnd: Instant?,
        actualStart: Instant,
        actualEnd: Instant?,
    ) = Segment(type, kind, plannedStart, plannedEnd, actualStart, actualEnd)
}

private fun DayState.isOpenForAutoEnd(): Boolean = this !is DayState.Ended && this != DayState.Off

private fun LocalTime.plusMinutes(minutes: Int): LocalTime {
    val total = (hour * 60 + minute + minutes).coerceAtMost(23 * 60 + 59)
    return LocalTime(total / 60, total % 60)
}

private fun minOf(a: LocalTime, b: LocalTime): LocalTime = if (a <= b) a else b
