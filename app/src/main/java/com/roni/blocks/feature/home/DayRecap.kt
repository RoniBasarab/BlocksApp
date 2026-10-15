package com.roni.blocks.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.CheckIn
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.DayRecord
import com.roni.blocks.core.model.MissReason
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.isWork
import com.roni.blocks.core.schedule.PlanEditor
import com.roni.blocks.core.schedule.RecordEditor
import com.roni.blocks.core.schedule.RowFix
import com.roni.blocks.core.schedule.toRecordRows
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.AppType
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.designsystem.blockColors
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class RecapTone { OVER, NEUTRAL, GOOD, MUTED }

@Immutable
data class RecapBadge(val text: String, val tone: RecapTone)

@Immutable
data class RecapRow(
    val id: String,
    val type: BlockType?,
    val title: String,
    val range: String,
    val duration: String,
    val badges: ImmutableList<RecapBadge>,
    val note: String?,
    val missed: Boolean,
    val segmentIndex: Int? = null,
    val fix: RowFix = RowFix.NONE,
    val endMin: Int? = null,
)

@Immutable
data class DayRecapUi(
    val headlineLabel: String?,
    val headline: String,
    val caption: String,
    val chips: ImmutableList<RecapBadge>,
    val rows: ImmutableList<RecapRow>,
    val checkInTitle: String? = null,
)

/**
 * Turns a finished day into a recap the eye can follow: a summary card in words, then one row
 * per planned block in time order. Pure: no clock, no context, no side effects.
 */
