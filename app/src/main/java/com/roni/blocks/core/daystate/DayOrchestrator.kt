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
import com.roni.blocks.core.model.WalkLog
import com.roni.blocks.core.model.WalkSession
import com.roni.blocks.core.model.isWork
import com.roni.blocks.core.schedule.RecordEdit
import com.roni.blocks.core.schedule.toRecordRows
import com.roni.blocks.core.schedule.withRows
import com.roni.blocks.core.notifications.ActionEvent
import com.roni.blocks.core.notifications.NotificationModelMapper
import com.roni.blocks.core.notifications.NotificationModel
import com.roni.blocks.core.notifications.NotificationPort
import com.roni.blocks.core.notifications.WalkRinger
import com.roni.blocks.core.notifications.blockName
import com.roni.blocks.core.widget.WidgetPort
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
import com.roni.blocks.core.steps.StepBook
import com.roni.blocks.core.steps.StepSensor
import com.roni.blocks.core.steps.StepState
import com.roni.blocks.core.steps.StepStateSerializer
import com.roni.blocks.core.time.at
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
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
    private val widgetUpdater: WidgetPort = object : WidgetPort {
        override fun update(model: NotificationModel?) {}
    },
    /** The walk timer (2.3.0); a no-op by default so hand-built tests compile. */
    private val walkRinger: WalkRinger = object : WalkRinger {
        override fun ring(kind: com.roni.blocks.core.schedule.AlertKind, homeAt: Instant, nextLabel: String?, nextEnd: Instant?) {}
        override fun stop() {}
    },
    /** The phone's step counter (2.5.0); a no-op by default so hand-built tests compile. */
    private val stepSensor: StepSensor = object : StepSensor {
        override suspend fun read(): Long? = null
        override fun live(): Flow<Long> = emptyFlow()
    },
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

    /** The walk Home shows: the open day-block walk's segment, else a running standalone walk (2.4.0). */
    private val _walkSession = MutableStateFlow<WalkSession?>(null)
    val walkSession: StateFlow<WalkSession?> = _walkSession.asStateFlow()

    /**
     * False until prefs are read and the date rollover is applied. [state] is [DayState.NotStarted]
     * until then, so callers wanting today's real state must wait for this flag first.
     */
    private val _isRestored = MutableStateFlow(false)
    val isRestored: StateFlow<Boolean> = _isRestored.asStateFlow()
    private var book = DayBook()
    /**
     * The last instant Roni reacted to anything: a tap, a notification action, the app coming to
     * the foreground. Used to suppress nagging alerts once Roni has acknowledged the day.
     */
    private var reactedAt: Instant? = null
    /** A standalone walk (2.4.0) that runs outside the day; survives the date rollover. */
    private var standaloneWalk: WalkSession? = null
    /** Today's step arithmetic (2.5.0): the baseline and the day's total, kept across rollover. */
    private var steps = StepState()
    /** The counter reading when the open walk started (2.5.0), for the walk's own step total. */
    private var walkStartCounter: Long? = null
    private val _todaySteps = MutableStateFlow<Long?>(null)

    /** Today's steps so far (2.5.0); null until a reading has ever worked. */
    val todaySteps: StateFlow<Long?> = _todaySteps.asStateFlow()

    /** Tonight's activity picked during Dishes (2.5.0), persisted with the day; null until picked. */
    private var pendingChoice: Choice? = null
    private val _pendingEvening = MutableStateFlow<Choice?>(null)

    /** The activity picked during Dishes, before it starts (2.5.0); Home draws it as the planned block. */
    val pendingEvening: StateFlow<Choice?> = _pendingEvening.asStateFlow()

    /**
     * Today's steps including the live sensor reading while it is collected (2.5.0), for the Home
     * top bar. Emits null first, so a saved total still shows when the live sensor is silent. Nothing
     * here is ever written: the saved total only moves at a checkpoint.
     */
    val liveSteps: Flow<Long?> = stepSensor.live()
        .map { counter -> liveTotal(counter) }
        .onStart { emit(null) }

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
        if (event == ActionEvent.TURN_BACK) {
            turnBack()
            return
        }
        if (standaloneWalk != null && event == ActionEvent.START_NEXT) {
            endWalk()
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
        // Ran over is measured against the edited plan, so the current block's open segment follows
        // the new plan: a planned segment takes its new end (and new type when retyped); a
        // synthetic-free segment from "Done early" takes the current block's new end (2.2.0).
        val currentBlockSpec = blocks.firstOrNull { it.id == current.id }
        val currentEnd = currentBlockSpec?.end?.at(today(), zone)
        val currentType = currentBlockSpec?.type
        book = book.copy(
            segments = book.segments.map { segment ->
                if (segment.actualEnd == null) {
                    when {
                        segment.blockId == current.id && segment.kind == SegmentKind.PLANNED ->
                            segment.copy(
                                plannedEnd = currentEnd ?: segment.plannedEnd,
                                type = currentType ?: segment.type,
                            )
                        segment.blockId == null && segment.kind == SegmentKind.SYNTHETIC_FREE ->
                            segment.copy(plannedEnd = currentEnd ?: segment.plannedEnd)
                        else -> segment
                    }
                } else {
                    segment
                }
            },
            index = index,
        )
        _segments.value = book.segments
        reactedAt = clock.now()
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

    /**
     * A snapshot of today's record, useful for the recap UI and for correcting a past block
     * while the day is still running. Built from the in-memory book so it reflects the live
     * segments, not the last persisted record (2.2.0).
     */
    fun liveRecord(): DayRecord = DayRecord(
        date = today(),
        template = currentTemplate()?.id,
        segments = book.segments,
        flags = book.flags,
        plannedBlocks = _todayPlan.value?.blocks,
    )

    /**
     * Correct a past block of the live day: a closed PLANNED/SYNTHETIC_FREE segment or a CONSUMED
     * one. Limits the edit's end to the open segment's start (or to `now` when nothing is open).
     * Returns false when the edit isn't allowed or changes nothing. Works in every state that has
     * segments, including kid time and the evening (2.2.0).
     */
    suspend fun correctPast(edit: RecordEdit): Boolean {
        ensureRestored()
        val live = liveRecord()
        val open = book.segments.lastOrNull { it.actualEnd == null }
        val limitMin = open?.actualStart?.let { it.toLocalDateTime(zone).hour * 60 + it.toLocalDateTime(zone).minute }
            ?: clock.now().toLocalDateTime(zone).let { it.hour * 60 + it.minute }
        val rows = live.toRecordRows(zone)
        val newRows = com.roni.blocks.core.schedule.RecordEditor.apply(rows, edit, limitMin) ?: return false
        val updated = live.withRows(newRows, zone)
        book = book.copy(
            segments = updated.segments,
            flags = updated.flags,
        )
        _segments.value = book.segments
        reactedAt = clock.now()
        persist()
        sideEffects()
        return true
    }

    /**
     * The walk the start sheet would take (2.3.0): when to turn back, when Roni is home, and what
     * runs after the walk. Uses the same [PlanEditor] calls [startWalk] commits with, so the sheet
     * and the walk agree. Null when no walk fits: not running, a walk already on, or under ten
     * minutes before kid time.
     */
    fun walkPreview(minutes: Int): WalkPreview? {
        val state = _state.value
        if (state !is DayState.Running) {
            // A standalone walk (2.4.0) needs no plan: now out, now + minutes home, no "then".
            if (state is DayState.Paused || standaloneWalk != null) return null
            val now = clock.now()
            val end = now + minutes.minutes
            return WalkPreview(
                turnBackAt = now + (end - now) / 2,
                homeAt = end,
                afterTitle = null,
                afterEnd = null,
            )
        }
        val plan = _todayPlan.value ?: return null
        val currentId = state.currentBlockId() ?: return null
        val open = book.segments.lastOrNull { it.actualEnd == null }
        if (open?.type == BlockType.Walk) return null
        val now = clock.now()
        val nowMin = now.toLocalDateTime(zone).let { it.hour * 60 + it.minute }
        val today = today()
        val end = PlanEditor.walkEnd(plan.blocks.toPlanBlocks(), currentId, nowMin, minutes) ?: return null
        val inserted = PlanEditor.insertWalk(
            plan.blocks.toPlanBlocks(),
            currentId,
            nowMin,
            minutes,
            doneEarly = open?.kind == SegmentKind.SYNTHETIC_FREE,
        ) ?: return null
        val after = inserted.sortedBy { it.startMin }.firstOrNull { it.startMin == end && it.type != BlockType.Walk }
        val turnBackMin = nowMin + (end - nowMin) / 2
        return WalkPreview(
            turnBackAt = LocalTime(turnBackMin / 60, turnBackMin % 60).at(today, zone),
            homeAt = LocalTime(end / 60, end % 60).at(today, zone),
            afterTitle = after?.let { blockName(it.type) },
            afterEnd = after?.let { LocalTime(it.endMin / 60, it.endMin % 60).at(today, zone) },
        )
    }

    /**
     * Starts a walk now (2.3.0): the current block stops, the walk runs for [minutes] (cut at kid
     * time), and the current block resumes after it. A user action: it counts as Roni reacting. A
     * longer walk eats the start of the blocks after it. False when no walk fits.
     */
    suspend fun startWalk(minutes: Int): Boolean {
        ensureRestored()
        val state = _state.value
        if (state !is DayState.Running) {
            // Any other time (2.4.0): a standalone walk that leaves the day completely alone.
            if (state is DayState.Paused || standaloneWalk != null) return false
            val now = clock.now()
            walkStartCounter = checkpointCounter()
            standaloneWalk = WalkSession(now, now + minutes.minutes, now.toLocalDateTime(zone).date, walkStartCounter)
            reactedAt = now
            persist()
            sideEffects()
            logger.info("standalone walk started for $minutes min at $now")
            return true
        }
        val plan = _todayPlan.value ?: return false
        val currentId = state.currentBlockId() ?: return false
        val open = book.segments.lastOrNull { it.actualEnd == null }
        if (open?.type == BlockType.Walk) return false
        val now = clock.now()
        val nowMin = now.toLocalDateTime(zone).let { it.hour * 60 + it.minute }
        val edited = PlanEditor.insertWalk(
            plan.blocks.toPlanBlocks(),
            currentId,
            nowMin,
            minutes,
            doneEarly = open?.kind == SegmentKind.SYNTHETIC_FREE,
        ) ?: return false
        val walkIndex = edited.indexOfFirst {
            it.type == BlockType.Walk && plan.blocks.none { old -> old.id == it.id }
        }
        val blocks = edited.toBlockSpecs(plan.blocks)
        val walk = blocks.getOrNull(walkIndex) ?: return false
        _todayPlan.value = plan.copy(blocks = blocks, edited = true)
        val closed = book.segments.map { if (it.actualEnd == null) it.copy(actualEnd = now) else it }
        val walkSegment = Segment(
            type = BlockType.Walk,
            kind = SegmentKind.PLANNED,
            plannedStart = now,
            plannedEnd = walk.end.at(today(), zone),
            actualStart = now,
            actualEnd = null,
            blockId = walk.id,
        )
        book = book.copy(segments = closed + walkSegment, index = walkIndex)
        _segments.value = book.segments
        _state.value = state.copy(blockIndex = walkIndex)
        walkStartCounter = checkpointCounter()
        reactedAt = now
        persist()
        sideEffects()
        logger.info("walk started for $minutes min at $now")
        return true
    }

    /**
     * "Turn back now" on a walk before half time (2.3.0): the way home takes as long as the way out
     * did, so the walk ends at now + (now - its start). The open segment takes that end to the
     * second; the plan takes it rounded up to the minute. False when nothing is walking or half
     * time has passed.
     */
    suspend fun turnBack(): Boolean {
        ensureRestored()
        val session = standaloneWalk
        if (session != null) {
            // A standalone walk turns back the same way (2.4.0); it only moves its own end.
            val now = clock.now()
            val half = session.start + (session.end - session.start) / 2
            if (now >= half) return false
            standaloneWalk = session.copy(end = now + (now - session.start))
            stepCheckpoint()
            reactedAt = now
            persist()
            sideEffects()
            logger.info("standalone walk turns back at $now")
            return true
        }
        val state = _state.value
        if (state !is DayState.Running) return false
        val plan = _todayPlan.value ?: return false
        val open = book.segments.lastOrNull { it.actualEnd == null } ?: return false
        val walkEnd = open.plannedEnd
        if (open.type != BlockType.Walk || open.kind != SegmentKind.PLANNED || walkEnd == null) return false
        val now = clock.now()
        val half = open.actualStart + (walkEnd - open.actualStart) / 2
        if (now >= half) return false
        val newEnd = now + (now - open.actualStart)
        val local = newEnd.toLocalDateTime(zone)
        val newEndMin = local.hour * 60 + local.minute + if (local.second > 0 || local.nanosecond > 0) 1 else 0
        val walkId = open.blockId ?: return false
        val edited = PlanEditor.shortenWalk(plan.blocks.toPlanBlocks(), walkId, newEndMin) ?: return false
        _todayPlan.value = plan.copy(blocks = edited.toBlockSpecs(plan.blocks), edited = true)
        book = book.copy(
            segments = book.segments.map {
                if (it.actualEnd == null && it.type == BlockType.Walk) it.copy(plannedEnd = newEnd) else it
            },
        )
        _segments.value = book.segments
        stepCheckpoint()
        reactedAt = now
        persist()
        sideEffects()
        logger.info("turn back at $now, walk ends $newEnd")
        return true
    }

    /**
     * "End walk" on a standalone walk (2.4.0): logs it from its start to now and clears it, leaving
     * the day untouched. A day-block walk still ends through Next.
     */
    suspend fun endWalk() {
        ensureRestored()
        val session = standaloneWalk ?: return
        val now = clock.now()
        val endCounter = checkpointCounter()
        history.addWalk(session.date, WalkLog(session.start, now, walkSteps(session.startCounter, endCounter)))
        standaloneWalk = null
        reactedAt = now
        persist()
        sideEffects()
        logger.info("standalone walk ended at $now")
    }

    /** The open Dishes segment (2.5.0), or null when Dishes isn't the block now. */
    private fun openDishes(): Segment? =
        book.segments.lastOrNull { it.actualEnd == null && it.kind == SegmentKind.PLANNED && it.type == BlockType.Dishes }

    /**
     * The evening choice (2.5.0). During Dishes the pick is only remembered ([pendingEvening]); Dishes
     * runs to its planned end as usual. Once Dishes is over, or while the activity is running, the
     * pick starts/switches the activity at [now].
     */
    private suspend fun applyEveningChoice(choice: Choice, now: Instant) {
        val state = _state.value
        if (state !is DayState.Evening) {
            logger.info("ignoring evening choice $choice from $state")
            return
        }
        val dishes = openDishes()
        if (state.blockIndex == 0 && dishes?.plannedEnd != null && now < dishes.plannedEnd!!) {
            pendingChoice = choice
            persist()
            sideEffects()
            logger.info("evening pending $choice")
            return
        }
        startEvening(choice, now)
    }

    /**
     * Starts (or switches to) [choice] at [at]: the open segment ends and a new EveningChoice segment
     * runs to 22:30. Every activity segment is its own block; more than one means a switch (2.5.0).
     */
    private suspend fun startEvening(choice: Choice, at: Instant) {
        book = SegmentBook.eveningChoice(book, choice, today(), zone, at)
        pendingChoice = null
        _segments.value = book.segments
        _state.value = DayState.Evening(1)
        reactedAt = clock.now()
        persist()
        sideEffects(transition = true)
        logger.info("evening started: $choice at $at")
    }

    /**
     * "Switch activity" on the evening card (2.5.0): the running activity's segment ends now and the
     * new one starts now. Deliberately not routed through [DayStateMachine], which refuses a second
     * choice. False when the activity isn't running.
     */
    suspend fun switchEvening(choice: Choice): Boolean {
        ensureRestored()
        val state = _state.value
        if (state !is DayState.Evening || state.blockIndex != 1) return false
        val open = book.segments.lastOrNull { it.actualEnd == null } ?: return false
        if (open.type !is BlockType.EveningChoice) return false
        startEvening(choice, clock.now())
        return true
    }

    /**
     * "Skip dishes" (2.5.0): Dishes ends now and the picked activity starts now. With nothing picked
     * yet, Dishes still ends now and the caller opens the sheet; false tells it so.
     */
    suspend fun skipDishes(): Boolean {
        ensureRestored()
        val state = _state.value
        if (state !is DayState.Evening || state.blockIndex != 0) return false
        val now = clock.now()
        val pick = pendingChoice
        if (pick != null) {
            startEvening(pick, now)
            return true
        }
        book = book.copy(segments = closeOpenSegments(book.segments, now))
        _segments.value = book.segments
        reactedAt = now
        persist()
        sideEffects()
        logger.info("dishes skipped, nothing picked yet")
        return false
    }

    /**
     * Moves the open Dishes segment's end to [toMin] minutes of the day (2.5.0): never before now + 5
     * min, never past 22:30. The alarm follows through [sideEffects]. False when Dishes isn't open.
     */
    suspend fun resizeDishesTo(toMin: Int): Boolean {
        ensureRestored()
        val state = _state.value
        if (state !is DayState.Evening || state.blockIndex != 0) return false
        val dishes = openDishes() ?: return false
        val now = clock.now()
        val local = now.toLocalDateTime(zone)
        val nowMin = local.hour * 60 + local.minute
        val maxMin = 22 * 60 + 30
        val minMin = minOf(nowMin + 5, maxMin)
        val target = toMin.coerceIn(minMin, maxMin)
        if (dishes.plannedEnd?.toLocalDateTime(zone)?.let { it.hour * 60 + it.minute } == target) return false
        val end = LocalTime(target / 60, target % 60).at(today(), zone)
        book = book.copy(segments = book.segments.map {
            if (it.actualEnd == null && it.type == BlockType.Dishes) it.copy(plannedEnd = end) else it
        })
        _segments.value = book.segments
        reactedAt = now
        persist()
        sideEffects()
        logger.info("dishes end moved to $target min")
        return true
    }

    private fun closeOpenSegments(segments: List<Segment>, at: Instant): List<Segment> =
        segments.map { if (it.actualEnd == null) it.copy(actualEnd = at) else it }

    suspend fun onAlarm() {
        ensureRestored()
        stepCheckpoint()
        applyRollover()
        val now = clock.now()
        // A walk rings before anything else: its end is a transition too, so the ring has to be
        // worked out while the walk is still the open segment.
        val walk = openWalk()
        val walkEnd = walk?.plannedEnd
        if (walk != null && walkEnd != null) {
            com.roni.blocks.core.schedule.AlertPlanner
                .dueWalk(walk.actualStart.epochSeconds, walkEnd.epochSeconds, now.epochSeconds)
                ?.let { due ->
                    val (label, end) = walkNext(walk)
                    walkRinger.ring(due.kind, walkEnd, label, end)
                }
        }
        // A standalone walk (2.4.0) rings the same way but touches nothing else. It is worked out
        // before the day's own transitions, and its end is not a reason to return early.
        val session = standaloneWalk
        if (session != null) {
            com.roni.blocks.core.schedule.AlertPlanner
                .dueWalk(session.start.epochSeconds, session.end.epochSeconds, now.epochSeconds)
                ?.let { due -> walkRinger.ring(due.kind, session.end, null, null) }
            if (now >= session.end) {
                val endCounter = checkpointCounter()
                history.addWalk(session.date, WalkLog(session.start, session.end, walkSteps(session.startCounter, endCounter)))
                standaloneWalk = null
                persist()
            }
        }
        if (applyOneMissedTransition()) return
        if (walk != null && walkEnd != null && now < walkEnd) {
            // Still walking: the walk ringer owns the sound, so the day notification stays quiet.
            sideEffects()
            return
        }
        val template = currentTemplate()
        val open = _segments.value.lastOrNull { it.actualEnd == null }
        val anchor = if (open?.kind == SegmentKind.PLANNED) open.actualStart else null
        val due = if (open != null && _state.value is DayState.Running) {
            val templateIndex = (_state.value as DayState.Running).blockIndex
            val pomodoro = template?.blocks?.getOrNull(templateIndex)?.pomodoro
            val boundaries = if (pomodoro != null && open.kind == SegmentKind.PLANNED) {
                com.roni.blocks.core.schedule.AlertPlanner.pomodoroBoundaries(
                    (anchor ?: open.actualStart).epochSeconds,
                    (open.plannedEnd ?: now).epochSeconds,
                    pomodoro.focusMinutes,
                    pomodoro.breakMinutes,
                )
            } else emptyList()
            val endSec = (open.plannedEnd ?: now).epochSeconds
            val startSec = (anchor ?: open.actualStart).epochSeconds
            com.roni.blocks.core.schedule.AlertPlanner.due(
                startSec = startSec,
                endSec = endSec,
                boundaries = boundaries,
                reactedSec = reactedAt?.epochSeconds,
                nowSec = now.epochSeconds,
            )
        } else null
        // Nothing picked by the time Dishes ends (2.5.0): alert "Dishes done · pick your evening".
        val dishesDue = _state.value.isDishesSlot &&
            openDishes()?.plannedEnd?.let { now >= it } == true
        sideEffects(alert = due != null || dishesDue)
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
            pendingChoice = null
            reactedAt = null
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
        stepCheckpoint()
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
        standaloneWalk = null
        pendingChoice = null
        persist()
        alarms.cancel()
        sideEffects()
    }

    private suspend fun apply(event: DayEvent) {
        val now = clock.now()
        val walkBefore = openWalk()
        if (event is DayEvent.StartDay) {
            val nowLocal = now.toLocalDateTime(zone).time
            val tooLate = (nowLocal.hour > 22) || (nowLocal.hour == 22 && nowLocal.minute >= 30)
            if (tooLate) {
                logger.info("refusing StartDay after 22:30")
                return
            }
        }
        // User actions count as "Roni reacted": internal transitions (alarms, catch-up) don't.
        if (event !is DayEvent.KidTimeStarted) {
            reactedAt = now
        }
        if (event is DayEvent.SwapTemplate) return applySwap(event, now)
        // The evening choice is not a plain state-machine step (2.5.0): picked during Dishes it is
        // only remembered; picked once Dishes is over (or already in the activity) it starts the
        // activity now. See [applyEveningChoice].
        if (event is DayEvent.EveningChoice) {
            applyEveningChoice(event.choice, now)
            return
        }
        if (event is DayEvent.Next) {
            val current = _state.value.currentBlockId()
            if (event.expectedBlockId != current) {
                logger.info("dropping Next for ${event.expectedBlockId}, current is ${current ?: "-"}")
                return
            }
            if (startNextEarly(now)) {
                reactedAt = now
                _state.value = DayState.KidTime
                _segments.value = book.segments
                persist()
                logger.info("Next started kid time at $now")
                sideEffects(transition = true)
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
        logClosedWalk(walkBefore, now)
    }

    /**
     * "Next starts the next thing now" (2.3.0): pressed from free time, or from a block finished
     * early, the first real block after it starts now and keeps its length; the free time in between
     * is skipped. Returns true when the landing block is kid time, which the caller runs as
     * [DayEvent.KidTimeStarted] instead of Next. A [DayEvent.Next] from a work block that is not over
     * yet is "Done early" and is left to [SegmentBook.next].
     */
    private fun startNextEarly(now: Instant): Boolean {
        val plan = _todayPlan.value ?: return false
        val currentId = _state.value.currentBlockId() ?: return false
        val open = book.segments.lastOrNull { it.actualEnd == null }
        val doneEarly = open != null && open.kind == SegmentKind.PLANNED && open.type.isWork &&
            open.plannedEnd != null && now < open.plannedEnd!!
        if (doneEarly) return false
        val nowMin = now.toLocalDateTime(zone).let { it.hour * 60 + it.minute }
        val edited = PlanEditor.startEarly(plan.blocks.toPlanBlocks(), currentId, nowMin) ?: return false
        _todayPlan.value = plan.copy(blocks = edited.toBlockSpecs(plan.blocks), edited = true)
        val landing = edited.indexOfFirst { it.startMin == nowMin && it.id != currentId }
        if (landing < 0) return false
        if (edited[landing].type == BlockType.KidTime) {
            book = SegmentBook.enterKidTime(book, currentTemplate() ?: return false, today(), zone)
            return true
        }
        book = book.copy(index = landing - 1)
        return false
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
            DayEvent.KidAsleep -> SegmentBook.kidAsleep(book, today, zone, now).also { pendingChoice = null }
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
        widgetUpdater.update(null)
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

    private suspend fun sideEffects(transition: Boolean = false, alert: Boolean = false) {
        val now = clock.now()
        val today = today()
        val template = currentTemplate()
        val index = (_state.value as? DayState.Running)?.blockIndex ?: 0
        val anchor = wakeAnchor()
        val wake = engine.nextWakeUp(_state.value, today, now, template, index, anchor)
        // While a walk runs the day's own alert is irrelevant: the walk rings at half time and at
        // the end, and those alarms must survive Doze (clock = true).
        val walk = openWalk()
        val session = standaloneWalk
        // Dishes ends at its planned end (2.5.0), so that end needs its own alarm even though the
        // evening state has no Pomodoro boundaries of its own.
        val dishesEnd = if (_state.value.isDishesSlot) openDishes()?.plannedEnd?.takeIf { it > now } else null
        val nextAlert = when {
            walk?.plannedEnd != null -> com.roni.blocks.core.schedule.AlertPlanner
                .nextWalk(walk.actualStart.epochSeconds, walk.plannedEnd!!.epochSeconds, now.epochSeconds)
                ?.let { Instant.fromEpochSeconds(it.atSec) }
            session != null -> com.roni.blocks.core.schedule.AlertPlanner
                .nextWalk(session.start.epochSeconds, session.end.epochSeconds, now.epochSeconds)
                ?.let { Instant.fromEpochSeconds(it.atSec) }
            dishesEnd != null -> dishesEnd
            else -> nextAlertTime(now, template, index, anchor)
        }
        val alarmAt = listOfNotNull(wake, nextAlert).minOrNull()
        if (alarmAt == null) alarms.cancel() else alarms.schedule(alarmAt, clock = walk != null || session != null)
        val timeline = engine.timelineAt(template, today, zone, _segments.value, now)
        val model = if (session != null) notificationMapper.walk(session.start, session.end, now)
        else notificationMapper.from(_state.value, timeline, now)
        notificationRenderer.render(model, transition, alert)
        widgetUpdater.update(model)
    }

    /**
     * The instant the Pomodoro cursor should start from, or null when the open segment is free
     * time / none (then the engine uses the planned start, or no boundaries at all). 2.2.0:
     * `SYNTHETIC_FREE` is "Done early", so the wake-up is the free time's planned end and the
     * Pomodoro cursor stays parked.
     */
    private fun wakeAnchor(): Instant? {
        val open = _segments.value.lastOrNull { it.actualEnd == null } ?: return null
        return if (open.kind == SegmentKind.SYNTHETIC_FREE) null else open.actualStart
    }

    /** The open walk segment, or null when nothing is walking (2.3.0). */
    private fun openWalk(): Segment? =
        book.segments.lastOrNull { it.actualEnd == null && it.kind == SegmentKind.PLANNED && it.type == BlockType.Walk }

    /** What runs after [walk]: its title and end, for the "Walk done" ring. */
    private fun walkNext(walk: Segment): Pair<String?, Instant?> {
        val plan = _todayPlan.value ?: return null to null
        val i = plan.blocks.indexOfFirst { it.id == walk.blockId }
        val next = plan.blocks.getOrNull(i + 1) ?: return null to null
        return blockName(next.type) to next.end.at(today(), zone)
    }

    /** The next sound to play, or null when no alert is needed. */
    private fun nextAlertTime(now: Instant, template: Template?, index: Int, anchor: Instant?): Instant? {
        val open = _segments.value.lastOrNull { it.actualEnd == null } ?: return null
        if (_state.value !is DayState.Running) return null
        val end = open.plannedEnd ?: return null
        val start = anchor ?: open.actualStart
        val pomodoro = template?.blocks?.getOrNull(index)?.pomodoro
        val boundaries = if (pomodoro != null && open.kind == SegmentKind.PLANNED) {
            com.roni.blocks.core.schedule.AlertPlanner.pomodoroBoundaries(
                start.epochSeconds, end.epochSeconds, pomodoro.focusMinutes, pomodoro.breakMinutes,
            )
        } else emptyList()
        val reactedSec = reactedAt?.epochSeconds
        return com.roni.blocks.core.schedule.AlertPlanner.next(
            startSec = start.epochSeconds,
            endSec = end.epochSeconds,
            boundaries = boundaries,
            reactedSec = reactedSec,
            afterSec = now.epochSeconds,
        )?.let { Instant.fromEpochSeconds(it.atSec) }
    }

    /**
     * The app came to the foreground: Roni is back, so drop any pending nag and refresh the
     * notification so it stops its sound. Called from the Application via the
     * [ProcessLifecycleOwner] observer that drives [AppVisibility].
     */
    suspend fun onAppForeground() {
        ensureRestored()
        stepCheckpoint()
        walkRinger.stop()
        reactedAt = clock.now()
        persist()
        sideEffects()
    }

    /** Re-draws only the home-screen widget with the model [sideEffects] would compute right now. */
    suspend fun refreshWidget() {
        val now = clock.now()
        val today = today()
        val template = currentTemplate()
        val timeline = engine.timelineAt(template, today, zone, _segments.value, now)
        widgetUpdater.update(notificationMapper.from(_state.value, timeline, now))
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
        reactedAt = if (storedDate != null && storedDate != today) null
        else prefs.read(PrefsFile.DAY, REACTED_AT, InstantSerializer.nullable)
        // A standalone walk survives the rollover: it is read and kept whatever the stored date.
        standaloneWalk = prefs.read(PrefsFile.DAY, STANDALONE_WALK, WalkSession.serializer().nullable)
        pendingChoice = prefs.read(PrefsFile.DAY, PENDING_EVENING, Choice.serializer().nullable)
        steps = prefs.read(PrefsFile.DAY, STEP_STATE, StepStateSerializer) ?: StepState()
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
            // A walk ends by itself when its planned end passes (2.3.0): "Next", exactly as free
            // time after "Done early". A walk has no warning and no nags.
            val walk = book.segments.lastOrNull { it.actualEnd == null && it.kind == SegmentKind.PLANNED && it.type == BlockType.Walk }
            if (walk?.plannedEnd != null && now >= walk.plannedEnd) {
                apply(DayEvent.Next(_state.value.currentBlockId().orEmpty()))
                return true
            }
            val freeEnd = book.segments.lastOrNull { it.kind == SegmentKind.SYNTHETIC_FREE && it.actualEnd == null }?.plannedEnd
            if (freeEnd != null && now >= freeEnd) {
                apply(DayEvent.Next(_state.value.currentBlockId().orEmpty()))
                return true
            }
        }
        // Dishes ends by itself at its planned end (2.5.0): with a pick waiting, the activity starts
        // there, to the second. With nothing picked, Dishes runs over until the pick (onAlarm alerts).
        if (_state.value.isDishesSlot) {
            val dishes = openDishes()
            val end = dishes?.plannedEnd
            if (end != null && now >= end && pendingChoice != null) {
                startEvening(pendingChoice!!, end)
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
        prefs.write(PrefsFile.DAY, REACTED_AT, reactedAt, InstantSerializer.nullable)
        prefs.write(PrefsFile.DAY, STANDALONE_WALK, standaloneWalk, WalkSession.serializer().nullable)
        prefs.write(PrefsFile.DAY, PENDING_EVENING, pendingChoice, Choice.serializer().nullable)
        prefs.write(PrefsFile.DAY, STEP_STATE, steps, StepStateSerializer)
        _walkSession.value = currentWalkSession()
        _todaySteps.value = if (steps.baseline < 0) null else steps.today
        _pendingEvening.value = pendingChoice
    }

    /** The walk Home should show: an open day-block walk's segment, else the standalone walk (2.4.0). */
    private fun currentWalkSession(): WalkSession? {
        val open = openWalk()
        if (open != null && open.plannedEnd != null) {
            return WalkSession(open.actualStart, open.plannedEnd, open.actualStart.toLocalDateTime(zone).date)
        }
        return standaloneWalk
    }

    /**
     * Reads the step counter once and moves the day's total forward (2.5.0): each day that just
     * closed is written to history, and [todaySteps] republishes. Called at every checkpoint.
     */
    suspend fun stepCheckpoint() {
        checkpointCounter()
    }

    /** The same read as [stepCheckpoint], returning the counter so a walk can use the same number. */
    private suspend fun checkpointCounter(): Long? {
        val counter = stepSensor.read() ?: return null
        val update = StepBook.read(steps, counter, today().toEpochDays())
        update.closed.forEach { (day, total) -> history.setSteps(LocalDate.fromEpochDays(day), total) }
        steps = update.state
        prefs.write(PrefsFile.DAY, STEP_STATE, steps, StepStateSerializer)
        _todaySteps.value = if (steps.baseline < 0) null else steps.today
        return counter
    }

    /** Steps during a walk (2.5.0); null when either reading is missing or the phone restarted. */
    private fun walkSteps(start: Long?, end: Long?): Long? =
        if (start == null || end == null) null else StepBook.between(start, end)

    /** Today's total plus the live reading past the last checkpoint; null before any reading (2.5.0). */
    private fun liveTotal(counter: Long): Long? {
        val state = steps
        return when {
            state.baseline < 0 -> null
            counter >= state.baseline -> state.today + (counter - state.baseline)
            else -> state.today
        }
    }

    /** A day-block walk just ended: log it in `walks_<date>` with its steps so History lists every walk. */
    private suspend fun logClosedWalk(walk: Segment?, now: Instant) {
        if (walk == null) return
        val closed = _segments.value.firstOrNull { it.blockId == walk.blockId && it.actualEnd != null } ?: return
        val endCounter = checkpointCounter()
        history.addWalk(today(), WalkLog(closed.actualStart, closed.actualEnd ?: now, walkSteps(walkStartCounter, endCounter)))
        walkStartCounter = null
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
        const val REACTED_AT = "reacted_at"
        const val STANDALONE_WALK = "standalone_walk"
        const val PENDING_EVENING = "pending_evening"
        const val STEP_STATE = "step_state"
        const val MAX_REPLAY_STEPS = 500
    }
}
