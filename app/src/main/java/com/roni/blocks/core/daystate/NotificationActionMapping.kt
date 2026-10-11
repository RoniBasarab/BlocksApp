package com.roni.blocks.core.daystate

import com.roni.blocks.core.notifications.ActionEvent

/**
 * How a notification button maps onto a day event. Kept here, next to the state machine, so the
 * two vocabularies cannot drift apart: a new [ActionEvent] without a mapping fails to compile.
 *
 * [expectedBlockId] is the block the notification was posted for. Only actions that advance the
 * day need it; an empty id matches no real block, so a Start-next that lost its id is ignored
 * rather than blindly skipping forward.
 */
internal fun ActionEvent.toDayEvent(expectedBlockId: String?): DayEvent = when (this) {
    ActionEvent.PAUSE -> DayEvent.Pause
    ActionEvent.RESUME -> DayEvent.Resume
    ActionEvent.START_NEXT -> DayEvent.Next(expectedBlockId.orEmpty())
    ActionEvent.KID_ASLEEP -> DayEvent.KidAsleep
    ActionEvent.END_DAY -> DayEvent.EndDay(EndReason.NORMAL)
    // Never reached: the orchestrator handles TURN_BACK itself, before this mapping.
    ActionEvent.TURN_BACK -> DayEvent.Next(expectedBlockId.orEmpty())
}
