package com.roni.blocks.testing

import android.content.Context
import com.roni.blocks.core.alarms.AlarmScheduler
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.notifications.NotificationModel
import com.roni.blocks.core.notifications.NotificationModelMapper
import com.roni.blocks.core.notifications.NotificationPort
import com.roni.blocks.core.schedule.ScheduleEngine
import com.roni.blocks.core.storage.CategoryActions
import com.roni.blocks.core.storage.CategoryStore
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.storage.TemplateActions
import com.roni.blocks.core.storage.TemplateStore
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.core.time.SystemTimeMapper
import com.roni.blocks.core.time.TimeFormatter
import com.roni.blocks.core.time.at
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

/** A fixed clock the orchestrator reads, the same approach [DayOrchestratorTest] uses. */
class MovableClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}

/** Records nothing; the track tests assert on storage, not on notifications. */
object SilentNotificationPort : NotificationPort {
    override fun ensureChannels() {}
    override fun render(model: NotificationModel?, transition: Boolean, alert: Boolean) {}
    override fun onDismissed(key: String) {}
    override fun sendTestNotification() {}
}

/**
 * The template and category actions for the track tests (3.1.0): real Robolectric stores and an
 * orchestrator built the way `DayOrchestratorTest` builds one, with a fake clock and the store
 * passed in.
 */
fun testActions(context: Context): Pair<TemplateActions, CategoryActions> {
    val zone = TimeZone.of("Asia/Jerusalem")
    val clock = MovableClock(LocalTime(9, 0).at(LocalDate(2026, 9, 27), zone))
    val prefs = PrefsStorage(context, UnconfinedTestDispatcher())
    val logger = AppLogger(context)
    val engine = ScheduleEngine(clock, zone)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val store = TemplateStore(context, prefs, logger)
    val categories = CategoryStore(prefs)
    val day = DayOrchestrator(
        prefs = prefs,
        engine = engine,
        templates = TemplateManager(context),
        history = HistoryManager(prefs),
        alarms = AlarmScheduler(context, SystemTimeMapper.Identity, logger),
        notificationMapper = NotificationModelMapper(engine, zone, TimeFormatter(prefs, scope)),
        notificationRenderer = SilentNotificationPort,
        clock = clock,
        zone = zone,
        logger = logger,
        scope = scope,
        categories = categories,
        templateStore = store,
    )
    return TemplateActions(store, day) to CategoryActions(categories, store, day)
}