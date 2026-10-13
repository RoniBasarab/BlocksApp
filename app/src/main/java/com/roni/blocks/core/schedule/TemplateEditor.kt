package com.roni.blocks.core.schedule

import com.roni.blocks.core.model.BlockType

/** The outcome of one edit in the template editor (3.1.0). */
sealed interface TemplateEdit {
    data class Ok(val blocks: List<PlanBlock>) : TemplateEdit
    data class Refused(val why: Refusal) : TemplateEdit
}

/** Why an edit was refused; the editor turns it into one short sentence. */
sealed interface Refusal {
    /** The new start falls inside block [id], which runs until [endMin]: "Coding runs until 11:30". */
    data class StartsInside(val id: String, val endMin: Int) : Refusal
    /** Under [TemplateRules.MIN_BLOCK] minutes. */
    data object TooShort : Refusal
    /** Before [TemplateEditor.EARLIEST]. */
    data object TooEarly : Refusal
    /** Kid time at the end would be under [TemplateRules.MIN_BLOCK] minutes. */
    data object NoRoomForKidTime : Refusal
    /** The last kid time can't be deleted or retyped: every day ends with it. */
    data object KidTimeEndsTheDay : Refusal
    /** A safety net: the result broke [TemplateRules]. The tests and the fuzzer never reach it. */
    data class Broken(val problems: List<TemplateProblem>) : Refusal
}

/**
 * Editing a template's blocks (3.1.0), in minutes of the day. A template is a sorted, gap-free list
 * (see [TemplateRules.normalize]) whose last block is kid time until 22:30.
 *
 * The one rule (Roni, 13 Oct): moving or lengthening a block pushes the blocks after it. Free time is
 * used first; then later blocks shift later and keep their length; the kid time at the end gets
 * shorter, and if it would drop under 10 minutes the edit is refused. Blocks before the edited one
 * never move: a start inside an earlier block is refused ("Coding runs until 11:30").
 * Free time is rebuilt after every edit with ids `free@<startMin>`.
 * Talks to: TemplateRules. Pure; the editor's ViewModel calls it, then saves through TemplateStore.
 */
object TemplateEditor {
    const val EARLIEST = 5 * 60
    const val DEFAULT_LENGTH = 30
    const val FIRST_START = 9 * 60
    const val BLANK_KID_START = 17 * 60
    private const val DAY_END = TemplateRules.DAY_END
    private const val MIN = TemplateRules.MIN_BLOCK

    /** A new template: only kid time, 17:00–22:30. */
    fun blank(prefix: String): List<PlanBlock> = listOf(PlanBlock("$prefix.kid_time", BlockType.KidTime, BLANK_KID_START, DAY_END))

    /** The last block: the kid time that ends the day. */
    fun dayEnd(blocks: List<PlanBlock>): PlanBlock = blocks.maxBy { it.startMin }

    /**
     * Puts block [id] (new or existing) at [startMin] for [lengthMin] minutes as [type]. For the last kid
     * time only the start counts: it always runs until 22:30. Free time is always `free@<start>`, and a
     * free row that becomes a real block gets a fresh id from [newId] with [prefix] (the template's key).
     */
    fun place(blocks: List<PlanBlock>, id: String, type: BlockType, startMin: Int, lengthMin: Int, prefix: String): TemplateEdit {
        val sorted = blocks.sortedBy { it.startMin }
        val kid = dayEnd(sorted)
        if (startMin < EARLIEST) return refused(Refusal.TooEarly)
        if (id == kid.id) {
            return if (type != BlockType.KidTime) refused(Refusal.KidTimeEndsTheDay) else placeDayEnd(sorted, kid, startMin)
        }
        if (lengthMin < MIN) return refused(Refusal.TooShort)
        val end = startMin + lengthMin
        val others = sorted.filter { it.id != id && it.id != kid.id }
        val solid = others.filter { it.type != BlockType.Free }
        solid.lastOrNull { it.startMin < startMin && it.endMin > startMin }?.let { return refused(Refusal.StartsInside(it.id, it.endMin)) }

        val placed = solid.filter { it.startMin < startMin }.toMutableList()
        var cursor = end
        for (b in solid.filter { it.startMin >= startMin }) {                      // already sorted by start
            val s = maxOf(b.startMin, cursor)
            placed += b.copy(startMin = s, endMin = s + b.length)
            cursor = s + b.length
        }
        val kidStart = maxOf(kid.startMin, cursor)
        if (DAY_END - kidStart < MIN) return refused(Refusal.NoRoomForKidTime)
        val blockId = when {
            type == BlockType.Free -> "free@$startMin"
            id.startsWith("free@") -> newId(blocks, prefix)
            else -> id
        }
        val occupied = placed + PlanBlock(blockId, type, startMin, end) + kid.copy(startMin = kidStart)
        return finish(occupied, others.filter { it.type == BlockType.Free })
    }

