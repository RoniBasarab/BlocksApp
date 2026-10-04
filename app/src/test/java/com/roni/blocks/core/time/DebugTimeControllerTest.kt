package com.roni.blocks.core.time

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.alarms.AlarmScheduler
import com.roni.blocks.core.daystate.DayEvent
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.daystate.isDishesSlot
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.notifications.NotificationModel
import com.roni.blocks.core.notifications.NotificationModelMapper
import com.roni.blocks.core.notifications.NotificationPort
import com.roni.blocks.core.schedule.ScheduleEngine
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.storage.TemplateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DebugTimeControllerTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val zone = TimeZone.of("Asia/Jerusalem")

    private lateinit var clock: DebugClock
    private lateinit var orchestrator: DayOrchestrator
    private lateinit var controller: DebugTimeController

    @Before
    fun setUp() {
        clock = DebugClock(context).apply { setOffsetMinutes(0) }
        val prefs = PrefsStorage(context)
        val logger = AppLogger(context)
        val engine = ScheduleEngine(clock, zone)
        orchestrator = DayOrchestrator(
            prefs = prefs,
            engine = engine,
            templates = TemplateManager(context),
            history = HistoryManager(prefs),
            alarms = AlarmScheduler(context, SystemTimeMapper.Identity, logger),
            notificationMapper = NotificationModelMapper(
                engine,
                zone,
                TimeFormatter(prefs, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)),
            ),
            notificationRenderer = SilentNotificationPort,
            clock = clock,
            zone = zone,
            logger = logger,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        controller = DebugTimeController(clock, orchestrator, logger, zone)
    }

    @Test
    fun evening_canBeReachedWithoutWaiting() = runBlocking {
        orchestrator.handle(DayEvent.StartDay(TemplateId.SUN_TUE_WED))

        assertTrue("20:05 should be a time the clock accepts", controller.setTo("20:05"))
        assertEquals(DayState.KidTime, orchestrator.state.value)

        controller.sendKidAsleep()
        assertEquals(DayState.Evening(0), orchestrator.state.value)

        assertTrue("skipping dishes needs the dishes slot", controller.skipDishes())
        assertTrue(
            "the evening choice is pending once dishes are over",
            orchestrator.state.value.isDishesSlot,
        )
        val dishes = orchestrator.segments.value.last { it.type == BlockType.Dishes }
        assertTrue(
            "the clock should now be past the dishes window",
            clock.now() >= requireNotNull(dishes.plannedEnd),
        )
    }

    private object SilentNotificationPort : NotificationPort {
        override fun ensureChannels() = Unit

        override fun render(model: NotificationModel?, transition: Boolean) = Unit

        override fun onDismissed(key: String) = Unit

        override fun sendTestNotification() = Unit
    }
}