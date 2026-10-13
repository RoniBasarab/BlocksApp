package com.roni.blocks.core.schedule

import com.roni.blocks.core.model.BlockType

/** The outcome of one edit from Home's block sheet (3.3.0). */
sealed interface TodayEdit {
    data class Ok(val blocks: List<PlanBlock>) : TodayEdit
    data class Refused(val why: TodayRefusal) : TodayEdit
}

sealed interface TodayRefusal {
    /** Not a block you can edit from the sheet: past, running, kid time or a walk, or no day running. */
    data object Locked : TodayRefusal
    /** Starts before [floorMin]: the running block's end, and never in the next 5 minutes. "Starts at 14:35 at the earliest." */
    data class TooSoon(val floorMin: Int) : TodayRefusal
    /** Under 10 minutes (free time may be shorter). */
    data object TooShort : TodayRefusal
    /** The start falls inside block [id], which runs until [endMin]. */
    data class StartsInside(val id: String, val endMin: Int) : TodayRefusal
    /** Pushing later would run into [id] (kid time, a walk) at [atMin], or past 22:30 when [id] is null. */
    data class NoRoom(val id: String?, val atMin: Int) : TodayRefusal
}

/**
 * Home's block sheet on today's plan (3.3.0), with the template editor's rule: moving or lengthening a block
 * pushes the blocks after it (free time first, then later blocks keep their length and shift); blocks before it
 * never move. Today adds three things: nothing up to the running block moves, nothing starts in the next five
 * minutes, and kid time and walks are walls (no room before one → refused). Gaps ahead become free time,
 * `free@<startMin>` (a piece that still starts where its free block did keeps that block's id).
 * Delete stays `PlanEdit.Retype(id, Free)` and the running block stays with [PlanEditor].
 * Talks to: PlanEditor (constants, isEditable). Pure; HomeViewModel previews with it, DayOrchestrator commits.
 */
object TodayEditor {
    const val ADD_MINUTES = 30

    /** Puts block [id] (existing ahead, or new) at [startMin] for [lengthMin] minutes as [type]. */
    fun place(
        plan: List<PlanBlock>,
        id: String,
        type: BlockType,
        startMin: Int,
        lengthMin: Int,
        nowMin: Int,
        current: CurrentBlock?,
    ): TodayEdit {
        val blocks = plan.sortedBy { it.startMin }
        val cur = current?.let { c -> blocks.indexOfFirst { it.id == c.id } } ?: -1
        if (cur < 0 || !type.isEditable()) return refused(TodayRefusal.Locked)
        val existing = blocks.indexOfFirst { it.id == id }
        if (existing >= 0 && (existing <= cur || !blocks[existing].type.isEditable())) return refused(TodayRefusal.Locked)
        val floor = floorMin(blocks, cur, nowMin)
        if (startMin < floor) return refused(TodayRefusal.TooSoon(floor))
        if (type != BlockType.Free && lengthMin < PlanEditor.MIN_WORK) return refused(TodayRefusal.TooShort)
        if (lengthMin <= 0) return refused(TodayRefusal.TooShort)
        val end = startMin + lengthMin

        // Running over eats what's ahead from the top (as Home draws it): a block wholly before the floor is
        // already lost and stays as it is; one the floor cuts into now starts at the floor.
        val fixed = blocks.take(cur + 1) + blocks.drop(cur + 1).filter { it.endMin <= floor && it.id != id }
        val ahead = blocks.drop(cur + 1).filter { it.id != id && it.endMin > floor }
            .map { if (it.startMin < floor && it.type.isEditable()) it.copy(startMin = floor) else it }
        val solid = ahead.filter { it.type != BlockType.Free }
        solid.lastOrNull { it.startMin < startMin && it.endMin > startMin }?.let { return refused(TodayRefusal.StartsInside(it.id, it.endMin)) }

        val placed = solid.filter { it.startMin < startMin }.toMutableList()
        var cursor = end
        for (b in solid.filter { it.startMin >= startMin }) {
            if (!b.type.isEditable()) {                                             // a wall: kid time, a walk
                if (cursor > b.startMin) return refused(TodayRefusal.NoRoom(b.id, b.startMin))
                placed += b
                cursor = b.endMin
                continue
            }
            val s = maxOf(b.startMin, cursor)
            placed += b.copy(startMin = s, endMin = s + b.length)
            cursor = s + b.length
        }
        val lastEnd = maxOf(cursor, placed.maxOfOrNull { it.endMin } ?: end)
        if (lastEnd > PlanEditor.DAY_END && placed.none { !it.type.isEditable() && it.endMin >= lastEnd }) {
            return refused(TodayRefusal.NoRoom(null, PlanEditor.DAY_END))
        }
        val wasFree = existing >= 0 && blocks[existing].type == BlockType.Free
        val blockId = when {
            type == BlockType.Free -> "free@$startMin"                              // free time is always named by its start
            existing >= 0 && !wasFree -> id
            else -> newId(plan)                                                     // new, or free time turned into a block
        }
        val target = PlanBlock(blockId, type, startMin, end)
        val regionStart = fixed.maxOf { it.endMin }
        val regionEnd = maxOf(blocks.last().endMin, lastEnd)
        val freeStarts = ahead.filter { it.type == BlockType.Free }.associate { it.startMin to it.id } +
            (if (existing >= 0 && blocks[existing].type == BlockType.Free) mapOf(blocks[existing].startMin to id) else emptyMap())
        val items = (placed + target).sortedBy { it.startMin }
        val filled = mutableListOf<PlanBlock>()
        var at = regionStart
        for (item in items) {
            if (item.startMin > at) filled += freeBlock(at, item.startMin, freeStarts, blockId)
            filled += item
            at = maxOf(at, item.endMin)
        }
        if (regionEnd > at) filled += freeBlock(at, regionEnd, freeStarts, blockId)
        return TodayEdit.Ok(fixed.sortedBy { it.startMin } + merged(filled))
    }

