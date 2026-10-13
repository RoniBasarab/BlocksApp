package com.roni.blocks.core.schedule

import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.isWork

/** A block of today's plan in minutes of the day. `BlockSpec` converts to and from it. */
data class PlanBlock(val id: String, val type: BlockType, val startMin: Int, val endMin: Int) {
    val length: Int get() = endMin - startMin
}

enum class Edge { TOP, BOTTOM }

sealed interface PlanEdit {
    val blockId: String
    data class Resize(override val blockId: String, val edge: Edge, val toMin: Int) : PlanEdit
    data class Retype(override val blockId: String, val type: BlockType) : PlanEdit
    /** Adds a work block of [type] at the start of the free block [blockId] (2.1.0). */
    data class Insert(override val blockId: String, val type: BlockType) : PlanEdit
}

/**
 * What a block offers in the UI. END_AND_TYPE is the running work block: its end moves and it can become
 * another work type (2.2.0). END_ONLY is any other running block: free time, or a block Next finished early.
 */
enum class BlockEdit { NONE, END_ONLY, END_AND_TYPE, FULL, TYPE_ONLY }

/** The block the day is on. `endEditable` is false once Next ended it early: its leftover is free time, so its end still moves but its type doesn't. */
data class CurrentBlock(val id: String, val endEditable: Boolean)

/**
 * The rules for reshaping today's plan. Work blocks keep their length when pushed; free time absorbs the change.
 * Kid time (anything that isn't CV, Coding, Gym or Free) never moves, and nothing before the current block changes.
 * Returns the new plan, or null when the edit isn't allowed or changes nothing.
 * Talks to: nothing (pure). HomeViewModel uses it for the live preview and DayOrchestrator for the commit, so both agree.
 */
object PlanEditor {
    const val SNAP = 5
    const val MIN_WORK = 10
    const val DAY_END = 22 * 60 + 30
    const val INSERT_MINUTES = 30

    fun editMode(plan: List<PlanBlock>, blockId: String, nowMin: Int, current: CurrentBlock?): BlockEdit {
        if (current == null) return BlockEdit.NONE
        val blocks = plan.sortedBy { it.startMin }
        val cur = blocks.indexOfFirst { it.id == current.id }
        val i = blocks.indexOfFirst { it.id == blockId }
        if (cur < 0 || i < cur) return BlockEdit.NONE                              // past blocks are read-only
        val b = blocks[i]
        if (!b.type.isEditable()) return BlockEdit.NONE
        if (i == cur) return if (current.endEditable && b.type.isWork) BlockEdit.END_AND_TYPE else BlockEdit.END_ONLY
        if (b.endMin <= snapUp(nowMin + SNAP)) return BlockEdit.NONE                // over before it could start: it will be missed
        return if (b.type == BlockType.Free) BlockEdit.TYPE_ONLY else BlockEdit.FULL
    }

    fun apply(plan: List<PlanBlock>, edit: PlanEdit, nowMin: Int, current: CurrentBlock?): List<PlanBlock>? {
        val mode = editMode(plan, edit.blockId, nowMin, current)
        val allowed = when (edit) {
            is PlanEdit.Retype -> mode == BlockEdit.FULL || mode == BlockEdit.TYPE_ONLY || (mode == BlockEdit.END_AND_TYPE && edit.type.isWork)
            is PlanEdit.Insert -> mode == BlockEdit.TYPE_ONLY && edit.type.isWork
            is PlanEdit.Resize -> mode == BlockEdit.FULL || ((mode == BlockEdit.END_ONLY || mode == BlockEdit.END_AND_TYPE) && edit.edge == Edge.BOTTOM)
        }
        if (!allowed || current == null) return null
        val blocks = plan.sortedBy { it.startMin }.toMutableList()
        val cur = blocks.indexOfFirst { it.id == current.id }
        val i = blocks.indexOfFirst { it.id == edit.blockId }
        return when (edit) {
            is PlanEdit.Retype -> retype(blocks, i, edit.type)
            is PlanEdit.Insert -> insert(blocks, i, edit.type, nowMin)
            is PlanEdit.Resize -> when (edit.edge) {
                Edge.BOTTOM -> if (pullsNext(blocks, i, cur, snap(edit.toMin))) {
                    moveStart(blocks, i + 1, cur, snap(edit.toMin), nowMin)                 // running free time ends sooner: the next block starts sooner
                } else {
                    moveEnd(blocks, i, snap(edit.toMin), nowMin)
                }
                Edge.TOP -> moveStart(blocks, i, cur, snap(edit.toMin), nowMin)
            }
        }
    }

