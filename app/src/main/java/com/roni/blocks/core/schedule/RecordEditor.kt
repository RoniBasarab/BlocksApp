package com.roni.blocks.core.schedule

import com.roni.blocks.core.model.BlockType

/**
 * One line of a day's record, in minutes of the day. [segmentIndex] points back into `DayRecord.segments`.
 * A done row is a PLANNED or SYNTHETIC_FREE segment (actual times); a missed row is a CONSUMED one (planned times).
 */
data class RecordRow(
    val segmentIndex: Int,
    val type: BlockType,
    val done: Boolean,
    val startMin: Int,
    val endMin: Int,
)

sealed interface RecordEdit {
    val segmentIndex: Int

    /** "When it ended": move this block's end; it never runs into the next block. */
    data class SetEnd(override val segmentIndex: Int, val toMin: Int) : RecordEdit

    /** "What I did instead": the block happened as another type; the plan doesn't change. */
    data class Retype(override val segmentIndex: Int, val type: BlockType) : RecordEdit

    /** "It happened": a missed block took place at its planned times; blocks overlapping it are trimmed. */
    data class MarkDone(override val segmentIndex: Int) : RecordEdit
}

/** What a recap row offers for correcting. */
enum class RowFix { NONE, END_AND_TYPE, MARK_DONE }

/**
 * Corrects the record of a day that already happened (2.1.0). Only CV, Coding, Gym and Free rows can be
 * corrected; kid time and the evening stay as recorded. Returns the new rows, or null when the edit isn't
 * allowed or changes nothing. Pure: the caller converts segments to rows and back.
 * Talks to: nothing. Used by HistoryManager.correctDay.
 */
object RecordEditor {
    const val MIN_LENGTH = 5
    const val LAST_MINUTE = 23 * 60 + 59

    fun fixMode(rows: List<RecordRow>, segmentIndex: Int): RowFix {
        val row = rows.firstOrNull { it.segmentIndex == segmentIndex } ?: return RowFix.NONE
        if (!row.type.isEditable()) return RowFix.NONE
        return if (row.done) RowFix.END_AND_TYPE else RowFix.MARK_DONE
    }

    /** [limitMin] is the latest minute a block may end: now for a day still running, [LAST_MINUTE] otherwise. */
    fun apply(rows: List<RecordRow>, edit: RecordEdit, limitMin: Int = LAST_MINUTE): List<RecordRow>? {
        val sorted = rows.sortedBy { it.startMin }.toMutableList()
        val i = sorted.indexOfFirst { it.segmentIndex == edit.segmentIndex }
        if (i < 0) return null
        val row = sorted[i]
        val mode = fixMode(sorted, edit.segmentIndex)
        return when (edit) {
            is RecordEdit.SetEnd -> if (mode != RowFix.END_AND_TYPE) null else setEnd(sorted, i, edit.toMin, limitMin)
            is RecordEdit.Retype ->
                if (mode != RowFix.END_AND_TYPE || !edit.type.isEditable() || edit.type == row.type) null
                else sorted.also { it[i] = row.copy(type = edit.type) }
            is RecordEdit.MarkDone -> if (mode != RowFix.MARK_DONE) null else markDone(sorted, i, limitMin)
        }
    }

    private fun setEnd(rows: MutableList<RecordRow>, i: Int, toMin: Int, limitMin: Int): List<RecordRow>? {
        val row = rows[i]
        val nextStart = rows.filterIndexed { j, r -> j != i && r.done && r.startMin >= row.startMin }.minOfOrNull { it.startMin }
        val upper = minOf(nextStart ?: limitMin, limitMin)
        val end = minOf(maxOf(toMin, row.startMin + MIN_LENGTH), upper)
        if (end == row.endMin || end - row.startMin < MIN_LENGTH) return null
        rows[i] = row.copy(endMin = end)
        return rows
    }

    private fun markDone(rows: MutableList<RecordRow>, i: Int, limitMin: Int): List<RecordRow>? {
        val row = rows[i]
        if (row.endMin > limitMin) return null
        for (j in rows.indices) {
            val other = rows[j]
            if (j == i || !other.done || other.endMin <= row.startMin || other.startMin >= row.endMin) continue
            if (!other.type.isEditable()) return null
            val trimmed = when {
                other.startMin < row.startMin -> other.copy(endMin = row.startMin)
                other.endMin > row.endMin -> other.copy(startMin = row.endMin)
                else -> return null // it would swallow a whole block
            }
            if (trimmed.endMin - trimmed.startMin < MIN_LENGTH) return null
            rows[j] = trimmed
        }
        rows[i] = row.copy(done = true)
        return rows.sortedBy { it.startMin }
    }
}
