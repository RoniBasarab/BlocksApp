package com.roni.blocks.core.daystate

import com.roni.blocks.core.model.ResumeMode
import com.roni.blocks.core.notifications.ActionEvent

/**
 * How a notification button maps onto a day event. Kept here, next to the state machine, so the
 * two vocabularies cannot drift apart: a new [ActionEvent] without a mapping fails to compile.
 */
internal fun ActionEvent.toDayEvent(): DayEvent = when (this) {
    ActionEvent.PAUSE -> DayEvent.Pause
    ActionEvent.RESUME -> DayEvent.Resume(ResumeMode.RESUME_WHERE_I_WAS)
    ActionEvent.START_NEXT -> DayEvent.Next
    ActionEvent.CONTINUE_WHERE_I_WAS -> DayEvent.Resume(ResumeMode.RESUME_WHERE_I_WAS)
    ActionEvent.JUMP_TO_NOW -> DayEvent.Resume(ResumeMode.JUMP_TO_NOW)
    ActionEvent.KID_ASLEEP -> DayEvent.KidAsleep
    ActionEvent.END_DAY -> DayEvent.EndDay
}
