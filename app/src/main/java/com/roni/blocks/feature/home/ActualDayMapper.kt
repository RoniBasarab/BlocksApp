package com.roni.blocks.feature.home

import android.util.Log
import androidx.compose.runtime.Immutable
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.DayRecord
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.time.TimeFormatter
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Immutable
data class ActualBlockUi(
    val id: String,
    val type: BlockType,
    val title: String,
    val startMin: Int,
    val endMin: Int,
    val plannedEndMin: Int?,
    val timeLabel: String,
    val pauses: ImmutableList<IntRange>,
)

@Immutable
data class ActualDayUi(
    val blocks: ImmutableList<ActualBlockUi>,
    val downtime: ImmutableList<IntRange>,
    val emergencyAtMin: Int?,
    val summary: String,
)

/**
 * Turns a finished day into the flat, overlap-free timeline the calendar draws. Overlaps are
 * resolved here rather than at draw time so the screen has exactly one thing to render.
 */
object ActualDayMapper {
    fun map(record: DayRecord, formatter: TimeFormatter): ActualDayUi {
        val zone = TimeZone.currentSystemDefault()
        val paused = record.segments.filter { it.kind == SegmentKind.PAUSE }
        val downtime = record.segments
            .filter { it.kind == SegmentKind.INTERRUPTION }
            .mapNotNull { segment -> segment.minuteSpan(zone) }
            .toPersistentList()

        val drawn = record.segments
            .filter { it.kind == SegmentKind.PLANNED || it.kind == SegmentKind.SYNTHETIC_FREE }
            .sortedBy { it.actualStart }
            .mapNotNull { segment ->
                val end = segment.actualEnd ?: return@mapNotNull null
                RawBlock(segment, segment.actualStart.minuteOfDay(zone), end.minuteOfDay(zone))
            }

        val blocks = buildBlocks(drawn, paused, formatter, zone)

        return ActualDayUi(
            blocks = blocks,
            downtime = downtime,
            emergencyAtMin = emergencyMinute(record, zone),
            summary = summary(record, blocks, formatter, zone),
        )
    }

    private fun buildBlocks(
        raw: List<RawBlock>,
        paused: List<Segment>,
        formatter: TimeFormatter,
        zone: TimeZone,
    ): ImmutableList<ActualBlockUi> {
        val result = mutableListOf<ActualBlockUi>()
        var floor = DAY_START_MIN
        for (block in raw) {
            val start = block.startMin.coerceIn(DAY_START_MIN, DAY_END_MIN)
            val end = block.endMin.coerceIn(DAY_START_MIN, DAY_END_MIN)
            if (start < floor) {
                Log.w(TAG, "${block.segment.type} at $start overlaps the previous block ending $floor; moved to $floor")
            }
            val from = maxOf(start, floor)
            val to = maxOf(end, from)
            if (to <= from) {
                Log.w(TAG, "${block.segment.type} at $from has no length after overlap resolution; dropped")
                continue
            }
            floor = to
            result += ActualBlockUi(
                id = uniqueId(block.segment.type, from, result),
                type = block.segment.type,
                title = blockTitle(block.segment.type),
                startMin = from,
                endMin = to,
                plannedEndMin = plannedEnd(block.segment, from, zone),
                timeLabel = formatter.timeLabel(from, to),
                pauses = pausesInside(paused, from, to, zone),
            )
        }
        return result.toPersistentList()
    }

    private fun plannedEnd(segment: Segment, from: Int, zone: TimeZone): Int? {
        if (segment.kind != SegmentKind.PLANNED) return null
        return segment.plannedEnd
            ?.minuteOfDay(zone)
            ?.coerceIn(DAY_START_MIN, DAY_END_MIN)
            ?.takeIf { it > from }
    }

    private fun pausesInside(
        paused: List<Segment>,
        from: Int,
        to: Int,
        zone: TimeZone,
    ): ImmutableList<IntRange> = paused
        .mapNotNull { pause ->
            val span = pause.minuteSpan(zone) ?: return@mapNotNull null
            val start = maxOf(span.first, from)
            val end = minOf(span.last, to)
            if (end > start) start..end else null
        }
        .toPersistentList()

    private fun uniqueId(type: BlockType, startMin: Int, taken: List<ActualBlockUi>): String {
        val base = "$type@$startMin"
        return if (taken.none { it.id == base }) base else "$base#${taken.size}"
    }

    private fun emergencyMinute(record: DayRecord, zone: TimeZone): Int? {
        if (DayFlag.EMERGENCY_STOP !in record.flags) return null
        return record.segments
            .mapNotNull { it.actualEnd?.minuteOfDay(zone) }
            .maxOrNull()
    }

    private fun summary(
        record: DayRecord,
        blocks: ImmutableList<ActualBlockUi>,
        formatter: TimeFormatter,
        zone: TimeZone,
    ): String {
        val total = record.segments.count {
            it.kind == SegmentKind.PLANNED ||
                it.kind == SegmentKind.SYNTHETIC_FREE ||
                it.kind == SegmentKind.CONSUMED
        }
        val ranOver = blocks.sumOf { block ->
            (block.endMin - (block.plannedEndMin ?: block.endMin)).coerceAtLeast(0)
        }
        val pausedMinutes = blocks.sumOf { block -> block.pauses.sumOf { it.last - it.first } }
        val skipped = record.segments.filter { it.kind == SegmentKind.CONSUMED }
            .joinToString(", ") { segment ->
                val start = (segment.plannedStart ?: segment.actualStart).minuteOfDay(zone)
                val end = (segment.plannedEnd ?: segment.actualEnd)?.minuteOfDay(zone) ?: start
                "${blockTitle(segment.type)} ${formatter.timeLabel(start, end)}"
            }

        val parts = mutableListOf("${blocks.size}/$total blocks")
        if (ranOver > 0) parts += "ran over ${ranOver}m"
        if (pausedMinutes > 0) parts += "paused ${pausedMinutes}m"
        if (skipped.isNotEmpty()) parts += "skipped: $skipped"
        return parts.joinToString(" · ")
    }

    private fun Segment.minuteSpan(zone: TimeZone): IntRange? {
        val start = actualStart.minuteOfDay(zone).coerceIn(DAY_START_MIN, DAY_END_MIN)
        val end = (actualEnd?.minuteOfDay(zone) ?: start).coerceIn(DAY_START_MIN, DAY_END_MIN)
        return if (end > start) start..end else null
    }

    private fun Instant.minuteOfDay(zone: TimeZone): Int {
        val local = toLocalDateTime(zone)
        return local.hour * 60 + local.minute
    }

    private data class RawBlock(
        val segment: Segment,
        val startMin: Int,
        val endMin: Int,
    )

    private const val TAG = "ActualDayMapper"
}