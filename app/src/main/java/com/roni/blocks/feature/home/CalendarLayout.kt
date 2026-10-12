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
 * The real heights of block text on this phone, in dp, measured by the UI with its TextMeasurer (they grow with the
 * system font size). [oneLineDp]: the one-line style ("Title · 20:12"); [twoLinesDp]: title plus time.
 */
data class TextMetrics(val oneLineDp: Float = 16f, val twoLinesDp: Float = 34f)

/**
 * Where everything on the day calendar goes so that no text ever overlaps or gets cut (2.6.0; 2.6.1: real text
 * heights). All sizes are dp from the top of the day (minute [dayStartMin] is 0 dp).
 * - A block is drawn [GAP_DP] inside its time range at the top and bottom, and its text keeps [PAD_DP] from those edges.
 * - A block too short for even one line of text is a *sliver*: no text inside; its name goes into a pill at its start,
 *   so a 1-minute block still shows that it happened. A block whose text is squeezed out by pills gets a pill too.
 * - Ticks (walks that aren't blocks) get a pill too, with a thin line across the block they happened in.
 * - Pills sit centred on their moment. A pill that would overlap the row above joins it (up to [MAX_PER_ROW] side by
 *   side) or moves down just below it; rows never overlap.
 * - A block's text starts below any pill row that would cover it, then gets two lines, one line or none, depending on
 *   the room left before the next pill row or its bottom. Text never reaches past [BlockFit.textTopDp] + its height.
 * Talks to: nothing (pure). HomeViewModel calls [fit] with measured [TextMetrics]; DayCalendar draws exactly what it
 * returns.
 */
object CalendarLayout {
    const val DP_PER_MIN = 80f / 60f
    const val PILL_DP = 18f
    const val ROW_GAP_DP = 2f
    const val GAP_DP = 1f
    const val PAD_DP = 3f
    const val MAX_PER_ROW = 2

    fun fit(
        blocks: List<LayoutBlock>,
        ticks: List<LayoutTick>,
        dayStartMin: Int,
        text: TextMetrics = TextMetrics(),
    ): CalendarFit {
        fun y(min: Int) = (min - dayStartMin) * DP_PER_MIN
        val sorted = blocks.sortedBy { it.startMin }
        val inner = GAP_DP + PAD_DP
        val pilled = sorted.filter { (it.endMin - it.startMin) * DP_PER_MIN - 2 * inner < text.oneLineDp }.map { it.id }.toMutableSet()
        repeat(sorted.size + 1) {
            val rows = rows(sorted, pilled, ticks, ::y)
            val fits = sorted.map { b -> fitBlock(b, b.id in pilled, rows, y(b.startMin), y(b.endMin), text) }
            val squeezed = fits.filter { !it.sliver && it.fit == TextFit.NONE }.map { it.id }
            if (squeezed.isEmpty()) return CalendarFit(fits, rows)
            pilled += squeezed                                                       // text squeezed out: the name goes to a pill
        }
        error("unreachable: every round pills at least one more block")
    }

    private fun rows(sorted: List<LayoutBlock>, pilled: Set<String>, ticks: List<LayoutTick>, y: (Int) -> Float): List<PillRow> {
        val anchors = (sorted.filter { it.id in pilled }.map { it.id to y(it.startMin) } + ticks.map { it.id to y(it.atMin) })
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
        return rows
    }

    private fun fitBlock(b: LayoutBlock, pilled: Boolean, rows: List<PillRow>, top: Float, bottom: Float, text: TextMetrics): BlockFit {
        if (pilled) return BlockFit(b.id, sliver = true, textTopDp = top, fit = TextFit.NONE)
        var textTop = top + GAP_DP + PAD_DP
        for (r in rows) {                                                            // rows are in order: one pass pushes past each
            val rBottom = r.topDp + PILL_DP
            if (r.topDp < textTop + text.oneLineDp && rBottom > textTop) textTop = rBottom + ROW_GAP_DP
        }
        val nextRow = rows.firstOrNull { it.topDp >= textTop }?.topDp ?: Float.MAX_VALUE
        val room = minOf(nextRow - ROW_GAP_DP, bottom - GAP_DP - PAD_DP) - textTop
        val fit = when {
            room >= text.twoLinesDp -> TextFit.TWO_LINES
            room >= text.oneLineDp -> TextFit.ONE_LINE
            else -> TextFit.NONE
        }
        return BlockFit(b.id, sliver = false, textTopDp = textTop, fit = fit)
    }
}