    private fun pullsNext(blocks: List<PlanBlock>, i: Int, cur: Int, to: Int): Boolean =
        i == cur && blocks[i].type == BlockType.Free && to < blocks[i].endMin && blocks.getOrNull(i + 1)?.type?.isWork == true

    private fun moveEnd(blocks: MutableList<PlanBlock>, i: Int, to: Int, nowMin: Int): List<PlanBlock>? {
        val b = blocks[i]
        val end = maxOf(to, b.startMin + MIN_WORK, snapUp(nowMin + SNAP))          // at least 10 min long; never ends in the next 5 min
        if (end == b.endMin) return null
        if (end < b.endMin) {                                                       // shorter: the freed time becomes free time
            blocks[i] = b.copy(endMin = end)
            freeAfter(blocks, i, end, b.endMin)
            return blocks
        }
        val delta = minOf(end - b.endMin, slackAfter(blocks, i))                    // longer: free time absorbs it, work blocks get pushed
        if (delta <= 0) return null
        pushForward(blocks, i, b.endMin + delta)
        return blocks
    }

    private fun moveStart(blocks: MutableList<PlanBlock>, i: Int, cur: Int, to: Int, nowMin: Int): List<PlanBlock>? {
        val b = blocks[i]
        val floor = floorBefore(blocks, i, cur, nowMin)
        val target = maxOf(to, floor)
        if (target > b.startMin) {                                                  // later: the freed time becomes free time
            val start = minOf(target, b.endMin - MIN_WORK)
            if (start <= b.startMin) return null
            blocks[i] = b.copy(startMin = start)
            freeBefore(blocks, i, b.startMin, start)
            return blocks
        }
        val delta = minOf(b.startMin - target, slackBefore(blocks, i, cur, floor))  // earlier: the free time before it shrinks, work blocks get pushed
        if (delta <= 0) return null
        pushBackward(blocks, i, cur, b.startMin - delta)
        return blocks
    }

    /** The earliest minute block i may start: not in the next 5 minutes, not before kid time, and not before the end of the
     *  current block, unless the current block is free time (free time can shrink). */
    private fun floorBefore(blocks: List<PlanBlock>, i: Int, cur: Int, nowMin: Int): Int {
        var floor = snapUp(nowMin + SNAP)
        for (j in i - 1 downTo cur) {
            val bj = blocks[j]
            if (j == cur && bj.type == BlockType.Free) break
            if (j == cur || !bj.type.isEditable()) { floor = maxOf(floor, bj.endMin); break }
        }
        return floor
    }

    /** Free minutes (free blocks and gaps) between block i's end and the next block that can't move (or 22:30). */
    private fun slackAfter(blocks: List<PlanBlock>, i: Int): Int {
        val anchor = blocks.drop(i + 1).firstOrNull { !it.type.isEditable() }?.startMin ?: DAY_END
        var slack = 0
        var cursor = blocks[i].endMin
        for (j in i + 1 until blocks.size) {
            val bj = blocks[j]
            if (bj.startMin >= anchor) break
            slack += maxOf(0, bj.startMin - cursor)
            if (bj.type == BlockType.Free) slack += bj.length
            cursor = bj.endMin
        }
        return slack + maxOf(0, anchor - cursor)
    }

    /** Free minutes (free blocks and gaps) between `floor` and block i's start. */
    private fun slackBefore(blocks: List<PlanBlock>, i: Int, cur: Int, floor: Int): Int {
        var slack = 0
        var cursor = blocks[i].startMin
        for (j in i - 1 downTo cur) {
            val bj = blocks[j]
            if (bj.endMin <= floor) break
            slack += maxOf(0, cursor - bj.endMin)
            if (bj.type == BlockType.Free) slack += bj.endMin - maxOf(bj.startMin, floor)
            cursor = bj.startMin
        }
        return slack + maxOf(0, cursor - floor)
    }

