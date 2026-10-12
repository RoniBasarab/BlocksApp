package com.roni.blocks.feature.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.alarms.AlarmScheduler
import com.roni.blocks.core.daystate.DayEvent
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.daystate.EndReason
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.EVENING_CHOICE_ID
import com.roni.blocks.core.model.EVENING_DISHES_ID
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.notifications.NotificationModel
import com.roni.blocks.core.notifications.NotificationModelMapper
import com.roni.blocks.core.notifications.NotificationPort
import com.roni.blocks.core.schedule.ScheduleEngine
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.core.time.DebugClock
import com.roni.blocks.core.time.DebugTimeController
import com.roni.blocks.core.time.SystemTimeMapper
import com.roni.blocks.core.time.Ticker
import com.roni.blocks.core.time.TimeFormatter
import com.roni.blocks.core.time.at
import com.roni.blocks.core.walk.WalkPhase
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Drives real [HomeViewModel.onAction] against a real [DayOrchestrator] (fake clock, in-memory
 * storage) and asserts on the orchestrator's state, so no "Finish early" dead button can hide
 * behind a UI-free ViewModel.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HomeViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val zone = TimeZone.of("Asia/Jerusalem")

    /** Sunday 2026-09-27. */
    private val date = LocalDate(2026, 9, 27)

    private lateinit var clock: MovableClock
    private lateinit var prefs: PrefsStorage
    private lateinit var history: HistoryManager
    private lateinit var orchestrator: DayOrchestrator
    private lateinit var viewModel: HomeViewModel

    private val firstBlockId = "sun_tue_wed.coding_am"

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        clock = MovableClock(LocalTime(10, 0).at(date, zone))
        val logger = AppLogger(context)
        prefs = PrefsStorage(context, UnconfinedTestDispatcher())
        history = HistoryManager(prefs)
        orchestrator = DayOrchestrator(
            prefs = prefs,
            engine = ScheduleEngine(clock, zone),
            templates = TemplateManager(context),
            history = history,
            alarms = AlarmScheduler(context, SystemTimeMapper.Identity, logger),
            notificationMapper = NotificationModelMapper(ScheduleEngine(clock, zone), zone, TimeFormatter(prefs, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))),
            notificationRenderer = SilentNotificationPort,
            clock = clock,
            zone = zone,
            logger = logger,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        viewModel = HomeViewModel(
            day = orchestrator,
            templates = TemplateManager(context),
            history = history,
            ticker = Ticker(clock, zone),
            clock = clock,
            zone = zone,
            prefs = prefs,
            timeFormatter = TimeFormatter(prefs, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)),
            debugTime = DebugTimeController(DebugClock(context).apply { setOffsetMinutes(0) }, orchestrator, logger, zone),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun pauseConfirmed_movesStateToPaused() = runBlocking {
        startDayInsideCoding()

        viewModel.onAction(HomeAction.Pause)

        awaitRunningOrPaused { it is DayState.Paused }
        val paused = orchestrator.state.value as DayState.Paused
        assertEquals(TemplateId.SUN_TUE_WED, paused.running.template)
    }

    @Test
    fun nextDuringCodingBeforeItsEnd_runsSyntheticFreeUntilThePlannedEnd() = runBlocking {
        startDayInsideCoding()

        viewModel.onAction(HomeAction.Next)

        awaitRunningOrPaused { it.blockIndex() == 1 }
        val free = orchestrator.segments.value.last { it.actualEnd == null }
        assertEquals(SegmentKind.SYNTHETIC_FREE, free.kind)
        assertEquals(LocalTime(10, 0).at(date, zone), free.actualStart)
        assertEquals("the rest of the coding block becomes free", LocalTime(11, 30).at(date, zone), free.plannedEnd)
        assertEquals(
            "Home tracks the slot the auto-advance will land in",
            "sun_tue_wed.coding_am",
            viewModel.state.value.currentBlockId,
        )
    }

    @Test
    fun nextDuringThatFree_runsTheNextBlockNow() = runBlocking {
        startDayInsideCoding()
        viewModel.onAction(HomeAction.Next)
        awaitRunningOrPaused { it.blockIndex() == 1 }

        clock.now = LocalTime(10, 5).at(date, zone)
        viewModel.onAction(HomeAction.Next)

        awaitRunningOrPaused { it.blockIndex() == 2 }
        val next = orchestrator.segments.value.last { it.actualEnd == null }
        assertEquals(SegmentKind.PLANNED, next.kind)
        assertEquals(BlockType.Free, next.type)
        assertEquals(LocalTime(10, 5).at(date, zone), next.actualStart)
        assertEquals("the next block now is the midday free, which starts sooner and ends where it did", LocalTime(14, 0).at(date, zone), requireNotNull(next.plannedEnd))
    }

    @Test
    fun emergencyConfirmed_endsDayWithEmergencyStop() = runBlocking {
        startDayInsideCoding()

        viewModel.onAction(HomeAction.Emergency)

        awaitRunningOrPaused { it == DayState.Ended }
        withTimeout(5_000) {
            // State flips to Ended before the flush persists the record, so wait for it.
            while (DayFlag.EMERGENCY_STOP !in (history.readDay(date)?.flags ?: emptySet())) {
                kotlinx.coroutines.delay(10)
            }
        }
    }

    @Test
    fun emergencyCancelled_leavesTheDayUnchanged() = runBlocking {
        startDayInsideCoding()

        clock.now = LocalTime(10, 5).at(date, zone)
        awaitRunningOrPaused { it.blockIndex() == 1 }

        assertEquals(
            orchestrator.state.value,
            DayState.Running(TemplateId.SUN_TUE_WED, 1),
        )
        assertTrue(history.readDay(date) == null)
    }

    @Test
    fun startedDayHomeTracksFirstBlock() = runBlocking {
        startDayInsideCoding()
        assertEquals(firstBlockId, viewModel.state.value.currentBlockId)
    }

    @Test
    fun homeTitle_afterEmergency_isTheTemplateTitle() = runBlocking {
        startDayInsideCoding()

        viewModel.onAction(HomeAction.Emergency)

        awaitRunningOrPaused { it == DayState.Ended }
        withTimeout(5_000) { viewModel.state.first { it.status == "Stopped early (emergency)" } }
        assertEquals("Sunday / Tuesday / Wednesday", viewModel.state.value.title)
        assertEquals("Stopped early (emergency)", viewModel.state.value.status)
    }

    @Test
    fun status_inTheEvening_countsDoneAndMissed() = runBlocking {
        clock.now = LocalTime(22, 0).at(date, zone)
        val debug = DebugClock(context).apply { setOffsetMinutes(0) }
        val debugCtrl = DebugTimeController(
            debug,
            orchestrator,
            AppLogger(context),
            zone,
        )
        debugCtrl.setTo("22:00")
        viewModel = HomeViewModel(
            day = orchestrator,
            templates = TemplateManager(context),
            history = history,
            ticker = Ticker(clock, zone),
            clock = clock,
            zone = zone,
            prefs = prefs,
            timeFormatter = TimeFormatter(prefs, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)),
            debugTime = debugCtrl,
        )
        orchestrator.send(DayEvent.StartDay(TemplateId.MON_THU))
        withTimeout(5_000) { orchestrator.state.first { it == DayState.KidTime } }

        viewModel.onAction(HomeAction.KidAsleep)
        withTimeout(5_000) { orchestrator.state.first { it == DayState.Evening(0) } }

        clock.now = LocalTime(22, 0).at(date, zone)
        withTimeout(5_000) { viewModel.state.first { it.status == "0 done · 7 missed" } }
        assertEquals("0 done · 7 missed", viewModel.state.value.status)
    }

    @Test
    fun status_whileRunning_countsBlocksAhead() = runBlocking {
        clock.now = LocalTime(10, 0).at(date, zone)
        orchestrator.send(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        withTimeout(5_000) { viewModel.state.first { it.currentBlockId == "sun_tue_wed.coding_am" } }
        assertEquals("3 blocks left", viewModel.state.value.status)
    }

    @Test
    fun evening_showsTheEveningBlocks_notThePlan() = runBlocking {
        clock.now = LocalTime(21, 12).at(date, zone)
        orchestrator.send(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        withTimeout(5_000) { orchestrator.state.first { it == DayState.KidTime } }

        viewModel.onAction(HomeAction.KidAsleep)
        withTimeout(5_000) { orchestrator.state.first { it == DayState.Evening(0) } }

        clock.now = LocalTime(21, 32).at(date, zone)
        viewModel.onAction(HomeAction.PickEvening(Choice.GAME))
        withTimeout(5_000) { orchestrator.state.first { it == DayState.Evening(1) } }

        clock.now = LocalTime(21, 40).at(date, zone)
        withTimeout(5_000) { viewModel.state.first { it.currentBlockId == EVENING_CHOICE_ID } }

        val blocks = viewModel.state.value.blocks
        val dishes = requireNotNull(blocks.firstOrNull { it.id == EVENING_DISHES_ID })
        assertEquals(21 * 60 + 12, dishes.startMin)
        assertEquals(21 * 60 + 32, dishes.endMin)
        val choice = requireNotNull(blocks.firstOrNull { it.id == EVENING_CHOICE_ID })
        assertEquals(21 * 60 + 32, choice.startMin)
        assertEquals(22 * 60 + 30, choice.endMin)
        val kid = requireNotNull(blocks.firstOrNull { it.id == "sun_tue_wed.kid_time" })
        assertEquals("kid time ends when it was tapped", 21 * 60 + 12, kid.endMin)
    }

    @Test
    fun evening_keepsKidTime_thenDishes_thenTheActivity() = runBlocking {
        clock.now = LocalTime(20, 12).at(date, zone)
        orchestrator.send(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        withTimeout(5_000) { orchestrator.state.first { it == DayState.KidTime } }

        viewModel.onAction(HomeAction.KidAsleep)
        withTimeout(5_000) { orchestrator.state.first { it == DayState.Evening(0) } }

        clock.now = LocalTime(20, 13).at(date, zone)
        viewModel.onAction(HomeAction.PickEvening(Choice.READ))
        withTimeout(5_000) { viewModel.state.first { s -> s.blocks.any { it.id == EVENING_CHOICE_ID } } }

        val blocks = viewModel.state.value.blocks
        val kid = requireNotNull(blocks.firstOrNull { it.id == "sun_tue_wed.kid_time" })
        assertEquals("kid time starts when the plan had it", 16 * 60 + 20, kid.startMin)
        assertEquals("kid time ends at the tap", 20 * 60 + 12, kid.endMin)

        val dishes = requireNotNull(blocks.firstOrNull { it.id == EVENING_DISHES_ID })
        assertEquals(20 * 60 + 12, dishes.startMin)
        assertEquals(20 * 60 + 32, dishes.endMin)
        assertEquals("Dishes is the current block", EVENING_DISHES_ID, viewModel.state.value.currentBlockId)

        val read = requireNotNull(blocks.firstOrNull { it.id == EVENING_CHOICE_ID })
        assertEquals(BlockType.EveningChoice(Choice.READ), read.type)
        assertEquals(20 * 60 + 32, read.startMin)
        assertEquals(22 * 60 + 30, read.endMin)
    }

    @Test
    fun insertIntoFreeTime_selectsTheNewBlock() = runBlocking {
        startDayInsideCoding()

        viewModel.onAction(HomeAction.Insert("sun_tue_wed.free_midday", BlockType.Cv))

        withTimeout(5_000) { viewModel.state.first { s -> s.blocks.any { it.id == "edit-1" } } }
        val inserted = viewModel.state.value.blocks.first { it.id == "edit-1" }
        assertEquals(BlockType.Cv, inserted.type)
        assertEquals(11 * 60 + 30, inserted.startMin)
        assertEquals("edit-1", viewModel.state.value.selectId)
    }

    @Test
    fun lockedTap_onAPastBlock_opensTheCorrectionSheet() = runBlocking {
        startDayInsideCoding()

        viewModel.onAction(HomeAction.LockedBlockTapped("sun_tue_wed.cv"))

        val row = viewModel.correctingRow.value ?: error("no correcting row opened")
        assertEquals("sun_tue_wed.cv@540", row.id)
    }

    @Test
    fun lockedTap_onKidTime_saysKidTimeCannotMove() = runBlocking {
        startDayInsideCoding()
        val messages = mutableListOf<String>()
        val job = launch(Dispatchers.Unconfined) { viewModel.messages.collect { messages += it } }

        viewModel.onAction(HomeAction.LockedBlockTapped("sun_tue_wed.kid_time"))

        assertEquals("Kid time and the evening can't be moved.", messages.single())
        job.cancel()
    }

    @Test
    fun doneEarly_saysFreeTimeAndNamesTheNextBlock() = runBlocking {
        startDayInsideCoding()

        viewModel.onAction(HomeAction.Next)
        awaitRunningOrPaused { it.blockIndex() == 1 }

        val nowCard = viewModel.state.value.nowCard ?: error("no nowCard")
        assertEquals("Free time", nowCard.title)
        assertEquals("Done early", nowCard.chip)
        assertEquals(
            listOf(NowButtonId.NEXT, NowButtonId.WALK, NowButtonId.EMERGENCY),
            nowCard.buttons.map { it.id },
        )
        assertEquals("Next: Free", nowCard.buttons.first { it.id == NowButtonId.NEXT }.label)
        assertEquals("until 11:30 · then Free", nowCard.subtitle)

        val coding = viewModel.state.value.blocks.first { it.id == firstBlockId }
        assertEquals(0, coding.phases.size)
        assertTrue(coding.subtitle?.startsWith("Done early · free until") == true)
    }

    @Test
    fun walkIcon_opensTheSheet_inTheEvening() = runBlocking {
        clock.now = LocalTime(21, 12).at(date, zone)
        orchestrator.send(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        withTimeout(5_000) { orchestrator.state.first { it == DayState.KidTime } }

        viewModel.onAction(HomeAction.KidAsleep)
        withTimeout(5_000) { orchestrator.state.first { it == DayState.Evening(0) } }

        // Dishes runs 21:12-21:32; the pick lands exactly on its end, so the activity starts at once.
        clock.now = LocalTime(21, 32).at(date, zone)
        viewModel.onAction(HomeAction.PickEvening(Choice.READ))
        withTimeout(5_000) { orchestrator.state.first { it == DayState.Evening(1) } }

        viewModel.onAction(HomeAction.Walk)
        withTimeout(5_000) { viewModel.state.first { it.walkSheet != null } }

        assertNotNull(viewModel.state.value.walkSheet)
    }

    @Test
    fun walkCard_countsInMinutesAndSeconds() = runBlocking {
        clock.now = LocalTime(21, 0, 0).at(date, zone)
        assertTrue(orchestrator.startWalk(30))
        clock.now = LocalTime(21, 1, 5).at(date, zone)

        // walkView is WhileSubscribed, so keep a collector alive for the assertions.
        val job = launch(Dispatchers.Unconfined) { viewModel.walkView.collect {} }
        val view = requireNotNull(withTimeout(5_000) { viewModel.walkView.first { it != null } })
        assertEquals("13:55", view.text)
        assertEquals(WalkPhase.OUT, view.phase)
        job.cancel()
    }

    @Test
    fun oneMinuteBlock_isASliverWithAPill() = runBlocking {
        clock.now = LocalTime(9, 0).at(date, zone)
        orchestrator.send(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        withTimeout(5_000) { viewModel.state.first { it.currentBlockId == "sun_tue_wed.cv" } }

        clock.now = LocalTime(9, 1).at(date, zone)
        viewModel.onAction(HomeAction.Next)                                    // Done early: the rest of CV becomes free
        withTimeout(5_000) { viewModel.state.first { it.nowCard?.chip == "Done early" } }

        viewModel.onAction(HomeAction.Next)                                    // the next block starts now, so CV is a minute

        val state = withTimeout(5_000) {
            viewModel.state.first { s -> s.calendarFit.blocks.any { it.id == "sun_tue_wed.cv" && it.sliver } }
        }
        assertTrue(state.calendarFit.blocks.first { it.id == "sun_tue_wed.cv" }.sliver)
        assertEquals(listOf("sun_tue_wed.cv"), state.calendarFit.rows.single().ids)
        assertEquals("CV · 1 min", state.pillLabels["sun_tue_wed.cv"])
    }

    @Test
    fun biggerFont_shortBlocksUseOneLine() = runBlocking {
        clock.now = LocalTime(9, 0).at(date, zone)
        orchestrator.send(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        withTimeout(5_000) { viewModel.state.first { it.currentBlockId == "sun_tue_wed.cv" } }

        // Real text is taller than the old constants: two lines would not fit a 30-minute block.
        viewModel.onAction(HomeAction.TextMeasured(TextMetrics(22f, 44f)))

        val state = withTimeout(5_000) {
            viewModel.state.first { s ->
                s.calendarFit.blocks.firstOrNull { it.id == "sun_tue_wed.cv" }?.fit == TextFit.ONE_LINE
            }
        }
        assertEquals(TextFit.ONE_LINE, state.calendarFit.blocks.first { it.id == "sun_tue_wed.cv" }.fit)
    }

    private suspend fun startDayInsideCoding() {
        orchestrator.send(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        awaitRunningOrPaused { it.blockIndex() == 1 }
        withTimeout(5_000) { viewModel.state.first { it.currentBlockId == firstBlockId } }
    }

    private suspend fun awaitRunningOrPaused(
        predicate: (DayState) -> Boolean,
    ): DayState = withTimeout(5_000) {
        orchestrator.state.first { (it is DayState.Running || it is DayState.Paused || it == DayState.Ended) && predicate(it) }
    }

    private fun DayState.blockIndex(): Int = when (this) {
        is DayState.Running -> blockIndex
        is DayState.Paused -> running.blockIndex
        is DayState.Evening -> blockIndex
        else -> -1
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
}
