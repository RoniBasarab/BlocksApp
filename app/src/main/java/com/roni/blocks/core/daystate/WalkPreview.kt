package com.roni.blocks.core.daystate

import kotlin.time.Instant

/**
 * What the walk start sheet shows before it is committed (2.3.0): when to turn back, when Roni is
 * home, and what the day does after the walk. Pure data; [DayOrchestrator.walkPreview] builds it
 * from the same [com.roni.blocks.core.schedule.PlanEditor] the walk itself commits with.
 */
data class WalkPreview(
    val turnBackAt: Instant,
    val homeAt: Instant,
    val afterTitle: String?,
    val afterEnd: Instant?,
)
