package com.roni.blocks.core.schedule

import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.SegmentKind

/** A recorded segment in minutes of the day; [endMin] null while it's open. The caller converts `Segment`s. */
data class SegmentSpan(val blockId: String?, val kind: SegmentKind, val startMin: Int, val endMin: Int?)

enum class TimelinePhase { DONE, NOW, AHEAD, MISSED }

/**
 * One block as Home draws it (3.3.0): where it really is, not where it was planned.
 * [overrunFromMin]: the part from there to [endMin] ran over (the red tail). [lateMin]: it started this many
 * minutes after its plan. [pauses]: pauses inside it. A block drawn with no height ([startMin] == [endMin]) is
 * a sliver; Home gives it a pill ("Coding · missed").
 */
data class TimelineBlock(
    val id: String,
    val type: BlockType,
    val startMin: Int,
    val endMin: Int,
    val phase: TimelinePhase,
    val overrunFromMin: Int? = null,
    val lateMin: Int = 0,
    val pauses: List<IntRange> = emptyList(),
)

/**
 * The real day on the calendar (3.3.0), pure. Blocks before the current one are drawn as they happened
 * (their recorded times; one never started is MISSED and keeps only the room nobody else used). The current
 * block starts when it really started and, while it runs over, ends now with the overrun marked. Blocks ahead
 * are the plan, except that one whose start was already taken starts where the room begins (and an eaten one
 * is a sliver). The result is sorted and never overlaps; blocks ahead that weren't reached keep their plan.
 * Talks to: nothing. HomeViewModel feeds it `CalendarLayout.fit` and the block mapping.
 */
object DayTimeline {
    /**
     * [plan]: today's blocks (with the evening's Dishes / activity appended), sorted. [currentId]: the block
     * running now, or null (waiting, ended, evening over). [segments]: the day's segments.
     */
    fun build(plan: List<PlanBlock>, segments: List<SegmentSpan>, currentId: String?, nowMin: Int): List<TimelineBlock> {
        val sorted = plan.sortedBy { it.startMin }
        val cur = currentId?.let { id -> sorted.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
        val anyRecorded = segments.any { it.kind == SegmentKind.PLANNED }
        val pauses = segments.filter { it.kind == SegmentKind.PAUSE }
        val out = mutableListOf<TimelineBlock>()
        var floor = Int.MIN_VALUE                                                    // the end of what's drawn so far
        sorted.forEachIndexed { i, b ->
            val ran = segments.filter { it.blockId == b.id && it.kind == SegmentKind.PLANNED }
            val past = when {
                cur != null -> i < cur
                else -> anyRecorded && ran.isNotEmpty() && ran.all { it.endMin != null }       // ended day: what ran is past
            }
            val drawn = when {
                cur != null && i == cur -> now(b, ran, pauses, nowMin, floor)
                past && ran.isNotEmpty() -> done(b, ran, pauses, floor)
                past || (cur == null && anyRecorded && b.endMin <= nowMin) ->
                    TimelineBlock(b.id, b.type, maxOf(b.startMin, floor), maxOf(b.endMin, floor), TimelinePhase.MISSED)
                else -> ahead(b, floor)
            }
            out += drawn
            floor = maxOf(floor, drawn.endMin)
        }
        return out
    }

    private fun done(b: PlanBlock, ran: List<SegmentSpan>, pauses: List<SegmentSpan>, floor: Int): TimelineBlock {
        val start = maxOf(ran.minOf { it.startMin }, floor)
        val end = maxOf(ran.maxOf { it.endMin!! }, start)
        return TimelineBlock(
            b.id, b.type, start, end, TimelinePhase.DONE,
            overrunFromMin = b.endMin.takeIf { end > it && it >= start },
            lateMin = maxOf(0, start - b.startMin),
            pauses = pausesIn(pauses, start, end, end),
        )
    }

    private fun now(b: PlanBlock, ran: List<SegmentSpan>, pauses: List<SegmentSpan>, nowMin: Int, floor: Int): TimelineBlock {
        val start = maxOf(ran.minOfOrNull { it.startMin } ?: b.startMin, floor)
        val end = maxOf(b.endMin, nowMin, start)
        return TimelineBlock(
            b.id, b.type, start, end, TimelinePhase.NOW,
            overrunFromMin = b.endMin.takeIf { nowMin > it && it >= start },
            lateMin = maxOf(0, start - b.startMin),
            pauses = pausesIn(pauses, start, end, nowMin),
        )
    }

    /** A block not reached yet: its plan, minus what the block before it already took. Taken whole, it's MISSED. */
    private fun ahead(b: PlanBlock, floor: Int): TimelineBlock {
        val start = maxOf(b.startMin, floor)
        val eaten = b.endMin <= floor
        return TimelineBlock(b.id, b.type, start, maxOf(b.endMin, start), if (eaten) TimelinePhase.MISSED else TimelinePhase.AHEAD)
    }

    /** Pauses that touch [start, end), clipped to it; an open pause runs to [openEnd]. */
    private fun pausesIn(pauses: List<SegmentSpan>, start: Int, end: Int, openEnd: Int): List<IntRange> =
        pauses.mapNotNull { p ->
            val s = maxOf(p.startMin, start)
            val e = minOf(p.endMin ?: openEnd, end)
            if (e > s) s until e else null
        }
}