    /** Removes block [id]; its time becomes free time. The last kid time can't go. */
    fun delete(blocks: List<PlanBlock>, id: String): TemplateEdit {
        val sorted = blocks.sortedBy { it.startMin }
        if (id == dayEnd(sorted).id) return refused(Refusal.KidTimeEndsTheDay)
        return finish(sorted.filter { it.id != id && it.type != BlockType.Free }, sorted.filter { it.id != id && it.type == BlockType.Free })
    }

    /** Where "+ Add block" starts: right after the last block that isn't free time, or 09:00 in an empty day. */
    fun addStart(blocks: List<PlanBlock>): Int {
        val sorted = blocks.sortedBy { it.startMin }
        val kid = dayEnd(sorted)
        val last = sorted.lastOrNull { it.id != kid.id && it.type != BlockType.Free }
        return last?.endMin ?: minOf(FIRST_START, kid.startMin - DEFAULT_LENGTH).coerceAtLeast(EARLIEST)
    }

    /** A fresh id: "<prefix>.b1", "<prefix>.b2", … one past the highest in use. */
    fun newId(blocks: List<PlanBlock>, prefix: String): String {
        val n = blocks.mapNotNull { it.id.removePrefix("$prefix.b").takeIf { r -> r != it.id }?.toIntOrNull() }.maxOrNull() ?: 0
        return "$prefix.b${n + 1}"
    }

    private fun placeDayEnd(sorted: List<PlanBlock>, kid: PlanBlock, startMin: Int): TemplateEdit {
        val others = sorted.filter { it.id != kid.id }
        others.filter { it.type != BlockType.Free && it.endMin > startMin }.maxByOrNull { it.endMin }
            ?.let { return refused(Refusal.StartsInside(it.id, it.endMin)) }
        if (DAY_END - startMin < MIN) return refused(Refusal.NoRoomForKidTime)
        return finish(others.filter { it.type != BlockType.Free } + kid.copy(startMin = startMin), others.filter { it.type == BlockType.Free })
    }

    /** The blocks plus what's left of the old free time, normalized and checked. */
    private fun finish(occupied: List<PlanBlock>, free: List<PlanBlock>): TemplateEdit {
        val pieces = free.flatMap { f -> minus(f, occupied) }
        val result = TemplateRules.normalize(occupied + pieces)
        val problems = TemplateRules.problems(result)
        return if (problems.isEmpty()) TemplateEdit.Ok(result) else refused(Refusal.Broken(problems))
    }

    /** The parts of free block [f] no block covers, each `free@<start>`. */
    private fun minus(f: PlanBlock, occupied: List<PlanBlock>): List<PlanBlock> {
        var parts = listOf(f.startMin to f.endMin)
        for (o in occupied) {
            parts = parts.flatMap { (s, e) ->
                if (o.endMin <= s || o.startMin >= e) listOf(s to e)
                else listOfNotNull((s to o.startMin).takeIf { o.startMin > s }, (o.endMin to e).takeIf { o.endMin < e })
            }
        }
        return parts.map { (s, e) -> PlanBlock("free@$s", BlockType.Free, s, e) }
    }

    private fun refused(why: Refusal) = TemplateEdit.Refused(why)
}
