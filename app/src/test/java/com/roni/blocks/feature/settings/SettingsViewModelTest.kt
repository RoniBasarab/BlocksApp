package com.roni.blocks.feature.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.alarms.AlarmScheduler
import com.roni.blocks.core.demo.DemoDataGenerator
import com.roni.blocks.core.daystate.DayEvent
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.DayRecord
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.notifications.NotificationModel
import com.roni.blocks.core.notifications.NotificationPort
import com.roni.blocks.core.notifications.NotificationModelMapper
import com.roni.blocks.core.schedule.ScheduleEngine
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.core.system.SystemStatuses
import com.roni.blocks.core.time.DebugClock
import com.roni.blocks.core.time.DebugTimeController
import com.roni.blocks.core.time.SystemTimeMapper
import com.roni.blocks.core.time.TimeFormatter
import com.roni.blocks.core.time.at
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.builtins.serializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Every Settings action must have an observable effect: the status rows reflect the system only
 * after refresh(), the format toggle actually flips the formatter, and destructive actions keep
 * what the spec promises (today survives Clear history).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val zone = TimeZone.of("Asia/Jerusalem")

    /** Sunday 2026-09-27. */
    private val date = LocalDate(2026, 9, 27)
    private val yesterday = LocalDate(2026, 9, 26)

    private lateinit var clock: MovableClock
    private lateinit var logger: AppLogger
    private lateinit var prefs: PrefsStorage
    private lateinit var history: HistoryManager
    private lateinit var timeFormatter: TimeFormatter
    private lateinit var orchestrator: DayOrchestrator
    private lateinit var port: FakeNotificationPort
    private lateinit var statuses: FakeSystemStatuses
    private lateinit var debugTime: DebugTimeController
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        clock = MovableClock(LocalTime(10, 0).at(date, zone))
        logger = AppLogger(context)
        prefs = PrefsStorage(context, UnconfinedTestDispatcher())
        history = HistoryManager(prefs)
        timeFormatter = TimeFormatter(prefs, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        port = FakeNotificationPort()
        statuses = FakeSystemStatuses(notificationsEnabled = true, exactAlarmsAllowed = true)
        orchestrator = DayOrchestrator(
            prefs = prefs,
            engine = ScheduleEngine(clock, zone),
            templates = TemplateManager(context),
            history = history,
            alarms = AlarmScheduler(context, SystemTimeMapper.Identity, logger),
            notificationMapper = NotificationModelMapper(ScheduleEngine(clock, zone), zone, timeFormatter),
            notificationRenderer = port,
            clock = clock,
            zone = zone,
            logger = logger,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        debugTime = DebugTimeController(DebugClock(context).apply { setOffsetMinutes(0) }, orchestrator, logger, zone)
        viewModel = newViewModel(DemoDataGenerator(TemplateManager(context), history, clock, zone))
    }

    private fun newViewModel(demoData: DemoDataGenerator) = SettingsViewModel(
        logger = logger,
        history = history,
        timeFormatter = timeFormatter,
        orchestrator = orchestrator,
        notificationPort = port,
        statuses = statuses,
        demoData = demoData,
        debugTime = debugTime,
        clock = clock,
        zone = zone,
    )

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refresh_reflectsTheSystemStatuses() = runBlocking {
        awaitState { true }

        statuses.notificationsEnabled = false
        statuses.exactAlarmsAllowed = false
        viewModel.refresh()

        val state = awaitState { it.notificationPermission == PermissionStatus.BLOCKED && it.exactAlarms == PermissionStatus.BLOCKED }
        assertTrue(state.notificationPermission == PermissionStatus.BLOCKED)
    }

    @Test
    fun setHourFormat_persistsAndFlipsTheFormatter() = runBlocking {
        awaitState { true }

        viewModel.setHourFormat(false)

        val state = awaitState { !it.hourFormat24 }
        assertFalse(state.hourFormat24)
        assertEquals("9:30 AM", timeFormatter.label(9 * 60 + 30))
        assertEquals(false, prefs.read(PrefsFile.SETTINGS, "hour_format_24", kotlin.Boolean.serializer()))
    }

    @Test
    fun sendTestNotification_postsThroughThePort() = runBlocking {
        viewModel.sendTestNotification()
        assertEquals(1, port.testNotifications)
    }

    @Test
    fun resetToday_returnsToNotStartedWithNoSegments() = runBlocking {
        orchestrator.send(DayEvent.StartDay(TemplateId.SUN_TUE_WED))
        withTimeout(5_000) { orchestrator.state.first { it is DayState.Running } }

        viewModel.resetToday()

        withTimeout(5_000) {
            orchestrator.state.first { it == DayState.NotStarted }
        }
        assertEquals(emptyList<Any>(), orchestrator.segments.value)
    }

    @Test
    fun clearHistory_deletesPastDaysAndKeepsToday() = runBlocking {
        history.writeDay(DayRecord(date, TemplateId.SUN_TUE_WED, emptyList()))
        history.writeDay(DayRecord(yesterday, TemplateId.SUN_TUE_WED, emptyList()))
        assertNotNull(history.readDay(yesterday))

        viewModel.clearHistory()

        withTimeout(5_000) {
            while (history.readDay(yesterday) != null) delay(10)
        }
        assertNotNull(history.readDay(date))
        assertNull(history.readDay(yesterday))
    }

    @Test
    fun loadDemoData_reportsHowManyDaysWereSeeded() = runBlocking {
        viewModel.loadDemoData()

        val effect = withTimeout(60_000) { viewModel.effects.first() }

        assertEquals(SettingsEffect.ShowMessage("Demo data loaded: 28 days"), effect)
    }

    @Test
    fun loadDemoData_reportsFailureInTheLog() = runBlocking {
        val failing = object : DemoDataGenerator(TemplateManager(context), history, clock, zone) {
            override suspend fun seed(weeks: Int, seed: Int): Int = throw IllegalStateException("seed failed")
        }
        val failingViewModel = newViewModel(failing)

        failingViewModel.loadDemoData()

        val effect = withTimeout(5_000) { failingViewModel.effects.first() }

        assertEquals(SettingsEffect.ShowMessage("Couldn't load demo data. Details are in the log."), effect)
    }

    private suspend fun awaitState(
        predicate: (SettingsUiState) -> Boolean,
    ): SettingsUiState = withTimeout(5_000) {
        viewModel.state.first(predicate)
    }

    private class MovableClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private class FakeNotificationPort : NotificationPort {
        var testNotifications = 0

        override fun ensureChannels() = Unit

        override fun render(model: NotificationModel?, transition: Boolean, alert: Boolean) = Unit

        override fun onDismissed(key: String) = Unit

        override fun sendTestNotification() {
            testNotifications++
        }
    }

    private class FakeSystemStatuses(
        var notificationsEnabled: Boolean,
        var exactAlarmsAllowed: Boolean,
    ) : SystemStatuses {
        override fun notificationsEnabled(): Boolean = notificationsEnabled

        override fun canScheduleExactAlarms(): Boolean = exactAlarmsAllowed
    }
}
