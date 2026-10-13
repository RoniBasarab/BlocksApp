package com.roni.blocks.core.schedule

import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.Template
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * The timeline is **computed, never persisted**. It is derived on demand from the template,
 * the persisted segments and `now`, so nothing here is `@Serializable` — only segments and
 * day records are written to disk.
 */
data class EngineTimeline(
    /** The template's blocks, resolved to real instants for the day. */
    val blocks: List<ResolvedBlock> = emptyList(),
    /** The segment that is still open (the one currently running), if any. */
    val open: Segment? = null,
    /** Total paused minutes so far; the current block's end is pushed later by this much. */
    val pausedMinutes: Int = 0,
)

/**
 * Pomodoro position of a block, in the shape the notification needs: which half of the cycle
 * we are in and when it flips. Wraps the engine's [ScheduleEngine.pomodoroPhase] rather than
 * re-implementing it, so the phase maths stays owned by the engine.
 */
data class PomodoroStatus(
    val onBreak: Boolean,
    val index: Int,
    val total: Int,
    val phaseEnd: Instant,
)

/** Builds the timeline for [date] from the template plus the persisted [segments]. */
fun ScheduleEngine.timelineAt(
    template: Template?,
    date: LocalDate,
    zone: TimeZone,
    segments: List<Segment>,
    now: Instant,
): EngineTimeline {
    if (template == null) {
        return EngineTimeline(open = openSegment(segments), pausedMinutes = pausedMinutes(segments, now))
    }
    return EngineTimeline(
        blocks = planned(template, date),
        open = openSegment(segments),
        pausedMinutes = pausedMinutes(segments, now),
    )
}

/** The block the clock is inside right now, or the last one that has not ended yet. */
fun currentBlock(blocks: List<ResolvedBlock>, now: Instant): ResolvedBlock? =
    blocks.lastOrNull { it.plannedStart <= now } ?: blocks.firstOrNull()

/**
 * The block a "start next" press actually lands in. Normally the next block; if the clock has
 * already run past that one too, it is the block the clock is in.
 */
fun landingBlockForStartNext(blocks: List<ResolvedBlock>, now: Instant): ResolvedBlock? =
    blocks.firstOrNull { now < it.plannedEnd } ?: blocks.lastOrNull()

/** The current block's end. A pause never moves it: the pause shortens the block instead. */
fun effectiveEnd(timeline: EngineTimeline, now: Instant): Instant? = timeline.open?.plannedEnd

/** Null when the block has no Pomodoro configured. */
fun ScheduleEngine.pomodoroStatus(
    block: ResolvedBlock,
    now: Instant,
    zone: TimeZone,
    actualStart: Instant = block.plannedStart,
): PomodoroStatus? {
    val pomodoro = block.spec.pomodoro ?: return null
    val phase = pomodoroPhase(actualStart, block.plannedEnd, now, pomodoro)
    return PomodoroStatus(
        onBreak = !phase.focusing,
        index = phase.index,
        total = phase.total,
        phaseEnd = now + phase.minutesLeft.minutes,
    )
}

/**
 * One span of a Coding block's Pomodoro cycle, in minutes-of-day: a focus stretch or the break
 * after it. Unlike the engine's [PomodoroPhase] (which answers "which half are we in right now"),
 * this is the whole cycle laid out on the clock, for drawing the block. Named `Slot` because
 * [PomodoroPhase] already lives in this package.
 */
data class PomodoroSlot(val number: Int, val isBreak: Boolean, val startMin: Int, val endMin: Int)

/** 50/10 from `fromMin` until `endMin`; the last phase is cut at the end. `number` counts focus phases from 1. */
fun pomodoroPhases(fromMin: Int, endMin: Int, focus: Int = 50, rest: Int = 10): List<PomodoroSlot> {
    val phases = mutableListOf<PomodoroSlot>()
    var t = fromMin
    var n = 1
    while (t < endMin) {
        val focusEnd = minOf(t + focus, endMin)
        phases += PomodoroSlot(n, isBreak = false, startMin = t, endMin = focusEnd)
        t = focusEnd
        if (t >= endMin) break
        val restEnd = minOf(t + rest, endMin)
        phases += PomodoroSlot(n, isBreak = true, startMin = t, endMin = restEnd)
        t = restEnd
        n++
    }
    return phases
}

private fun openSegment(segments: List<Segment>): Segment? =
    segments.lastOrNull { it.actualEnd == null }

private fun pausedMinutes(segments: List<Segment>, now: Instant): Int =
    segments.filter { it.kind == SegmentKind.PAUSE }
        .sumOf { ((it.actualEnd ?: now) - it.actualStart).inWholeMinutes }
        .toInt()
