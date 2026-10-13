package com.roni.blocks.core.schedule

/** Why the notification makes a sound (2.2.0). */
enum class AlertKind { WARNING, ENDED, NAG, BREAK, FOCUS, TURN_BACK, WALK_DONE }

/** One sound at [atSec] (epoch seconds). */
data class Alert(val atSec: Long, val kind: AlertKind)

/**
 * When the running block makes a sound: 5 minutes before it ends, when it ends, then every 5 minutes
 * until Roni reacts (taps anything or opens the app), plus each Pomodoro break and focus start.
 * Times are epoch seconds so it stays pure; the orchestrator passes `Instant.epochSeconds`.
 * Talks to: nothing (pure). DayOrchestrator schedules the next alert and asks which one is due.
 */
object AlertPlanner {
    const val WARNING_SEC = 5 * 60L
    const val NAG_SEC = 5 * 60L
    /** Blocks shorter than this get no warning: the warning would come right after the start. */
    const val MIN_WARNED_SEC = 15 * 60L

    /**
     * The first alert after [afterSec] for a block that started at [startSec] and ends at [endSec].
     * [boundaries] are the block's Pomodoro phase changes (BREAK or FOCUS). Nags stop once
     * [reactedSec] is at or after the end. [warn] false skips the 5-minute warning (Dishes, 3.3.0).
     */
    fun next(startSec: Long, endSec: Long, boundaries: List<Alert>, reactedSec: Long?, afterSec: Long, warn: Boolean = true): Alert? {
        val candidates = buildList {
            if (warn && endSec - startSec >= MIN_WARNED_SEC) add(Alert(endSec - WARNING_SEC, AlertKind.WARNING))
            boundaries.filter { it.atSec > startSec && it.atSec < endSec - 60 }.forEach { add(it) }
            add(Alert(endSec, AlertKind.ENDED))
        }
        candidates.filter { it.atSec > afterSec }.minByOrNull { it.atSec }?.let { return it }
        if (reactedSec != null && reactedSec >= endSec) return null
        val k = (afterSec - endSec) / NAG_SEC + 1
        return Alert(endSec + k * NAG_SEC, AlertKind.NAG)
    }

    /** The alert that is due at [nowSec]: the latest one in the last [graceSec] seconds (alarms can be late), or null. */
    fun due(startSec: Long, endSec: Long, boundaries: List<Alert>, reactedSec: Long?, nowSec: Long, graceSec: Long = 90, warn: Boolean = true): Alert? {
        var last: Alert? = null
        var a = next(startSec, endSec, boundaries, reactedSec, nowSec - graceSec, warn)
        while (a != null && a.atSec <= nowSec) {
            last = a
            a = next(startSec, endSec, boundaries, reactedSec, a.atSec, warn)
        }
        return last
    }

    /** A Pomodoro block's phase changes, anchored on when the block really started (the same anchor the notification uses). */
    fun pomodoroBoundaries(startSec: Long, endSec: Long, focusMin: Int, breakMin: Int): List<Alert> = buildList {
        if (focusMin <= 0) return@buildList
        var t = startSec
        while (true) {
            t += focusMin * 60L
            if (t >= endSec) break
            add(Alert(t, AlertKind.BREAK))
            if (breakMin <= 0) continue
            t += breakMin * 60L
            if (t >= endSec) break
            add(Alert(t, AlertKind.FOCUS))
        }
    }

    /**
     * A walk rings twice (2.3.0): at half time, to turn back (the way home takes as long as the way out), and at the
     * end. A walk gets no warning and no nags: it ends by itself.
     */
    fun walkAlerts(startSec: Long, endSec: Long): List<Alert> =
        listOf(Alert(startSec + (endSec - startSec) / 2, AlertKind.TURN_BACK), Alert(endSec, AlertKind.WALK_DONE))

    /** The walk alert that is due at [nowSec] (the latest one in the last [graceSec] seconds), or null. */
    fun dueWalk(startSec: Long, endSec: Long, nowSec: Long, graceSec: Long = 90): Alert? =
        walkAlerts(startSec, endSec).lastOrNull { it.atSec <= nowSec && it.atSec > nowSec - graceSec }

    /** The next walk alert after [afterSec], or null when both have rung. */
    fun nextWalk(startSec: Long, endSec: Long, afterSec: Long): Alert? =
        walkAlerts(startSec, endSec).firstOrNull { it.atSec > afterSec }
}
