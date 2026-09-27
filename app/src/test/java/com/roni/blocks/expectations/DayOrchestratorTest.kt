package com.roni.blocks.expectations

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.alarms.AlarmScheduler
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.daystate.DayEvent
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.notifications.ActionEvent
import com.roni.blocks.core.notifications.NotificationModel
import com.roni.blocks.core.notifications.NotificationModelMapper
import com.roni.blocks.core.notifications.NotificationPort
import com.roni.blocks.core.schedule.ScheduleEngine
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.core.time.at
import com.roni.blocks.core.time.SystemTimeMapper
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The orchestrator owns the rules a notification button cannot enforce on its own: a tap that
 * belongs to a block the day has already left must do nothing, and a tap must not count twice.
 *
 * Real storage and a real engine run here; only the notification port is faked, because what is
 * posted is covered by [NotificationRendererTest] and [NotificationModelMapperTest].
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DayOrchestratorTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val zone = TimeZone.of("Asia/Jerusalem")

    /** Sunday 2026-09-27. */
    private val date = LocalDate(2026, 9, 27)

    private lateinit var clock: MovableClock
    private lateinit var orchestrator: DayOrchestrator

    @Before
    fun setUp() {
        clock = MovableClock(LocalTime(9, 0).at(date, zone))
        val prefs = PrefsStorage(context)
        val logger = AppLogger(context)
        val engine = ScheduleEngine(clock, zone)
        orchestrator = DayOrchestrator(
            prefs = prefs,
            engine = engine,
            templates = TemplateManager(context),
            history = HistoryManager(prefs),
            alarms = AlarmScheduler(context, SystemTimeMapper.Identity, logger),
            notificationMapper = NotificationModelMapper(engine, zone),
            notificationRenderer = SilentNotificationPort,
            clock = clock,
            zone = zone,
            logger = logger,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
    }

    @Test
    fun staleAction_isIgnored() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 0), orchestrator.state.value)

        orchestrator.handleNotificationAction(ActionEvent.START_NEXT, "b0")
        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 1), orchestrator.state.value)

        // The same button is tapped again after the day already moved past b0.
        orchestrator.handleNotificationAction(ActionEvent.START_NEXT, "b0")
        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 1), orchestrator.state.value)
    }

    @Test
    fun doubleTap_advancesOnce() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        orchestrator.handleNotificationAction(ActionEvent.START_NEXT, "b0")
        orchestrator.handleNotificationAction(ActionEvent.START_NEXT, "b0")
        assertEquals(1, (orchestrator.state.value as DayState.Running).blockIndex)
    }

    @Test
    fun aTapWithoutABlockIsNeverStale() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        orchestrator.handle(DayEvent.KidTimeStarted)
        orchestrator.handleNotificationAction(ActionEvent.KID_ASLEEP, null)
        assertEquals(DayState.Evening(0), orchestrator.state.value)
    }

    @Test
    fun startNext_whenClockPassedNextBlock_landsOnBlockContainingNow() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        // 12:00 sits inside the third block (free, 11:30-14:00), so the second block is already
        // in the past when the button is pressed.
        clock.now = LocalTime(12, 0).at(date, zone)
        orchestrator.handleNotificationAction(ActionEvent.START_NEXT, "b0")

        val open = orchestrator.segments.value.last { it.actualEnd == null }
        assertEquals(SegmentKind.PLANNED, open.kind)
        assertEquals(LocalTime(11, 30).at(date, zone), requireNotNull(open.plannedStart))
        assertEquals(LocalTime(14, 0).at(date, zone), requireNotNull(open.plannedEnd))
        assertTrue(
            "the skipped block should be recorded as consumed",
            orchestrator.segments.value.any { it.kind == SegmentKind.CONSUMED && it.type == com.roni.blocks.core.model.BlockType.Coding },
        )
    }

    @Test
    fun timeTravel_isNotAnInterruption() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        val before: List<Segment> = orchestrator.segments.value

        clock.now = LocalTime(10, 0).at(date, zone)
        orchestrator.onTimeChanged()

        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 0), orchestrator.state.value)
        val after = orchestrator.segments.value
        assertTrue(
            "moving the clock must not create an interruption",
            after.none { it.kind == SegmentKind.INTERRUPTION },
        )
        assertTrue(
            "the same open block should still be open",
            after.count { it.actualEnd == null } == before.count { it.actualEnd == null },
        )
    }

    private class MovableClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private object SilentNotificationPort : NotificationPort {
        override fun ensureChannels() = Unit

        override fun render(model: NotificationModel?, transition: Boolean) = Unit

        override fun onDismissed(key: String) = Unit
    }
}
