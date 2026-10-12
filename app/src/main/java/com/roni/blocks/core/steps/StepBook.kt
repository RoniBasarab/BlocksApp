package com.roni.blocks.core.steps

/**
 * Where the step count stands (2.5.0). Android's step counter only says "steps since the phone booted", so the app keeps
 * the last reading ([baseline]) and adds the difference to [today] at every checkpoint. Days are epoch days so this stays
 * pure; [baseline] is -1 before the first reading ever.
 */
data class StepState(val day: Long = -1, val baseline: Long = -1, val today: Long = 0)

/** A new state, plus the days that just closed with their final totals (written to history). */
data class StepUpdate(val state: StepState, val closed: List<Pair<Long, Long>> = emptyList())

/**
 * The step arithmetic, nothing else. Rules: a reading lower than the last one means the phone restarted, so the new
 * reading itself is the steps since the restart (what was counted before the last checkpoint is already saved; only the
 * unread stretch before the restart is lost). Steps since the last checkpoint belong to that checkpoint's day: the
 * midnight alarm makes that stretch tiny. A clock that moved back is treated as the same day. A jump over 100,000 steps is
 * a glitch and is dropped.
 * Talks to: nothing (pure). The orchestrator feeds it sensor readings and stores the result.
 */
object StepBook {
    const val MAX_DELTA = 100_000L

    fun read(state: StepState, counter: Long, day: Long): StepUpdate {
        if (counter < 0) return StepUpdate(state)
        if (state.baseline < 0) return StepUpdate(StepState(day, counter, 0))
        val raw = if (counter >= state.baseline) counter - state.baseline else counter
        val delta = if (raw > MAX_DELTA) 0 else raw
        if (day <= state.day) return StepUpdate(state.copy(baseline = counter, today = state.today + delta))
        return StepUpdate(StepState(day, counter, 0), closed = listOf(state.day to state.today + delta))
    }

    /** Steps between two readings of the same boot (a walk's start and end); null when the phone restarted in between. */
    fun between(start: Long, end: Long): Long? = if (start in 0..end) end - start else null
}
