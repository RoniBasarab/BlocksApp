package com.roni.blocks.core.daystate

import com.roni.blocks.core.alarms.AlarmScheduler
import com.roni.blocks.core.di.ApplicationScope
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.DayRecord
import com.roni.blocks.core.model.DebtRecord
import com.roni.blocks.core.model.InstantSerializer
import com.roni.blocks.core.model.LocalDateSerializer
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.notifications.ActionEvent
import com.roni.blocks.core.notifications.NotificationModelMapper
import com.roni.blocks.core.notifications.NotificationPort
import com.roni.blocks.core.schedule.DayBook
import com.roni.blocks.core.schedule.ScheduleEngine
import com.roni.blocks.core.schedule.SegmentBook
import com.roni.blocks.core.schedule.timelineAt
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.debtMinutes
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.core.time.at
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable

/**
 * The single writer of the day's state: it applies events, persists the result, and pushes the
 * consequences (alarm, notification, history) so none of those can drift from the state.
 *
 * Rules: restore runs once behind a mutex and a failed restore still counts as restored, so a bad
 * prefs file can never strand the app on the splash; every mutation ends in `sideEffects()`.
 *
 * Talks to: [DayStateMachine], [ScheduleEngine], [TemplateManager], [HistoryManager],
 * [AlarmScheduler], [NotificationModelMapper], [NotificationPort], [PrefsStorage], [Clock].
 */
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
    private val restoreMutex = Mutex()

    init {
        scope.launch {
            ensureRestored()
        }
        scope.launch {
            state.catch { logger.error("state flow", it) }.collect { }
        }
    }

    fun send(event: DayEvent) {
        scope.launch {
            ensureRestored()
            handle(event)
        }
    }

    suspend fun handle(event: DayEvent) {
        ensureRestored()
        apply(event)
    }

    /**
     * Idempotent and single-flight. Every entry point calls this before touching the day: the UI
     * gate, [send], and each receiver. It always finishes and always ends up marking the day
     * restored, because a restore that throws must not leave the splash up forever.
     */
    suspend fun ensureRestored() {
        if (_isRestored.value) return
        restoreMutex.withLock {
            if (_isRestored.value) return
            try {
                restoreFromStorage()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Restore failed; continuing with the safest state", e)
            } finally {
                _isRestored.value = true
            }
        }
    }

    /**
     * A tap on a notification button. [expectedBlockId] is the block the button was built for: a
     * tap whose block is no longer the current one is stale and does nothing, which is also what
     * makes a double tap advance exactly once.
     */
    suspend fun handleNotificationAction(event: ActionEvent, expectedBlockId: String?) {
        ensureRestored()
        val current = _state.value.currentBlockId()
        if (expectedBlockId != null && expectedBlockId != current) {
            logger.info("stale $event for $expectedBlockId, now ${current ?: "-"}")
            return
        }
        apply(event.toDayEvent(expectedBlockId))
    }

    suspend fun onAlarm() {
        ensureRestored()
        val today = today()
        val stored = prefs.read(PrefsFile.DAY, DATE, LocalDateSerializer) ?: today
        val rolled = rollover(_state.value, stored, today)
        if (rolled != _state.value) {
            _state.value = rolled
            book = DayBook()
            _segments.value = emptyList()
            persist()
        }
        if (applyOneMissedTransition()) return
        sideEffects()
    }

    /**
     * The clock moved, but the day did not actually progress. Re-evaluate and reschedule without
     * advancing a block: nothing was run, so there is nothing to record.
     */
    suspend fun onTimeChanged() {
        ensureRestored()
        if (endDayIfDue()) return
        sideEffects()
    }

    /**
     * The debug clock was moved on purpose, so the day catches up to where it now is: every
     * transition the clock already made true is applied, exactly as a cold start would. This is
     * the opposite of [onTimeChanged], which is for a clock that drifted while nothing was missed.
     */
    suspend fun onTimeTravelled() {
        ensureRestored()
        replayMissedTransitions()
        if (_state.value == DayState.Ended || _state.value == DayState.Off) return
        sideEffects()
    }

    /** Settings "Reset today": back to NotStarted, nothing recorded, alerts restored for a fresh pick. */
    suspend fun resetToday() {
        ensureRestored()
        book = DayBook()
        _state.value = DayState.NotStarted
        _segments.value = emptyList()
        persist()
        alarms.cancel()
        sideEffects()
    }

    private suspend fun apply(event: DayEvent) {
        val now = clock.now()
        if (event is DayEvent.SwapTemplate) return applySwap(event, now)
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

    /**
     * The one event whose result is not a function of the old state: where the day lands depends on
     * which new-template block contains now, so it is resolved here instead of by [DayStateMachine].
     */
    private suspend fun applySwap(event: DayEvent.SwapTemplate, now: kotlin.time.Instant) {
        if (now >= kotlinx.datetime.LocalTime(22, 30).at(today(), zone)) {
            logger.info("refusing ${event.template} at $now: past 22:30")
            return
        }
        record(event, now)
        val next = when {
            event.template == TemplateId.OFF -> DayState.Off
            templates.get(event.template).blocks.getOrNull(book.index)?.type == BlockType.KidTime -> DayState.KidTime
            else -> DayState.Running(event.template, book.index)
        }
        logger.info("swapped to ${event.template} at $now -> $next")
        _state.value = next
        _segments.value = book.segments
        persist()
        if (next == DayState.Off) flush(book.flags + DayFlag.OFF_DAY) else sideEffects(transition = true)
    }

    private fun record(event: DayEvent, now: kotlin.time.Instant) {
        val today = today()
        if (event is DayEvent.StartDay && event.template == com.roni.blocks.core.model.TemplateId.OFF) {
            book = DayBook()
            return
        }
        book = when (event) {
            is DayEvent.StartDay -> SegmentBook.startDay(templates.get(event.template), today, zone, now)
            is DayEvent.SwapTemplate -> SegmentBook.swapTemplate(book, templates.get(event.template), today, zone, now)
            is DayEvent.Next -> SegmentBook.next(book, currentTemplate() ?: return, today, zone, now)
            DayEvent.Pause -> SegmentBook.pause(book, now)
            DayEvent.Resume -> SegmentBook.resumePause(book, now)
            DayEvent.KidTimeStarted -> SegmentBook.enterKidTime(book, currentTemplate() ?: return, today, zone)
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
        val record = DayRecord(today, template, _segments.value, flags, book.plannedBlocks.ifEmpty { null })
        history.writeDay(record)
        history.writeDebt(DebtRecord(today, debtMinutes(_segments.value), 0))
        rebuildWeek(today)
        notificationRenderer.render(null, transition = true)
        alarms.cancel()
        if (_state.value != DayState.Off && _state.value != DayState.Ended) {
            _state.value = DayState.Ended
        }
        persist()
        logger.info("flushed $today $flags")
    }

    private suspend fun rebuildWeek(date: LocalDate) {
        val week = history.weekStart(date)
        val weekDays = history.days().filter { history.weekStart(it.date) == week }
        val debts = weekDays.mapNotNull { day ->
            history.readDebt(day.date)?.let { debt -> day.date to debt }
        }.toMap()
        history.writeWeek(history.aggregateWeek(week, weekDays, debts))
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
        val timeline = engine.timelineAt(template, today, zone, _segments.value, now, book.planStartsAt)
        notificationRenderer.render(notificationMapper.from(_state.value, timeline, now), transition)
    }

    /**
     * Reads the day back and puts the process back where it was. Deliberately does not call [send],
     * [ensureRestored] or anything else that takes [restoreMutex]: the replay below has to reach the
     * internal transition path or a cold start deadlocks against itself.
     */
    private suspend fun restoreFromStorage() {
        val today = today()
        val storedDate = prefs.read(PrefsFile.DAY, DATE, LocalDateSerializer)
        val stored = prefs.read(PrefsFile.DAY, STATE, DayStateSerializer) ?: DayState.NotStarted
        _state.value = if (storedDate != null && storedDate != today) DayState.NotStarted else stored
        _segments.value = prefs.read(PrefsFile.DAY, SEGMENTS, ListSerializer(Segment.serializer())) ?: emptyList()
        book = rehydratedBook().copy(
            planStartsAt = prefs.read(PrefsFile.DAY, PLAN_STARTS_AT, InstantSerializer.nullable),
        )
        persist()
        replayMissedTransitions()
        if (_state.value == DayState.Ended || _state.value == DayState.Off) return
        sideEffects()
    }

    /**
     * Segments are the day's history, so the book has to be rebuilt from them: without this the
     * first action after a process death would write a fresh segment list and drop everything the
     * day had already recorded.
     */
    private fun rehydratedBook(): DayBook {
        val template = _state.value.templateId()?.let { runCatching { templates.get(it) }.getOrNull() }
            ?: return DayBook(segments = _segments.value)
        return SegmentBook.rehydrate(_segments.value, template, today(), zone)
    }

    private suspend fun replayMissedTransitions() {
        var steps = 0
        while (applyOneMissedTransition()) {
            steps++
            if (steps >= MAX_REPLAY_STEPS) {
                logger.error("Replay stopped at the $MAX_REPLAY_STEPS step bound; the day may be behind")
                return
            }
        }
    }

    /** One automatic transition the clock has already made true, or false when there is none left. */
    private suspend fun applyOneMissedTransition(): Boolean {
        val template = currentTemplate() ?: return false
        val now = clock.now()
        val today = today()
        if (_state.value is DayState.Paused) {
            val blockEnd = book.segments.lastOrNull { it.kind == SegmentKind.PLANNED && it.actualEnd == null }?.plannedEnd
            if (blockEnd != null && now >= blockEnd) {
                apply(DayEvent.Resume)
                return true
            }
        }
        if (_state.value is DayState.Running) {
            val kidStart = template.blocks.firstOrNull { it.type == BlockType.KidTime }?.start
            if (kidStart != null && now >= kidStart.at(today, zone)) {
                apply(DayEvent.KidTimeStarted)
                return true
            }
            val freeEnd = book.segments.lastOrNull { it.kind == SegmentKind.SYNTHETIC_FREE && it.actualEnd == null }?.plannedEnd
            if (freeEnd != null && now >= freeEnd) {
                apply(DayEvent.Next(_state.value.currentBlockId().orEmpty()))
                return true
            }
        }
        return endDayIfDue()
    }

    private suspend fun persist() {
        prefs.write(PrefsFile.DAY, STATE, _state.value, DayStateSerializer)
        prefs.write(PrefsFile.DAY, SEGMENTS, _segments.value, ListSerializer(Segment.serializer()))
        prefs.write(PrefsFile.DAY, DATE, today(), LocalDateSerializer)
        prefs.write(PrefsFile.DAY, PLAN_STARTS_AT, book.planStartsAt, InstantSerializer.nullable)
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
        const val PLAN_STARTS_AT = "plan_starts_at"
        const val MAX_REPLAY_STEPS = 500
    }
}
