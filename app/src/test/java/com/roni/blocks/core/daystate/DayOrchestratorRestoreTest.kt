package com.roni.blocks.core.daystate

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.alarms.AlarmScheduler
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.LocalDateSerializer
import com.roni.blocks.core.model.TemplateId
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
import com.roni.blocks.navigation.StartDestination
import com.roni.blocks.navigation.resolveStartDestination
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Cold start used to open the Template picker for a day that was already running. The start
 * destination is now derived from the restored state, so these tests pin the restore contract:
 * the flag only flips after prefs were read and the date rollover was applied.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DayOrchestratorRestoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val zone = TimeZone.of("Asia/Jerusalem")

    /** Sunday 2026-09-27. */
    private val today = LocalDate(2026, 9, 27)
    private val yesterday = LocalDate(2026, 9, 26)
    private val instant = LocalTime(9, 40).at(today, zone)

    private lateinit var prefs: PrefsStorage

    @Before
    fun setUp() {
        context.getSharedPreferences(PrefsFile.DAY.name.lowercase(), 0).edit().clear().commit()
        prefs = PrefsStorage(context)
    }

    @Test
    fun todaysRunningState_isRestoredAsRunning() = runBlocking {
        seed(DayState.Running(TemplateId.SUN_TUE_WED, 1), today)
        val orchestrator = orchestrator(Dispatchers.Unconfined)
        orchestrator.awaitRestored()

        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 1), orchestrator.state.value)
    }

    @Test
    fun yesterdaysState_isRestoredAsNotStarted() = runBlocking {
        seed(DayState.Running(TemplateId.SUN_TUE_WED, 1), yesterday)
        val orchestrator = orchestrator(Dispatchers.Unconfined)
        orchestrator.awaitRestored()

        assertEquals(DayState.NotStarted, orchestrator.state.value)
    }

    @Test
    fun startDestination_isOnlyCorrectOnceRestoreHasRun() = runBlocking {
        seed(DayState.Running(TemplateId.SUN_TUE_WED, 1), today)

        val pending = orchestrator(Pending)
        assertFalse("restore has not run yet", pending.isRestored.value)
        assertEquals(DayState.NotStarted, pending.state.value)
        assertEquals(
            "reading the destination before restore is what opened the picker",
            StartDestination.TEMPLATE_PICKER,
            resolveStartDestination(pending.state.value),
        )

        val restored = orchestrator(Dispatchers.Unconfined)
        restored.awaitRestored()
        assertEquals(StartDestination.HOME, resolveStartDestination(restored.state.value))
    }

    @Test
    fun legacyInterrupted_restoresAsRunning() = runBlocking {
        // Deliberately not written through a serializer: this is the exact payload a pre-1.4.0
        // build left behind, and the decoder has to survive the discriminator it can no longer name.
        seedRaw(
            state = """{"type":"Interrupted","running":{"type":"Running","template":"SUN_TUE_WED","blockIndex":2},"since":"2026-09-27T06:00:00Z"}""",
            storedDate = today,
        )
        val orchestrator = orchestrator(Dispatchers.Unconfined)
        orchestrator.awaitRestored()

        assertEquals(DayState.Running(TemplateId.SUN_TUE_WED, 2), orchestrator.state.value)
    }

    /** Restore finishes on the IO dispatcher, so the flag has to be awaited, not assumed. */
    private suspend fun DayOrchestrator.awaitRestored() {
        withTimeout(5_000) { isRestored.first { it } }
    }

    private suspend fun seed(state: DayState, storedDate: LocalDate) {
        prefs.write(PrefsFile.DAY, "day_state", state, DayState.serializer())
        prefs.write(PrefsFile.DAY, "today_date", storedDate, LocalDateSerializer)
    }

    private suspend fun seedRaw(state: String, storedDate: LocalDate) {
        check(
            context.getSharedPreferences(PrefsFile.DAY.name.lowercase(), 0).edit()
                .putString("day_state", state)
                .commit(),
        )
        prefs.write(PrefsFile.DAY, "today_date", storedDate, LocalDateSerializer)
    }

    private fun orchestrator(dispatcher: CoroutineDispatcher): DayOrchestrator {
        val clock = object : Clock {
            override fun now(): Instant = instant
        }
        val logger = AppLogger(context)
        val engine = ScheduleEngine(clock, zone)
        return DayOrchestrator(
            prefs = prefs,
            engine = engine,
            templates = TemplateManager(context),
            history = HistoryManager(prefs),
            alarms = AlarmScheduler(context, SystemTimeMapper.Identity, logger),
            notificationMapper = NotificationModelMapper(engine, zone, TimeFormatter(prefs, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))),
            notificationRenderer = SilentNotificationPort,
            clock = clock,
            zone = zone,
            logger = logger,
            scope = CoroutineScope(SupervisorJob() + dispatcher),
        )
    }

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
