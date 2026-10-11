package com.roni.blocks.core.schedule

import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.DayRecord
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.time.at
import com.roni.blocks.core.time.minuteOfDay
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

/** Done rows: closed PLANNED and SYNTHETIC_FREE segments; missed rows: CONSUMED ones. Other segments aren't rows. */
fun DayRecord.toRecordRows(zone: TimeZone): List<RecordRow> = segments.mapIndexedNotNull { i, s ->
    when {
        (s.kind == SegmentKind.PLANNED || s.kind == SegmentKind.SYNTHETIC_FREE) && s.actualEnd != null ->
            RecordRow(i, s.type, done = true, startMin = s.actualStart.minuteOfDay(zone), endMin = s.actualEnd.minuteOfDay(zone))
        s.kind == SegmentKind.CONSUMED && s.plannedStart != null && s.plannedEnd != null ->
            RecordRow(i, s.type, done = false, startMin = s.plannedStart.minuteOfDay(zone), endMin = s.plannedEnd.minuteOfDay(zone))
        else -> null
    }
}.sortedBy { it.startMin }

/** Folds corrected rows back into the segments and flags the day. The caller writes the result. */
fun DayRecord.withRows(rows: List<RecordRow>, zone: TimeZone): DayRecord {
    val fixed = segments.toMutableList()
    rows.forEach { r ->
        val s = fixed[r.segmentIndex]
        val kind = when {
            s.kind == SegmentKind.CONSUMED && r.done -> SegmentKind.PLANNED
            s.kind == SegmentKind.SYNTHETIC_FREE && r.type != BlockType.Free -> SegmentKind.PLANNED
            else -> s.kind
        }
        fixed[r.segmentIndex] = s.copy(
            type = r.type,
            kind = kind,
            actualStart = if (r.done) LocalTime(r.startMin / 60, r.startMin % 60).at(date, zone) else s.actualStart,
            actualEnd = if (r.done) LocalTime(r.endMin / 60, r.endMin % 60).at(date, zone) else s.actualEnd,
            missReason = if (kind == SegmentKind.CONSUMED) s.missReason else null,
        )
    }
    return copy(segments = fixed, flags = flags + DayFlag.CORRECTED)
}