    private fun pushForward(blocks: MutableList<PlanBlock>, i: Int, newEnd: Int) {
        blocks[i] = blocks[i].copy(endMin = newEnd)
        var cursor = newEnd
        var j = i + 1
        while (j < blocks.size && cursor > blocks[j].startMin) {
            val bj = blocks[j]
            if (bj.type == BlockType.Free) {
                if (cursor < bj.endMin) { blocks[j] = bj.copy(startMin = cursor); return }  // partly absorbed: the push ends here
                blocks.removeAt(j)                                                          // fully absorbed
                continue
            }
            val shift = cursor - bj.startMin                                                // a work block moves later, same length
            blocks[j] = bj.copy(startMin = bj.startMin + shift, endMin = bj.endMin + shift)
            cursor = blocks[j].endMin
            j++
        }
    }

    private fun pushBackward(blocks: MutableList<PlanBlock>, i: Int, cur: Int, newStart: Int) {
        blocks[i] = blocks[i].copy(startMin = newStart)
        var cursor = newStart
        var j = i - 1
        while (j >= cur && cursor < blocks[j].endMin) {
            val bj = blocks[j]
            if (bj.type == BlockType.Free) {
                if (cursor > bj.startMin) { blocks[j] = bj.copy(endMin = cursor); return }  // partly absorbed: the push ends here
                blocks.removeAt(j)                                                          // fully absorbed
                j--
                continue
            }
            val shift = bj.endMin - cursor                                                  // a work block moves earlier, same length
            blocks[j] = bj.copy(startMin = bj.startMin - shift, endMin = bj.endMin - shift)
            cursor = blocks[j].startMin
            j--
        }
    }

    private fun freeAfter(blocks: MutableList<PlanBlock>, i: Int, from: Int, to: Int) {
        val next = blocks.getOrNull(i + 1)
        if (next != null && next.type == BlockType.Free && next.startMin == to) blocks[i + 1] = next.copy(startMin = from)
        else blocks.add(i + 1, PlanBlock(newId(blocks), BlockType.Free, from, to))
    }

    private fun freeBefore(blocks: MutableList<PlanBlock>, i: Int, from: Int, to: Int) {
        val prev = blocks.getOrNull(i - 1)
        if (prev != null && prev.type == BlockType.Free && prev.endMin == from) blocks[i - 1] = prev.copy(endMin = to)
        else blocks.add(i, PlanBlock(newId(blocks), BlockType.Free, from, to))
    }

    private fun retype(blocks: MutableList<PlanBlock>, i: Int, type: BlockType): List<PlanBlock>? {
        val b = blocks[i]
        if (type == b.type || !type.isEditable()) return null
        if (type != BlockType.Free && b.length < MIN_WORK) return null
        blocks[i] = b.copy(type = type)
        if (type != BlockType.Free) return blocks
        val next = blocks.getOrNull(i + 1)                                          // free time merges with the free time around it
        if (next != null && next.type == BlockType.Free && next.startMin == blocks[i].endMin) {
            blocks[i] = blocks[i].copy(endMin = next.endMin)
            blocks.removeAt(i + 1)
        }
        val prev = blocks.getOrNull(i - 1)
        if (prev != null && prev.type == BlockType.Free && prev.endMin == blocks[i].startMin) {
            blocks[i - 1] = prev.copy(endMin = blocks[i].endMin)
            blocks.removeAt(i)
        }
        return blocks
    }

    /** A new [type] block takes the first [INSERT_MINUTES] of the free block that can still start; the rest stays free. */
    private fun insert(blocks: MutableList<PlanBlock>, i: Int, type: BlockType, nowMin: Int): List<PlanBlock>? {
        val free = blocks[i]
        val start = maxOf(free.startMin, snapUp(nowMin + SNAP))
        val end = minOf(start + INSERT_MINUTES, free.endMin)
        if (end - start < MIN_WORK) return null
        if (start == free.startMin && end == free.endMin) return retype(blocks, i, type)
        val newBlock = PlanBlock(newId(blocks), type, start, end) // ids computed before anything is removed, so none repeats
        val pieces = mutableListOf<PlanBlock>()
        if (start > free.startMin) pieces += free.copy(endMin = start)
        pieces += newBlock
        if (end < free.endMin) {
            val restId = if (start > free.startMin) newId(blocks + newBlock) else free.id
            pieces += PlanBlock(restId, BlockType.Free, end, free.endMin)
        }
        blocks.removeAt(i)
        blocks.addAll(i, pieces)
        return blocks
    }

