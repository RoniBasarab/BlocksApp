package com.roni.blocks.core.time

import com.roni.blocks.core.daystate.DayEvent
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.daystate.isDishesSlot
import com.roni.blocks.core.logging.AppLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Moves the app's clock so a whole evening can be walked through in seconds. Rules: a jump catches
 * the day up to where the clock now is, applying and recording the transitions it skipped; it lives
 * in `src/main` because Home and Settings both show the shifted time, but every caller sits behind
 * `BuildConfig.DEBUG`.
 *
 * Talks to: [DebugClock] for the offset, [DayOrchestrator] to replay the day.
 */
@Singleton
class DebugTimeController @Inject constructor(
    private val clock: DebugClock,
    private val orchestrator: DayOrchestrator,
    private val logger: AppLogger,
    private val zone: TimeZone,
) {
    private val _offsetMinutes = MutableStateFlow(clock.offsetMinutes)

    /** Reactive because the shifted time is shown in two places that must repaint the moment it moves. */
    val offsetMinutes: StateFlow<Int> = _offsetMinutes.asStateFlow()

    /** Minutes to add to real time so the app clock reads [hhmm] on the current day. */
    fun offsetForTimeOfDay(hhmm: String): Int? {
        val time = runCatching { LocalTime.parse(hhmm) }.getOrNull() ?: return null
        val today = clock.now().toLocalDateTime(zone).date
        return (time.at(today, zone) - Clock.System.now()).inWholeMinutes.toInt()
    }

    /** False when [hhmm] is not a time the day can be moved to. */
    suspend fun setTo(hhmm: String): Boolean {
        val target = offsetForTimeOfDay(hhmm)
        if (target == null) {
            logger.info("DebugTimeController: unreadable time $hhmm")
            return false
        }
        shiftTo(target)
        return true
    }

    suspend fun shiftBy(minutes: Int) = shiftTo(_offsetMinutes.value + minutes)

    suspend fun useRealTime() = shiftTo(0)

    suspend fun sendKidAsleep() {
        orchestrator.handle(DayEvent.KidAsleep)
    }

    /**
     * Dishes last [DISHES_MINUTES], so skipping them is the same as jumping past that window.
     * Refused outside the dishes slot, where it would move the clock for no reason.
     */
    suspend fun skipDishes(): Boolean {
        if (!orchestrator.state.value.isDishesSlot) return false
        shiftBy(DISHES_MINUTES)
        return true
    }

    private suspend fun shiftTo(target: Int) {
        clock.setOffsetMinutes(target)
        _offsetMinutes.value = target
        logger.info("DebugTimeController: offset $target min")
        orchestrator.onTimeTravelled()
    }

    companion object {
        const val DISHES_MINUTES = 20
    }
}