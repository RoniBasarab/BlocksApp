package com.roni.blocks.feature.home

/** A block as the layout sees it: minutes of the day. */
data class LayoutBlock(val id: String, val startMin: Int, val endMin: Int)

/** A moment worth a label that isn't a block: a short walk, a standalone walk. */
data class LayoutTick(val id: String, val atMin: Int)

enum class TextFit { TWO_LINES, ONE_LINE, NONE }

/** How one block is drawn: a sliver has no text (its pill carries it); [textTopDp] is where its text starts. */
data class BlockFit(val id: String, val sliver: Boolean, val textTopDp: Float, val fit: TextFit)

/** One row of pills, left to right, its top at [topDp]. Ids are sliver blocks' or ticks' ids. */
data class PillRow(val topDp: Float, val ids: List<String>)

data class CalendarFit(val blocks: List<BlockFit>, val rows: List<PillRow>)

/**
 * Where everything on the day calendar goes so that no text ever overlaps or gets cut (2.6.0). All sizes are dp from
 * the top of the day (minute [dayStartMin] is 0 dp).
 * - A block shorter than [SLIVER_DP] (about 13 minutes) is a *sliver*: a thin coloured bar with no text. Its name goes
 *   into a pill at its start, so a 1-minute block still shows that it happened.
 * - Ticks (walks that aren't blocks) get a pill too, with a thin line across the block they happened in.
 * - Pills sit centred on their moment. A pill that would overlap the row above joins it (up to [MAX_PER_ROW] side by
 *   side) or moves down just below it; rows never overlap.
 * - A block's text starts below any pill row that would cover it, then gets two lines, one line or none, depending on
 *   the room left before the next pill row or its bottom.
 * Talks to: nothing (pure). HomeViewModel calls [fit]; DayCalendar draws exactly what it returns.
 */
object CalendarLayout {
    const val DP_PER_MIN = 80f / 60f
    const val SLIVER_DP = 18f
    const val PILL_DP = 18f
    const val ROW_GAP_DP = 2f
    const val PAD_DP = 3f
    const val ONE_LINE_DP = 16f
    const val TWO_LINES_DP = 34f
    const val MAX_PER_ROW = 2

    fun fit(blocks: List<LayoutBlock>, ticks: List<LayoutTick>, dayStartMin: Int): CalendarFit {
        fun y(min: Int) = (min - dayStartMin) * DP_PER_MIN
        val sorted = blocks.sortedBy { it.startMin }
        val slivers = sorted.filter { (it.endMin - it.startMin) * DP_PER_MIN < SLIVER_DP }.map { it.id }.toSet()
        val anchors = (sorted.filter { it.id in slivers }.map { it.id to y(it.startMin) } + ticks.map { it.id to y(it.atMin) })
            .sortedWith(compareBy({ it.second }, { it.first }))

        val rows = mutableListOf<PillRow>()
        for ((id, at) in anchors) {
            val wanted = (at - PILL_DP / 2).coerceAtLeast(0f)
            val last = rows.lastOrNull()
            when {
                last == null -> rows += PillRow(wanted, listOf(id))
                wanted < last.topDp + PILL_DP && last.ids.size < MAX_PER_ROW -> rows[rows.lastIndex] = last.copy(ids = last.ids + id)
                else -> rows += PillRow(maxOf(wanted, last.topDp + PILL_DP + ROW_GAP_DP), listOf(id))
            }
        }

        val fits = sorted.map { b ->
            val top = y(b.startMin)
            val bottom = y(b.endMin)
            if (b.id in slivers) return@map BlockFit(b.id, sliver = true, textTopDp = top, fit = TextFit.NONE)
            var textTop = top + PAD_DP
            for (r in rows) {                                                        // rows are in order: one pass pushes past each
                val rBottom = r.topDp + PILL_DP
                if (r.topDp < textTop + ONE_LINE_DP && rBottom > textTop) textTop = rBottom + ROW_GAP_DP
            }
            val nextRow = rows.firstOrNull { it.topDp >= textTop }?.topDp ?: Float.MAX_VALUE
            val room = minOf(nextRow - ROW_GAP_DP, bottom - PAD_DP) - textTop
            val fit = when {
                room >= TWO_LINES_DP -> TextFit.TWO_LINES
                room >= ONE_LINE_DP -> TextFit.ONE_LINE
                else -> TextFit.NONE
            }
            BlockFit(b.id, sliver = false, textTopDp = textTop, fit = fit)
        }
        return CalendarFit(fits, rows)
    }
}
