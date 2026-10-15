package com.roni.blocks.core.model

/*
 * A segment's lengths in minutes (3.5.0). They replace `actualEnd!!` / `plannedEnd!!` after a filter:
 * Kotlin can't smart-cast a property inside `sumOf { }`, so the old code promised non-null by hand.
 * Here an open segment, or one without a plan, is simply null; callers add `?: 0`.
 */

/** Minutes the segment really lasted; null while it is still open. */
val Segment.actualMinutes: Long?
    get() = actualEnd?.let { (it - actualStart).inWholeMinutes }

/** Minutes it was planned to last; null when it has no planned start or end. */
val Segment.plannedMinutes: Long?
    get() {
        val start = plannedStart ?: return null
        val end = plannedEnd ?: return null
        return (end - start).inWholeMinutes
    }

/** Minutes it ended after its planned end (negative when early); null while open or without a planned end. */
val Segment.minutesPastPlan: Long?
    get() {
        val end = actualEnd ?: return null
        val planned = plannedEnd ?: return null
        return (end - planned).inWholeMinutes
    }
