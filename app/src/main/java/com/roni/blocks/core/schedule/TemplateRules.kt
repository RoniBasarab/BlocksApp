package com.roni.blocks.core.schedule

import com.roni.blocks.core.model.BlockType

/** Something wrong with a template; the editor (3.1) shows it, loading (3.0) falls back to the seed. */
sealed interface TemplateProblem {
    data object Empty : TemplateProblem
    data class Overlap(val firstId: String, val secondId: String) : TemplateProblem
    data class TooShort(val id: String) : TemplateProblem
    data class PastDayEnd(val id: String) : TemplateProblem
    data class DuplicateId(val id: String) : TemplateProblem
    data object NoKidTimeAtEnd : TemplateProblem
}

/**
 * The rules every template follows (3.0.0), in minutes of the day:
 * - blocks don't overlap and are at least [MIN_BLOCK] minutes (kid time may be any length);
 * - nothing runs past 22:30, and the day ends with kid time until 22:30 (more kid time earlier is fine, as Fri/Sat);
 * - ids are unique.
 * [normalize] makes a sorted, gap-free plan: gaps become free time and neighbouring free time merges. The Off day
 * (no blocks) is not a template these rules apply to.
 * Talks to: nothing (pure). TemplateStore checks stored templates on load; the 3.1 editor runs both after every edit.
 */
object TemplateRules {
    const val DAY_END = 22 * 60 + 30
    const val MIN_BLOCK = 10

    fun problems(blocks: List<PlanBlock>): List<TemplateProblem> {
        if (blocks.isEmpty()) return listOf(TemplateProblem.Empty)
        val out = mutableListOf<TemplateProblem>()
        val sorted = blocks.sortedBy { it.startMin }
        blocks.groupBy { it.id }.filter { it.value.size > 1 }.keys.sorted().forEach { out += TemplateProblem.DuplicateId(it) }
        sorted.zipWithNext().forEach { (a, b) -> if (b.startMin < a.endMin) out += TemplateProblem.Overlap(a.id, b.id) }
        sorted.forEach { b ->
            if (b.type != BlockType.KidTime && b.endMin - b.startMin < MIN_BLOCK) out += TemplateProblem.TooShort(b.id)
            if (b.endMin > DAY_END) out += TemplateProblem.PastDayEnd(b.id)
        }
        val last = sorted.last()
        if (last.type != BlockType.KidTime || last.endMin != DAY_END) out += TemplateProblem.NoKidTimeAtEnd
        return out
    }

    /**
     * Sorted, with every gap between blocks filled by free time (`free@<startMin>`) and neighbouring free time merged
     * (the first one's id stays). Overlaps are left alone: [problems] reports them.
     */
    fun normalize(blocks: List<PlanBlock>): List<PlanBlock> {
        val sorted = blocks.sortedBy { it.startMin }
        val filled = mutableListOf<PlanBlock>()
        for (b in sorted) {
            val prev = filled.lastOrNull()
            if (prev != null && b.startMin > prev.endMin) filled += PlanBlock("free@${prev.endMin}", BlockType.Free, prev.endMin, b.startMin)
            filled += b
        }
        val merged = mutableListOf<PlanBlock>()
        for (b in filled) {
            val prev = merged.lastOrNull()
            if (prev != null && prev.type == BlockType.Free && b.type == BlockType.Free && prev.endMin == b.startMin) {
                merged[merged.lastIndex] = prev.copy(endMin = b.endMin)
            } else {
                merged += b
            }
        }
        return merged
    }
}
