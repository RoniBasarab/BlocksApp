package com.roni.blocks.core.daystate

import com.roni.blocks.core.alarms.AlarmScheduler
import com.roni.blocks.core.di.ApplicationScope
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.CategoryInfo
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.DayRecord
import com.roni.blocks.core.model.DriveLog
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
import com.roni.blocks.core.schedule.PlanBlock
import com.roni.blocks.core.schedule.PlanEditor
import com.roni.blocks.core.schedule.ScheduleEngine
import com.roni.blocks.core.schedule.SegmentBook
import com.roni.blocks.core.schedule.TodayEdit
import com.roni.blocks.core.schedule.TodayEditor
import com.roni.blocks.core.schedule.timelineAt
import com.roni.blocks.core.schedule.toBlockSpecs
import com.roni.blocks.core.schedule.toPlanBlocks
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.CategoryStore
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.core.storage.TemplateStore
import com.roni.blocks.core.system.FailurePolicy
import com.roni.blocks.core.system.Failures
import com.roni.blocks.core.steps.StepBook
import com.roni.blocks.core.steps.StepSensor
import com.roni.blocks.core.steps.StepState
import com.roni.blocks.core.steps.StepStateSerializer
import com.roni.blocks.core.time.at
import com.roni.blocks.core.walk.WalkClock
import com.roni.blocks.core.walk.WalkCorrection
import com.roni.blocks.core.walk.WalkFix
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
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
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.Serializable

