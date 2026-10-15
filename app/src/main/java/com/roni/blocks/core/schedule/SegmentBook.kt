package com.roni.blocks.core.schedule

import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.MissReason
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.Template
import com.roni.blocks.core.model.actualMinutes
import com.roni.blocks.core.model.isWork
import com.roni.blocks.core.time.at
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class DayBook(
    val segments: List<Segment> = emptyList(),
    val shift: Duration = Duration.ZERO,
    val index: Int = 0,
    val flags: Set<DayFlag> = emptySet(),
)

/**
 * Builds the ordered segment list History renders, and rebuilds it after a process death.
 * Rules: `rehydrate` must bring the block index back with the segments, or the day restarts at its
 * first block; segment kinds are a persisted contract, so renaming one invalidates stored history.
 *
 * Talks to: [ScheduleEngine], [Template], [Segment], [DayBook].
 */
object SegmentBook {
    /**
     * Rebuilds a book from persisted segments after a process death. The index has to come back
     * with them: it is what "the next block" counts from, and a book that starts at 0 would send
     * the day back to its first block.
     */
    fun rehydrate(
        segments: List<Segment>,
        template: Template,
        date: LocalDate,
        zone: TimeZone,
        shift: Duration = Duration.ZERO,
    ): DayBook {
        val blocks = resolve(template, date, zone, shift)
        val anchor = segments.lastOrNull { it.kind == SegmentKind.PLANNED }
        val matched = anchor?.let { open ->
            blocks.indexOfFirst { it.plannedStart == open.plannedStart && it.plannedEnd == open.plannedEnd }
        }?.takeIf { it >= 0 }
        val index = matched ?: segments.count { it.kind == SegmentKind.CONSUMED }
        return DayBook(segments = segments, shift = shift, index = index.coerceAtLeast(0))
    }

    fun startDay(template: Template, date: LocalDate, zone: TimeZone, now: Instant): DayBook {
        val blocks = resolve(template, date, zone, Duration.ZERO)
        val first = blocks.firstOrNull() ?: return DayBook()
        if (now < first.plannedStart) return DayBook(index = 0)        // 2.7.0: the day waits for its first block
        val index = blocks.indexOfFirst { now >= it.plannedStart && now < it.plannedEnd }
            .let { if (it >= 0) it else blocks.indexOfFirst { block -> now < block.plannedEnd }.coerceAtLeast(0) }
        val consumed = blocks.take(index).map { consumedLateStart(it) }
        val current = blocks.getOrNull(index) ?: return DayBook(consumed)
        return DayBook(consumed + open(current, now), index = index)
    }

    /**
     * Re-plans the segments around [plan] from [now]: the open segment is closed and the block
     * containing [now] is opened. Segments already recorded stay exactly as they are.
     */
    fun swapTemplate(book: DayBook, plan: Template, date: LocalDate, zone: TimeZone, now: Instant): DayBook {
        val blocks = resolve(plan, date, zone, Duration.ZERO)
        val index = blocks.indexOfFirst { now >= it.plannedStart && now < it.plannedEnd }
            .let { if (it >= 0) it else blocks.indexOfFirst { block -> now < block.plannedEnd }.coerceAtLeast(0) }
        val current = blocks.getOrNull(index)
        return book.copy(
            segments = closeOpen(book.segments, now) + listOfNotNull(current?.let { open(it, now) }),
            shift = Duration.ZERO,
            index = index,
            flags = (book.flags - DayFlag.OFF_DAY) + DayFlag.TEMPLATE_SWAPPED,
        )
    }