object DayRecapMapper {
    fun map(record: DayRecord, use24: Boolean, checkInTitle: String = "How was today?"): DayRecapUi {
        val zone = TimeZone.currentSystemDefault()
        if (DayFlag.OFF_DAY in record.flags) {
            return DayRecapUi(
                headlineLabel = null,
                headline = "Off day",
                caption = "Nothing was planned.",
                chips = persistentListOf(),
                rows = persistentListOf(),
            )
        }

        val plans = planBlocks(record, zone)
        val recordRows = record.toRecordRows(zone)
        val stopMin = if (DayFlag.EMERGENCY_STOP in record.flags) {
            record.segments.mapNotNull { it.actualEnd?.minuteOfDay(zone) }.maxOrNull()
        } else {
            null
        }

        val built = mutableListOf<RowAccum>()
        var prevHappened: PlanBlock? = null
        var prevRanOver = 0

        for (plan in plans) {
            val happened = record.segments.firstOrNull {
                it.kind == SegmentKind.PLANNED && plan.matches(it, zone) && it.actualEnd != null
            }
            val consumed = record.segments.firstOrNull {
                it.kind == SegmentKind.CONSUMED && plan.matches(it, zone)
            }

            if (happened != null) {
                val actualEnd = happened.actualEnd ?: continue
                val start = happened.actualStart.minuteOfDay(zone)
                val end = actualEnd.minuteOfDay(zone)
                val rowType = happened.type
                // A short walk isn't a row of its own: its line goes under the block it interrupted.
                if (rowType == BlockType.Walk && end - start < PlanEditor.WALK_MIN) {
                    if (built.isNotEmpty()) {
                        val last = built.last()
                        val note = "Walked ${end - start} min at ${label(start, use24)}"
                        built[built.lastIndex] = last.copy(
                            row = last.row.copy(note = listOfNotNull(last.row.note, note).joinToString(" ")),
                        )
                    }
                    prevHappened = plan
                    prevRanOver = 0
                    continue
                }
                val happenedIndex = record.segments.indexOf(happened)
                val ranOver = (end - plan.endMin).coerceAtLeast(0)
                val paused = pausesWithin(record, start, end, zone)
                val lateBy = start - plan.startMin
                val doneEarly = end < plan.endMin
                val badges = buildList {
                    if (ranOver > 0) add(RecapBadge("Ran over ${durationText(ranOver)}", RecapTone.OVER))
                    if (paused > 0) add(RecapBadge("Paused ${durationText(paused)}", RecapTone.NEUTRAL))
                    if (doneEarly) add(RecapBadge("Done early", RecapTone.GOOD))
                }
                val notes = buildList {
                    if (lateBy >= LATE_THRESHOLD && prevHappened != null && prevRanOver > 0) {
                        add("Started ${durationText(lateBy)} late (${recapTitle(prevHappened.type)} ran over).")
                    }
                    addAll(phoneOffNotes(record, start, end, zone, use24))
                    if (doneEarly) add("The last ${durationText(plan.endMin - end)} became free time.")
                }
                built += RowAccum(
                    startMin = start,
                    ranOver = ranOver,
                    paused = paused,
                    workedMin = if (rowType.isWork) (end - start - paused).coerceAtLeast(0) else 0,
                    row = RecapRow(
                        id = rowId(plan),
                        type = rowType,
                        title = recapTitle(rowType),
                        range = rangeLabel(start, end, use24),
                        duration = durationText(end - start),
                        badges = badges.toPersistentList(),
                        note = notes.joinToString(" ").ifEmpty { null },
                        missed = false,
                        segmentIndex = happenedIndex,
                        fix = RecordEditor.fixMode(recordRows, happenedIndex),
                        endMin = end,
                    ),
                )
                prevHappened = plan
                prevRanOver = ranOver
            } else if (consumed != null) {
                val consumedIndex = record.segments.indexOf(consumed)
                val note = when (consumed.missReason) {
                    MissReason.LATE_START -> record.segments
                        .filter { it.kind == SegmentKind.PLANNED && it.actualEnd != null }
                        .mapNotNull { it.actualStart.minuteOfDay(zone) }
                        .minOrNull()
                        ?.let { "The day started at ${label(it, use24)}." }

                    else -> prevHappened
                        ?.let { "${recapTitle(it.type)} ran over." }
                        ?: "An earlier block ran over."
                }
                built += RowAccum(
                    startMin = plan.startMin,
                    row = RecapRow(
                        id = rowId(plan),
                        type = plan.type,
                        title = recapTitle(plan.type),
                        range = rangeLabel(plan.startMin, plan.endMin, use24),
                        duration = durationText(plan.endMin - plan.startMin),
                        badges = persistentListOf(RecapBadge("Missed", RecapTone.MUTED)),
                        note = note,
                        missed = true,
                        segmentIndex = consumedIndex,
                        fix = RecordEditor.fixMode(recordRows, consumedIndex),
                    ),
                )
            }
            // A plan block with no segment of its own isn't listed: the day ended before it.
        }

        val visible = if (stopMin != null) built.filter { it.startMin < stopMin } else built
        val rows = visible.toMutableList()
        if (stopMin != null) {
            rows += RowAccum(
                startMin = stopMin,
                row = RecapRow(
                    id = "stop",
                    type = null,
                    title = "Day stopped (emergency)",
                    range = label(stopMin, use24),
                    duration = "",
                    badges = persistentListOf(),
                    note = null,
                    missed = false,
                ),
            )
        }
        if (DayFlag.EVENING_SKIPPED in record.flags) {
            rows += RowAccum(
                startMin = Int.MAX_VALUE,
                row = RecapRow(
                    id = "evening_skipped",
                    type = null,
                    title = "Evening skipped",
                    range = "",
                    duration = "",
                    badges = persistentListOf(),
                    note = "Kid asleep wasn't tapped before 22:30.",
                    missed = false,
                ),
            )
        }

        val plannedWork = plans.filter { it.type.isWork }.sumOf { plan ->
            val end = stopMin?.let { minOf(plan.endMin, it) } ?: plan.endMin
            (end - plan.startMin).coerceAtLeast(0)
        }
        val workedWork = visible.sumOf { it.workedMin }
        val ranOverTotal = visible.sumOf { it.ranOver }
        val pausedTotal = visible.sumOf { it.paused }
        val missedCount = visible.count { it.row.missed }

        val chips = buildList {
            if (ranOverTotal > 0) add(RecapBadge("Ran over ${durationText(ranOverTotal)}", RecapTone.OVER))
            if (pausedTotal > 0) add(RecapBadge("Paused ${durationText(pausedTotal)}", RecapTone.NEUTRAL))
            if (missedCount > 0) {
                add(RecapBadge(if (missedCount == 1) "Missed 1 block" else "Missed $missedCount blocks", RecapTone.MUTED))
            }
            if (DayFlag.PLAN_EDITED in record.flags) add(RecapBadge("Plan changed", RecapTone.NEUTRAL))
            if (DayFlag.TEMPLATE_SWAPPED in record.flags) add(RecapBadge("Template swapped", RecapTone.NEUTRAL))
            if (DayFlag.CORRECTED in record.flags) add(RecapBadge("Corrected", RecapTone.NEUTRAL))
            if (record.checkIn == CheckIn.GOOD) add(RecapBadge("Felt good", RecapTone.GOOD))
            if (record.checkIn == CheckIn.OK) add(RecapBadge("Felt OK", RecapTone.NEUTRAL))
            if (record.checkIn == CheckIn.ROUGH) add(RecapBadge("Felt rough", RecapTone.MUTED))
        }

        val headline: String
        val caption: String
        if (plannedWork <= 0) {
            headline = "—"
            caption = "No CV, Coding or Gym was planned."
        } else {
            headline = "${(workedWork * 100 / plannedWork).coerceAtMost(100)}%"
            caption = "${durationText(workedWork)} of the ${durationText(plannedWork)} planned for CV, Coding and Gym"
        }

        return DayRecapUi(
            headlineLabel = "Plan followed",
            headline = headline,
            caption = caption,
            chips = chips.toPersistentList(),
            rows = rows.map { it.row }.toPersistentList(),
            checkInTitle = if (DayFlag.OFF_DAY in record.flags || record.checkIn != null) null else checkInTitle,
        )
    }