/** A drive Android saw start and hasn't seen end yet (3.4.0); [startCounter] is the counter then. */
@Serializable
data class OpenDrive(
    @Serializable(with = InstantSerializer::class) val start: Instant,
    val startCounter: Long?,
)

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
    /** The categories in use (3.0.0). Production injects it and loads it at restore; hand-built tests default to null. */
    private val categories: CategoryStore? = null,
    /** The stored templates (3.0.0). Production injects it and loads it at restore; hand-built tests default to null. */
    private val templateStore: TemplateStore? = null,
    /** Failures (3.5.0); a loud default so hand-built tests catch an unknown template. */
    private val failures: Failures = Failures(logger, FailurePolicy(loud = true)),
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
    /**
     * The block whose plan/segment end drift (3.4.3) has already been logged, so the warning fires
     * once per block. It names the path for the architecture round that removes the duplication.
     */
    private var driftWarnedFor: String? = null
    /** The profile today started under (3.2.0), for History's label and the Off day's `profile_name` key. */
    private var dayProfileName: String? = null
    /** A standalone walk (2.4.0) that runs outside the day; survives the date rollover. */
    private var standaloneWalk: WalkSession? = null
    /** Today's step arithmetic (2.5.0): the baseline and the day's total, kept across rollover. */
    private var steps = StepState()
    /** The counter reading when the open walk started (2.5.0), for the walk's own step total. */
    private var walkStartCounter: Long? = null
    /** A drive Android saw start but not end yet (3.4.0); survives the rollover and a restart. */
    private var openDrive: OpenDrive? = null
    private val _todaySteps = MutableStateFlow<Long?>(null)

    /** Today's steps so far (2.5.0); null until a reading has ever worked. */
    val todaySteps: StateFlow<Long?> = _todaySteps.asStateFlow()

    /** Tonight's activity picked during Dishes (2.5.0), persisted with the day; null until picked. */
    private var pendingChoice: Choice? = null
    private val _pendingEvening = MutableStateFlow<Choice?>(null)

    /** The activity picked during Dishes, before it starts (2.5.0); Home draws it as the planned block. */
    val pendingEvening: StateFlow<Choice?> = _pendingEvening.asStateFlow()

    private val _startsAt = MutableStateFlow<Instant?>(null)

    /** The first block's start while the day waits for it (2.7.0); null when no block is waited on. */
    val startsAt: StateFlow<Instant?> = _startsAt.asStateFlow()

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
                categories?.load()
                templateStore?.load()
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
        commitPlan(edited)
        logger.info("edited plan with $edit")
        return true
    }

    /**
     * Places block [id] (an existing block ahead, or a new one when [id] is null — the sheet's Add after)
     * at [startMin] for [lengthMin] minutes (3.3.0), with [TodayEditor]'s rules and the orchestrator's own
     * clock, then commits it. False when the edit isn't allowed or changes nothing.
     */
    suspend fun placeBlock(id: String?, type: BlockType, startMin: Int, lengthMin: Int): Boolean {
        ensureRestored()
        val state = _state.value
        if (state !is DayState.Running && state !is DayState.Paused) return false
        val plan = _todayPlan.value ?: return false
        val current = currentForEditing() ?: return false
        val nowMin = clock.now().toLocalDateTime(zone).time.let { it.hour * 60 + it.minute }
        val planBlocks = plan.blocks.toPlanBlocks()
        val edit = if (id == null) {
            TodayEditor.place(planBlocks, TodayEditor.newId(planBlocks), type, startMin, lengthMin, nowMin, current)
        } else {
            TodayEditor.place(planBlocks, id, type, startMin, lengthMin, nowMin, current)
        }
        val placed = edit as? TodayEdit.Ok ?: return false
        commitPlan(placed.blocks)
        logger.info("placed block $id as $type at $startMin for $lengthMin min")
        return true
    }

    /**
     * Commits an edited plan (3.3.0): converts it back to specs, keeps the day on its block, follows the
     * open segment with the new plan, and persists. Shared by [editPlan] and [placeBlock].
     */
    private suspend fun commitPlan(edited: List<PlanBlock>) {
        val plan = _todayPlan.value ?: return
        val state = _state.value
        val current = currentForEditing()
        val blocks = edited.toBlockSpecs(plan.blocks)
        val index = (current?.let { c -> blocks.indexOfFirst { it.id == c.id } } ?: -1).coerceAtLeast(0)
        _todayPlan.value = plan.copy(blocks = blocks, edited = true)
        _state.value = when (state) {
            is DayState.Running -> state.copy(blockIndex = index)
            is DayState.Paused -> state.copy(running = state.running.copy(blockIndex = index))
            else -> state
        }
        // Ran over is measured against the edited plan, so the current block's open segment follows
        // the new plan: a planned segment takes its new end (and new type when retyped); a
        // synthetic-free segment from "Done early" takes the current block's new end (2.2.0).
        val currentId = current?.id
        val currentBlockSpec = currentId?.let { id -> blocks.firstOrNull { it.id == id } }
        val currentEnd = currentBlockSpec?.end?.at(today(), zone)
        val currentType = currentBlockSpec?.type
        book = book.copy(
            segments = book.segments.map { segment ->
                if (segment.actualEnd == null) {
                    when {
                        segment.blockId == currentId && segment.kind == SegmentKind.PLANNED ->
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
        profileName = _todayPlan.value?.profileName ?: dayProfileName,
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
     * Moves a logged walk's times (3.4.1): the walk log entry starting at [oldStart] gets the new
     * start/end minutes with its steps unchanged, and when a closed Walk segment of [date] starts at
     * [oldStart] its own times move the same way — in the live book for today (persist, re-render),
     * in the stored record for a past day. The day's plan isn't touched. Refused with the reason
     * when the day doesn't allow the new times.
     */
    suspend fun correctWalk(date: LocalDate, oldStart: Instant, newStartMin: Int, newEndMin: Int): WalkFix {
        ensureRestored()
        val old = history.walks(date).firstOrNull { it.start == oldStart }
        val segment = closedWalkSegment(date, oldStart)
        if (old == null && segment == null) return WalkFix.Refused("That walk isn't in the log.")
        // A walk of today can't be moved into the future; a past day has no such limit.
        val nowMin = if (date == today()) nowMinutes() else null
        val (floorMin, ceilMin) = if (segment != null) {
            walkSegmentBounds(date, segment)
        } else {
            walkLogBounds(date, oldStart)
        }
        val fix = WalkCorrection.check(newStartMin, newEndMin, floorMin, ceilMin, nowMin)
        if (fix is WalkFix.Refused) return fix
        val newStart = LocalTime(newStartMin / 60, newStartMin % 60).at(date, zone)
        val newEnd = LocalTime(newEndMin / 60, newEndMin % 60).at(date, zone)
        if (old != null) history.replaceWalk(date, old, old.copy(start = newStart, end = newEnd))
        if (segment != null) moveWalkSegment(date, segment, newStart, newEnd)
        return fix
    }

    /** The closed Walk segment of [date] that started at [start]: the live book for today, the record otherwise. */
    private suspend fun closedWalkSegment(date: LocalDate, start: Instant): Segment? {
        val segments = if (date == today()) book.segments else history.readDay(date)?.segments.orEmpty()
        return segments.firstOrNull { isClosedWalk(it) && it.actualStart == start }
    }

    private fun isClosedWalk(segment: Segment): Boolean =
        segment.kind == SegmentKind.PLANNED && segment.type == BlockType.Walk && segment.actualEnd != null

    /** What the day allows around a day-block [segment]: the segment before it ends at [floorMin], the one after starts at [ceilMin]. */
    private suspend fun walkSegmentBounds(date: LocalDate, segment: Segment): Pair<Int, Int> {
        val segments = if (date == today()) book.segments else history.readDay(date)?.segments.orEmpty()
        val i = segments.indexOf(segment)
        val floor = segments.getOrNull(i - 1)?.actualEnd?.let { minutesOfDay(it) } ?: 0
        val ceil = segments.getOrNull(i + 1)?.actualStart?.let { minutesOfDay(it) } ?: LAST_MINUTE
        return floor to ceil
    }

    /** What the day allows around a standalone walk: the walk before it ends at [floorMin], the next starts at [ceilMin]. */
    private suspend fun walkLogBounds(date: LocalDate, start: Instant): Pair<Int, Int> {
        val logs = history.walks(date)
        val i = logs.indexOfFirst { it.start == start }
        val floor = logs.getOrNull(i - 1)?.end?.let { minutesOfDay(it) } ?: 0
        val ceil = logs.getOrNull(i + 1)?.start?.let { minutesOfDay(it) } ?: LAST_MINUTE
        return floor to ceil
    }

    /** Moves a closed Walk segment's times: in the live book for today, in the stored record and its week for a past day. */
    private suspend fun moveWalkSegment(date: LocalDate, segment: Segment, newStart: Instant, newEnd: Instant) {
        fun move(s: Segment): Segment = if (isClosedWalk(s) && s.actualStart == segment.actualStart) {
            s.copy(actualStart = newStart, actualEnd = newEnd)
        } else s
        if (date == today()) {
            book = book.copy(segments = book.segments.map(::move))
            _segments.value = book.segments
            persist()
            sideEffects()
        } else {
            val record = history.readDay(date) ?: return
            history.writeDay(record.copy(segments = record.segments.map(::move)))
            rebuildWeek(date)
        }
    }

    private fun minutesOfDay(instant: Instant): Int =
        instant.toLocalDateTime(zone).let { it.hour * 60 + it.minute }

    private fun nowMinutes(): Int = minutesOfDay(clock.now())

    /**
     * What the day allows around the walk that started at [oldStart] (3.4.1): a day-block walk's own
     * segments when it has one, else the standalone walks around it. The correction sheet's live
     * line reads it; [correctWalk] stays the authority on Save.
     */
    suspend fun walkBounds(date: LocalDate, oldStart: Instant): Pair<Int, Int> {
        ensureRestored()
        val segment = closedWalkSegment(date, oldStart)
        return if (segment != null) walkSegmentBounds(date, segment) else walkLogBounds(date, oldStart)
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
                turnBackAt = Instant.fromEpochSeconds(WalkClock.turnSec(now.epochSeconds, end.epochSeconds)),
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
        val homeAt = LocalTime(end / 60, end % 60).at(today, zone)
        return WalkPreview(
            turnBackAt = Instant.fromEpochSeconds(WalkClock.turnSec(now.epochSeconds, homeAt.epochSeconds)),
            homeAt = homeAt,
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
     * "Start now" while the day waits for its first block (2.7.0): the first block starts now and
     * keeps its length; the gap it leaves behind becomes free time (kid time just starts now). A
     * user action, so it counts as Roni reacting. False when the day isn't waiting for its first block.
     */
    suspend fun startNow(): Boolean {
        ensureRestored()
        val now = clock.now()
        val today = today()
        val plan = _todayPlan.value ?: return false
        if (waitingStart(now, today) == null) return false
        val nowMin = now.toLocalDateTime(zone).let { it.hour * 60 + it.minute }
        val edited = PlanEditor.startFirstNow(plan.blocks.toPlanBlocks(), nowMin) ?: return false
        _todayPlan.value = plan.copy(blocks = edited.toBlockSpecs(plan.blocks), edited = true)
        val template = currentTemplate() ?: return false
        book = SegmentBook.startDay(template, today, zone, now)
        _segments.value = book.segments
        _state.value = if (template.blocks.getOrNull(book.index)?.type == BlockType.KidTime) {
            DayState.KidTime
        } else {
            DayState.Running(plan.templateId, book.index.coerceAtLeast(0))
        }
        reactedAt = now
        persist()
        sideEffects(transition = true)
        logger.info("started the day now at $now")
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
            val newEnd = WalkClock.turnBackEnd(session.start.epochSeconds, session.end.epochSeconds, now.epochSeconds)
                ?: return false
            standaloneWalk = session.copy(end = Instant.fromEpochSeconds(newEnd))
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
        val newEndSec = WalkClock.turnBackEnd(open.actualStart.epochSeconds, walkEnd.epochSeconds, now.epochSeconds)
            ?: return false
        val newEnd = Instant.fromEpochSeconds(newEndSec)
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
     * The evening choice (2.5.0; 3.3.0: Dishes never ends by itself). During Dishes the pick is only
     * remembered ([pendingEvening]) and Dishes runs over until "Done dishes" or Next. Once Dishes is
     * over, or while the activity is running, the pick starts/switches the activity at [now].
     */
    private suspend fun applyEveningChoice(choice: Choice, now: Instant) {
        val state = _state.value
        if (state !is DayState.Evening) {
            logger.info("ignoring evening choice $choice from $state")
            return
        }
        if (state.blockIndex == 0 && openDishes() != null) {
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
        // Dishes (3.3.0): its end, then a nag every 5 minutes, no 5-minute warning; vibrates only.
        val dishes = openDishes()
        val dishesDue = if (_state.value.isDishesSlot && dishes?.plannedEnd != null) {
            com.roni.blocks.core.schedule.AlertPlanner.due(
                startSec = dishes.actualStart.epochSeconds,
                endSec = dishes.plannedEnd!!.epochSeconds,
                boundaries = emptyList(),
                reactedSec = reactedAt?.epochSeconds,
                nowSec = now.epochSeconds,
                warn = false,
            ) != null
        } else false
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
            dayProfileName = null
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
        dayProfileName = null
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
        // 3.5.0: an id that no longer resolves is reported and the event is dropped, never crashes.
        if (event is DayEvent.StartDay && event.template != TemplateId.OFF && templates.find(event.template) == null) {
            failures.report("start day: unknown template ${event.template.key}", IllegalArgumentException())
            return
        }
        if (event is DayEvent.SwapTemplate && event.template != TemplateId.OFF && templates.find(event.template) == null) {
            failures.report("start day: unknown template ${event.template.key}", IllegalArgumentException())
            return
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
                val template = templates.find(event.template) ?: return
                dayProfileName = templateStore?.activeProfileName()
                _todayPlan.value = TodayPlan(event.template, template.blocks, profileName = dayProfileName)
                // 2.7.0: before its first block opens the day waits with an empty book, even when
                // that first block is kid time: the state stays Running until its time comes.
                val currentBlock = template.blocks.getOrNull(book.index)
                if (book.segments.isNotEmpty() && currentBlock?.type == BlockType.KidTime) {
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
            val template = templates.find(event.template) ?: return
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
            dayProfileName = templateStore?.activeProfileName()
            book = DayBook()
            return
        }
        book = when (event) {
            is DayEvent.StartDay -> SegmentBook.startDay(templates.find(event.template) ?: return, today, zone, now)
            is DayEvent.Next -> SegmentBook.next(book, currentTemplate() ?: return, today, zone, now)
            DayEvent.Pause -> SegmentBook.pause(book, now)
            DayEvent.Resume -> SegmentBook.resumePause(book, now)
            DayEvent.KidTimeStarted -> SegmentBook.enterKidTime(book, currentTemplate() ?: return, today, zone)
            DayEvent.KidAsleep -> SegmentBook.kidAsleep(book, today, zone, now).also { pendingChoice = null }
            is DayEvent.EveningChoice -> SegmentBook.eveningChoice(book, event.choice, today, zone, now)
            is DayEvent.EndDay -> SegmentBook.endDay(book, now, _state.value is DayState.KidTime)
            DayEvent.TakeOffDay -> {
                dayProfileName = templateStore?.activeProfileName()
                DayBook()
            }
            is DayEvent.SwapTemplate -> book
        }
    }

    /** Today's blocks as a template, from the plan when there is one, else from the raw template. */
    private fun currentTemplate(): Template? {
        val plan = _todayPlan.value
        if (plan != null) {
            val name = templates.find(plan.templateId)?.name ?: return null
            return Template(plan.templateId, name, plan.blocks)
        }
        return _state.value.templateId()?.let { templates.find(it) }
    }

    /**
     * The first block's start while the day waits for it (2.7.0): the day is [DayState.Running] with
     * no segment yet. Null once the day has started, or when there is no plan to read.
     */
    private fun planFirstStart(today: LocalDate): Instant? {
        val state = _state.value
        // Only a fresh pick waits: block 0, no segment yet. A legacy restored day (an old
        // "Interrupted" state with a higher block index and no segments) is not waiting.
        if (state !is DayState.Running || state.blockIndex != 0) return null
        if (_segments.value.isNotEmpty()) return null
        val plan = _todayPlan.value ?: return null
        return plan.blocks.firstOrNull()?.start?.at(today, zone)
    }

    /** [planFirstStart], but only while the clock is still before it: the day is waiting. */
    private fun waitingStart(now: Instant, today: LocalDate): Instant? =
        planFirstStart(today)?.takeIf { now < it }

    private suspend fun flush(flags: Set<DayFlag>) {
        val today = today()
        val template = _state.value.templateId() ?: prefs.read(PrefsFile.DAY, TEMPLATE, TemplateId.serializer())
        val plan = _todayPlan.value
        val finalFlags = if (plan?.edited == true) flags + DayFlag.PLAN_EDITED else flags
        val record = DayRecord(today, template, _segments.value, finalFlags, plannedBlocks = plan?.blocks?.ifEmpty { null }, profileName = plan?.profileName ?: dayProfileName)
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

    /**
     * 3.4.3 tripwire, not a fix: the open PLANNED segment carries a copy of its planned end, and
     * some path can leave that copy different from today's plan. The card and the notification now
     * follow the plan, but the log still names the block if the copy drifts, so the path shows up.
     */
    private fun warnOnPlanSegmentDrift(today: LocalDate) {
        val open = _segments.value.lastOrNull { it.actualEnd == null && it.kind == SegmentKind.PLANNED } ?: return
        val blockId = open.blockId ?: return
        val segmentEnd = open.plannedEnd ?: return
        val planEnd = _todayPlan.value?.blocks?.firstOrNull { it.id == blockId }?.end?.at(today, zone) ?: return
        if (driftWarnedFor == blockId || segmentEnd == planEnd) return
        driftWarnedFor = blockId
        logger.warn("plan/segment end drift: $blockId segment=$segmentEnd plan=$planEnd state=${_state.value}")
    }

    private suspend fun sideEffects(transition: Boolean = false, alert: Boolean = false) {
        val now = clock.now()
        val today = today()
        val template = currentTemplate()
        warnOnPlanSegmentDrift(today)
        val index = (_state.value as? DayState.Running)?.blockIndex ?: 0
        val anchor = wakeAnchor()
        val wake = engine.nextWakeUp(_state.value, today, now, template, index, anchor)
        // While a walk runs the day's own alert is irrelevant: the walk rings at half time and at
        // the end, and those alarms must survive Doze (clock = true).
        val walk = openWalk()
        val session = standaloneWalk
        // Dishes never ends by itself any more (3.3.0): while it is open the alert is its planned end,
        // then a nag every 5 minutes, with no 5-minute warning. It vibrates only (the DISHES channel).
        val dishesAlert = if (_state.value.isDishesSlot) openDishes()?.let { d ->
            d.plannedEnd?.let { end ->
                com.roni.blocks.core.schedule.AlertPlanner
                    .next(d.actualStart.epochSeconds, end.epochSeconds, emptyList(), reactedAt?.epochSeconds, now.epochSeconds, warn = false)
                    ?.let { Instant.fromEpochSeconds(it.atSec) }
            }
        } else null
        val nextAlert = when {
            walk?.plannedEnd != null -> com.roni.blocks.core.schedule.AlertPlanner
                .nextWalk(walk.actualStart.epochSeconds, walk.plannedEnd!!.epochSeconds, now.epochSeconds)
                ?.let { Instant.fromEpochSeconds(it.atSec) }
            session != null -> com.roni.blocks.core.schedule.AlertPlanner
                .nextWalk(session.start.epochSeconds, session.end.epochSeconds, now.epochSeconds)
                ?.let { Instant.fromEpochSeconds(it.atSec) }
            dishesAlert != null -> dishesAlert
            else -> nextAlertTime(now, template, index, anchor)
        }
        val alarmAt = listOfNotNull(wake, nextAlert, waitingStart(now, today)).minOrNull()
        if (alarmAt == null) alarms.cancel() else alarms.schedule(alarmAt, clock = walk != null || session != null)
        val timeline = engine.timelineAt(template, today, zone, _segments.value, now)
        val model = if (session != null) notificationMapper.walk(session.start, session.end, now)
        else notificationMapper.from(_state.value, timeline, now, template?.name)
        // While waiting the day is quiet: no transition sound and no alert until the first block starts.
        val quiet = waitingStart(now, today) != null
        notificationRenderer.render(model, transition && !quiet, alert && !quiet)
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
        widgetUpdater.update(notificationMapper.from(_state.value, timeline, now, template?.name))
    }

    /**
     * A saved template may change today's plan while the day still waits for its first block
     * (3.1.0): the plan takes the stored blocks, and if the new first start is already due the day
     * starts exactly as a missed transition would, otherwise just [startsAt] and the alarm move to
     * the new first start. A day that already started, or an edited plan, keeps its plan.
     */
    suspend fun onTemplateSaved(id: TemplateId) {
        ensureRestored()
        val plan = _todayPlan.value ?: return
        if (plan.edited) return
        if (plan.templateId != id) return
        val stored = templateStore?.find(id) ?: return
        val now = clock.now()
        val today = today()
        if (waitingStart(now, today) == null) return
        _todayPlan.value = plan.copy(blocks = stored.blocks)
        val firstStart = planFirstStart(today)
        if (firstStart != null && now >= firstStart) {
            val template = currentTemplate() ?: return
            book = SegmentBook.startDay(template, today, zone, now)
            _segments.value = book.segments
            _state.value = if (template.blocks.getOrNull(book.index)?.type == BlockType.KidTime) {
                DayState.KidTime
            } else {
                DayState.Running(id, book.index.coerceAtLeast(0))
            }
            persist()
            sideEffects(transition = true)
        } else {
            persist()
            sideEffects()
        }
    }

    /**
     * A custom category changed (3.1.0): today's plan blocks of that category take the new copy
     * (or become free time when it was deleted, ids and times unchanged), and so does its open
     * segment. Closed segments and History keep what they had. Persists and re-renders.
     */
    suspend fun onCategoryChanged(categoryId: String, info: CategoryInfo?) {
        ensureRestored()
        val newType = info?.let { BlockType.Custom(it.id, it.name, it.colorArgb, it.work, it.pomodoro) }
            ?: BlockType.Free
        val plan = _todayPlan.value
        if (plan != null) {
            val replaced = plan.blocks.map { b ->
                val custom = b.type as? BlockType.Custom
                if (custom != null && custom.id == categoryId) b.copy(type = newType) else b
            }
            if (replaced != plan.blocks) _todayPlan.value = plan.copy(blocks = replaced)
        }
        if (info != null) {
            val index = _segments.value.indexOfLast { it.actualEnd == null }
            if (index >= 0) {
                val open = _segments.value[index]
                val custom = open.type as? BlockType.Custom
                if (custom != null && custom.id == categoryId) {
                    val segments = _segments.value.toMutableList()
                    segments[index] = open.copy(type = newType)
                    _segments.value = segments
                    book = book.copy(segments = segments)
                }
            }
        }
        persist()
        sideEffects()
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
        // A drive also survives the rollover; it is logged on the date it started (3.4.0).
        openDrive = prefs.read(PrefsFile.DAY, OPEN_DRIVE, OpenDrive.serializer().nullable)
        pendingChoice = prefs.read(PrefsFile.DAY, PENDING_EVENING, Choice.serializer().nullable)
        dayProfileName = prefs.read(PrefsFile.DAY, PROFILE_NAME, String.serializer().nullable)
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
        val template = templates.find(templateId) ?: return null
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
        // 2.7.0: the day waits for its first block; when its time comes the day starts by itself,
        // exactly as a StartDay at this clock would.
        val firstStart = planFirstStart(today)
        if (firstStart != null && now >= firstStart) {
            book = SegmentBook.startDay(template, today, zone, now)
            _segments.value = book.segments
            _state.value = if (template.blocks.getOrNull(book.index)?.type == BlockType.KidTime) {
                DayState.KidTime
            } else {
                DayState.Running(template.id, book.index.coerceAtLeast(0))
            }
            persist()
            sideEffects(transition = true)
            return true
        }
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
        // Dishes never ends by itself any more (3.3.0): it runs over until "Done dishes" or Next. The
        // alert nags (vibration only) from its planned end every 5 minutes; see [sideEffects]/[onAlarm].
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
        prefs.write(PrefsFile.DAY, PROFILE_NAME, dayProfileName, String.serializer().nullable)
        prefs.write(PrefsFile.DAY, STEP_STATE, steps, StepStateSerializer)
        _walkSession.value = currentWalkSession()
        _todaySteps.value = if (steps.baseline < 0) null else steps.today
        _pendingEvening.value = pendingChoice
        _startsAt.value = waitingStart(clock.now(), today())
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
     * Android's activity recognition reports entering or leaving a car (3.4.0). The steps the
     * counter moved in between are logged as a drive, on the date it started; they stay in the day's
     * total, never subtracted. A drive left open past [DRIVE_MAX] (a missed exit) is closed at its
     * start plus the cap with no step total.
     */
    suspend fun onDrive(enter: Boolean, at: Instant) {
        ensureRestored()
        closeStaleDrive(at)
        if (enter) {
            openDrive?.let { closeDrive(it, at) }
            openDrive = OpenDrive(at, checkpointCounter())
            persistDrive()
            logger.info("drive started at $at")
        } else {
            val open = openDrive ?: return
            closeDrive(open, at)
        }
    }

    /**
     * Debug (3.4.0): logs a fake drive so History shows "about 150 while driving" without a car. The
     * drive started 20 minutes ago with a counter 150 below the current reading, then closes now.
     */
    suspend fun simulateDrive() {
        ensureRestored()
        val counter = checkpointCounter()
        openDrive = OpenDrive(clock.now() - 20.minutes, counter?.minus(150))
        persistDrive()
        onDrive(enter = false, at = clock.now())
    }

    /** Closes a drive open longer than [DRIVE_MAX] (a missed exit) at its start plus the cap. */
    private suspend fun closeStaleDrive(now: Instant) {
        val open = openDrive ?: return
        if (now - open.start > DRIVE_MAX) {
            history.addDrive(open.start.toLocalDateTime(zone).date, DriveLog(open.start, open.start + DRIVE_MAX, null))
            openDrive = null
            persistDrive()
            logger.info("closed a stale drive that started at ${open.start}")
        }
    }

    private suspend fun closeDrive(open: OpenDrive, end: Instant) {
        history.addDrive(
            open.start.toLocalDateTime(zone).date,
            DriveLog(open.start, end, walkSteps(open.startCounter, checkpointCounter())),
        )
        openDrive = null
        persistDrive()
        logger.info("drive ended at $end")
    }

    private suspend fun persistDrive() {
        prefs.write(PrefsFile.DAY, OPEN_DRIVE, openDrive, OpenDrive.serializer().nullable)
    }

    /**
     * Reads the step counter once and moves the day's total forward (2.5.0): each day that just
     * closed is written to history, and [todaySteps] republishes. Called at every checkpoint.
     */
    suspend fun stepCheckpoint() {
        closeStaleDrive(clock.now())
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
        templates.find(template)?.blocks?.getOrNull(index)?.id

    private companion object {
        const val STATE = "day_state"
        const val SEGMENTS = "today_segments"
        const val DATE = "today_date"
        const val TEMPLATE = "template_id"
        const val TODAY_PLAN = "today_plan"
        const val PLAN_STARTS_AT = "plan_starts_at"
        const val REACTED_AT = "reacted_at"
        const val STANDALONE_WALK = "standalone_walk"
        const val OPEN_DRIVE = "open_drive"
        const val PENDING_EVENING = "pending_evening"
        const val PROFILE_NAME = "profile_name"
        const val STEP_STATE = "step_state"
        const val MAX_REPLAY_STEPS = 500

        /** The last minute of a day (3.4.1): a walk with no walk after it may end here. */
        const val LAST_MINUTE = 23 * 60 + 59

        /** A drive open longer than this (a missed exit) is closed with no step total (3.4.0). */
        val DRIVE_MAX = 4.hours
    }
}
