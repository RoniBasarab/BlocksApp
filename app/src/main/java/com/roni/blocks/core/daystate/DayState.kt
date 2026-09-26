package com.roni.blocks.core.daystate

import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.InstantSerializer
import com.roni.blocks.core.model.ResumeMode
import com.roni.blocks.core.model.TemplateId
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
sealed interface DayState {
    @Serializable
    data object NotStarted : DayState

    @Serializable
    data class Running(
        val template: TemplateId,
        val blockIndex: Int,
    ) : DayState

    @Serializable
    data class Paused(
        val running: Running,
        @Serializable(with = InstantSerializer::class) val since: Instant,
    ) : DayState

    @Serializable
    data class Interrupted(
        val running: Running,
        @Serializable(with = InstantSerializer::class) val since: Instant,
    ) : DayState

    @Serializable
    data object KidTime : DayState

    @Serializable
    data class Evening(
        val blockIndex: Int,
    ) : DayState

    @Serializable
    data object Ended : DayState

    @Serializable
    data object Off : DayState
}

@Serializable
sealed interface DayEvent {
    @Serializable
    data class StartDay(val template: TemplateId) : DayEvent

    @Serializable
    data object TakeOffDay : DayEvent

    @Serializable
    data object Next : DayEvent

    @Serializable
    data object EndBlockEarly : DayEvent

    @Serializable
    data object Pause : DayEvent

    @Serializable
    data object Interrupt : DayEvent

    @Serializable
    data class Resume(val mode: ResumeMode) : DayEvent

    @Serializable
    data object KidTimeStarted : DayEvent

    @Serializable
    data object KidAsleep : DayEvent

    @Serializable
    data class EveningChoice(val choice: Choice) : DayEvent

    @Serializable
    data object EndDay : DayEvent
}

fun DayState.isActive(): Boolean = when (this) {
    is DayState.Running,
    is DayState.Paused,
    is DayState.Interrupted,
    DayState.KidTime,
    is DayState.Evening,
    -> true
    DayState.NotStarted, DayState.Ended, DayState.Off -> false
}

fun DayState.templateId(): TemplateId? = when (this) {
    is DayState.Running -> template
    is DayState.Paused -> running.template
    is DayState.Interrupted -> running.template
    DayState.Off -> TemplateId.OFF
    else -> null
}

fun rollover(state: DayState, storedDate: LocalDate, today: LocalDate): DayState =
    if (storedDate != today) DayState.NotStarted else state