    /** "Add after" [afterId]: a new [type] block of [ADD_MINUTES] right after it (or at the earliest allowed minute). */
    fun addAfter(plan: List<PlanBlock>, afterId: String, type: BlockType, nowMin: Int, current: CurrentBlock?): TodayEdit {
        val blocks = plan.sortedBy { it.startMin }
        val after = blocks.firstOrNull { it.id == afterId } ?: return refused(TodayRefusal.Locked)
        val cur = current?.let { c -> blocks.indexOfFirst { it.id == c.id } } ?: -1
        if (cur < 0) return refused(TodayRefusal.Locked)
        val start = maxOf(after.endMin, floorMin(blocks, cur, nowMin))
        return place(plan, newId(plan), type, start, ADD_MINUTES, nowMin, current)
    }

    /** The earliest start the sheet allows: the running block's end, and not in the next five minutes. */
    fun floorMin(blocks: List<PlanBlock>, cur: Int, nowMin: Int): Int =
        maxOf(blocks[cur].endMin, ((nowMin + PlanEditor.SNAP + PlanEditor.SNAP - 1) / PlanEditor.SNAP) * PlanEditor.SNAP)

    /** "edit-N", one past the highest in [plan]: the same ids PlanEditor gives new blocks. */
    fun newId(plan: List<PlanBlock>): String {
        val last = plan.mapNotNull { if (it.id.startsWith("edit-")) it.id.removePrefix("edit-").toIntOrNull() else null }.maxOrNull() ?: 0
        return "edit-${last + 1}"
    }

    private fun freeBlock(from: Int, to: Int, freeStarts: Map<Int, String>, targetId: String): PlanBlock {
        val keep = freeStarts[from]?.takeIf { it != targetId }
        return PlanBlock(keep ?: "free@$from", BlockType.Free, from, to)
    }

    private fun merged(blocks: List<PlanBlock>): List<PlanBlock> {
        val out = mutableListOf<PlanBlock>()
        for (b in blocks) {
            val prev = out.lastOrNull()
            if (prev != null && prev.type == BlockType.Free && b.type == BlockType.Free && prev.endMin == b.startMin) {
                out[out.lastIndex] = prev.copy(endMin = b.endMin)
            } else {
                out += b
            }
        }
        return out
    }

    private fun refused(why: TodayRefusal) = TodayEdit.Refused(why)
}