    private fun planBlocks(record: DayRecord, zone: TimeZone): List<PlanBlock> {
        val plans = mutableListOf<PlanBlock>()
        val declared = record.plannedBlocks
        if (declared != null) {
            declared.forEach { spec ->
                plans += PlanBlock(spec.id, spec.type, spec.start.hour * 60 + spec.start.minute, spec.end.hour * 60 + spec.end.minute)
            }
        } else {
            record.segments.filter { it.kind == SegmentKind.PLANNED }
                .forEach { segment -> segment.toPlanBlock(zone)?.let { plans += it } }
        }
        record.segments.filter { it.kind == SegmentKind.PLANNED }.forEach { segment ->
            if (plans.none { it.matches(segment, zone) }) segment.toPlanBlock(zone)?.let { plans += it }
        }
        record.segments.filter { it.kind == SegmentKind.CONSUMED }.forEach { segment ->
            if (plans.none { it.matches(segment, zone) }) segment.toPlanBlock(zone)?.let { plans += it }
        }
        return plans.sortedWith(compareBy({ it.startMin }, { it.endMin }))
    }

    private fun Segment.toPlanBlock(zone: TimeZone): PlanBlock? {
        val startMin = plannedStart?.minuteOfDay(zone) ?: return null
        val endMin = plannedEnd?.minuteOfDay(zone) ?: return null
        if (endMin <= startMin) return null
        return PlanBlock(blockId, type, startMin, endMin)
    }

    private fun PlanBlock.matches(segment: Segment, zone: TimeZone): Boolean =
        if (segment.blockId != null) id == segment.blockId
        else startMin == segment.plannedStart?.minuteOfDay(zone)

    private fun rowId(plan: PlanBlock): String = "${plan.id ?: plan.type}@${plan.startMin}"

    private fun pausesWithin(record: DayRecord, start: Int, end: Int, zone: TimeZone): Int =
        record.segments.filter { it.kind == SegmentKind.PAUSE }.sumOf { pause ->
            val from = maxOf(pause.actualStart.minuteOfDay(zone), start)
            val to = minOf(pause.actualEnd?.minuteOfDay(zone) ?: pause.actualStart.minuteOfDay(zone), end)
            (to - from).coerceAtLeast(0)
        }

    private fun phoneOffNotes(record: DayRecord, start: Int, end: Int, zone: TimeZone, use24: Boolean): List<String> =
        record.segments.filter { it.kind == SegmentKind.INTERRUPTION }.mapNotNull { off ->
            val from = off.actualStart.minuteOfDay(zone)
            val to = off.actualEnd?.minuteOfDay(zone) ?: from
            if (to <= start || from >= end) return@mapNotNull null
            "The phone was off ${label(from, use24)}–${label(to, use24)}."
        }

    private fun recapTitle(type: BlockType): String = when (type) {
        is BlockType.EveningChoice -> "Evening · ${blockTitle(type)}"
        else -> blockTitle(type)
    }

