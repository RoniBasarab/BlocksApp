package com.roni.blocks.core.daystate

import com.roni.blocks.core.alarms.AlarmScheduler
import com.roni.blocks.core.di.ApplicationScope
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.DayRecord
import com.roni.blocks.core.model.InstantSerializer
import com.roni.blocks.core.model.LocalDateSerializer
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.Template
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.model.TodayPlan
import com.roni.blocks.core.notifications.ActionEvent
import com.roni.blocks.core.notifications.NotificationModelMapper
import com.roni.blocks.core.notifications.NotificationPort
import com.roni.blocks.core.schedule.CurrentBlock
import com.roni.blocks.core.schedule.DayBook
import com.roni.blocks.core.schedule.PlanEdit
import com.roni.blocks.core.schedule.PlanEditor
import com.roni.blocks.core.schedule.ScheduleEngine
import com.roni.blocks.core.schedule.SegmentBook
import com.roni.blocks.core.schedule.timelineAt
import com.roni.blocks.core.schedule.toBlockSpecs
import com.roni.blocks.core.schedule.toPlanBlocks
import com.roni.blocks.core.storage.HistoryManager
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
    private val _todayPlan = MutableStateFlow<TodayPlan?>(null)
    val todayPlan: StateFlow<TodayPlan?> = _todayPlan.asStateFlow()

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
                logger.info("restored day_state=${_state.value}")
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

    /**
     * Applies one edit to today's plan; false when it isn't allowed or changes nothing. It goes
     * through [ensureRestored] and the same in-memory-then-save path a transition uses, so an alarm
     * cannot interleave, and it only touches the day in [DayState.Running] or [DayState.Paused].
     */
    suspend fun editPlan(edit: PlanEdit): Boolean {
        ensureRestored()
        val state = _state.value
        if (state !is DayState.Running && state !is DayState.Paused) {
            logger.info("refusing plan edit $edit from $state")
            return false
        }
        val plan = _todayPlan.value
        val current = currentForEditing()
        if (plan == null || current == null) {
            logger.info("refusing plan edit $edit: there is no plan to edit")
            return false
        }
        val nowMin = clock.now().toLocalDateTime(zone).time.let { it.hour * 60 + it.minute }
        val edited = PlanEditor.apply(plan.blocks.toPlanBlocks(), edit, nowMin, current)
        if (edited == null) {
            logger.info("refusing plan edit $edit: not allowed or it changes nothing")
            return false
        }
        val blocks = edited.toBlockSpecs(plan.blocks)
        val index = blocks.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        _todayPlan.value = plan.copy(blocks = blocks, edited = true)
        _state.value = when (state) {
            is DayState.Running -> state.copy(blockIndex = index)
            is DayState.Paused -> state.copy(running = state.running.copy(blockIndex = index))
            else -> state
        }
        // Ran over is measured against the edited plan, so the current block's open planned segment
        // takes its new end; an edit to a later block can shorten the current free time too.
        val currentEnd = blocks.firstOrNull { it.id == current.id }?.end?.at(today(), zone)
        if (currentEnd != null) {
            book = book.copy(
                segments = book.segments.map { segment ->
                    if (segment.kind == SegmentKind.PLANNED && segment.actualEnd == null && segment.blockId == current.id) {
                        segment.copy(plannedEnd = currentEnd)
                    } else {
                        segment
                    }
                },
                index = index,
            )
            _segments.value = book.segments
        }
        persist()
        sideEffects()
        logger.info("edited plan with $edit")
        return true
    }

    /**
     * The block the day is on, for the edit UI. [CurrentBlock.endEditable] is false once Next ended
     * that block early: its leftover is free time, so only its neighbours can still move its edge.
     */
    fun currentForEditing(): CurrentBlock? {
        val id = _state.value.currentBlockId() ?: return null
        val open = _segments.value.lastOrNull { it.actualEnd == null }
        return CurrentBlock(id, endEditable = open == null || open.kind != SegmentKind.SYNTHETIC_FREE)
    }

    suspend fun onAlarm() {
        ensureRestored()
        applyRollover()
        if (applyOneMissedTransition()) return
        sideEffects()
    }

    private suspend fun applyRollover() {
        val today = today()
        val stored = prefs.read(PrefsFile.DAY, DATE, LocalDateSerializer) ?: today
        val rolled = rollover(_state.value, stored, today)
        if (rolled != _state.value) {
            _state.value = rolled
            book = DayBook()
            _segments.value = emptyList()
            _todayPlan.value = null
            persist()
        }
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
        applyRollover()
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
        _todayPlan.value = null
        persist()
        alarms.cancel()
        sideEffects()
    }

    private suspend fun apply(event: DayEvent) {
        val now = clock.now()
        if (event is DayEvent.StartDay) {
            val nowLocal = now.toLocalDateTime(zone).time
            val tooLate = (nowLocal.hour > 22) || (nowLocal.hour == 22 && nowLocal.minute >= 30)
            if (tooLate) {
                logger.info("refusing StartDay after 22:30")
                return
            }
        }
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
        if (event is DayEvent.EndDay && event.reason == EndReason.EMERGENCY) {
            book = book.copy(flags = book.flags + DayFlag.EMERGENCY_STOP)
        }
        _state.value = when {
            event is DayEvent.StartDay && event.template != TemplateId.OFF -> {
                val template = templates.get(event.template)
                _todayPlan.value = TodayPlan(event.template, template.blocks)
                val currentBlock = template.blocks.getOrNull(book.index)
                if (currentBlock?.type == BlockType.KidTime) {
                    DayState.KidTime
                } else {
                    DayState.Running(event.template, book.index.coerceAtLeast(0))
                }
            }
            event is DayEvent.Next && next is DayState.Running -> next.copy(blockIndex = book.index.coerceAtLeast(0))
            else -> next
        }
        _segments.value = book.segments
        persist()
        if (_state.value == DayState.Ended || _state.value == DayState.Off) {
            val fromEvent = buildSet {
                if (_state.value == DayState.Off) add(DayFlag.OFF_DAY)
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
     * The plan is rebuilt first (old blocks up to the cutoff, new template from now) and the
     * segments are re-planned around that same plan, so both always agree.
     */
    private suspend fun applySwap(event: DayEvent.SwapTemplate, now: kotlin.time.Instant) {
        if (now >= kotlinx.datetime.LocalTime(22, 30).at(today(), zone)) {
            logger.info("refusing ${event.template} at $now: past 22:30")
            return
        }
        if (event.template == TemplateId.OFF) {
            _todayPlan.value = null
        } else {
            val template = templates.get(event.template)
            val oldBlocks = _todayPlan.value?.blocks.orEmpty()
            val cutoff = if (_state.value == DayState.Ended) {
                book.segments.lastOrNull { it.actualEnd != null }?.actualEnd ?: now
            } else {
                now
            }
            val blocks = SegmentBook.swapPlan(oldBlocks, template, today(), zone, now, cutoff)
            _todayPlan.value = TodayPlan(event.template, blocks)
            book = SegmentBook.swapTemplate(book, Template(template.id, template.name, blocks), today(), zone, now)
        }
        _segments.value = book.segments
        val next = when {
            event.template == TemplateId.OFF -> DayState.Off
            currentTemplate()?.blocks?.getOrNull(book.index)?.type == BlockType.KidTime -> DayState.KidTime
            else -> {
                val planId = book.segments.lastOrNull { it.actualEnd == null }?.blockId
                val index = planId?.let { id -> _todayPlan.value?.blocks?.indexOfFirst { it.id == id } }
                    ?.takeIf { it >= 0 } ?: book.index
                DayState.Running(event.template, index)
            }
        }
        logger.info("swapped to ${event.template} at $now -> $next")
        _state.value = next
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
            is DayEvent.Next -> SegmentBook.next(book, currentTemplate() ?: return, today, zone, now)
            DayEvent.Pause -> SegmentBook.pause(book, now)
            DayEvent.Resume -> SegmentBook.resumePause(book, now)
            DayEvent.KidTimeStarted -> SegmentBook.enterKidTime(book, currentTemplate() ?: return, today, zone)
            DayEvent.KidAsleep -> SegmentBook.kidAsleep(book, today, zone, now)
            is DayEvent.EveningChoice -> SegmentBook.eveningChoice(book, event.choice, today, zone, now)
            is DayEvent.EndDay -> SegmentBook.endDay(book, now, _state.value is DayState.KidTime)
            DayEvent.TakeOffDay -> DayBook()
            is DayEvent.SwapTemplate -> book
        }
    }

    /** Today's blocks as a template, from the plan when there is one, else from the raw template. */
    private fun currentTemplate(): Template? {
        val plan = _todayPlan.value
        if (plan != null) {
            val name = runCatching { templates.get(plan.templateId).name }.getOrNull() ?: return null
            return Template(plan.templateId, name, plan.blocks)
        }
        return _state.value.templateId()?.let { runCatching { templates.get(it) }.getOrNull() }
    }

    private suspend fun flush(flags: Set<DayFlag>) {
        val today = today()
        val template = _state.value.templateId() ?: prefs.read(PrefsFile.DAY, TEMPLATE, TemplateId.serializer())
        val plan = _todayPlan.value
        val finalFlags = if (plan?.edited == true) flags + DayFlag.PLAN_EDITED else flags
        val record = DayRecord(today, template, _segments.value, finalFlags, plannedBlocks = plan?.blocks?.ifEmpty { null })
        history.writeDay(record)
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
        history.writeWeek(history.aggregateWeek(week, weekDays))
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
        val template = currentTemplate()
        val index = (_state.value as? DayState.Running)?.blockIndex ?: 0
        val wake = engine.nextWakeUp(_state.value, today, now, template, index)
        if (wake == null) alarms.cancel() else alarms.schedule(wake)
        val timeline = engine.timelineAt(template, today, zone, _segments.value, now)
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
        val storedPlanStartsAt = prefs.read(PrefsFile.DAY, PLAN_STARTS_AT, InstantSerializer.nullable)
        var plan = prefs.read(PrefsFile.DAY, TODAY_PLAN, TodayPlan.serializer().nullable)
        if (plan == null && _state.value.isActive()) {
            plan = migratePlan(storedPlanStartsAt)
            _state.value = plan?.let { repointBlockIndex(_state.value, it) } ?: _state.value
        }
        _todayPlan.value = plan
        book = rehydratedBook()
        if (storedPlanStartsAt != null) prefs.remove(PrefsFile.DAY, PLAN_STARTS_AT)
        persist()
        replayMissedTransitions()
        if (_state.value == DayState.Ended || _state.value == DayState.Off) return
        sideEffects()
    }

    /**
     * Builds the 2.0.0 plan for a day saved by 1.5.1: the day's template minus the blocks that had
     * already ended by [planStartsAt]. 1.5.1 never persisted its in-memory swap snapshot, so the
     * planned-blocks path of the original migration plan is dead; the segments below are already
     * the only record of what the day actually did.
     */
    private suspend fun migratePlan(planStartsAt: kotlin.time.Instant?): TodayPlan? {
        val templateId = _state.value.templateId()
            ?: prefs.read(PrefsFile.DAY, TEMPLATE, TemplateId.serializer())
            ?: return null
        val template = runCatching { templates.get(templateId) }.getOrNull() ?: return null
        val blocks = template.blocks
            .filter { spec -> planStartsAt == null || spec.end.at(today(), zone) > planStartsAt }
            .distinctBy { it.id }
        return TodayPlan(templateId, blocks)
    }

    /** Points [state]'s block index at the same block id inside the migrated plan, which may sit at a different index. */
    private fun repointBlockIndex(state: DayState, plan: TodayPlan): DayState = when (state) {
        is DayState.Running -> {
            val id = templateBlockId(state.template, state.blockIndex)
            val index = plan.blocks.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: fallbackIndex(state.blockIndex, plan)
            if (index == state.blockIndex) state else state.copy(blockIndex = index)
        }
        is DayState.Paused -> {
            val id = templateBlockId(state.running.template, state.running.blockIndex)
            val index = plan.blocks.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: fallbackIndex(state.running.blockIndex, plan)
            if (index == state.running.blockIndex) state else state.copy(running = state.running.copy(blockIndex = index))
        }
        else -> state
    }

    private fun fallbackIndex(oldIndex: Int, plan: TodayPlan): Int =
        oldIndex.coerceAtMost(plan.blocks.lastIndex.coerceAtLeast(0))

    /**
     * Segments are the day's history, so the book has to be rebuilt from them: without this the
     * first action after a process death would write a fresh segment list and drop everything the
     * day had already recorded.
     */
    private fun rehydratedBook(): DayBook {
        val template = currentTemplate() ?: return DayBook(segments = _segments.value)
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
        prefs.write(PrefsFile.DAY, TODAY_PLAN, _todayPlan.value, TodayPlan.serializer().nullable)
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

    private fun blockIdAt(template: TemplateId, index: Int): String? {
        val plan = _todayPlan.value
        return if (plan != null && plan.templateId == template) plan.blocks.getOrNull(index)?.id
        else templateBlockId(template, index)
    }

    private fun templateBlockId(template: TemplateId, index: Int): String? =
        runCatching { templates.get(template).blocks.getOrNull(index)?.id }.getOrNull()

    private companion object {
        const val STATE = "day_state"
        const val SEGMENTS = "today_segments"
        const val DATE = "today_date"
        const val TEMPLATE = "template_id"
        const val TODAY_PLAN = "today_plan"
        const val PLAN_STARTS_AT = "plan_starts_at"
        const val MAX_REPLAY_STEPS = 500
    }
}