    /**
     * Builds the plan after a swap to [template] from [now]. The old blocks are kept up to
     * [cutoff], the last one cut there; the new template's blocks then start from [now], dropping
     * every block that had already ended. A taken block whose id is already kept gets a
     * `<id>@<startMin>` suffix so ids stay unique (a swap to the same template).
     */
    fun swapPlan(
        oldBlocks: List<BlockSpec>,
        template: Template,
        date: LocalDate,
        zone: TimeZone,
        now: Instant,
        cutoff: Instant,
    ): List<BlockSpec> {
        val kept = oldBlocks.filter { it.start.at(date, zone) < cutoff }
        val past = if (kept.isEmpty()) {
            kept
        } else {
            val cut = cutoff.toLocalDateTime(zone).time
            kept.dropLast(1) + kept.last().copy(end = LocalTime(cut.hour, cut.minute))
        }
        val containing = template.blocks.indexOfFirst { now >= it.start.at(date, zone) && now < it.end.at(date, zone) }
        val from = if (containing >= 0) containing else template.blocks.indexOfFirst { block -> now < block.end.at(date, zone) }
        if (from < 0) return past
        val nowLocal = now.toLocalDateTime(zone).time
        val start = LocalTime(nowLocal.hour, nowLocal.minute)
        val taken = template.blocks.drop(from).mapIndexed { i, spec ->
            val block = if (i == 0 && containing >= 0) spec.copy(start = start) else spec
            val startMin = block.start.hour * 60 + block.start.minute
            if (past.any { it.id == block.id }) block.copy(id = "${block.id}@$startMin") else block
        }
        return past + taken
    }

    fun next(book: DayBook, template: Template, date: LocalDate, zone: TimeZone, now: Instant): DayBook {
        val open = book.segments.lastOrNull { it.actualEnd == null } ?: return advanceAfter(book, template, date, zone, now)
        val end = open.plannedEnd ?: return advanceAfter(book, template, date, zone, now)

        if (open.kind == SegmentKind.SYNTHETIC_FREE) {
            return advanceAfter(book, template, date, zone, if (now < end) now else end)
        }
        if (now >= end) {
            return advanceAfter(book, template, date, zone, now)
        }
        return if (open.type.isWork) {
            openSyntheticFree(book, now, end)
        } else {
            advanceAfter(book, template, date, zone, now)
        }
    }

    fun pause(book: DayBook, now: Instant): DayBook =
        book.copy(segments = book.segments + segment(currentType(book), SegmentKind.PAUSE, null, null, now, null))

    fun resumePause(book: DayBook, now: Instant): DayBook {
        val blockEnd = book.segments.lastOrNull { it.kind == SegmentKind.PLANNED && it.actualEnd == null }?.plannedEnd
        val at = if (blockEnd != null && now > blockEnd) blockEnd else now
        return book.copy(segments = closeKind(book.segments, SegmentKind.PAUSE, at))
    }

    fun enterKidTime(book: DayBook, template: Template, date: LocalDate, zone: TimeZone): DayBook {
        val kid = resolve(template, date, zone, book.shift).first { it.spec.type == BlockType.KidTime }
        val closed = book.segments.map { segment ->
            if (segment.actualEnd == null) segment.copy(actualEnd = kid.plannedStart) else segment
        }
        return book.copy(segments = closed + open(kid, kid.plannedStart), index = template.blocks.indexOfFirst { it.type == BlockType.KidTime })
    }

    fun kidAsleep(book: DayBook, date: LocalDate, zone: TimeZone, now: Instant): DayBook {
        val end = minOf(now + 20.minutes, LocalTime(22, 30).at(date, zone))
        return book.copy(segments = closeOpen(book.segments, now) + segment(BlockType.Dishes, SegmentKind.PLANNED, now, end, now, null))
    }

    fun eveningChoice(book: DayBook, choice: Choice, date: LocalDate, zone: TimeZone, now: Instant): DayBook {
        val end = LocalTime(22, 30).at(date, zone)
        return book.copy(
            segments = closeOpen(book.segments, now) + segment(BlockType.EveningChoice(choice), SegmentKind.PLANNED, now, end, now, null),
        )
    }

    fun endDay(book: DayBook, now: Instant, fromKidTime: Boolean): DayBook {
        val flags = book.flags.toMutableSet()
        if (fromKidTime) flags += DayFlag.EVENING_SKIPPED
        if (gymMissed(book)) flags += DayFlag.GYM_MISSED
        return book.copy(segments = closeOpen(book.segments, now), flags = flags)
    }

