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
}

/** What a block offers in the UI. */
enum class BlockEdit { NONE, END_ONLY, FULL, TYPE_ONLY }

/** The block the day is on. `endEditable` is false once Next ended it early (its leftover is free time). */
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

    fun editMode(plan: List<PlanBlock>, blockId: String, nowMin: Int, current: CurrentBlock?): BlockEdit {
        if (current == null) return BlockEdit.NONE
        val blocks = plan.sortedBy { it.startMin }
        val cur = blocks.indexOfFirst { it.id == current.id }
        val i = blocks.indexOfFirst { it.id == blockId }
        if (cur < 0 || i < cur) return BlockEdit.NONE                              // past blocks are read-only
        val b = blocks[i]
        if (!b.type.isEditable()) return BlockEdit.NONE
        if (i == cur) return if (current.endEditable && b.type.isWork) BlockEdit.END_ONLY else BlockEdit.NONE
        if (b.endMin <= snapUp(nowMin + SNAP)) return BlockEdit.NONE                // over before it could start: it will be missed
        return if (b.type == BlockType.Free) BlockEdit.TYPE_ONLY else BlockEdit.FULL
    }

    fun apply(plan: List<PlanBlock>, edit: PlanEdit, nowMin: Int, current: CurrentBlock?): List<PlanBlock>? {
        val mode = editMode(plan, edit.blockId, nowMin, current)
        val allowed = when (edit) {
            is PlanEdit.Retype -> mode == BlockEdit.FULL || mode == BlockEdit.TYPE_ONLY
            is PlanEdit.Resize -> mode == BlockEdit.FULL || (mode == BlockEdit.END_ONLY && edit.edge == Edge.BOTTOM)
        }
        if (!allowed || current == null) return null
        val blocks = plan.sortedBy { it.startMin }.toMutableList()
        val cur = blocks.indexOfFirst { it.id == current.id }
        val i = blocks.indexOfFirst { it.id == edit.blockId }
        return when (edit) {
            is PlanEdit.Retype -> retype(blocks, i, edit.type)
            is PlanEdit.Resize -> when (edit.edge) {
                Edge.BOTTOM -> moveEnd(blocks, i, snap(edit.toMin), nowMin)
                Edge.TOP -> moveStart(blocks, i, cur, snap(edit.toMin), nowMin)
            }
        }
    }

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

    /** Ids for new free blocks: "edit-1", "edit-2", … The same plan always gets the same id, so preview and commit agree. */
    private fun newId(blocks: List<PlanBlock>): String {
        val last = blocks.mapNotNull { if (it.id.startsWith("edit-")) it.id.removePrefix("edit-").toIntOrNull() else null }.maxOrNull() ?: 0
        return "edit-${last + 1}"
    }

    fun snap(m: Int): Int = ((m + SNAP / 2) / SNAP) * SNAP
    private fun snapUp(m: Int): Int = ((m + SNAP - 1) / SNAP) * SNAP
}

/** CV, Coding, Gym and Free can be edited; kid time, Dishes and the evening can't. */
fun BlockType.isEditable(): Boolean = isWork || this == BlockType.Free
