package com.roni.blocks.core.daystate

import com.roni.blocks.core.alarms.AlarmScheduler
import com.roni.blocks.core.di.ApplicationScope
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.DayRecord
import com.roni.blocks.core.model.DebtRecord
import com.roni.blocks.core.model.LocalDateSerializer
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.notifications.ActionEvent
import com.roni.blocks.core.notifications.NotificationModelMapper
import com.roni.blocks.core.notifications.NotificationPort
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.schedule.DayBook
import com.roni.blocks.core.schedule.ScheduleEngine
import com.roni.blocks.core.schedule.SegmentBook
import com.roni.blocks.core.schedule.timelineAt
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.core.time.at
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.builtins.ListSerializer

@Singleton
class DayOrchestrator @Inject constructor(
    private val prefs: PrefsStorage,
    private val engine: ScheduleEngine,
    private val templates: TemplateManager,
    private val history: HistoryManager,
    private val alarms: AlarmScheduler,
    private val notificationMapper: NotificationModelMapper,
    private val notificationRenderer: NotificationPort,
    private val clock: Clock,
    private val zone: TimeZone,
    private val logger: AppLogger,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<DayState>(DayState.NotStarted)
    val state: StateFlow<DayState> = _state.asStateFlow()
    private val _segments = MutableStateFlow<List<Segment>>(emptyList())
    val segments: StateFlow<List<Segment>> = _segments.asStateFlow()

    /**
     * False until prefs are read and the date rollover is applied. [state] is [DayState.NotStarted]
     * until then, so callers wanting today's real state must wait for this flag first.
     */
    private val _isRestored = MutableStateFlow(false)
    val isRestored: StateFlow<Boolean> = _isRestored.asStateFlow()
    private var book = DayBook()

    init {
        scope.launch {
            restore()
        }
        scope.launch {
            state.catch { logger.error("state flow", it) }.collect { }
        }
    }

    fun send(event: DayEvent) {
        scope.launch { handle(event) }
    }

    suspend fun handle(event: DayEvent) = apply(event)

    /**
     * A tap on a notification button. [expectedBlockId] is the block the button was built for: a
     * tap whose block is no longer the current one is stale and does nothing, which is also what
     * makes a double tap advance exactly once.
     */
    suspend fun handleNotificationAction(event: ActionEvent, expectedBlockId: String?) {
        val current = _state.value.currentBlockId()
        if (expectedBlockId != null && expectedBlockId != current) {
            logger.info("stale $event for $expectedBlockId, now ${current ?: "-"}")
            return
        }
        handle(event.toDayEvent(expectedBlockId))
    }

    suspend fun onAlarm() {
        val today = today()
        val stored = prefs.read(PrefsFile.DAY, DATE, LocalDateSerializer) ?: today
        val rolled = rollover(_state.value, stored, today)
        if (rolled != _state.value) {
            _state.value = rolled
            book = DayBook()
            _segments.value = emptyList()
            persist()
        }
        val template = currentTemplate()
        val now = clock.now()
        if (template != null && _state.value is DayState.Running) {
            val kidStart = template.blocks.firstOrNull { it.type == com.roni.blocks.core.model.BlockType.KidTime }?.start
            if (kidStart != null && now >= kidStart.at(today, zone)) {
                apply(DayEvent.KidTimeStarted)
                return
            }
            val freeEnd = book.segments.lastOrNull { it.kind == com.roni.blocks.core.model.SegmentKind.SYNTHETIC_FREE && it.actualEnd == null }?.plannedEnd
            if (freeEnd != null && now >= freeEnd) {
                apply(DayEvent.Next(_state.value.currentBlockId().orEmpty()))
                return
            }
        }
        if (endDayIfDue()) return
        sideEffects()
    }

    /**
     * The clock moved, but the day did not actually progress. Re-evaluate and reschedule without
     * advancing a block: nothing was run, so there is nothing to record.
     */
    suspend fun onTimeChanged() {
        if (endDayIfDue()) return
        sideEffects()
    }

    private suspend fun apply(event: DayEvent) {
        val now = clock.now()
        if (event is DayEvent.Next) {
            val current = _state.value.currentBlockId()
            if (event.expectedBlockId != current) {
                logger.info("dropping Next for ${event.expectedBlockId}, current is ${current ?: "-"}")
                return
            }
        }
        val next = DayStateMachine.transition(_state.value, event, now)
        if (next == null) {
            logger.info("illegal $event from ${_state.value}")
            return
        }
        logger.info("transition ${_state.value} + $event -> $next")
        val fromKid = _state.value is DayState.KidTime
        record(event, now)
        _state.value = next
        _segments.value = book.segments
        persist()
        if (next == DayState.Ended || next == DayState.Off) {
            val fromEvent = buildSet {
                if (next == DayState.Off) add(DayFlag.OFF_DAY)
                if (event is DayEvent.EndDay) {
                    if (fromKid) add(DayFlag.EVENING_SKIPPED)
                    if (event.reason == EndReason.EMERGENCY) add(DayFlag.EMERGENCY_STOP)
                }
            }
            flush(fromEvent + book.flags)
        } else {
            sideEffects(transition = true)
        }
    }

    private fun record(event: DayEvent, now: kotlin.time.Instant) {
        val today = today()
        if (event is DayEvent.StartDay && event.template == com.roni.blocks.core.model.TemplateId.OFF) {
            book = DayBook()
            return
        }
        val template = if (event is DayEvent.StartDay) templates.get(event.template) else currentTemplate() ?: return
        book = when (event) {
            is DayEvent.StartDay -> SegmentBook.startDay(template, today, zone, now)
            is DayEvent.Next -> SegmentBook.next(book, template, today, zone, now)
            DayEvent.Pause -> SegmentBook.pause(book, now)
            DayEvent.Resume -> SegmentBook.resumePause(book, now)
            DayEvent.KidTimeStarted -> SegmentBook.enterKidTime(book, template, today, zone)
            DayEvent.KidAsleep -> SegmentBook.kidAsleep(book, today, zone, now)
            is DayEvent.EveningChoice -> SegmentBook.eveningChoice(book, event.choice, today, zone, now)
            is DayEvent.EndDay -> SegmentBook.endDay(book, now, _state.value is DayState.KidTime)
            DayEvent.TakeOffDay -> DayBook()
        }
    }

    private fun currentTemplate() = _state.value.templateId()?.let { runCatching { templates.get(it) }.getOrNull() }

    private suspend fun flush(flags: Set<DayFlag>) {
        val today = today()
        val template = _state.value.templateId() ?: prefs.read(PrefsFile.DAY, TEMPLATE, TemplateId.serializer())
        val record = DayRecord(today, template, _segments.value, flags)
        history.writeDay(record)
        history.writeDebt(DebtRecord(today, history.debtMinutes(_segments.value), 0))
        notificationRenderer.render(null, transition = true)
        alarms.cancel()
        if (_state.value != DayState.Off && _state.value != DayState.Ended) {
            _state.value = DayState.Ended
        }
        persist()
        logger.info("flushed $today $flags")
    }

    private suspend fun endDayIfDue(): Boolean {
        val now = clock.now()
        val flags = engine.autoEndFlags(_state.value, now, today()) ?: return false
        book = SegmentBook.endDay(book, now, _state.value is DayState.KidTime)
        _segments.value = book.segments
        flush(book.flags + flags)
        return true
    }

    private suspend fun sideEffects(transition: Boolean = false) {
        val now = clock.now()
        val today = today()
        val template = _state.value.templateId()?.let { templates.get(it) }
        val index = (_state.value as? DayState.Running)?.blockIndex ?: 0
        val wake = engine.nextWakeUp(_state.value, today, now, template, index)
        if (wake == null) alarms.cancel() else alarms.schedule(wake)
        val timeline = engine.timelineAt(template, today, zone, _segments.value, now)
        notificationRenderer.render(notificationMapper.from(_state.value, timeline, now), transition)
    }

    private suspend fun restore() {
        val today = today()
        val storedDate = prefs.read(PrefsFile.DAY, DATE, LocalDateSerializer)
        val stored = prefs.read(PrefsFile.DAY, STATE, DayStateSerializer) ?: DayState.NotStarted
        _state.value = if (storedDate != null && storedDate != today) DayState.NotStarted else stored
        _segments.value = prefs.read(PrefsFile.DAY, SEGMENTS, ListSerializer(Segment.serializer())) ?: emptyList()
        persist()
        sideEffects()
        _isRestored.value = true
    }

    private suspend fun persist() {
        prefs.write(PrefsFile.DAY, STATE, _state.value, DayStateSerializer)
        prefs.write(PrefsFile.DAY, SEGMENTS, _segments.value, ListSerializer(Segment.serializer()))
        prefs.write(PrefsFile.DAY, DATE, today(), LocalDateSerializer)
        _state.value.templateId()?.let { prefs.write(PrefsFile.DAY, TEMPLATE, it, TemplateId.serializer()) }
    }

    private fun today() = clock.now().toLocalDateTime(zone).date

    /**
     * Must stay in step with the ids [NotificationModelMapper] posts, or every notification tap is
     * discarded as stale and the buttons silently stop working.
     */
    private fun DayState.currentBlockId(): String? = when (this) {
        is DayState.Running -> blockIdAt(template, blockIndex)
        is DayState.Paused -> blockIdAt(running.template, running.blockIndex)
        DayState.NotStarted, DayState.KidTime, is DayState.Evening, DayState.Ended, DayState.Off -> null
    }

    private fun blockIdAt(template: TemplateId, index: Int): String? =
        runCatching { templates.get(template).blocks.getOrNull(index)?.id }.getOrNull()

    private companion object {
        const val STATE = "day_state"
        const val SEGMENTS = "today_segments"
        const val DATE = "today_date"
        const val TEMPLATE = "template_id"
    }
}