    fun gymMissed(book: DayBook): Boolean {
        val gym = book.segments.filter { it.type == BlockType.Gym }
        if (gym.isEmpty()) return false
        if (gym.any { it.kind == SegmentKind.CONSUMED || it.kind == SegmentKind.INTERRUPTION }) return true
        val paused = book.segments.filter { it.kind == SegmentKind.PAUSE && it.actualEnd != null }
            .sumOf { it.actualMinutes ?: 0 }
        val actual = gym.filter { it.kind == SegmentKind.PLANNED && it.actualEnd != null }
            .sumOf { it.actualMinutes ?: 0 }
        return actual - paused < 30
    }

    fun effectiveEnd(book: DayBook, now: Instant): Instant =
        book.segments.lastOrNull { it.kind == SegmentKind.PLANNED && it.actualEnd == null }?.plannedEnd ?: now

    private fun advanceAfter(book: DayBook, template: Template, date: LocalDate, zone: TimeZone, at: Instant): DayBook {
        val closed = closeOpen(book.segments, at)
        val blocks = resolve(template, date, zone, book.shift)
        var index = book.index + 1
        val consumed = mutableListOf<Segment>()
        while (index < blocks.size && at >= blocks[index].plannedEnd) {
            consumed += consumed(blocks[index])
            index++
        }
        val next = blocks.getOrNull(index)?.let { open(it, at) }
        return book.copy(segments = closed + consumed + listOfNotNull(next), index = index.coerceAtMost(blocks.lastIndex.coerceAtLeast(0)))
    }

    private fun openSyntheticFree(book: DayBook, now: Instant, end: Instant): DayBook =
        book.copy(segments = closeOpen(book.segments, now) + segment(BlockType.Free, SegmentKind.SYNTHETIC_FREE, now, end, now, null))

    private fun resolve(template: Template, date: LocalDate, zone: TimeZone, shift: Duration) =
        template.blocks.map { spec ->
            ResolvedBlock(spec, SegmentKind.PLANNED, spec.start.at(date, zone) + shift, spec.end.at(date, zone) + shift, spec.start.at(date, zone) + shift, spec.end.at(date, zone) + shift)
        }

    private fun currentType(book: DayBook): BlockType =
        book.segments.lastOrNull { it.kind == SegmentKind.PLANNED }?.type ?: BlockType.Free

    private fun closeKind(segments: List<Segment>, kind: SegmentKind, at: Instant) =
        segments.map { if (it.kind == kind && it.actualEnd == null) it.copy(actualEnd = at) else it }

    private fun closeOpen(segments: List<Segment>, at: Instant) =
        segments.map { if (it.actualEnd == null) it.copy(actualEnd = at) else it }

    private fun open(block: ResolvedBlock, at: Instant) =
        segment(block.spec.type, SegmentKind.PLANNED, block.plannedStart, block.plannedEnd, at, null, block.spec.id)

    private fun consumed(block: ResolvedBlock) =
        segment(block.spec.type, SegmentKind.CONSUMED, block.plannedStart, block.plannedEnd, block.plannedStart, block.plannedEnd, block.spec.id, MissReason.OVERRUN)

    private fun consumedLateStart(block: ResolvedBlock) =
        segment(block.spec.type, SegmentKind.CONSUMED, block.plannedStart, block.plannedEnd, block.plannedStart, block.plannedEnd, block.spec.id, MissReason.LATE_START)

    private fun segment(
        type: BlockType,
        kind: SegmentKind,
        plannedStart: Instant?,
        plannedEnd: Instant?,
        actualStart: Instant,
        actualEnd: Instant?,
        blockId: String? = null,
        missReason: MissReason? = null,
    ) = Segment(type, kind, plannedStart, plannedEnd, actualStart, actualEnd, blockId, missReason)
}