    /** Ids for new free blocks: "edit-1", "edit-2", … The same plan always gets the same id, so preview and commit agree. */
    private fun newId(blocks: List<PlanBlock>): String {
        val last = blocks.mapNotNull { if (it.id.startsWith("edit-")) it.id.removePrefix("edit-").toIntOrNull() else null }.maxOrNull() ?: 0
        return "edit-${last + 1}"
    }

    const val WALK_DEFAULT = 30
    const val WALK_MIN = 10
    const val WALK_MAX = 60

    /**
     * Next was pressed before the next block's planned start (2.3.0; 2.6.0: no skipping). Next means "the next block,
     * now": the current block ends now and the very next block starts now. A work block keeps its length and the time it
     * leaves behind becomes free time; free time simply starts sooner and ends where it did; kid time just starts now.
     * Nothing leaves the plan except a current block that never ran (Next in the minute it started).
     * Returns null when the next block is already due (the normal Next handles it).
     */
    fun startEarly(plan: List<PlanBlock>, currentId: String, nowMin: Int): List<PlanBlock>? {
        val blocks = plan.sortedBy { it.startMin }.toMutableList()
        val cur = blocks.indexOfFirst { it.id == currentId }
        if (cur < 0) return null
        val next = blocks.getOrNull(cur + 1) ?: return null
        if (next.startMin <= nowMin) return null
        val start = maxOf(nowMin, blocks[cur].startMin)
        when {
            !next.type.isEditable() || next.type == BlockType.Free -> blocks[cur + 1] = next.copy(startMin = start)
            else -> {
                val end = start + next.length                                       // earlier, so it can't reach kid time
                blocks[cur + 1] = next.copy(startMin = start, endMin = end)
                freeAfter(blocks, cur + 1, end, next.endMin)
            }
        }
        return endCurrent(blocks, cur, start)
    }

    /**
     * "Start now" before the day's first block (2.7.0): the first block starts now. A work or free block keeps its length
     * and the time it leaves behind becomes free time; kid time just starts now. Null when the first block is already due.
     */
    fun startFirstNow(plan: List<PlanBlock>, nowMin: Int): List<PlanBlock>? {
        val blocks = plan.sortedBy { it.startMin }.toMutableList()
        val first = blocks.firstOrNull() ?: return null
        if (first.startMin <= nowMin) return null
        if (!first.type.isEditable()) {
            blocks[0] = first.copy(startMin = nowMin)
            return blocks
        }
        val end = nowMin + first.length
        blocks[0] = first.copy(startMin = nowMin, endMin = end)
        freeAfter(blocks, 0, end, first.endMin)
        return blocks
    }

    /** The block Next lands on from [currentId]: simply the next one. */
    fun nextStop(plan: List<PlanBlock>, currentId: String): PlanBlock? {
        val blocks = plan.sortedBy { it.startMin }
        val cur = blocks.indexOfFirst { it.id == currentId }
        return if (cur < 0) null else blocks.getOrNull(cur + 1)
    }

    /** The current block ends at [at]; a block that never really ran (it would end where it starts) leaves the plan. */
    private fun endCurrent(blocks: MutableList<PlanBlock>, cur: Int, at: Int): List<PlanBlock> {
        if (at <= blocks[cur].startMin) blocks.removeAt(cur) else blocks[cur] = blocks[cur].copy(endMin = at)
        return blocks
    }

    /** The walk would start now and end at most here: kid time (or 22:30) is a wall. Null when no walk fits. */
    fun walkEnd(plan: List<PlanBlock>, currentId: String, nowMin: Int, minutes: Int): Int? {
        val blocks = plan.sortedBy { it.startMin }
        val cur = blocks.indexOfFirst { it.id == currentId }
        if (cur < 0 || !blocks[cur].type.isEditable()) return null
        val wall = blocks.drop(cur + 1).firstOrNull { !it.type.isEditable() }?.startMin ?: DAY_END
        val end = minOf(nowMin + minutes.coerceIn(WALK_MIN, WALK_MAX), wall)
        return end.takeIf { it - nowMin >= WALK_MIN }
    }