    private fun rangeLabel(start: Int, end: Int, use24: Boolean): String =
        "${label(start, use24)}–${label(end, use24)}"

    private fun durationText(minutes: Int): String = when {
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }

    private fun Instant.minuteOfDay(zone: TimeZone): Int {
        val local = toLocalDateTime(zone)
        return local.hour * 60 + local.minute
    }

    private data class PlanBlock(val id: String?, val type: BlockType, val startMin: Int, val endMin: Int)

    private data class RowAccum(
        val startMin: Int,
        val row: RecapRow,
        val ranOver: Int = 0,
        val paused: Int = 0,
        val workedMin: Int = 0,
    )

    private const val LATE_THRESHOLD = 5
}

@Composable
fun RecapSummaryCard(s: DayRecapUi) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth().testTag("recap_summary")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            s.headlineLabel?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = c.textSecondary) }
            Text(s.headline, style = MaterialTheme.typography.displaySmall.merge(AppType.numbers))
            Text(s.caption, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            if (s.chips.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(top = Spacing.s),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    s.chips.forEach { RecapPill(it) }
                }
            }
        }
    }
}

@Composable
fun RecapRowCard(r: RecapRow, onClick: (() -> Unit)? = null) {
    val c = AppTheme.colors
    val accent = r.type?.let { blockColors(it).accent } ?: c.textMuted
    val tappable = if (onClick != null && r.fix != RowFix.NONE) Modifier.clickable { onClick() } else Modifier
    AppCard(Modifier.fillMaxWidth().alpha(if (r.missed) 0.55f else 1f).then(tappable).testTag("recap_row")) {
        Column(
            Modifier
                .drawBehind { drawRoundRect(accent, size = Size(4.dp.toPx(), size.height), cornerRadius = CornerRadius(2.dp.toPx())) }
                .padding(start = 4.dp + Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(r.duration, style = MaterialTheme.typography.bodyMedium.merge(AppType.numbers), color = c.textSecondary)
            }
            Text(r.range, style = MaterialTheme.typography.bodySmall.merge(AppType.numbers), color = c.textSecondary)
            if (r.badges.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    r.badges.forEach { RecapPill(it) }
                }
            }
            r.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textMuted) }
        }
    }
}

@Composable
fun RecapPill(b: RecapBadge) {
    val c = AppTheme.colors
    val outline = MaterialTheme.colorScheme.outline
    val (fg, bg, border) = when (b.tone) {
        RecapTone.OVER -> Triple(c.leak, c.leakContainer, null)
        RecapTone.GOOD -> Triple(c.good, c.good.copy(alpha = 0.16f), null)
        RecapTone.NEUTRAL -> Triple(c.textSecondary, Color.Transparent, outline)
        RecapTone.MUTED -> Triple(c.textMuted, Color.Transparent, outline)
    }
    Text(
        b.text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = Modifier
            .background(bg, CircleShape)
            .then(if (border != null) Modifier.border(1.dp, border, CircleShape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun RecapCheckInCard(title: String, onPick: (CheckIn) -> Unit) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth().testTag("recap_checkin")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                TextButton(onClick = { onPick(CheckIn.GOOD) }) { Text("Good") }
                TextButton(onClick = { onPick(CheckIn.OK) }) { Text("OK") }
                TextButton(onClick = { onPick(CheckIn.ROUGH) }) { Text("Rough") }
                TextButton(onClick = { onPick(CheckIn.SKIPPED) }) { Text("Skip") }
            }
        }
    }
}

@Composable
fun RecapList(
    s: DayRecapUi,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    onRowTap: (RecapRow) -> Unit = {},
    onCheckIn: (CheckIn) -> Unit = {},
    header: (@Composable () -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = Spacing.l, top = Spacing.s, end = Spacing.l, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        if (header != null) {
            item(key = "recap_header") { header() }
        }
        if (s.checkInTitle != null) {
            item(key = "recap_checkin") { RecapCheckInCard(s.checkInTitle, onCheckIn) }
        }
        item(key = "recap_summary") { RecapSummaryCard(s) }
        items(items = s.rows, key = { it.id }) { row ->
            RecapRowCard(row, onClick = if (row.fix != RowFix.NONE) ({ onRowTap(row) }) else null)
        }
    }
}
