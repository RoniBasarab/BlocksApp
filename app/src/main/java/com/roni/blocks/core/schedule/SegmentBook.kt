package com.roni.blocks.core.schedule

import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.Template
import com.roni.blocks.core.time.at
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

data class DayBook(
    val segments: List<Segment> = emptyList(),
    val shift: Duration = Duration.ZERO,
    val index: Int = 0,
    val flags: Set<DayFlag> = emptySet(),
)

object SegmentBook {
    fun startDay(template: Template, date: LocalDate, zone: TimeZone, now: Instant): DayBook {
        val blocks = resolve(template, date, zone, Duration.ZERO)
        val index = blocks.indexOfFirst { now >= it.plannedStart && now < it.plannedEnd }
            .let { if (it >= 0) it else blocks.indexOfFirst { block -> now < block.plannedEnd }.coerceAtLeast(0) }
        val consumed = blocks.take(index).map { consumed(it) }
        val current = blocks.getOrNull(index) ?: return DayBook(consumed)
        return DayBook(consumed + open(current, now), index = index)
    }

    fun next(book: DayBook, template: Template, date: LocalDate, zone: TimeZone, now: Instant): DayBook {
        val openFree = book.segments.lastOrNull { it.kind == SegmentKind.SYNTHETIC_FREE && it.actualEnd == null }
        if (openFree != null && openFree.plannedEnd != null && now >= openFree.plannedEnd) {
            return advanceAfter(book, template, date, zone, openFree.plannedEnd)
        }
        val end = effectiveEnd(book, now)
        return if (now >= end) advanceAfter(book, template, date, zone, now) else openSyntheticFree(book, now, end)
    }

    fun pause(book: DayBook, now: Instant): DayBook =
        book.copy(segments = book.segments + segment(currentType(book), SegmentKind.PAUSE, null, null, now, null))

    fun resumePause(book: DayBook, now: Instant): DayBook = book.copy(segments = closeKind(book.segments, SegmentKind.PAUSE, now))

    fun enterKidTime(book: DayBook, template: Template, date: LocalDate, zone: TimeZone): DayBook {
        val kid = resolve(template, date, zone, book.shift).first { it.spec.type == BlockType.KidTime }
        val closed = book.segments.map { segment ->
            if (segment.actualEnd == null && segment.kind == SegmentKind.PLANNED) segment.copy(actualEnd = kid.plannedStart) else segment
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
            .sumOf { (it.actualEnd!! - it.actualStart).inWholeMinutes }
        val actual = gym.filter { it.kind == SegmentKind.PLANNED && it.actualEnd != null }
            .sumOf { (it.actualEnd!! - it.actualStart).inWholeMinutes }
        return actual - paused < 30
    }

    fun effectiveEnd(book: DayBook, now: Instant): Instant {
        val current = book.segments.lastOrNull { it.kind == SegmentKind.PLANNED && it.actualEnd == null }
        val planned = current?.plannedEnd ?: now
        val paused = book.segments.filter { it.kind == SegmentKind.PAUSE }.sumOf { ((it.actualEnd ?: now) - it.actualStart).inWholeMinutes }
        return planned + paused.minutes
    }

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
        segment(block.spec.type, SegmentKind.PLANNED, block.plannedStart, block.plannedEnd, at, null)

    private fun consumed(block: ResolvedBlock) =
        segment(block.spec.type, SegmentKind.CONSUMED, block.plannedStart, block.plannedEnd, block.plannedStart, block.plannedEnd)

    private fun segment(
        type: BlockType,
        kind: SegmentKind,
        plannedStart: Instant?,
        plannedEnd: Instant?,
        actualStart: Instant,
        actualEnd: Instant?,
    ) = Segment(type, kind, plannedStart, plannedEnd, actualStart, actualEnd)
}
