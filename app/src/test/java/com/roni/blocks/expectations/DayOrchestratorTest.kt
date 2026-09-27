package com.roni.blocks.expectations

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.alarms.AlarmScheduler
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.daystate.DayEvent
import com.roni.blocks.core.daystate.EndReason
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.DayFlag
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
import com.roni.blocks.core.time.TimeFormatter
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
    private lateinit var history: HistoryManager
    private val rendered: java.util.concurrent.CopyOnWriteArrayList<NotificationModel?> =
        java.util.concurrent.CopyOnWriteArrayList()

    @Before
    fun setUp() {
        clock = MovableClock(LocalTime(9, 0).at(date, zone))
        val prefs = PrefsStorage(context)
        val logger = AppLogger(context)
        val engine = ScheduleEngine(clock, zone)
        history = HistoryManager(prefs)
        rendered.clear()
        orchestrator = DayOrchestrator(
            prefs = prefs,
            engine = engine,
            templates = TemplateManager(context),
            history = history,
            alarms = AlarmScheduler(context, SystemTimeMapper.Identity, logger),
            notificationMapper = NotificationModelMapper(engine, zone, TimeFormatter(prefs, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))),
            notificationRenderer = RecordingNotificationPort(rendered),
            clock = clock,
            zone = zone,
            logger = logger,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
    }

    private val firstBlockId = "sun_tue_wed.cv"

    @Test
    fun staleAction_isIgnored() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 0), orchestrator.state.value)

        orchestrator.handleNotificationAction(ActionEvent.START_NEXT, firstBlockId)
        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 1), orchestrator.state.value)

        // The same button is tapped again after the day already moved past that block.
        orchestrator.handleNotificationAction(ActionEvent.START_NEXT, firstBlockId)
        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 1), orchestrator.state.value)
    }

    @Test
    fun doubleTap_nextSlot_advancesOnce() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        orchestrator.handleNotificationAction(ActionEvent.START_NEXT, firstBlockId)
        orchestrator.handleNotificationAction(ActionEvent.START_NEXT, firstBlockId)
        assertEquals(1, (orchestrator.state.value as DayState.Running).blockIndex)
        assertEquals(
            "the second tap must not open anything else",
            1,
            orchestrator.segments.value.count { it.actualEnd == null },
        )
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
        orchestrator.handleNotificationAction(ActionEvent.START_NEXT, firstBlockId)

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
    fun emergency_endsDayFlagsItAndClearsTheNotification() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        rendered.clear()

        orchestrator.handle(DayEvent.EndDay(EndReason.EMERGENCY))

        assertEquals(DayState.Ended, orchestrator.state.value)
        assertEquals(setOf(DayFlag.EMERGENCY_STOP), requireNotNull(history.readDay(date)).flags)
        assertEquals(null, rendered.last())
    }

    @Test
    fun normalEndDay_isNotFlaggedAsEmergency() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))

        orchestrator.handle(DayEvent.EndDay(EndReason.NORMAL))

        assertEquals(DayState.Ended, orchestrator.state.value)
        assertTrue(
            "a normal end must not claim an emergency stop",
            DayFlag.EMERGENCY_STOP !in requireNotNull(history.readDay(date)).flags,
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

        override fun sendTestNotification() = Unit
    }

    private class RecordingNotificationPort(
        private val sink: java.util.concurrent.CopyOnWriteArrayList<NotificationModel?>,
    ) : NotificationPort {
        override fun ensureChannels() = Unit

        override fun render(model: NotificationModel?, transition: Boolean) {
            sink += model
        }

        override fun onDismissed(key: String) = Unit

        override fun sendTestNotification() = Unit
    }
}
