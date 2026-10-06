package com.roni.blocks.core.daystate

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.alarms.AlarmScheduler
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.LocalDateSerializer
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.notifications.ActionEvent
import com.roni.blocks.core.notifications.NotificationModel
import com.roni.blocks.core.notifications.NotificationModelMapper
import com.roni.blocks.core.notifications.NotificationPort
import com.roni.blocks.core.schedule.ScheduleEngine
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.core.time.SystemTimeMapper
import com.roni.blocks.core.time.TimeFormatter
import com.roni.blocks.core.time.at
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Process death is what Android does to this app whenever it is killed mid-day, and it is the one
 * path [DayOrchestratorRestoreTest] could not see: that test drove restore directly, while a real
 * cold start arrives through `send`, a receiver or the UI gate on a brand new orchestrator that
 * still has `isRestored == false`.
 *
 * Every test here builds a fresh orchestrator over storage that already holds a started day, and
 * reaches the day the way a cold start does. Restore has to finish, has to finish quickly, and must
 * not swallow a tap that arrived before it did.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DayOrchestratorProcessDeathTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val zone = TimeZone.of("Asia/Jerusalem")

    /** Sunday 2026-09-27, a Sun / Tue / Wed day. */
    private val date = LocalDate(2026, 9, 27)

    private lateinit var prefs: PrefsStorage

    private val scopes = mutableListOf<CoroutineScope>()

    @Before
    fun setUp() {
        context.getSharedPreferences(PrefsFile.DAY.name.lowercase(), 0).edit().clear().commit()
        prefs = PrefsStorage(context, UnconfinedTestDispatcher())
        File(context.filesDir, "logs/blocks.log").delete()
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        scopes.clear()
        // This class deliberately stores an unreadable day state, so it must not leave that behind for
        // whichever test class runs next: restore fails closed and every later expectation breaks.
        context.getSharedPreferences(PrefsFile.DAY.name.lowercase(), 0).edit().clear().commit()
    }

    @Test
    fun coldStart_restoresWithinTwoSeconds() = runTest {
        val cases = listOf(
            Case("running mid-block", DayState.Running(TemplateId.SUN_TUE_WED, 1), LocalTime(9, 40), planned("sun_tue_wed.coding_am", 9, 30, 11, 30)),
            Case("running after its end", DayState.Running(TemplateId.SUN_TUE_WED, 1), LocalTime(11, 45), planned("sun_tue_wed.coding_am", 9, 30, 11, 30)),
            Case("paused", DayState.Paused(DayState.Running(TemplateId.SUN_TUE_WED, 1), LocalTime(9, 40).at(date, zone)), LocalTime(9, 40), planned("sun_tue_wed.coding_am", 9, 30, 11, 30)),
            Case("synthetic free", DayState.Running(TemplateId.SUN_TUE_WED, 0), LocalTime(9, 20), syntheticFree()),
            Case("kid time", DayState.KidTime, LocalTime(17, 0), emptyList()),
            Case("evening", DayState.Evening(blockIndex = 0), LocalTime(20, 10), emptyList()),
            Case("ended", DayState.Ended, LocalTime(22, 35), emptyList()),
            Case("off", DayState.Off, LocalTime(10, 0), emptyList()),
        )

        for (case in cases) {
            seed(case.state, case.segments)
            val orchestrator = orchestrator(at(case.time))

            assertTrue(case.name + " must finish restoring", orchestrator.isRestored.value)
            assertEquals(case.name, case.state, orchestrator.state.value)
            assertEquals(case.name, case.segments, orchestrator.segments.value)
        }
    }

    @Test
    fun notificationAction_onFreshProcess_isApplied() = runTest {
        seed(
            DayState.Running(TemplateId.SUN_TUE_WED, 0),
            listOf(segment("sun_tue_wed.cv", BlockType.Cv, SegmentKind.PLANNED, 9, 0, 9, 30, 9, 0)),
        )

        // A scope that never runs queued work is what a real cold start looks like: the Application
        // does not touch the orchestrator, so the tap is the first thing to reach it and restore has
        // not finished. Handled the way the receiver handles it, from the caller's own coroutine.
        val tapped = orchestrator(LocalTime(9, 40).at(date, zone), Pending)
        assertFalse("restore has not run yet", tapped.isRestored.value)

        tapped.handleNotificationAction(ActionEvent.START_NEXT, "sun_tue_wed.cv")

        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 1), tapped.state.value)

        seed(
            DayState.Running(TemplateId.SUN_TUE_WED, 0),
            listOf(segment("sun_tue_wed.cv", BlockType.Cv, SegmentKind.PLANNED, 9, 0, 9, 30, 9, 0)),
        )
        val sent = orchestrator(LocalTime(9, 40).at(date, zone))
        sent.send(DayEvent.Next(expectedBlockId = "sun_tue_wed.cv"))

        val state = withContext(Dispatchers.Default) {
            withTimeout(5_000) { sent.state.first { it == DayState.Running(TemplateId.SUN_TUE_WED, 1) } }
        }
        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 1), state)
    }

    @Test
    fun restoreFailure_stillMarksRestored() = runTest {
        // The one thing a restore cannot survive is unreadable state. It must still let the app open.
        check(
            context.getSharedPreferences(PrefsFile.DAY.name.lowercase(), 0).edit()
                .putString("day_state", "{ this is not a day state")
                .commit(),
        )
        val orchestrator = orchestrator(LocalTime(9, 40).at(date, zone))

        awaitRestored(orchestrator)

        assertEquals(DayState.NotStarted, orchestrator.state.value)
        val log = File(context.filesDir, "logs/blocks.log").readText()
        assertTrue("the failure has to be logged, not swallowed: $log", log.contains("Restore failed"))
    }

    private data class Case(
        val name: String,
        val state: DayState,
        val time: LocalTime,
        val segments: List<Segment>,
    )

    private fun at(time: LocalTime): Instant = time.at(date, zone)

    private fun planned(id: String, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int): List<Segment> =
        listOf(segment(id, BlockType.Coding, SegmentKind.PLANNED, startHour, startMinute, endHour, endMinute, startHour, startMinute))

    private fun syntheticFree(): List<Segment> = listOf(
        segment("sun_tue_wed.cv", BlockType.Cv, SegmentKind.PLANNED, 9, 0, 9, 30, 9, 0, actualEndMinute = 15),
        segment("sun_tue_wed.cv", BlockType.Free, SegmentKind.SYNTHETIC_FREE, 9, 0, 9, 30, 9, 15),
    )

    private fun segment(
        id: String,
        type: BlockType,
        kind: SegmentKind,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        actualStartHour: Int,
        actualStartMinute: Int,
        actualEndMinute: Int? = null,
    ) = Segment(
        type = type,
        kind = kind,
        plannedStart = at(LocalTime(startHour, startMinute)),
        plannedEnd = at(LocalTime(endHour, endMinute)),
        actualStart = at(LocalTime(actualStartHour, actualStartMinute)),
        actualEnd = actualEndMinute?.let { at(LocalTime(actualStartHour, it)) },
    )

    private suspend fun awaitRestored(orchestrator: DayOrchestrator) {
        withContext(Dispatchers.Default) { withTimeout(2_000) { orchestrator.isRestored.first { it } } }
    }

    private suspend fun seed(state: DayState, segments: List<Segment>) {
        prefs.write(PrefsFile.DAY, "day_state", state, DayState.serializer())
        prefs.write(PrefsFile.DAY, "today_segments", segments, kotlinx.serialization.builtins.ListSerializer(Segment.serializer()))
        prefs.write(PrefsFile.DAY, "today_date", date, LocalDateSerializer)
    }

    private fun orchestrator(now: Instant, dispatcher: CoroutineDispatcher = Dispatchers.Unconfined): DayOrchestrator {
        val clock = object : Clock {
            override fun now(): Instant = now
        }
        val logger = AppLogger(context)
        val engine = ScheduleEngine(clock, zone)
        val formatterScope = scope(Dispatchers.Unconfined)
        val scope = scope(dispatcher)
        return DayOrchestrator(
            prefs = prefs,
            engine = engine,
            templates = TemplateManager(context),
            history = HistoryManager(prefs),
            alarms = AlarmScheduler(context, SystemTimeMapper.Identity, logger),
            notificationMapper = NotificationModelMapper(engine, zone, TimeFormatter(prefs, formatterScope)),
            notificationRenderer = SilentNotificationPort,
            clock = clock,
            zone = zone,
            logger = logger,
            scope = scope,
        )
    }

    private fun scope(dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher).also(scopes::add)

    /** Never runs queued work, so the orchestrator is observed exactly as it is before restore. */
    private object Pending : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) = Unit
        override fun isDispatchNeeded(context: CoroutineContext) = true
    }

    private object SilentNotificationPort : NotificationPort {
        override fun ensureChannels() = Unit

        override fun render(model: NotificationModel?, transition: Boolean) = Unit

        override fun onDismissed(key: String) = Unit

        override fun sendTestNotification() = Unit
    }
}
