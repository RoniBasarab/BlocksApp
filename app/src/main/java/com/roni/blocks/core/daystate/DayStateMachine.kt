package com.roni.blocks.core.daystate

import com.roni.blocks.core.model.TemplateId
import kotlin.time.Instant

object DayStateMachine {
    fun transition(state: DayState, event: DayEvent, now: Instant): DayState? = when (state) {
        DayState.NotStarted -> fromNotStarted(event)
        is DayState.Running -> fromRunning(state, event, now)
        is DayState.Paused -> fromPaused(state, event)
        is DayState.Interrupted -> fromInterrupted(state, event)
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
        DayEvent.Next -> state.copy(blockIndex = state.blockIndex + 1)
        DayEvent.EndBlockEarly -> state
        DayEvent.Pause -> DayState.Paused(state, now)
        DayEvent.Interrupt -> DayState.Interrupted(state, now)
        DayEvent.KidTimeStarted -> DayState.KidTime
        DayEvent.EndDay -> DayState.Ended
        else -> null
    }

    private fun fromPaused(state: DayState.Paused, event: DayEvent): DayState? = when (event) {
        is DayEvent.Resume -> state.running
        is DayEvent.StartDay -> start(event.template)
        DayEvent.TakeOffDay -> DayState.Off
        DayEvent.EndDay -> DayState.Ended
        else -> null
    }

    private fun fromInterrupted(state: DayState.Interrupted, event: DayEvent): DayState? = when (event) {
        is DayEvent.Resume -> state.running
        is DayEvent.StartDay -> start(event.template)
        DayEvent.TakeOffDay -> DayState.Off
        DayEvent.EndDay -> DayState.Ended
        else -> null
    }

    private fun fromKidTime(event: DayEvent): DayState? = when (event) {
        DayEvent.KidAsleep -> DayState.Evening(blockIndex = 0)
        is DayEvent.StartDay -> start(event.template)
        DayEvent.TakeOffDay -> DayState.Off
        DayEvent.EndDay -> DayState.Ended
        else -> null
    }

    private fun fromEvening(state: DayState.Evening, event: DayEvent): DayState? = when (event) {
        is DayEvent.EveningChoice -> if (state.blockIndex == 0) DayState.Evening(1) else null
        DayEvent.Next -> if (state.blockIndex == 0) DayState.Evening(1) else null
        is DayEvent.StartDay -> start(event.template)
        DayEvent.TakeOffDay -> DayState.Off
        DayEvent.EndDay -> DayState.Ended
        else -> null
    }

    private fun start(template: TemplateId): DayState =
        if (template == TemplateId.OFF) DayState.Off else DayState.Running(template, 0)
}
