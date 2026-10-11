package com.roni.blocks.expectations

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.alarms.AlarmScheduler
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.daystate.DayEvent
import com.roni.blocks.core.daystate.DayStateSerializer
import com.roni.blocks.core.daystate.EndReason
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.InstantSerializer
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.notifications.ActionEvent
import com.roni.blocks.core.notifications.NotificationModel
import com.roni.blocks.core.notifications.NotificationModelMapper
import com.roni.blocks.core.notifications.NotificationPort
import com.roni.blocks.core.schedule.Edge
import com.roni.blocks.core.schedule.PlanEdit
import com.roni.blocks.core.schedule.ScheduleEngine
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.core.storage.debtMinutes
import com.roni.blocks.core.time.at
import com.roni.blocks.core.time.SystemTimeMapper
import com.roni.blocks.core.time.TimeFormatter
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
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
    private lateinit var prefs: PrefsStorage
    private lateinit var engine: ScheduleEngine
    private lateinit var logger: AppLogger
    private val rendered: java.util.concurrent.CopyOnWriteArrayList<NotificationModel?> =
        java.util.concurrent.CopyOnWriteArrayList()
    private val transitions: java.util.concurrent.CopyOnWriteArrayList<Boolean> =
        java.util.concurrent.CopyOnWriteArrayList()
    private val alerts: java.util.concurrent.CopyOnWriteArrayList<Boolean> =
        java.util.concurrent.CopyOnWriteArrayList()

    @Before
    fun setUp() {
        clock = MovableClock(LocalTime(9, 0).at(date, zone))
        prefs = PrefsStorage(context, UnconfinedTestDispatcher())
        logger = AppLogger(context)
        engine = ScheduleEngine(clock, zone)
        history = HistoryManager(prefs)
        rendered.clear()
        transitions.clear()
        orchestrator = newOrchestrator()
    }

    private fun newOrchestrator(): DayOrchestrator = DayOrchestrator(
        prefs = prefs,
        engine = engine,
        templates = TemplateManager(context),
        history = history,
        alarms = AlarmScheduler(context, SystemTimeMapper.Identity, logger),
        notificationMapper = NotificationModelMapper(engine, zone, TimeFormatter(prefs, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))),
        notificationRenderer = RecordingNotificationPort(rendered, transitions, alerts),
        clock = clock,
        zone = zone,
        logger = logger,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

    private val firstBlockId = "sun_tue_wed.cv"

    @Test
    fun staleAction_isIgnored() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 0), orchestrator.state.value)

        // Finishing CV early turns the rest of its slot into free; the day stays in the CV slot.
        orchestrator.handleNotificationAction(ActionEvent.START_NEXT, firstBlockId)
        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 0), orchestrator.state.value)

        // A second tap leaves the CV slot and moves on to the next block.
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

    @Test
    fun timeSet_jumpForward_replaysMissedTransitions() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        clock.now = LocalTime(9, 10).at(date, zone)
        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 0), orchestrator.state.value)

        clock.now = LocalTime(16, 30).at(date, zone)
        orchestrator.onTimeTravelled()

        assertEquals(DayState.KidTime, orchestrator.state.value)
    }

    @Test
    fun swapAfterEmergency_restartsFromNow_keepsEarlierSegments() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        clock.now = LocalTime(10, 0).at(date, zone)
        orchestrator.handle(DayEvent.EndDay(EndReason.EMERGENCY))
        assertEquals(DayState.Ended, orchestrator.state.value)
        val before: List<Segment> = orchestrator.segments.value

        clock.now = LocalTime(12, 10).at(date, zone)
        orchestrator.handle(DayEvent.SwapTemplate(TemplateId.MON_THU))

        assertEquals(DayState.Running(TemplateId.MON_THU, 2), orchestrator.state.value)
        val segments = orchestrator.segments.value
        assertEquals("segments before the swap must stay exactly as recorded", before, segments.take(before.size))
        assertEquals(2, segments.size)
        assertTrue("skipped new-template blocks must not be marked consumed", segments.none { it.kind == SegmentKind.CONSUMED })
        assertTrue("mon_thu.coding_am must leave no segment", segments.none { it.type == BlockType.Coding })
        assertTrue("mon_thu.free_pre_gym (11:30-12:00) must leave no segment", segments.none { it.plannedStart == LocalTime(11, 30).at(date, zone) })
        val open = segments.last { it.actualEnd == null }
        assertEquals(BlockType.Gym, open.type)
        assertEquals("the new day starts the gym block right away", LocalTime(12, 10).at(date, zone), open.actualStart)
        assertEquals("gym still ends at its planned 14:00", LocalTime(14, 0).at(date, zone), requireNotNull(open.plannedEnd))

        clock.now = LocalTime(14, 30).at(date, zone)
        orchestrator.handle(DayEvent.EndDay(EndReason.NORMAL))
        val flags = requireNotNull(history.readDay(date)).flags
        assertTrue("an emergency stop stays part of the day after a swap", DayFlag.EMERGENCY_STOP in flags)
        assertTrue("a swap is flagged on the final day record", DayFlag.TEMPLATE_SWAPPED in flags)
    }

    @Test
    fun swapMidDay_replansFromNow() = runBlocking {
        clock.now = LocalTime(10, 0).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        val running = orchestrator.segments.value.last { it.actualEnd == null }
        assertEquals(BlockType.Coding, running.type)
        assertEquals(LocalTime(9, 30).at(date, zone), requireNotNull(running.plannedStart))

        clock.now = LocalTime(10, 15).at(date, zone)
        orchestrator.handle(DayEvent.SwapTemplate(TemplateId.FRI_SAT))

        assertEquals(DayState.KidTime, orchestrator.state.value)
        val closedCoding = orchestrator.segments.value.first { it.type == BlockType.Coding }
        assertEquals("the swap closes the coding block at the swap time", LocalTime(10, 15).at(date, zone), closedCoding.actualEnd)
        val open = orchestrator.segments.value.last { it.actualEnd == null }
        assertEquals(BlockType.KidTime, open.type)
        assertEquals("fri_sat.kid_time_am starts running from the swap time", LocalTime(10, 15).at(date, zone), open.actualStart)
        assertEquals("the swapped-in block starts from the swap time too", LocalTime(10, 15).at(date, zone), requireNotNull(open.plannedStart))
        assertEquals(LocalTime(13, 0).at(date, zone), requireNotNull(open.plannedEnd))
    }

    @Test
    fun swapAfter2230_isRefused() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        clock.now = LocalTime(22, 30).at(date, zone)
        orchestrator.handle(DayEvent.EndDay(EndReason.NORMAL))
        assertEquals(DayState.Ended, orchestrator.state.value)
        val before: List<Segment> = orchestrator.segments.value

        clock.now = LocalTime(22, 40).at(date, zone)
        orchestrator.handle(DayEvent.SwapTemplate(TemplateId.MON_THU))

        assertEquals(DayState.Ended, orchestrator.state.value)
        assertEquals("a refused swap must leave the day untouched", before, orchestrator.segments.value)
        assertTrue(DayFlag.TEMPLATE_SWAPPED !in requireNotNull(history.readDay(date)).flags)
    }

    private class MovableClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private object SilentNotificationPort : NotificationPort {
        override fun ensureChannels() = Unit

        override fun render(model: NotificationModel?, transition: Boolean, alert: Boolean) = Unit

        override fun onDismissed(key: String) = Unit

        override fun sendTestNotification() = Unit
    }

    private class RecordingNotificationPort(
        private val sink: java.util.concurrent.CopyOnWriteArrayList<NotificationModel?>,
        private val transitionSink: java.util.concurrent.CopyOnWriteArrayList<Boolean>,
        private val alertSink: java.util.concurrent.CopyOnWriteArrayList<Boolean>,
    ) : NotificationPort {
        override fun ensureChannels() = Unit

        override fun render(model: NotificationModel?, transition: Boolean, alert: Boolean) {
            sink += model
            transitionSink += transition
            alertSink += alert
        }

        override fun onDismissed(key: String) = Unit

        override fun sendTestNotification() = Unit
    }

    @Test
    fun startDay_late_landsOnTheCurrentBlock() = runBlocking {
        clock.now = LocalTime(10, 40).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        val state = orchestrator.state.value
        assertTrue(state is DayState.Running)
        assertEquals(1, (state as DayState.Running).blockIndex)
        val consumedCv = orchestrator.segments.value.find { it.kind == SegmentKind.CONSUMED && it.type == com.roni.blocks.core.model.BlockType.Cv }
        assertNotNull(consumedCv)
        assertEquals(com.roni.blocks.core.model.MissReason.LATE_START, consumedCv!!.missReason)
        assertEquals("sun_tue_wed.cv", consumedCv.blockId)
    }

    @Test
    fun startDay_inKidTime_isKidTime() = runBlocking {
        clock.now = LocalTime(16, 30).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        assertEquals(DayState.KidTime, orchestrator.state.value)
    }

    @Test
    fun startDay_after2230_isRefused() = runBlocking {
        clock.now = LocalTime(22, 40).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        assertEquals(DayState.NotStarted, orchestrator.state.value)
    }

    @Test
    fun todayPlan_startsAsTheTemplate() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))

        val plan = requireNotNull(orchestrator.todayPlan.value)
        val template = TemplateManager(context).get(TemplateId.SUN_TUE_WED)
        assertEquals(TemplateId.SUN_TUE_WED, plan.templateId)
        assertEquals(template.blocks.map { it.id }, plan.blocks.map { it.id })
        assertEquals(false, plan.edited)
    }

    @Test
    fun todayPlan_swapKeepsThePast_andTakesTheNewTemplateFromNow() = runBlocking {
        clock.now = LocalTime(9, 0).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        clock.now = LocalTime(9, 30).at(date, zone)
        orchestrator.handle(DayEvent.Next("sun_tue_wed.cv"))
        clock.now = LocalTime(11, 30).at(date, zone)
        orchestrator.handle(DayEvent.Next("sun_tue_wed.coding_am"))

        clock.now = LocalTime(12, 10).at(date, zone)
        orchestrator.handle(DayEvent.SwapTemplate(TemplateId.MON_THU))

        val plan = requireNotNull(orchestrator.todayPlan.value)
        assertEquals(TemplateId.MON_THU, plan.templateId)
        assertEquals(
            listOf(
                "sun_tue_wed.cv",
                "sun_tue_wed.coding_am",
                "sun_tue_wed.free_midday",
                "mon_thu.gym",
                "mon_thu.free_post_gym",
                "mon_thu.coding_pm",
                "mon_thu.free_pm",
                "mon_thu.kid_time",
            ),
            plan.blocks.map { it.id },
        )
        assertEquals("the last kept block is cut at the swap time", LocalTime(12, 10), plan.blocks[2].end)
        assertEquals("the block containing now restarts at the swap time", LocalTime(12, 10), plan.blocks[3].start)
        assertEquals(DayState.Running(TemplateId.MON_THU, 3), orchestrator.state.value)
    }

    @Test
    fun todayPlan_swapToTheSameTemplate_keepsIdsUnique() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        clock.now = LocalTime(9, 30).at(date, zone)
        orchestrator.handle(DayEvent.Next("sun_tue_wed.cv"))
        clock.now = LocalTime(10, 0).at(date, zone)
        orchestrator.handle(DayEvent.EndDay(EndReason.EMERGENCY))

        clock.now = LocalTime(10, 30).at(date, zone)
        orchestrator.handle(DayEvent.SwapTemplate(TemplateId.SUN_TUE_WED))

        val plan = requireNotNull(orchestrator.todayPlan.value)
        assertEquals(
            listOf(
                "sun_tue_wed.cv",
                "sun_tue_wed.coding_am",
                "sun_tue_wed.coding_am@630",
                "sun_tue_wed.free_midday",
                "sun_tue_wed.coding_pm",
                "sun_tue_wed.free_pm",
                "sun_tue_wed.kid_time",
            ),
            plan.blocks.map { it.id },
        )
        assertEquals("no block id may appear twice", plan.blocks.size, plan.blocks.map { it.id }.distinct().size)
        assertEquals(LocalTime(10, 30), plan.blocks[2].start)
        assertEquals(LocalTime(11, 30), plan.blocks[2].end)
        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 2), orchestrator.state.value)
    }

    @Test
    fun todayPlan_migratesAnOldSave() = runBlocking {
        clock.now = LocalTime(9, 0).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        clock.now = LocalTime(12, 10).at(date, zone)
        orchestrator.handle(DayEvent.SwapTemplate(TemplateId.MON_THU))
        prefs.remove(PrefsFile.DAY, "today_plan")
        prefs.write(PrefsFile.DAY, "plan_starts_at", LocalTime(12, 10).at(date, zone), InstantSerializer)
        prefs.write(PrefsFile.DAY, "day_state", DayState.Running(TemplateId.MON_THU, 3), DayStateSerializer)

        val restored = newOrchestrator()
        restored.ensureRestored()

        val plan = requireNotNull(restored.todayPlan.value)
        assertEquals(TemplateId.MON_THU, plan.templateId)
        val state = restored.state.value as DayState.Running
        assertEquals("the day must still be on the gym block", "mon_thu.gym", plan.blocks[state.blockIndex].id)
        assertTrue("plan_starts_at must be gone", "plan_starts_at" !in prefs.keys(PrefsFile.DAY))
    }

    @Test
    fun editPlan_keepsTheCurrentBlockRunning_andMovesTheAlarm() = runBlocking {
        clock.now = LocalTime(12, 30).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.MON_THU))
        assertEquals(DayState.Running(TemplateId.MON_THU, 3), orchestrator.state.value)

        val edited = orchestrator.editPlan(PlanEdit.Resize("mon_thu.gym", Edge.BOTTOM, 14 * 60 + 30))

        assertTrue("the edit must be allowed", edited)
        assertEquals(DayState.Running(TemplateId.MON_THU, 3), orchestrator.state.value)
        val plan = requireNotNull(orchestrator.todayPlan.value)
        assertTrue("the plan must be marked edited", plan.edited)
        assertEquals(LocalTime(14, 30), plan.blocks.first { it.id == "mon_thu.gym" }.end)
        assertTrue(
            "the alarm must move to the 5-minute warning before the edited end",
            LocalTime(14, 25).at(date, zone).toEpochMilliseconds() in scheduledAlarms(),
        )
    }

    @Test
    fun editPlan_ranOverIsMeasuredAgainstTheEditedEnd() = runBlocking {
        clock.now = LocalTime(12, 30).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.MON_THU))

        clock.now = LocalTime(14, 10).at(date, zone)
        assertTrue(orchestrator.editPlan(PlanEdit.Resize("mon_thu.gym", Edge.BOTTOM, 14 * 60 + 30)))

        clock.now = LocalTime(14, 25).at(date, zone)
        orchestrator.handle(DayEvent.Next("mon_thu.gym"))

        assertEquals(0, debtMinutes(orchestrator.segments.value))
    }

    @Test
    fun editPlan_survivesProcessDeath() = runBlocking {
        clock.now = LocalTime(12, 30).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.MON_THU))
        assertTrue(orchestrator.editPlan(PlanEdit.Resize("mon_thu.gym", Edge.BOTTOM, 14 * 60 + 30)))

        val restored = newOrchestrator()
        restored.ensureRestored()

        val plan = requireNotNull(restored.todayPlan.value)
        assertEquals(LocalTime(14, 30), plan.blocks.first { it.id == "mon_thu.gym" }.end)
        assertEquals(DayState.Running(TemplateId.MON_THU, 3), restored.state.value)
        assertEquals(
            LocalTime(14, 30).at(date, zone),
            requireNotNull(restored.segments.value.last { it.actualEnd == null }.plannedEnd),
        )
    }

    @Test
    fun editPlan_isSilent() = runBlocking {
        clock.now = LocalTime(12, 30).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.MON_THU))
        rendered.clear()
        transitions.clear()

        assertTrue(orchestrator.editPlan(PlanEdit.Resize("mon_thu.gym", Edge.BOTTOM, 14 * 60 + 30)))

        assertTrue("the edit must re-render the notification", rendered.isNotEmpty())
        assertEquals("the edit's re-render must be silent", false, transitions.last())
    }

    private fun scheduledAlarms(): List<Long> =
        shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.map { it.triggerAtTime }

    @Test
    fun alarm_fiveMinutesBeforeTheEnd_alerts() = runBlocking {
        // Coding 09:30–11:30, Pomodoro 50/10. At 11:25:10 a 5-minute warning should fire (11:30 - 5 min).
        clock.now = LocalTime(9, 40).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        awaitBlockIndex(1)

        alerts.clear()
        clock.now = LocalTime(11, 25, 10).at(date, zone)
        orchestrator.onAlarm()

        assertTrue("the 5-minute warning must alert", alerts.last())
    }

    @Test
    fun nags_stopOnceRoniReacts() = runBlocking {
        clock.now = LocalTime(9, 40).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        awaitBlockIndex(1)

        // 11:35:05 = 5 min after coding_am end (11:30) → first nag.
        clock.now = LocalTime(11, 35, 5).at(date, zone)
        orchestrator.onAlarm()
        assertTrue("first nag alerts", alerts.last())

        // 11:36:00 = Roni comes back, reactedAt is set.
        clock.now = LocalTime(11, 36).at(date, zone)
        orchestrator.onAppForeground()

        // 11:40:05 = another nag time, but Roni reacted → no alert, no 11:45 nag.
        clock.now = LocalTime(11, 40, 5).at(date, zone)
        alerts.clear()
        orchestrator.onAlarm()

        assertEquals("reachedAt suppressed the nag", false, alerts.last())
        val scheduled = scheduledAlarms()
        // The next alarm should be 20:00 (evening) or 22:30 (end of day), not 11:45.
        assertTrue(
            "no nag at 11:45 should be scheduled",
            scheduled.none { LocalTime(11, 45).at(date, zone).toEpochMilliseconds() == it },
        )
    }

    private suspend fun awaitBlockIndex(expected: Int) {
        kotlinx.coroutines.withTimeout(5_000) {
            orchestrator.state.first { (it is DayState.Running) && (it.blockIndex == expected) }
        }
    }
}