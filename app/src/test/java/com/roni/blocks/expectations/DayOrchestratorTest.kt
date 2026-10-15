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
import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.CategoryInfo
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.DriveLog
import com.roni.blocks.core.model.InstantSerializer
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.StoredTemplate
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.notifications.ActionEvent
import com.roni.blocks.core.notifications.NotificationModel
import com.roni.blocks.core.notifications.NotificationModelMapper
import com.roni.blocks.core.notifications.NotificationPort
import com.roni.blocks.core.notifications.WalkRinger
import com.roni.blocks.core.schedule.AlertKind
import com.roni.blocks.core.schedule.Edge
import com.roni.blocks.core.schedule.PlanEdit
import com.roni.blocks.core.schedule.ScheduleEngine
import com.roni.blocks.core.steps.StepSensor
import com.roni.blocks.core.storage.CategoryStore
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.storage.ProfileActions
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.core.storage.TemplateStore
import com.roni.blocks.core.storage.debtMinutes
import com.roni.blocks.core.walk.WalkFix
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import com.roni.blocks.testing.testFailures

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
    private lateinit var stepSensor: FakeStepSensor
    private val rendered: java.util.concurrent.CopyOnWriteArrayList<NotificationModel?> =
        java.util.concurrent.CopyOnWriteArrayList()
    private val transitions: java.util.concurrent.CopyOnWriteArrayList<Boolean> =
        java.util.concurrent.CopyOnWriteArrayList()
    private val alerts: java.util.concurrent.CopyOnWriteArrayList<Boolean> =
        java.util.concurrent.CopyOnWriteArrayList()
    private val ringerKinds: java.util.concurrent.CopyOnWriteArrayList<AlertKind> =
        java.util.concurrent.CopyOnWriteArrayList()

    @Before
    fun setUp() {
        clock = MovableClock(LocalTime(9, 0).at(date, zone))
        prefs = PrefsStorage(context, UnconfinedTestDispatcher(), testFailures(context))
        logger = AppLogger(context)
        engine = ScheduleEngine(clock, zone)
        history = HistoryManager(prefs)
        stepSensor = FakeStepSensor()
        rendered.clear()
        transitions.clear()
        ringerKinds.clear()
        orchestrator = newOrchestrator()
    }

    private fun newOrchestrator(
        walkRinger: WalkRinger = RecordingWalkRinger(ringerKinds),
        stepSensor: StepSensor = this.stepSensor,
        templates: TemplateManager = TemplateManager(context),
        templateStore: TemplateStore? = null,
        categories: CategoryStore? = null,
    ): DayOrchestrator = DayOrchestrator(
        prefs = prefs,
        engine = engine,
        templates = templates,
        history = history,
        alarms = AlarmScheduler(context, SystemTimeMapper.Identity, logger),
        notificationMapper = NotificationModelMapper(engine, zone, TimeFormatter(prefs, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))),
        notificationRenderer = RecordingNotificationPort(rendered, transitions, alerts),
        walkRinger = walkRinger,
        stepSensor = stepSensor,
        clock = clock,
        zone = zone,
        logger = logger,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        categories = categories,
        templateStore = templateStore,
    )

    private val firstBlockId = "sun_tue_wed.cv"

    @Test
    fun staleAction_isIgnored() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 0), orchestrator.state.value)

        // Finishing CV early turns the rest of its slot into free; the day stays in the CV slot.
        orchestrator.handleNotificationAction(ActionEvent.START_NEXT, firstBlockId)
        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 0), orchestrator.state.value)

        // A second tap leaves the CV slot: CV never actually ran (it was tapped in its first
        // minute), so it drops out of the plan and coding_am starts now as the first block.
        orchestrator.handleNotificationAction(ActionEvent.START_NEXT, firstBlockId)
        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 0), orchestrator.state.value)

        // The same button is tapped again after the day already moved past that block.
        orchestrator.handleNotificationAction(ActionEvent.START_NEXT, firstBlockId)
        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 0), orchestrator.state.value)
    }

    @Test
    fun doubleTap_nextSlot_advancesOnce() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        orchestrator.handleNotificationAction(ActionEvent.START_NEXT, firstBlockId)
        orchestrator.handleNotificationAction(ActionEvent.START_NEXT, firstBlockId)
        // CV drops out (it never ran), so coding_am is now the first plan block, started at 9:00.
        assertEquals(0, (orchestrator.state.value as DayState.Running).blockIndex)
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

    private class RecordingWalkRinger(
        private val sink: java.util.concurrent.CopyOnWriteArrayList<AlertKind>,
    ) : WalkRinger {
        override fun ring(kind: AlertKind, homeAt: Instant, nextLabel: String?, nextEnd: Instant?) {
            sink += kind
        }

        override fun stop() = Unit
    }

    /** A step counter whose single reading the test sets; empty when [next] is null (2.5.0). */
    private class FakeStepSensor(var next: Long? = null) : StepSensor {
        override suspend fun read(): Long? = next
        override fun live(): kotlinx.coroutines.flow.Flow<Long> = kotlinx.coroutines.flow.emptyFlow()
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
    fun startDay_beforeTheFirstBlock_waits() = runBlocking {
        clock.now = LocalTime(0, 30).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))

        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 0), orchestrator.state.value)
        assertTrue("the day must not open a block before its time", orchestrator.segments.value.isEmpty())
        assertEquals(LocalTime(9, 0).at(date, zone), orchestrator.startsAt.value)
        assertTrue(
            "the first block's start must be the next alarm",
            LocalTime(9, 0).at(date, zone).toEpochMilliseconds() in scheduledAlarms(),
        )
    }

    @Test
    fun firstBlock_startsByItselfAtItsTime() = runBlocking {
        clock.now = LocalTime(0, 30).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        transitions.clear()

        clock.now = LocalTime(9, 0, 5).at(date, zone)
        orchestrator.onAlarm()

        val open = orchestrator.segments.value.last { it.actualEnd == null }
        assertEquals(BlockType.Cv, open.type)
        assertEquals("sun_tue_wed.cv", open.blockId)
        assertEquals(LocalTime(9, 0).at(date, zone), requireNotNull(open.plannedStart))
        assertEquals("the day must start with the normal transition sound", true, transitions.last())
    }

    // 3.1.0: saving an own template can move today while the day still waits for its first block.

    private suspend fun ownOffice(store: TemplateStore): StoredTemplate {
        store.load()
        val mine = store.createIn(store.activeProfileId(), "Office day")
        val withCoding = mine.copy(
            blocks = listOf(
                BlockSpec("user_1.b1", BlockType.Coding, LocalTime(10, 0), LocalTime(12, 0)),
                mine.blocks.first(),
            ),
        )
        assertTrue(store.save(withCoding))
        return withCoding
    }

    @Test
    fun ownTemplateSaved_whileWaiting_todayFollows() = runBlocking {
        val store = TemplateStore(context, prefs, logger)
        val withCoding = ownOffice(store)
        orchestrator = newOrchestrator(templates = TemplateManager(store), templateStore = store)

        clock.now = LocalTime(7, 0).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(withCoding.id))
        assertEquals(DayState.Running(withCoding.id, 0), orchestrator.state.value)
        assertEquals(LocalTime(10, 0).at(date, zone), orchestrator.startsAt.value)

        store.save(
            withCoding.copy(
                blocks = listOf(
                    BlockSpec("user_1.b1", BlockType.Coding, LocalTime(8, 0), LocalTime(10, 0)),
                    withCoding.blocks.last(),
                ),
            ),
        )
        orchestrator.onTemplateSaved(withCoding.id)

        assertEquals(LocalTime(8, 0).at(date, zone), orchestrator.startsAt.value)
        assertEquals(LocalTime(8, 0), requireNotNull(orchestrator.todayPlan.value).blocks.first().start)
    }

    @Test
    fun ownTemplateSaved_afterTheDayStarted_todayKeepsItsPlan() = runBlocking {
        val store = TemplateStore(context, prefs, logger)
        val withCoding = ownOffice(store)
        orchestrator = newOrchestrator(templates = TemplateManager(store), templateStore = store)

        clock.now = LocalTime(7, 0).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(withCoding.id))
        clock.now = LocalTime(10, 30).at(date, zone) // the coding block is running
        orchestrator.onAlarm()

        store.save(
            withCoding.copy(
                blocks = listOf(
                    BlockSpec("user_1.b1", BlockType.Coding, LocalTime(8, 0), LocalTime(10, 0)),
                    withCoding.blocks.last(),
                ),
            ),
        )
        orchestrator.onTemplateSaved(withCoding.id)

        val plan = requireNotNull(orchestrator.todayPlan.value)
        assertEquals("the started day keeps its own plan", LocalTime(10, 0), plan.blocks.first().start)
    }

    @Test
    fun dayRecord_keepsTheProfileItStartedWith() = runBlocking {
        val store = TemplateStore(context, prefs, logger)
        store.load()
        val actions = ProfileActions(store)
        orchestrator = newOrchestrator(templates = TemplateManager(store), templateStore = store)

        clock.now = LocalTime(9, 5).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        val planBefore = requireNotNull(orchestrator.todayPlan.value)

        actions.create("Employed", copyActive = true)
        val employed = store.profiles.value.profiles.first { it.name == "Employed" }
        actions.switchTo(employed.id)

        assertEquals("today's plan never changes at the switch", planBefore, orchestrator.todayPlan.value)

        clock.now = LocalTime(20, 0).at(date, zone)
        orchestrator.handle(DayEvent.EndDay(EndReason.NORMAL))

        assertEquals("Unemployed", requireNotNull(history.readDay(date)).profileName)
    }

    @Test
    fun customCategoryRenamed_todaysPlanFollows() = runBlocking {
        val store = TemplateStore(context, prefs, logger)
        store.load()
        val mine = store.createIn(store.activeProfileId(), "Office day")
        val withCustom = mine.copy(
            blocks = listOf(
                BlockSpec("user_1.b1", BlockType.Custom("cat_1", "Office", 0xFFE08A7A, true, false), LocalTime(10, 0), LocalTime(12, 0)),
                mine.blocks.first(),
            ),
        )
        store.save(withCustom)
        orchestrator = newOrchestrator(templates = TemplateManager(store), templateStore = store)

        clock.now = LocalTime(7, 0).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(withCustom.id))

        orchestrator.onCategoryChanged("cat_1", CategoryInfo("cat_1", "Desk", 0xFF6FB7D6, true, true, builtIn = false))

        val type = requireNotNull(orchestrator.todayPlan.value).blocks.first().type as BlockType.Custom
        assertEquals("Desk", type.name)
        assertEquals(0xFF6FB7D6, type.colorArgb)
    }

    @Test
    fun startNow_movesTheFirstBlockToNow() = runBlocking {
        clock.now = LocalTime(7, 40).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 0), orchestrator.state.value)

        assertTrue(orchestrator.startNow())

        val open = orchestrator.segments.value.last { it.actualEnd == null }
        assertEquals(BlockType.Cv, open.type)
        assertEquals(LocalTime(7, 40).at(date, zone), open.actualStart)
        assertEquals(LocalTime(7, 40).at(date, zone), requireNotNull(open.plannedStart))
        assertEquals(LocalTime(8, 10).at(date, zone), requireNotNull(open.plannedEnd))

        val plan = requireNotNull(orchestrator.todayPlan.value)
        assertEquals(LocalTime(7, 40), plan.blocks[0].start)
        assertEquals(LocalTime(8, 10), plan.blocks[0].end)
        assertEquals("the gap the moved block leaves becomes free time", BlockType.Free, plan.blocks[1].type)
        assertEquals(LocalTime(8, 10), plan.blocks[1].start)
        assertEquals(LocalTime(9, 30), plan.blocks[1].end)
    }

    @Test
    fun friSat_startNow_startsKidTime() = runBlocking {
        clock.now = LocalTime(8, 15).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.FRI_SAT))
        assertEquals(DayState.Running(TemplateId.FRI_SAT, 0), orchestrator.state.value)
        assertEquals(LocalTime(9, 0).at(date, zone), orchestrator.startsAt.value)

        assertTrue(orchestrator.startNow())

        assertEquals(DayState.KidTime, orchestrator.state.value)
        val open = orchestrator.segments.value.last { it.actualEnd == null }
        assertEquals(BlockType.KidTime, open.type)
        assertEquals(LocalTime(8, 15).at(date, zone), open.actualStart)
    }

    @Test
    fun todayPlan_startsAsTheTemplate() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))

        val plan = requireNotNull(orchestrator.todayPlan.value)
        val template = requireNotNull(TemplateManager(context).find(TemplateId.SUN_TUE_WED))
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
    fun correctPast_fixesAnEarlierBlockWhileTheDayRuns() = runBlocking {
        // Sun/Tue/Wed, CV 9:00, Next at 9:35 → on coding_am; correct CV's end to 9:25.
        clock.now = LocalTime(9, 0).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        awaitBlockIndex(0)
        clock.now = LocalTime(9, 35).at(date, zone)
        orchestrator.handle(DayEvent.Next("sun_tue_wed.cv"))
        awaitBlockIndex(1)

        val cvIndex = orchestrator.segments.value.indexOfFirst {
            it.kind == SegmentKind.PLANNED && it.blockId == "sun_tue_wed.cv"
        }
        assertTrue(
            "correcting CV's end to 9:25 must be allowed",
            orchestrator.correctPast(com.roni.blocks.core.schedule.RecordEdit.SetEnd(cvIndex, 9 * 60 + 25)),
        )
        val updated = orchestrator.segments.value.first {
            it.kind == SegmentKind.PLANNED && it.blockId == "sun_tue_wed.cv"
        }
        assertEquals(LocalTime(9, 25).at(date, zone), updated.actualEnd)
        assertTrue(
            "the day must carry the CORRECTED flag after a fix",
            DayFlag.CORRECTED in orchestrator.liveRecord().flags,
        )
        assertTrue("the day must still be on coding_am", orchestrator.state.value is DayState.Running)
        assertEquals(1, (orchestrator.state.value as DayState.Running).blockIndex)
    }

    @Test
    fun correctPast_neverReachesTheRunningBlock() = runBlocking {
        clock.now = LocalTime(9, 0).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        awaitBlockIndex(0)
        clock.now = LocalTime(9, 35).at(date, zone)
        orchestrator.handle(DayEvent.Next("sun_tue_wed.cv"))
        awaitBlockIndex(1)

        val cvIndex = orchestrator.segments.value.indexOfFirst {
            it.kind == SegmentKind.PLANNED && it.blockId == "sun_tue_wed.cv"
        }
        // CV's open segment ends at 9:35 (the Next time). Correcting to 9:50 must be capped.
        assertTrue(
            "correcting past CV's actual end must be refused",
            !orchestrator.correctPast(com.roni.blocks.core.schedule.RecordEdit.SetEnd(cvIndex, 9 * 60 + 50)),
        )
        val cv = orchestrator.segments.value.first {
            it.kind == SegmentKind.PLANNED && it.blockId == "sun_tue_wed.cv"
        }
        assertTrue("CV ends at 9:35 at most", cv.actualEnd!! <= LocalTime(9, 35).at(date, zone))
    }

    @Test
    fun editPlan_retypesTheRunningBlock() = runBlocking {
        clock.now = LocalTime(10, 0).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        awaitBlockIndex(1)
        val codingId = (orchestrator.state.value as DayState.Running).let { "sun_tue_wed.coding_am" }

        val retype = PlanEdit.Retype(codingId, BlockType.Cv)
        assertTrue("retype to CV is allowed", orchestrator.editPlan(retype))

        val plan = requireNotNull(orchestrator.todayPlan.value)
        assertEquals(BlockType.Cv, plan.blocks.first { it.id == codingId }.type)
        val open = orchestrator.segments.value.lastOrNull { it.actualEnd == null }
        assertEquals(BlockType.Cv, open?.type)
        assertEquals(1, (orchestrator.state.value as DayState.Running).blockIndex)
    }

    @Test
    fun editPlan_doneEarly_movesTheFreeTimesEnd() = runBlocking {
        // CV 9:00–9:30, Next at 9:20 → on synthetic-free. Resize CV BOTTOM to 9:45.
        clock.now = LocalTime(9, 0).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        awaitBlockIndex(0)
        clock.now = LocalTime(9, 20).at(date, zone)
        orchestrator.handle(DayEvent.Next("sun_tue_wed.cv"))
        awaitBlockIndex(0)

        assertTrue(orchestrator.editPlan(PlanEdit.Resize("sun_tue_wed.cv", Edge.BOTTOM, 9 * 60 + 45)))

        val free = orchestrator.segments.value.lastOrNull { it.kind == SegmentKind.SYNTHETIC_FREE }
        assertEquals(LocalTime(9, 45).at(date, zone), free?.plannedEnd)

        val coding = requireNotNull(orchestrator.todayPlan.value).blocks.first { it.id == "sun_tue_wed.coding_am" }
        assertEquals(LocalTime(9, 45), coding.start)
        assertEquals(LocalTime(11, 45), coding.end)
    }

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

    @Test
    fun next_beforeTheNextBlock_startsItNowWithItsOwnLength() = runBlocking {
        clock.now = LocalTime(10, 0).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        awaitBlockIndex(1)

        clock.now = LocalTime(10, 1).at(date, zone)
        orchestrator.handle(DayEvent.Next("sun_tue_wed.coding_am"))

        clock.now = LocalTime(10, 2).at(date, zone)
        orchestrator.handle(DayEvent.Next("sun_tue_wed.coding_am"))

        // 2.6.0: Next no longer skips free time - it lands on the very next block, the midday free.
        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 2), orchestrator.state.value)
        val plan = requireNotNull(orchestrator.todayPlan.value)
        val freeMidday = plan.blocks.first { it.id == "sun_tue_wed.free_midday" }
        assertEquals(LocalTime(10, 2), freeMidday.start)
        assertEquals("free time starts sooner and ends where it did", LocalTime(14, 0), freeMidday.end)
        val codingAm = plan.blocks.first { it.id == "sun_tue_wed.coding_am" }
        assertEquals(LocalTime(10, 2), codingAm.end)
        val open = orchestrator.segments.value.last { it.actualEnd == null }
        assertEquals(BlockType.Free, open.type)
        assertEquals(LocalTime(14, 0).at(date, zone), requireNotNull(open.plannedEnd))
    }

    @Test
    fun next_intoKidTime_startsKidTime() = runBlocking {
        clock.now = LocalTime(10, 0).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        awaitBlockIndex(1)

        clock.now = LocalTime(10, 1).at(date, zone)
        orchestrator.handle(DayEvent.Next("sun_tue_wed.coding_am"))
        clock.now = LocalTime(10, 2).at(date, zone)
        orchestrator.handle(DayEvent.Next("sun_tue_wed.coding_am"))
        awaitBlockIndex(2)

        clock.now = LocalTime(15, 40).at(date, zone)
        // Next from the midday free block: the already-past coding_pm is skipped, free_pm opens; then Next from
        // free time is kid time.
        orchestrator.handle(DayEvent.Next("sun_tue_wed.free_midday"))
        orchestrator.handle(DayEvent.Next("sun_tue_wed.free_pm"))

        assertEquals(DayState.KidTime, orchestrator.state.value)
        val open = orchestrator.segments.value.last { it.actualEnd == null }
        assertEquals(BlockType.KidTime, open.type)
        assertEquals(LocalTime(15, 40).at(date, zone), requireNotNull(open.plannedStart))
        assertEquals(
            "kid time must be the only open segment",
            1,
            orchestrator.segments.value.count { it.actualEnd == null },
        )
    }

    @Test
    fun walk_onCoding_resumesCodingAfterwards() = runBlocking {
        clock.now = LocalTime(10, 0).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        awaitBlockIndex(1)

        assertTrue("the walk must start", orchestrator.startWalk(30))

        val running = orchestrator.state.value as DayState.Running
        val plan = requireNotNull(orchestrator.todayPlan.value)
        assertEquals("walk-600", plan.blocks[running.blockIndex].id)

        val open = orchestrator.segments.value.last { it.actualEnd == null }
        assertEquals(BlockType.Walk, open.type)
        assertEquals(SegmentKind.PLANNED, open.kind)
        assertEquals(LocalTime(10, 30).at(date, zone), requireNotNull(open.plannedEnd))

        val resumed = plan.blocks.first { it.id == "sun_tue_wed.coding_am@630" }
        assertEquals(LocalTime(10, 30), resumed.start)
        assertEquals(LocalTime(11, 30), resumed.end)
    }

    @Test
    fun walk_ringsAtHalfTimeAndAtTheEnd() = runBlocking {
        clock.now = LocalTime(10, 0).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        awaitBlockIndex(1)
        assertTrue(orchestrator.startWalk(30))
        ringerKinds.clear()

        clock.now = LocalTime(10, 15, 5).at(date, zone)
        orchestrator.onAlarm()
        assertEquals(listOf(AlertKind.TURN_BACK), ringerKinds.toList())

        clock.now = LocalTime(10, 30, 5).at(date, zone)
        orchestrator.onAlarm()
        assertEquals(listOf(AlertKind.TURN_BACK, AlertKind.WALK_DONE), ringerKinds.toList())

        val running = orchestrator.state.value as DayState.Running
        val plan = requireNotNull(orchestrator.todayPlan.value)
        assertEquals("sun_tue_wed.coding_am@630", plan.blocks[running.blockIndex].id)
    }

    @Test
    fun turnBack_bringsTheEndCloser() = runBlocking {
        clock.now = LocalTime(10, 0).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        awaitBlockIndex(1)
        assertTrue(orchestrator.startWalk(30))

        clock.now = LocalTime(10, 8).at(date, zone)
        assertTrue("turning back before half time must work", orchestrator.turnBack())

        val open = orchestrator.segments.value.last { it.actualEnd == null }
        assertEquals(LocalTime(10, 16).at(date, zone), requireNotNull(open.plannedEnd))
        val resumed = requireNotNull(orchestrator.todayPlan.value).blocks.first { it.id == "sun_tue_wed.coding_am@630" }
        assertEquals(LocalTime(10, 16), resumed.start)
        assertTrue(
            "the next alarm is the walk's new end",
            LocalTime(10, 16).at(date, zone).toEpochMilliseconds() in scheduledAlarms(),
        )
    }

    @Test
    fun walk_inTheEvening_leavesTheDayAlone() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        clock.now = LocalTime(20, 30).at(date, zone)
        orchestrator.handle(DayEvent.KidTimeStarted)
        orchestrator.handle(DayEvent.KidAsleep)
        clock.now = LocalTime(20, 50).at(date, zone)
        orchestrator.handle(DayEvent.EveningChoice(Choice.CHILL))
        // 3.3.0: Dishes runs over; the pick starts the evening only when "Done dishes" is tapped.
        assertTrue(orchestrator.skipDishes())
        val segmentsBefore = orchestrator.segments.value
        clock.now = LocalTime(21, 0).at(date, zone)

        assertNotNull(orchestrator.walkPreview(30))
        assertTrue(orchestrator.startWalk(30))

        assertEquals(DayState.Evening(1), orchestrator.state.value)
        assertEquals("a standalone walk must not touch the day", segmentsBefore, orchestrator.segments.value)
        assertNotNull(orchestrator.walkSession.value)
        ringerKinds.clear()

        clock.now = LocalTime(21, 15, 5).at(date, zone)
        orchestrator.onAlarm()
        assertEquals(listOf(AlertKind.TURN_BACK), ringerKinds.toList())

        clock.now = LocalTime(21, 30, 5).at(date, zone)
        orchestrator.onAlarm()
        assertEquals(listOf(AlertKind.TURN_BACK, AlertKind.WALK_DONE), ringerKinds.toList())
        assertEquals(null, orchestrator.walkSession.value)
        val walks = history.walks(date)
        assertEquals(1, walks.size)
        assertEquals(LocalTime(21, 0).at(date, zone), walks.first().start)
        assertEquals(LocalTime(21, 30).at(date, zone), walks.first().end)
    }

    @Test
    fun walk_afterMidnight_survivesTheRollover() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        orchestrator.handle(DayEvent.EndDay(EndReason.NORMAL))
        assertEquals(DayState.Ended, orchestrator.state.value)

        clock.now = LocalTime(23, 50).at(date, zone)
        assertNotNull(orchestrator.walkPreview(30))
        assertTrue(orchestrator.startWalk(30))
        ringerKinds.clear()

        val nextDay = date.plus(1, DateTimeUnit.DAY)
        clock.now = LocalTime(0, 5, 5).at(nextDay, zone)
        orchestrator.onAlarm()
        assertEquals(listOf(AlertKind.TURN_BACK), ringerKinds.toList())
        assertNotNull(orchestrator.walkSession.value)

        clock.now = LocalTime(0, 20, 5).at(nextDay, zone)
        orchestrator.onAlarm()
        assertEquals(listOf(AlertKind.TURN_BACK, AlertKind.WALK_DONE), ringerKinds.toList())
        assertEquals(null, orchestrator.walkSession.value)
        val walks = history.walks(date)
        assertEquals(1, walks.size)
        assertEquals(LocalTime(23, 50).at(date, zone), walks.first().start)
        assertEquals(LocalTime(0, 20).at(nextDay, zone), walks.first().end)
    }

    @Test
    fun walk_cantStartDuringAPause() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        orchestrator.handle(DayEvent.Pause)

        assertEquals(null, orchestrator.walkPreview(30))
    }

    @Test
    fun standaloneWalk_turnBackAfterFiveMinutes_isHomeFiveMinutesLater() = runBlocking {
        clock.now = LocalTime(21, 0, 0).at(date, zone)
        assertTrue(orchestrator.startWalk(30))

        clock.now = LocalTime(21, 5, 0).at(date, zone)
        assertTrue(orchestrator.turnBack())
        assertEquals(LocalTime(21, 10).at(date, zone), requireNotNull(orchestrator.walkSession.value).end)

        ringerKinds.clear()
        clock.now = LocalTime(21, 10, 5).at(date, zone)
        orchestrator.onAlarm()
        assertEquals(listOf(AlertKind.WALK_DONE), ringerKinds.toList())
        val walks = history.walks(date)
        assertEquals(1, walks.size)
        assertEquals(LocalTime(21, 0).at(date, zone), walks.first().start)
        assertEquals(LocalTime(21, 10).at(date, zone), walks.first().end)
        assertEquals(null, orchestrator.walkSession.value)
    }

    @Test
    fun standaloneWalk_secondTurnBack_changesNothing() = runBlocking {
        clock.now = LocalTime(21, 0, 0).at(date, zone)
        assertTrue(orchestrator.startWalk(30))
        clock.now = LocalTime(21, 5, 0).at(date, zone)
        assertTrue(orchestrator.turnBack())

        clock.now = LocalTime(21, 6, 0).at(date, zone)
        assertFalse(orchestrator.turnBack())
        assertEquals(LocalTime(21, 10).at(date, zone), requireNotNull(orchestrator.walkSession.value).end)
    }

    @Test
    fun standaloneWalk_endThenStartAgain_isAFreshWalk() = runBlocking {
        clock.now = LocalTime(21, 0, 0).at(date, zone)
        assertTrue(orchestrator.startWalk(30))

        clock.now = LocalTime(21, 2, 0).at(date, zone)
        orchestrator.endWalk()
        val logged = history.walks(date).single()
        assertEquals("the finished walk logs two minutes", 2L, (logged.end - logged.start).inWholeMinutes)

        clock.now = LocalTime(21, 2, 10).at(date, zone)
        assertTrue(orchestrator.startWalk(30))
        val session = requireNotNull(orchestrator.walkSession.value)
        assertEquals(LocalTime(21, 2, 10).at(date, zone), session.start)
        assertEquals(LocalTime(21, 32, 10).at(date, zone), session.end)
    }

    @Test
    fun dayBlockWalk_endThenStartAgain_isAFreshWalk() = runBlocking {
        clock.now = LocalTime(10, 0, 5).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        awaitBlockIndex(1)

        assertTrue(orchestrator.startWalk(30))
        clock.now = LocalTime(10, 1, 0).at(date, zone)
        assertTrue(orchestrator.turnBack())
        clock.now = LocalTime(10, 2, 0).at(date, zone)
        // End walk: the finished walk-600 is still in the plan before the new one.
        orchestrator.handle(DayEvent.Next("walk-600"))
        clock.now = LocalTime(10, 2, 30).at(date, zone)
        assertTrue(orchestrator.startWalk(30))

        val open = orchestrator.segments.value.last { it.actualEnd == null }
        assertEquals(BlockType.Walk, open.type)
        assertEquals("walk-602", open.blockId)
        assertEquals(LocalTime(10, 32).at(date, zone), requireNotNull(open.plannedEnd))
        val session = requireNotNull(orchestrator.walkSession.value)
        assertEquals(open.actualStart, session.start)
        assertEquals(open.plannedEnd, session.end)
        val running = orchestrator.state.value as DayState.Running
        val plan = requireNotNull(orchestrator.todayPlan.value)
        assertEquals("walk-602", plan.blocks[running.blockIndex].id)
    }

    @Test
    fun standaloneWalk_cameHomeEarly() = runBlocking {
        clock.now = LocalTime(21, 0, 0).at(date, zone)
        stepSensor.next = 60_000
        assertTrue(orchestrator.startWalk(60))

        clock.now = LocalTime(22, 0, 5).at(date, zone)
        stepSensor.next = 63_200
        orchestrator.onAlarm()
        val logged = history.walks(date).single()
        assertEquals(LocalTime(21, 0).at(date, zone), logged.start)
        assertEquals(LocalTime(22, 0).at(date, zone), logged.end)

        val fix = orchestrator.correctWalk(date, LocalTime(21, 0).at(date, zone), 21 * 60, 21 * 60 + 35)
        assertEquals(WalkFix.Ok(21 * 60, 21 * 60 + 35), fix)

        val after = history.walks(date).single()
        assertEquals(LocalTime(21, 0).at(date, zone), after.start)
        assertEquals(LocalTime(21, 35).at(date, zone), after.end)
        assertEquals("the steps stay as counted", 3_200L, after.steps)
    }

    @Test
    fun dayBlockWalk_segmentFollows() = runBlocking {
        clock.now = LocalTime(10, 0).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        awaitBlockIndex(1)
        assertTrue(orchestrator.startWalk(30))

        clock.now = LocalTime(10, 30).at(date, zone)
        orchestrator.handle(DayEvent.Next("walk-600"))
        val logged = history.walks(date).single()
        assertEquals(LocalTime(10, 0).at(date, zone), logged.start)
        assertEquals(LocalTime(10, 30).at(date, zone), logged.end)

        val fix = orchestrator.correctWalk(date, LocalTime(10, 0).at(date, zone), 10 * 60, 10 * 60 + 20)
        assertEquals(WalkFix.Ok(10 * 60, 10 * 60 + 20), fix)

        val walk = orchestrator.segments.value.single { it.type == BlockType.Walk }
        assertEquals(LocalTime(10, 0).at(date, zone), walk.actualStart)
        assertEquals(LocalTime(10, 20).at(date, zone), walk.actualEnd)
        val next = orchestrator.segments.value.first { it.actualStart == LocalTime(10, 30).at(date, zone) }
        assertEquals("the next segment still starts at 10:30", LocalTime(10, 30).at(date, zone), next.actualStart)
    }

    @Test
    fun walk_survivesProcessDeath() = runBlocking {
        clock.now = LocalTime(21, 0, 0).at(date, zone)
        assertTrue(orchestrator.startWalk(30))
        val before = requireNotNull(orchestrator.walkSession.value)

        val restored = newOrchestrator()
        restored.ensureRestored()

        assertEquals(before, restored.walkSession.value)
    }

    @Test
    fun steps_addUpThroughTheDay_andCloseAtMidnight() = runBlocking {
        clock.now = LocalTime(9, 0).at(date, zone)
        stepSensor.next = 52_000
        orchestrator.onAppForeground()

        clock.now = LocalTime(12, 0).at(date, zone)
        stepSensor.next = 53_500
        orchestrator.onAlarm()

        val nextDay = date.plus(1, DateTimeUnit.DAY)
        clock.now = LocalTime(0, 0, 5).at(nextDay, zone)
        stepSensor.next = 54_000
        orchestrator.onAlarm()

        assertEquals(2_000L, history.steps(date))
        assertEquals(0L, orchestrator.todaySteps.value)
    }

    @Test
    fun walk_logsItsSteps() = runBlocking {
        clock.now = LocalTime(21, 0).at(date, zone)
        stepSensor.next = 60_000
        assertTrue(orchestrator.startWalk(30))

        clock.now = LocalTime(21, 30).at(date, zone)
        stepSensor.next = 63_200
        orchestrator.endWalk()

        val walks = history.walks(date)
        assertEquals(1, walks.size)
        assertEquals(3_200L, walks.first().steps)
    }

    @Test
    fun switchEvening_closesReadingAndStartsGame() = runBlocking {
        clock.now = LocalTime(20, 12).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        orchestrator.handle(DayEvent.KidAsleep)
        clock.now = LocalTime(20, 13).at(date, zone)
        orchestrator.handle(DayEvent.EveningChoice(Choice.READ))
        // 3.3.0: Dishes runs over until "Done dishes"; the pick only starts it then.
        clock.now = LocalTime(20, 32, 5).at(date, zone)
        assertTrue(orchestrator.skipDishes())

        clock.now = LocalTime(21, 32).at(date, zone)
        assertTrue(orchestrator.switchEvening(Choice.GAME))

        val read = requireNotNull(
            orchestrator.segments.value.firstOrNull { it.type == BlockType.EveningChoice(Choice.READ) },
        )
        assertEquals(LocalTime(21, 32).at(date, zone), read.actualEnd)
        val game = orchestrator.segments.value.last { it.actualEnd == null }
        assertEquals(BlockType.EveningChoice(Choice.GAME), game.type)
        assertEquals(LocalTime(21, 32).at(date, zone), game.actualStart)
        assertEquals(LocalTime(22, 30).at(date, zone), game.plannedEnd)
    }

    @Test
    fun dishes_runsOverUntilDone() = runBlocking {
        clock.now = LocalTime(20, 0).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        assertEquals(DayState.KidTime, orchestrator.state.value)
        orchestrator.handle(DayEvent.KidAsleep)
        assertEquals(DayState.Evening(0), orchestrator.state.value)

        clock.now = LocalTime(20, 5).at(date, zone)
        orchestrator.handle(DayEvent.EveningChoice(Choice.GAME))
        assertEquals("a pick during Dishes is only remembered", DayState.Evening(0), orchestrator.state.value)

        alerts.clear()
        clock.now = LocalTime(20, 20, 5).at(date, zone)
        orchestrator.onAlarm()
        assertEquals("Dishes still runs over", DayState.Evening(0), orchestrator.state.value)
        assertTrue("its end alert must fire", alerts.last())
        assertEquals("dishes:done", rendered.last()?.key)

        clock.now = LocalTime(20, 25, 5).at(date, zone)
        orchestrator.onAlarm()
        assertTrue("the 5-minute nag must fire", alerts.last())

        clock.now = LocalTime(20, 27).at(date, zone)
        assertTrue(orchestrator.skipDishes())

        assertEquals(DayState.Evening(1), orchestrator.state.value)
        val dishes = orchestrator.segments.value.first { it.type == BlockType.Dishes }
        assertEquals(LocalTime(20, 0).at(date, zone), dishes.actualStart)
        assertEquals(LocalTime(20, 27).at(date, zone), dishes.actualEnd)
        val game = orchestrator.segments.value.last { it.actualEnd == null }
        assertEquals(BlockType.EveningChoice(Choice.GAME), game.type)
        assertEquals(LocalTime(20, 27).at(date, zone), game.actualStart)
    }

    @Test
    fun dishes_noWarningBeforeItsEnd() = runBlocking {
        clock.now = LocalTime(20, 0).at(date, zone)
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        orchestrator.handle(DayEvent.KidAsleep)
        assertEquals(DayState.Evening(0), orchestrator.state.value)

        alerts.clear()
        clock.now = LocalTime(20, 15).at(date, zone)
        orchestrator.onAlarm()

        assertEquals("no 5-minute warning before Dishes ends", false, alerts.last())
    }

    @Test
    fun drive_stepsBetweenEnterAndExit() = runBlocking {
        stepSensor.next = 1_000
        orchestrator.onDrive(enter = true, at = LocalTime(8, 0).at(date, zone))
        stepSensor.next = 1_180
        orchestrator.onDrive(enter = false, at = LocalTime(8, 25).at(date, zone))

        val drive = history.drives(date).single()
        assertEquals(LocalTime(8, 0).at(date, zone), drive.start)
        assertEquals(LocalTime(8, 25).at(date, zone), drive.end)
        assertEquals(180L, drive.steps)
        // The drive is only shown: nothing was subtracted, and no step total was rewritten.
        assertEquals(180L, orchestrator.todaySteps.value)
    }

    @Test
    fun drive_missedExit_closesAfterFourHours() = runBlocking {
        stepSensor.next = 1_000
        orchestrator.onDrive(enter = true, at = LocalTime(8, 0).at(date, zone))

        clock.now = LocalTime(12, 30).at(date, zone)
        orchestrator.stepCheckpoint()

        val drive = history.drives(date).single()
        assertEquals(LocalTime(8, 0).at(date, zone), drive.start)
        assertEquals(LocalTime(12, 0).at(date, zone), drive.end)
        assertEquals(null, drive.steps)

        // Nothing is left open: a later exit changes nothing.
        orchestrator.onDrive(enter = false, at = LocalTime(12, 31).at(date, zone))
        assertEquals(1, history.drives(date).size)
    }

    @Test
    fun drive_rebootInBetween_hasNoSteps() = runBlocking {
        stepSensor.next = 5_000
        orchestrator.onDrive(enter = true, at = LocalTime(8, 0).at(date, zone))

        stepSensor.next = 200 // the phone restarted
        orchestrator.onDrive(enter = false, at = LocalTime(8, 25).at(date, zone))

        assertEquals(null, history.drives(date).single().steps)
    }

    private suspend fun awaitBlockIndex(expected: Int) {
        kotlinx.coroutines.withTimeout(5_000) {
            orchestrator.state.first { (it is DayState.Running) && (it.blockIndex == expected) }
        }
    }
}