    /**
     * A walk starts now (2.3.0): the current block stops, the walk takes [minutes] (cut at kid time), then the rest of
     * the current block resumes; a walk longer than that eats the start of the blocks after it. A leftover work piece
     * under 10 minutes becomes free time, and so does the whole leftover when [doneEarly] (the block was already finished). The walk's id is `walk-<startMin>`; the resumed piece is `<id>@<startMin>`.
     */
    fun insertWalk(plan: List<PlanBlock>, currentId: String, nowMin: Int, minutes: Int, doneEarly: Boolean = false): List<PlanBlock>? {
        val end = walkEnd(plan, currentId, nowMin, minutes) ?: return null
        val blocks = plan.sortedBy { it.startMin }
        val cur = blocks.indexOfFirst { it.id == currentId }
        val c = blocks[cur]
        val out = blocks.take(cur).toMutableList()
        if (nowMin > c.startMin) out += c.copy(endMin = nowMin)
        out += PlanBlock(uniqueId("walk-$nowMin", blocks), BlockType.Walk, nowMin, end)
        val after = mutableListOf<PlanBlock>()
        if (end < c.endMin) after += c.copy(id = uniqueId("${c.id}@$end", blocks), type = if (doneEarly) BlockType.Free else c.type, startMin = end)
        for (b in blocks.drop(cur + 1)) {
            when {
                b.endMin <= end -> Unit                                             // covered by the walk
                b.startMin < end -> after += b.copy(startMin = end)                 // starts when the walk ends
                else -> after += b
            }
        }
        val tidy = after.map { if (it.type.isWork && it.length < MIN_WORK) it.copy(type = BlockType.Free) else it }
        return out + mergeFree(tidy)
    }

    /**
     * "Turn back now" (2.3.0): the way home takes as long as the way out did, so the walk ends at [newEndMin]. The
     * block after it starts that much sooner; if it can't move (kid time), the freed time is free time.
     */
    fun shortenWalk(plan: List<PlanBlock>, walkId: String, newEndMin: Int): List<PlanBlock>? {
        val blocks = plan.sortedBy { it.startMin }.toMutableList()
        val w = blocks.indexOfFirst { it.id == walkId }
        if (w < 0 || blocks[w].type != BlockType.Walk) return null
        val walk = blocks[w]
        if (newEndMin >= walk.endMin || newEndMin <= walk.startMin) return null
        blocks[w] = walk.copy(endMin = newEndMin)
        val next = blocks.getOrNull(w + 1)
        if (next != null && next.type.isEditable() && next.startMin == walk.endMin) blocks[w + 1] = next.copy(startMin = newEndMin)
        else blocks.add(w + 1, PlanBlock(uniqueId("$walkId~free", blocks), BlockType.Free, newEndMin, walk.endMin))
        return blocks
    }

    /** [base], or base+1, base+2… when a block already has it (a second walk can land on the same minute). */
    private fun uniqueId(base: String, blocks: List<PlanBlock>): String {
        if (blocks.none { it.id == base }) return base
        var n = 1
        while (blocks.any { it.id == "$base+$n" }) n++
        return "$base+$n"
    }

    private fun mergeFree(blocks: List<PlanBlock>): List<PlanBlock> {
        val out = mutableListOf<PlanBlock>()
        for (b in blocks) {
            val last = out.lastOrNull()
            if (last != null && last.type == BlockType.Free && b.type == BlockType.Free && last.endMin == b.startMin) {
                out[out.lastIndex] = last.copy(endMin = b.endMin)
            } else {
                out += b
            }
        }
        return out
    }

    fun snap(m: Int): Int = ((m + SNAP / 2) / SNAP) * SNAP
    private fun snapUp(m: Int): Int = ((m + SNAP - 1) / SNAP) * SNAP
}

/** CV, Coding, Gym and Free can be edited; kid time, Dishes and the evening can't. */
fun BlockType.isEditable(): Boolean = isWork || this == BlockType.Free
