package com.roni.blocks.core.daystate

import com.roni.blocks.core.model.TemplateId
import kotlin.time.Instant

/**
 * Every legal day-state change, as one pure function: the only clock it knows is the [now] it is
 * handed. Rules: an event that does not apply returns null rather than throwing, so callers decide
 * whether ignoring it is safe; `Ended` and `Off` accept nothing but a new `StartDay`.
 *
 * Talks to: [DayState], [DayEvent]. No IO, no coroutines, no Android.
 */
object DayStateMachine {
    fun transition(state: DayState, event: DayEvent, now: Instant): DayState? = when (state) {
        DayState.NotStarted -> fromNotStarted(event)
        is DayState.Running -> fromRunning(state, event, now)
        is DayState.Paused -> fromPaused(state, event)
        DayState.KidTime -> fromKidTime(event)
        is DayState.Evening -> fromEvening(state, event)
        DayState.Ended, DayState.Off -> null
    }

    private fun fromNotStarted(event: DayEvent): DayState? = when (event) {
        is DayEvent.StartDay -> start(event.template)
        DayEvent.TakeOffDay -> DayState.Off
        else -> null
    }

    private fun fromRunning(state: DayState.Running, event: DayEvent, now: Instant): DayState? = when (event) {
        is DayEvent.StartDay -> start(event.template)
        DayEvent.TakeOffDay -> DayState.Off
        is DayEvent.Next -> state.copy(blockIndex = state.blockIndex + 1)
        DayEvent.Pause -> DayState.Paused(state, now)
        DayEvent.KidTimeStarted -> DayState.KidTime
        is DayEvent.EndDay -> DayState.Ended
        else -> null
    }

    private fun fromPaused(state: DayState.Paused, event: DayEvent): DayState? = when (event) {
        DayEvent.Resume -> state.running
        is DayEvent.StartDay -> start(event.template)
        DayEvent.TakeOffDay -> DayState.Off
        is DayEvent.EndDay -> DayState.Ended
        else -> null
    }

    private fun fromKidTime(event: DayEvent): DayState? = when (event) {
        DayEvent.KidAsleep -> DayState.Evening(blockIndex = 0)
        is DayEvent.StartDay -> start(event.template)
        DayEvent.TakeOffDay -> DayState.Off
        is DayEvent.EndDay -> DayState.Ended
        else -> null
    }

    private fun fromEvening(state: DayState.Evening, event: DayEvent): DayState? = when (event) {
        is DayEvent.EveningChoice -> if (state.blockIndex == 0) DayState.Evening(1) else null
        is DayEvent.Next -> if (state.blockIndex == 0) DayState.Evening(1) else null
        is DayEvent.StartDay -> start(event.template)
        DayEvent.TakeOffDay -> DayState.Off
        is DayEvent.EndDay -> DayState.Ended
        else -> null
    }

    private fun start(template: TemplateId): DayState =
        if (template == TemplateId.OFF) DayState.Off else DayState.Running(template, 0)
}
