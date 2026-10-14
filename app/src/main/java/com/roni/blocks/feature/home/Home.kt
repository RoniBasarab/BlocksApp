package com.roni.blocks.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.roni.blocks.BuildConfig
import com.roni.blocks.core.daystate.DayEvent
import com.roni.blocks.core.schedule.BlockEdit
import com.roni.blocks.core.schedule.CurrentBlock
import com.roni.blocks.core.schedule.DayTimeline
import com.roni.blocks.core.schedule.Edge
import com.roni.blocks.core.schedule.PlanBlock
import com.roni.blocks.core.schedule.PlanEdit
import com.roni.blocks.core.schedule.PlanEditor
import com.roni.blocks.core.schedule.RecordEdit
import com.roni.blocks.core.schedule.SegmentSpan
import com.roni.blocks.core.schedule.TimelinePhase
import com.roni.blocks.core.schedule.TodayEdit
import com.roni.blocks.core.schedule.TodayEditor
import com.roni.blocks.core.schedule.TodayRefusal
import com.roni.blocks.core.schedule.isEditable
import com.roni.blocks.core.schedule.toBlockSpecs
import com.roni.blocks.core.schedule.toPlanBlocks
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.daystate.EndReason
import com.roni.blocks.core.daystate.isDishesSlot
import com.roni.blocks.core.daystate.templateId
import com.roni.blocks.core.daystate.WalkPreview
import com.roni.blocks.core.debug.RecompositionCounter
import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.CheckIn
import com.roni.blocks.core.model.Categories
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.DayRecord
import com.roni.blocks.core.model.isWork
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.EVENING_CHOICE_ID
import com.roni.blocks.core.model.EVENING_DISHES_ID
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.Template
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.model.WalkLog
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.core.time.DebugTimeController
import com.roni.blocks.core.time.Ticker
import com.roni.blocks.core.time.TimeFormatter
import com.roni.blocks.core.time.minuteOfDay
import com.roni.blocks.core.walk.WalkClock
import com.roni.blocks.core.walk.WalkCorrection
import com.roni.blocks.core.walk.WalkFix
import com.roni.blocks.core.walk.WalkPhase
import com.roni.blocks.core.walk.WalkView
import com.roni.blocks.designsystem.AppBottomBar
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppChip
import com.roni.blocks.designsystem.AppShapes
import com.roni.blocks.designsystem.AppTab
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.AppType
import com.roni.blocks.designsystem.Metrics
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.designsystem.formatStepsApprox
import com.roni.blocks.designsystem.templateDisplay
import com.roni.blocks.feature.templates.BlockSheet
import com.roni.blocks.feature.templates.BlockSheetAction
import com.roni.blocks.feature.templates.BlockSheetUi
import com.roni.blocks.feature.templates.typeForCategory
import com.roni.blocks.feature.weekly.tabKey
import com.roni.blocks.navigation.HistoryKey
import com.roni.blocks.navigation.ProfilesKey
import com.roni.blocks.navigation.SettingsKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.builtins.serializer

/** The walk the Home correction sheet is editing (3.4.1): its log, the times being changed, and the day's bounds. */
private data class WalkEdit(val log: WalkLog, val startMin: Int, val endMin: Int, val floorMin: Int, val ceilMin: Int)

@HiltViewModel
/**
 * Feeds the Home screen: the block in progress, what the day actually did, and the actions behind
 * its buttons. Rules: the published state holds plain values only, never Android or Compose types,
 * so it stays diffable and testable; day changes are requested from [DayOrchestrator], never applied
 * here; the debug clock label is published only in debug builds.
 *
 * Talks to: [DayOrchestrator], [HistoryManager], [TemplateManager], [Ticker], [DebugTimeController].
 */
class HomeViewModel @Inject constructor(
private val day: DayOrchestrator,
      private val templates: TemplateManager,
      private val history: HistoryManager,
      private val ticker: Ticker,
    private val clock: Clock,
    private val zone: TimeZone,
    private val prefs: PrefsStorage,
    private val timeFormatter: TimeFormatter,
    private val debugTime: DebugTimeController,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()
    private var recap: DayRecapUi? = null
    private var endedRecord: DayRecord? = null
    /** Today's standalone walks (2.5.0), the only walks_<date> log entries; drawn as markers. */
    private var walkLogs: List<WalkLog> = emptyList()

    private val _suppressedConfirmations = MutableStateFlow<Set<NowButtonId>>(emptySet())

    /** Buttons the user chose "Don't ask again" for; survives process death. */
    val suppressedConfirmations: StateFlow<Set<NowButtonId>> = _suppressedConfirmations.asStateFlow()

    private var preview: List<PlanBlock>? = null
    private var draggingId: String? = null
    private var eveningSheetOpen = false
    /** The evening sheet auto-opens once per Dishes (2.5.0), so dismissing it stays dismissed. */
    private var eveningAutoOpened = false
    private var walkSheet: WalkSheetUi? = null

    /** The block a just-finished insert should select, so its handles show. */
    private var insertedBlockId: String? = null

    /** The shared block sheet's state (3.3.0); null when it's closed. */
    private var sheet: BlockSheetUi? = null
    /** The block the sheet edits; null for a new (Add after) block. */
    private var sheetId: String? = null
    /** Which edit the sheet is doing: the block ahead, or the running block. */
    private var sheetMode: BlockEdit = BlockEdit.NONE
    /** The sheet's block start in the committed plan, for the running block's Resize. */
    private var sheetPlanStart: Int = 0

    /** The walk the correction sheet edits (3.4.1); non-null while the walk sheet is up. */
    private var walkEdit: WalkEdit? = null
    /** The start of the walk whose "got home earlier?" card was dismissed (3.4.1). */
    private var dismissedWalkStartSec: Long? = null

    /** The calendar's real text heights (2.6.1); [HomeAction.TextMeasured] replaces it and re-publishes. */
    private var textMetrics = TextMetrics()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** One-shot toasts: why a tap on a block did nothing. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _correctingRow = MutableStateFlow<RecapRow?>(null)

    /** The recap row whose correction sheet is up (past block and today are both eligible). */
    val correctingRow: StateFlow<RecapRow?> = _correctingRow.asStateFlow()

    val minuteOfDay: StateFlow<Int> =
        ticker.minutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val secondOfDay: StateFlow<Int> =
        ticker.seconds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val countdownText: StateFlow<String> =
        combine(_state, minuteOfDay, secondOfDay) { s, minute, second -> countdown(s, minute, second) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val progress: StateFlow<Float> =
        combine(_state, minuteOfDay) { s, minute -> progressOf(s, minute) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    /** The walk card's clock (2.6.0): one [WalkView] with text, progress and phase, refreshed every second. */
    val walkView: StateFlow<WalkView?> =
        combine(_state, secondOfDay) { s, _ ->
            s.walkCard?.let { WalkClock.view(it.startSec, it.endSec, clock.now().epochSeconds) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** "5,240 steps" for the top bar (2.5.0): the saved total plus the live reading; never written. */
    val stepsLabel: StateFlow<String?> =
        combine(day.todaySteps, day.liveSteps) { saved, live -> (live ?: saved)?.let { "about ${formatStepsApprox(it)} steps" } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            day.state.collect { state -> publish(state) }
        }
        viewModelScope.launch {
            // The orchestrator sets the state before the segments, so a publish on the state alone
            // can miss a just-added evening segment.
            day.segments.collect { publish(day.state.value) }
        }
        viewModelScope.launch {
            // Turn back and End walk only move the walk session, not the state or the segments.
            day.walkSession.collect { publish(day.state.value) }
        }
        viewModelScope.launch {
            // Picking during Dishes only moves the pending choice, not the state or the segments (2.5.0).
            day.pendingEvening.collect { publish(day.state.value) }
        }
        viewModelScope.launch {
            // The first block's start is written in persist(), after the state and the segments
            // (2.7.0), so it needs its own collector to flip Home into the waiting card.
            day.startsAt.collect { publish(day.state.value) }
        }
        viewModelScope.launch {
            history.observeWalks(clock.now().toLocalDateTime(zone).date).collect { logs ->
                walkLogs = logs
                publish(day.state.value)
            }
        }
        viewModelScope.launch {
            history.observeDay(clock.now().toLocalDateTime(zone).date).collect { record ->
                endedRecord = record?.takeIf { it.date == clock.now().toLocalDateTime(zone).date }
                recap = endedRecord?.let { DayRecapMapper.map(it, timeFormatter.use24.value) }
                publish(day.state.value)
            }
        }
        // The real day (3.3.0) moves with the minute: every tick re-publishes so the tail grows.
        viewModelScope.launch { minuteOfDay.collect { publish(day.state.value) } }
        viewModelScope.launch { timeFormatter.use24.collect { publish(day.state.value) } }
        if (BuildConfig.DEBUG) {
            viewModelScope.launch { debugTime.offsetMinutes.collect { publish(day.state.value) } }
        }
        viewModelScope.launch {
            val stored = prefs.read(
                PrefsFile.SETTINGS,
                SUPPRESSED_CONFIRMATIONS,
                SuppressedConfirmations.serializer(),
            )
            _suppressedConfirmations.value = suppressedFromKeys(stored?.ids.orEmpty())
        }
    }

    fun onSuppressConfirmation(button: NowButtonId) {
        if (button in _suppressedConfirmations.value) return
        val updated = _suppressedConfirmations.value + button
        _suppressedConfirmations.value = updated
        viewModelScope.launch {
            prefs.write(
                PrefsFile.SETTINGS,
                SUPPRESSED_CONFIRMATIONS,
                SuppressedConfirmations(keysFromSuppressed(updated)),
                SuppressedConfirmations.serializer(),
            )
        }
    }

    fun onAskAgain(button: NowButtonId) {
        if (button !in _suppressedConfirmations.value) return
        val updated = _suppressedConfirmations.value - button
        _suppressedConfirmations.value = updated
        viewModelScope.launch {
            if (updated.isEmpty()) {
                prefs.remove(PrefsFile.SETTINGS, SUPPRESSED_CONFIRMATIONS)
            } else {
                prefs.write(
                    PrefsFile.SETTINGS,
                    SUPPRESSED_CONFIRMATIONS,
                    SuppressedConfirmations(keysFromSuppressed(updated)),
                    SuppressedConfirmations.serializer(),
                )
            }
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Next -> day.send(DayEvent.Next(_state.value.currentBlockId.orEmpty()))
            HomeAction.Pause -> day.send(DayEvent.Pause)
            HomeAction.Emergency -> day.send(DayEvent.EndDay(EndReason.EMERGENCY))
            HomeAction.Resume -> day.send(DayEvent.Resume)
            HomeAction.StartNow -> viewModelScope.launch {
                day.startNow()
                publish(day.state.value)
            }
            HomeAction.KidAsleep -> day.send(DayEvent.KidAsleep)
            HomeAction.ChooseEvening -> eveningSheetOpen = true
            HomeAction.SkipDishes -> viewModelScope.launch {
                val started = day.skipDishes()
                if (!started) eveningSheetOpen = true
                publish(day.state.value)
            }
            HomeAction.DismissEveningSheet -> eveningSheetOpen = false
            HomeAction.EndDay -> day.send(DayEvent.EndDay(EndReason.NORMAL))
            HomeAction.Walk -> openWalkSheet()
            is HomeAction.WalkStep -> stepWalk(action.byMin)
            HomeAction.StartWalk -> startWalk()
            HomeAction.DismissWalkSheet -> {
                walkSheet = null
                publish(day.state.value)
            }
            HomeAction.TurnBack -> viewModelScope.launch {
                day.turnBack()
                publish(day.state.value)
            }
            HomeAction.EndWalk -> viewModelScope.launch {
                day.endWalk()
                publish(day.state.value)
            }
            is HomeAction.PickEvening -> {
                eveningSheetOpen = false
                day.send(DayEvent.EveningChoice(action.choice))
            }
            is HomeAction.DragEdge -> {
                draggingId = action.id
                preview = previewResize(action.id, action.edge, action.toMin)
            }
            is HomeAction.DragDone -> if (action.id == EVENING_DISHES_ID) resizeDishes(action.toMin)
                else commitEdit(PlanEdit.Resize(action.id, action.edge, action.toMin))
            HomeAction.DragCancelled -> {
                preview = null
                draggingId = null
            }
            is HomeAction.StepEdge -> if (action.id == EVENING_DISHES_ID) stepDishes(action.byMin)
                else stepEdge(action.id, action.edge, action.byMin)
            is HomeAction.OpenBlock -> openBlockSheet(action.id)
            is HomeAction.Sheet -> if (walkEdit != null) onWalkSheet(action.action) else onSheet(action.action)
            HomeAction.FixWalk -> openWalkFix()
            HomeAction.DismissWalkFix -> {
                dismissedWalkStartSec = latestWalkLog()?.start?.epochSeconds
                publish(day.state.value)
            }
            is HomeAction.LockedBlockTapped -> {
                val block = _state.value.blocks.firstOrNull { it.id == action.id }
                val row = block?.let { recapRowForBlock(it) }
                when {
                    row != null && row.fix != com.roni.blocks.core.schedule.RowFix.NONE -> {
                        _correctingRow.value = row
                    }
                    else -> block?.let { _messages.tryEmit(lockedReason(it)) }
                }
            }
            HomeAction.SwapTemplate,
            HomeAction.History,
            HomeAction.Profiles,
            HomeAction.Settings,
            -> Unit
            is HomeAction.TextMeasured -> {
                textMetrics = action.metrics
                publish(day.state.value)
            }
        }
        if (action.affectsOverlays || action.affectsCalendar) publish(day.state.value)
    }

    private fun previewResize(id: String, edge: Edge, toMin: Int): List<PlanBlock>? {
        val plan = day.todayPlan.value ?: return null
        return PlanEditor.apply(
            plan.blocks.toPlanBlocks(),
            PlanEdit.Resize(id, edge, toMin),
            clock.now().minuteOfDay(zone),
            day.currentForEditing(),
        )
    }

    private fun commitEdit(edit: PlanEdit) {
        viewModelScope.launch {
            day.editPlan(edit)
            preview = null
            draggingId = null
            publish(day.state.value)
        }
    }

    private fun stepEdge(id: String, edge: Edge, byMin: Int) {
        val block = day.todayPlan.value?.blocks?.firstOrNull { it.id == id } ?: return
        val edgeMin = if (edge == Edge.TOP) block.start.hour * 60 + block.start.minute else block.end.hour * 60 + block.end.minute
        commitEdit(PlanEdit.Resize(id, edge, edgeMin + byMin))
    }

    /** The Dishes block lives in the segments, not the plan (2.5.0), so its resize goes to the day. */
    private fun resizeDishes(toMin: Int) {
        viewModelScope.launch {
            day.resizeDishesTo(toMin)
            preview = null
            draggingId = null
            publish(day.state.value)
        }
    }

    private fun stepDishes(byMin: Int) {
        val endMin = _state.value.blocks.firstOrNull { it.id == EVENING_DISHES_ID }?.endMin ?: return
        resizeDishes(endMin + byMin)
    }

    // ── The shared block sheet (3.3.0) ──────────────────────────────────────────────────────────

    /** Opens the sheet for an editable block; the running block and blocks ahead edit differently. */
    private fun openBlockSheet(id: String) {
        val plan = day.todayPlan.value?.blocks?.toPlanBlocks() ?: return
        val block = plan.firstOrNull { it.id == id } ?: return
        val nowMin = clock.now().minuteOfDay(zone)
        val current = day.currentForEditing()
        val mode = PlanEditor.editMode(plan, id, nowMin, current)
        if (mode == BlockEdit.NONE) return
        sheetId = id
        sheetMode = mode
        sheetPlanStart = block.startMin
        insertedBlockId = id
        sheet = if (mode == BlockEdit.END_AND_TYPE || mode == BlockEdit.END_ONLY) {
            runningSheet(plan, block, mode, nowMin, current)
        } else {
            aheadSheet(plan, block, mode, nowMin, current)
        }
    }

    /** A block ahead of the day: category, start, length, Add after and (unless free) Delete. */
    private fun aheadSheet(plan: List<PlanBlock>, block: PlanBlock, mode: BlockEdit, nowMin: Int, current: CurrentBlock?): BlockSheetUi {
        val (line, error) = aheadLine(plan, block.id, block.type, block.startMin, block.length, nowMin, current)
        return BlockSheetUi(
            title = blockTitle(block.type),
            type = block.type,
            categories = homeCategories(),
            startMin = block.startMin,
            lengthMin = block.length,
            line = line,
            lineIsError = error,
            canSave = !error,
            canDelete = block.type != BlockType.Free,
            canAddAfter = true,
        )
    }

    /** The running block: category (only while it can be retyped), length (its end), Add after; no Delete/Start. */
    private fun runningSheet(plan: List<PlanBlock>, block: PlanBlock, mode: BlockEdit, nowMin: Int, current: CurrentBlock?): BlockSheetUi {
        val (line, error) = runningLine(plan, block.id, block.type, block.length, nowMin, current)
        return BlockSheetUi(
            title = blockTitle(block.type),
            type = block.type,
            categories = if (mode == BlockEdit.END_AND_TYPE) workCategories().toPersistentList() else persistentListOf(),
            startMin = null,
            lengthMin = block.length,
            line = line,
            lineIsError = error,
            canSave = true,
            canDelete = false,
            canAddAfter = true,
        )
    }

    private fun onSheet(action: BlockSheetAction) {
        val ui = sheet ?: return
        when (action) {
            is BlockSheetAction.Type -> editSheet(type = action.type)
            is BlockSheetAction.StartBy -> editSheet(startMin = (ui.startMin ?: sheetPlanStart) + action.minutes)
            is BlockSheetAction.StartAt -> editSheet(startMin = action.minute)
            is BlockSheetAction.LengthBy -> editSheet(lengthMin = (ui.lengthMin ?: 0) + action.minutes)
            BlockSheetAction.AddAfter -> addAfter()
            BlockSheetAction.Delete -> deleteSheetBlock()
            BlockSheetAction.Save -> saveSheet()
            BlockSheetAction.Dismiss -> dismissSheet()
        }
    }

    /** Recomputes the sheet's live line after one change; nothing is committed until Save. */
    private fun editSheet(type: BlockType? = null, startMin: Int? = null, lengthMin: Int? = null) {
        val ui = sheet ?: return
        val id = sheetId ?: return
        val plan = day.todayPlan.value?.blocks?.toPlanBlocks() ?: return
        val nowMin = clock.now().minuteOfDay(zone)
        val current = day.currentForEditing()
        val newType = type ?: ui.type
        if (sheetMode == BlockEdit.END_AND_TYPE || sheetMode == BlockEdit.END_ONLY) {
            val length = (lengthMin ?: ui.lengthMin ?: PlanEditor.MIN_WORK).coerceAtLeast(PlanEditor.MIN_WORK)
            val (line, error) = runningLine(plan, id, newType, length, nowMin, current)
            sheet = ui.copy(type = newType, lengthMin = length, line = line, lineIsError = error)
        } else {
            val start = startMin ?: ui.startMin ?: sheetPlanStart
            val length = lengthMin ?: ui.lengthMin ?: PlanEditor.MIN_WORK
            val (line, error) = aheadLine(plan, id, newType, start, length, nowMin, current)
            sheet = ui.copy(
                type = newType,
                startMin = start,
                lengthMin = length,
                line = line,
                lineIsError = error,
                canSave = !error,
                canDelete = newType != BlockType.Free,
            )
        }
    }

    /** "Add after": a new block right after the sheet's block; the sheet switches to it, uncommitted. */
    private fun addAfter() {
        val id = sheetId ?: return
        val plan = day.todayPlan.value?.blocks?.toPlanBlocks() ?: return
        val nowMin = clock.now().minuteOfDay(zone)
        val current = day.currentForEditing()
        val type = workCategories().firstOrNull() ?: BlockType.Cv
        val result = TodayEditor.addAfter(plan, id, type, nowMin, current) as? TodayEdit.Ok ?: return
        val newId = TodayEditor.newId(plan)
        val added = result.blocks.firstOrNull { it.id == newId } ?: return
        sheetId = null
        sheetMode = BlockEdit.FULL
        sheetPlanStart = added.startMin
        insertedBlockId = null
        sheet = BlockSheetUi(
            title = blockTitle(added.type),
            type = added.type,
            categories = homeCategories(),
            startMin = added.startMin,
            lengthMin = added.length,
            line = movedLine(plan, result.blocks, added.id),
            lineIsError = false,
            canSave = true,
            canDelete = added.type != BlockType.Free,
            canAddAfter = true,
        )
    }

    private fun saveSheet() {
        val ui = sheet ?: return
        val id = sheetId
        if (sheetMode == BlockEdit.END_AND_TYPE || sheetMode == BlockEdit.END_ONLY) {
            val plan = day.todayPlan.value?.blocks?.toPlanBlocks() ?: return
            val original = plan.firstOrNull { it.id == id } ?: return
            val type = ui.type
            val length = ui.lengthMin ?: original.length
            dismissSheet()
            viewModelScope.launch {
                if (type != original.type) day.editPlan(PlanEdit.Retype(original.id, type))
                if (length != original.length) day.editPlan(PlanEdit.Resize(original.id, Edge.BOTTOM, original.startMin + length))
                publish(day.state.value)
            }
            return
        }
        val start = ui.startMin ?: return
        val length = ui.lengthMin ?: return
        val type = ui.type
        dismissSheet()
        viewModelScope.launch {
            day.placeBlock(id, type, start, length)
            publish(day.state.value)
        }
    }

    private fun deleteSheetBlock() {
        val id = sheetId ?: return
        dismissSheet()
        viewModelScope.launch {
            day.editPlan(PlanEdit.Retype(id, BlockType.Free))
            publish(day.state.value)
        }
    }

    private fun dismissSheet() {
        sheet = null
        sheetId = null
        sheetMode = BlockEdit.NONE
    }

    // ── The walk correction sheet (3.4.1) ───────────────────────────────────────────────────────

    /** The walk a just-ended day-block or standalone walk logged; the latest end wins. */
    private fun latestWalkLog(): WalkLog? = walkLogs.maxByOrNull { it.end }

    /** "Fix the time": opens the shared sheet over the latest logged walk, with the day's real bounds. */
    private fun openWalkFix() {
        val log = latestWalkLog() ?: return
        viewModelScope.launch {
            val date = clock.now().toLocalDateTime(zone).date
            val (floor, ceil) = day.walkBounds(date, log.start)
            walkEdit = WalkEdit(log, log.start.minuteOfDay(zone), log.end.minuteOfDay(zone), floor, ceil)
            sheetId = null
            sheetMode = BlockEdit.NONE
            sheet = walkSheetUi()
            publish(day.state.value)
        }
    }

    private fun onWalkSheet(action: BlockSheetAction) {
        val edit = walkEdit ?: return
        when (action) {
            is BlockSheetAction.StartBy -> setWalk(startMin = edit.startMin + action.minutes)
            is BlockSheetAction.StartAt -> setWalk(startMin = action.minute)
            is BlockSheetAction.LengthBy -> setWalk(lengthMin = (edit.endMin - edit.startMin) + action.minutes)
            BlockSheetAction.Save -> saveWalk()
            BlockSheetAction.Dismiss -> {
                walkEdit = null
                sheet = null
                publish(day.state.value)
            }
            else -> Unit
        }
    }

    /** One change inside the walk sheet (3.4.1); nothing is committed until Save. */
    private fun setWalk(startMin: Int? = null, lengthMin: Int? = null) {
        val edit = walkEdit ?: return
        val start = startMin ?: edit.startMin
        val end = start + (lengthMin ?: (edit.endMin - edit.startMin))
        walkEdit = edit.copy(startMin = start, endMin = end)
        sheet = walkSheetUi()
        publish(day.state.value)
    }

    private fun saveWalk() {
        val edit = walkEdit ?: return
        val date = clock.now().toLocalDateTime(zone).date
        walkEdit = null
        sheet = null
        viewModelScope.launch {
            day.correctWalk(date, edit.log.start, edit.startMin, edit.endMin)
            publish(day.state.value)
        }
    }

    /** The shared sheet's state for the walk being corrected: no categories, Start/Length and the live line. */
    private fun walkSheetUi(): BlockSheetUi? {
        val edit = walkEdit ?: return null
        val fix = WalkCorrection.check(
            edit.startMin,
            edit.endMin,
            edit.floorMin,
            edit.ceilMin,
            clock.now().minuteOfDay(zone),
        )
        val use24 = timeFormatter.use24.value
        val line = when (fix) {
            is WalkFix.Ok -> "${label(fix.startMin, use24)}–${label(fix.endMin, use24)} · ${fix.endMin - fix.startMin} min"
            is WalkFix.Refused -> fix.why
        }
        return BlockSheetUi(
            title = "Walk",
            type = BlockType.Walk,
            categories = persistentListOf(),
            startMin = edit.startMin,
            lengthMin = edit.endMin - edit.startMin,
            line = line,
            lineIsError = fix is WalkFix.Refused,
            canSave = fix is WalkFix.Ok,
        )
    }

    /** The work categories in use plus Free, for a block ahead (3.3.0). */
    private fun homeCategories() = (workCategories() + BlockType.Free).toPersistentList()

    private fun workCategories(): List<BlockType> = buildList {
        Categories.current.builtIns.filter { it.work }.forEach { add(typeForCategory(it)) }
        Categories.current.customs.filter { it.work }.forEach { add(typeForCategory(it)) }
    }

    /** The live line for a block ahead: what moves, or why TodayEditor refused (3.3.0). */
    private fun aheadLine(plan: List<PlanBlock>, id: String, type: BlockType, start: Int, length: Int, nowMin: Int, current: CurrentBlock?): Pair<String?, Boolean> =
        when (val result = TodayEditor.place(plan, id, type, start, length, nowMin, current)) {
            is TodayEdit.Ok -> movedLine(plan, result.blocks, id) to false
            is TodayEdit.Refused -> todayRefusalLine(plan, result.why) to true
        }

    /** The live line for the running block: PlanEditor (Retype, then Resize BOTTOM), as EditCard did. */
    private fun runningLine(plan: List<PlanBlock>, id: String, type: BlockType, length: Int, nowMin: Int, current: CurrentBlock?): Pair<String?, Boolean> {
        val original = plan.firstOrNull { it.id == id } ?: return null to false
        var blocks = plan
        if (type != original.type) {
            blocks = PlanEditor.apply(blocks, PlanEdit.Retype(id, type), nowMin, current) ?: plan
        }
        val start = blocks.firstOrNull { it.id == id }?.startMin ?: original.startMin
        val resized = PlanEditor.apply(blocks, PlanEdit.Resize(id, Edge.BOTTOM, start + length), nowMin, current) ?: blocks
        return movedLine(plan, resized, id) to false
    }

    /** "Moves later: Coding, Gym": non-free blocks other than the edited one whose start changed. */
    private fun movedLine(before: List<PlanBlock>, after: List<PlanBlock>, editId: String): String? {
        val original = before.associateBy { it.id }
        val moved = after.sortedBy { it.startMin }.filter {
            it.id != editId && it.type != BlockType.Free &&
                original[it.id] != null && original.getValue(it.id).startMin != it.startMin
        }
        return if (moved.isEmpty()) null else "Moves later: " + moved.joinToString(", ") { blockTitle(it.type) }
    }

    private fun todayRefusalLine(plan: List<PlanBlock>, why: TodayRefusal): String {
        val use24 = timeFormatter.use24.value
        return when (why) {
            TodayRefusal.Locked -> "This block can't be changed now."
            is TodayRefusal.TooSoon -> "Starts at ${label(why.floorMin, use24)} at the earliest."
            TodayRefusal.TooShort -> "A block is at least 10 minutes."
            is TodayRefusal.StartsInside -> {
                val at = label(why.endMin, use24)
                "${plan.firstOrNull { it.id == why.id }?.let { blockTitle(it.type) } ?: "A block"} runs until $at. Start at $at or later."
            }
            is TodayRefusal.NoRoom -> when {
                why.id == null -> "No room before ${label(PlanEditor.DAY_END, use24)}."
                plan.firstOrNull { it.id == why.id }?.type == BlockType.KidTime -> "No room before kid time at ${label(why.atMin, use24)}."
                else -> "No room before your walk at ${label(why.atMin, use24)}."
            }
        }
    }

    /**
     * The reason a tapped block offers no handles. Mirrors the refusals of [PlanEditor.editMode],
     * most specific first, so the toast matches what the user sees. Past blocks (index < current)
     * open the correction sheet instead and never reach this function.
     */
    private fun lockedReason(tapped: CalendarBlockUi): String {
        val dayState = day.state.value
        val screen = _state.value
        val template = day.todayPlan.value?.templateId ?: dayState.templateId()
        val index = screen.blocks.indexOfFirst { it.id == tapped.id }
        val currentIndex = screen.currentBlockId?.let { id -> screen.blocks.indexOfFirst { it.id == id } } ?: -1
        if (!tapped.type.isEditable()) return LOCKED_KID_TIME
        if (dayState == DayState.NotStarted) return LOCKED_PICK_TEMPLATE
        if (currentIndex < 0) {
            if (dayState == DayState.KidTime && template != TemplateId.FRI_SAT) return LOCKED_DAY_BLOCK_RUNNING
            return LOCKED_DAY_BLOCK_RUNNING
        }
        val nowMin = clock.now().minuteOfDay(zone)
        return if (tapped.endMin <= snapUp(nowMin + PlanEditor.SNAP)) LOCKED_WILL_MISS else LOCKED_DAY_BLOCK_RUNNING
    }

    fun correctRecord(edit: RecordEdit) {
        val date = clock.now().toLocalDateTime(zone).date
        viewModelScope.launch { history.correctDay(date, edit, zone) }
    }

    /**
     * Corrects a past block of today. The recap row carries the segment index, so the record
     * editor knows which segment to edit. The day must still have that segment in memory, which
     * is the case until the day ends (and history has it from then on).
     */
    fun correctPast(edit: RecordEdit) {
        viewModelScope.launch {
            val open = _correctingRow.value
            val changed = day.correctPast(edit)
            if (changed && open != null) {
                _correctingRow.value = DayRecapMapper.map(day.liveRecord(), timeFormatter.use24.value)
                    .rows
                    .firstOrNull { it.id == open.id }
            }
            publish(day.state.value)
        }
    }

    fun dismissCorrectPast() {
        _correctingRow.value = null
    }

    private fun openWalkSheet() {
        val preview = day.walkPreview(PlanEditor.WALK_DEFAULT) ?: return
        walkSheet = sheetUi(PlanEditor.WALK_DEFAULT, preview)
        publish(day.state.value)
    }

    private fun stepWalk(delta: Int) {
        val current = walkSheet ?: return
        val minutes = (current.minutes + delta).coerceIn(PlanEditor.WALK_MIN, PlanEditor.WALK_MAX)
        val preview = day.walkPreview(minutes) ?: return
        walkSheet = sheetUi(minutes, preview)
        publish(day.state.value)
    }

    private fun startWalk() {
        val minutes = walkSheet?.minutes ?: return
        walkSheet = null
        publish(day.state.value)
        viewModelScope.launch {
            day.startWalk(minutes)
            publish(day.state.value)
        }
    }

    /** The sheet's labels come from the same [DayOrchestrator.walkPreview] the walk will commit with. */
    private fun sheetUi(minutes: Int, preview: WalkPreview): WalkSheetUi {
        val use24 = timeFormatter.use24.value
        val nowMin = clock.now().minuteOfDay(zone)
        val homeMin = preview.homeAt.minuteOfDay(zone)
        return WalkSheetUi(
            minutes = minutes,
            turnBackAt = label(preview.turnBackAt.minuteOfDay(zone), use24),
            homeAt = label(homeMin, use24),
            afterTitle = preview.afterTitle,
            afterEnd = preview.afterEnd?.let { label(it.minuteOfDay(zone), use24) },
            cappedByKidTime = nowMin + minutes > homeMin,
            canShrink = minutes > PlanEditor.WALK_MIN,
            canGrow = day.walkPreview(minutes + PlanEditor.SNAP) != null,
        )
    }

    /**
     * The recap row for the block the user just tapped, or null when no row exists. Past blocks
     * of today always have one; future blocks, kid time and not-started days don't.
     */
    private fun recapRowForBlock(block: CalendarBlockUi): RecapRow? {
        val liveRecord = day.liveRecord()
        val rows = DayRecapMapper.map(liveRecord, timeFormatter.use24.value).rows
        return rows.firstOrNull { it.id == "${block.id}@${block.startMin}" }
    }

    fun checkIn(pick: CheckIn) {
        val date = clock.now().toLocalDateTime(zone).date
        viewModelScope.launch { history.setCheckIn(date, pick) }
    }

    private fun publish(dayState: DayState) {
        val now = clock.now()
        val minute = now.minuteOfDay(zone)
        val id = dayState.templateId()
        val todayPlan = day.todayPlan.value
        val template = todayPlan?.let { plan ->
            runCatching { templates.get(plan.templateId) }.getOrNull()?.let { Template(it.id, it.name, plan.blocks) }
        } ?: id?.let { runCatching { templates.get(it) }.getOrNull() }
        val segments = day.segments.value
        val openAny = segments.lastOrNull { it.actualEnd == null }
        val open = segments.lastOrNull { it.kind == SegmentKind.PLANNED && it.actualEnd == null }
        val doneEarly = dayState is DayState.Running && openAny?.kind == SegmentKind.SYNTHETIC_FREE
        val specs = preview?.let { pv -> todayPlan?.blocks?.let { pv.toBlockSpecs(it) } } ?: template?.blocks
        val daySpecs = specs?.let { withEvening(it, segments, day.pendingEvening.value) }
        // 2.7.0: picked before its first block, the day waits: no current block, and the calendar
        // starts at min(08:00, the first start rounded down to the hour).
        val startsAt = day.startsAt.value
        val startsAtMin = startsAt?.minuteOfDay(zone)
        val waiting = startsAtMin != null && dayState is DayState.Running
        val index = if (waiting) -1 else currentIndex(dayState, daySpecs)
        val calendarStartMin = specs?.firstOrNull()?.start?.let { minOf(DAY_START_MIN, it.hour * 60) } ?: DAY_START_MIN
        val mapped = daySpecs?.let {
            CalendarMapper.toBlocks(
                it,
                timeFormatter.use24.value,
                currentIndex = index,
                actualStartMin = if (doneEarly) null else open?.actualStart?.minuteOfDay(zone),
            )
        } ?: persistentListOf()
        // 3.3.0: Home draws the real day. The plan (with the evening) and today's segments feed the
        // verified DayTimeline; every block's geometry, phase and tail come from it, not from the plan.
        val planBlocks = daySpecs?.map {
            PlanBlock(it.id, it.type, it.start.hour * 60 + it.start.minute, it.end.hour * 60 + it.end.minute)
        } ?: emptyList()
        val spans = segmentSpans(segments)
        val timelineCurrentId = spans.lastOrNull { it.kind == SegmentKind.PLANNED && it.endMin == null }?.blockId
        val timelineById = DayTimeline.build(planBlocks, spans, timelineCurrentId, minute).associateBy { it.id }
        val doneEarlyEndLabel = openAny?.plannedEnd?.let { timeFormatter.label(it.minuteOfDay(zone)) }
        val planForEdit = preview ?: todayPlan?.blocks?.toPlanBlocks()
        val currentForEdit = day.currentForEditing()
        val blocks = mapped.mapIndexed { i, b ->
            val tl = timelineById[b.id]
            val isCurrentAndDoneEarly = doneEarly && index >= 0 && i == index
            val subtitle = when {
                isCurrentAndDoneEarly ->
                    "Done early · free until $doneEarlyEndLabel" + lateSuffix(tl?.lateMin ?: 0)
                tl == null -> b.subtitle
                tl.phase == TimelinePhase.MISSED -> "Missed"
                tl.phase == TimelinePhase.DONE && tl.overrunFromMin != null -> "+${tl.endMin - tl.overrunFromMin} min"
                tl.phase == TimelinePhase.DONE && tl.lateMin > 0 -> "${tl.lateMin} min late"
                tl.phase == TimelinePhase.DONE -> null
                else -> if (tl.lateMin > 0) listOfNotNull(b.subtitle, "${tl.lateMin} min late").joinToString(" · ") else b.subtitle
            }
            b.copy(
                startMin = tl?.startMin ?: b.startMin,
                endMin = tl?.endMin ?: b.endMin,
                timeLabel = tl?.let { "${label(it.startMin, timeFormatter.use24.value)}–${label(it.endMin, timeFormatter.use24.value)}" } ?: b.timeLabel,
                subtitle = subtitle,
                phases = if (isCurrentAndDoneEarly) persistentListOf() else b.phases,
                edit = if (todayPlan == null) b.edit
                else if (b.id == EVENING_DISHES_ID) BlockEdit.END_ONLY
                else PlanEditor.editMode(planForEdit ?: emptyList(), b.id, minute, currentForEdit),
                phase = tl?.phase ?: b.phase,
                overrunFromMin = tl?.overrunFromMin,
                lateMin = tl?.lateMin ?: 0,
                pauses = tl?.pauses?.toPersistentList() ?: persistentListOf(),
            )
        }.toPersistentList()
        val current = blocks.getOrNull(index)
        // 3.4.3: the card follows the plan the way the calendar does. The end (and so the leak)
        // comes from the current TimelineBlock's plan end, never from the open segment's own copy
        // of it. Done early rides a synthetic-free segment, so it keeps that segment's end.
        val planEndMin = current?.let { it.overrunFromMin ?: it.endMin }
        val effectiveEndMin = if (doneEarly) openAny?.plannedEnd?.let { it.minuteOfDay(zone) } else planEndMin
        val leaking = !doneEarly && current?.overrunFromMin != null
        val nextLabel = if (current != null && daySpecs != null && (doneEarly || !current.type.isWork)) {
            PlanEditor.nextStop(daySpecs.toPlanBlocks(), current.id)?.let { stop -> blocks.firstOrNull { it.id == stop.id } }
        } else blocks.getOrNull(index + 1)
        val emergencyEnded = dayState is DayState.Ended && endedRecord?.flags?.contains(DayFlag.EMERGENCY_STOP) == true

        val walk = segments.lastOrNull { it.actualEnd == null && it.kind == SegmentKind.PLANNED && it.type == BlockType.Walk }
        // The walk card (2.4.0) comes from [DayOrchestrator.walkSession]: a day-block walk's open
        // segment, or the standalone timer that leaves the day alone.
        val walkSession = day.walkSession.value
        val walkCard = if (walkSession != null) {
            val startSec = walkSession.start.epochSeconds
            val endSec = walkSession.end.epochSeconds
            val turnSec = WalkClock.turnSec(startSec, endSec)
            val turnBackMin = Instant.fromEpochSeconds(turnSec).minuteOfDay(zone)
            val afterTitle = walk?.let { wb ->
                blocks.firstOrNull { it.id == wb.blockId }?.let { walkBlock ->
                    daySpecs?.let { PlanEditor.nextStop(it.toPlanBlocks(), walkBlock.id)?.let { stop -> blocks.firstOrNull { b -> b.id == stop.id }?.title } }
                }
            }
            WalkCardUi(
                startSec = startSec,
                turnSec = turnSec,
                endSec = endSec,
                turnBackAt = timeFormatter.label(turnBackMin),
                homeAt = timeFormatter.label(walkSession.end.minuteOfDay(zone)),
                nextLabel = afterTitle,
                standalone = walk == null,
            )
        } else null

        // Ticks are the walk markers (2.6.0): a day-block walk shorter than [PlanEditor.WALK_MIN] keeps
        // its block id, a standalone walk gets `walk@<startMin>`. Day-block walks are also in the walk
        // log now, so a log entry matching a closed walk segment is skipped to avoid drawing it twice.
        val shortWalks = segments.mapNotNull { seg ->
            if (seg.type == BlockType.Walk && seg.kind == SegmentKind.PLANNED && seg.actualEnd != null) {
                val mins = (seg.actualEnd - seg.actualStart).inWholeMinutes.toInt()
                if (mins < PlanEditor.WALK_MIN) {
                    val startMin = seg.actualStart.minuteOfDay(zone)
                    WalkMarkerUi(seg.blockId ?: "walk@$startMin", startMin, "Walked $mins min")
                } else null
            } else null
        }
        val loggedWalks = walkLogs
            .filter { log -> segments.none { it.type == BlockType.Walk && it.actualStart == log.start && it.actualEnd != null } }
            .map { log ->
                val startMin = log.start.minuteOfDay(zone)
                WalkMarkerUi("walk@$startMin", startMin, "Walk · ${(log.end - log.start).inWholeMinutes} min")
            }
        val walkMarkers = (shortWalks + loggedWalks).sortedBy { it.startMin }.toPersistentList()

        // The fit sees only the blocks that are drawn as blocks: a short-walk block is a tick instead,
        // so it hands its id to the tick and its geometry to the pill.
        val walkMarkerIds = walkMarkers.mapTo(HashSet()) { it.id }
        val calendarFit = CalendarLayout.fit(
            blocks = blocks.filterNot { it.id in walkMarkerIds }.map { LayoutBlock(it.id, it.startMin, it.endMin) },
            ticks = walkMarkers.map { LayoutTick(it.id, it.startMin) },
            dayStartMin = calendarStartMin,
            text = textMetrics,
        )
        val blockById = blocks.associateBy { it.id }
        val pillLabels = buildMap {
            calendarFit.blocks.filter { it.sliver }.forEach { bf ->
                blockById[bf.id]?.let {
                    put(bf.id, if (it.phase == TimelinePhase.MISSED) "${it.title} · missed" else "${it.title} · ${it.endMin - it.startMin} min")
                }
            }
            walkMarkers.forEach { put(it.id, it.label) }
        }

        if (dayState is DayState.Ended || dayState is DayState.Off || dayState is DayState.NotStarted) {
            preview = null
            draggingId = null
            if (walkEdit == null) {
                sheet = null
                sheetId = null
                sheetMode = BlockEdit.NONE
            }
        }
        // The evening sheet opens once when Dishes starts (2.5.0); dismissing it is fine, and the
        // card's "Choose evening" reopens it. Outside the evening it is always closed.
        if (dayState is DayState.Evening) {
            if (dayState.isDishesSlot && !eveningSheetOpen && !eveningAutoOpened && day.pendingEvening.value == null) {
                eveningSheetOpen = true
                eveningAutoOpened = true
            }
        } else {
            eveningSheetOpen = false
            eveningAutoOpened = false
        }
        if (walkCard != null) walkSheet = null

        // A walk that just ended (3.4.1): offer to fix its time for 30 minutes, unless Roni dismissed it.
        val lastWalk = latestWalkLog()
        val walkFix = if (lastWalk != null && lastWalk.start.epochSeconds != dismissedWalkStartSec) {
            val sinceEnd = (now - lastWalk.end).inWholeMinutes
            if (sinceEnd in 0..30) {
                WalkFixUi(
                    startEpochSec = lastWalk.start.epochSeconds,
                    text = "Walk logged ${timeFormatter.label(lastWalk.start.minuteOfDay(zone))}–" +
                        "${timeFormatter.label(lastWalk.end.minuteOfDay(zone))}. Got home earlier?",
                )
            } else null
        } else null

        _state.value = HomeState(
            weekdayShort = now.toLocalDateTime(zone).dayOfWeek.name.take(3),
            dayOfMonth = now.toLocalDateTime(zone).day.toString(),
            title = day.todayPlan.value?.templateId?.let { templateDisplay(it).title }
                ?: endedRecord?.template?.let { templateDisplay(it).title }
                ?: id?.let { templateDisplay(it).title }.orEmpty(),
            status = statusLine(
                dayState = dayState,
                blocks = blocks,
                index = index,
                ranOverMin = if (leaking) minute - (effectiveEndMin ?: minute) else null,
                emergency = emergencyEnded,
                nowMin = minute,
                startsAtMin = startsAtMin,
            ),
            debugClock = debugClockLabel(minute),
            hourLabels = CalendarMapper.hourLabels(calendarStartMin, DAY_END_MIN, timeFormatter.use24.value).toPersistentList(),
            blocks = blocks,
            currentBlockId = current?.id,
            recap = recap,
            nowCard = if (walkCard != null) null else nowCard(dayState, current, index, blocks.size, leaking, effectiveEndMin, minute, nextLabel, doneEarly, startsAtMin),
            walkCard = walkCard,
            walkMarkers = walkMarkers,
            calendarFit = calendarFit,
            pillLabels = pillLabels,
            walkEnabled = day.walkPreview(PlanEditor.WALK_DEFAULT) != null,
            walkSheet = walkSheet,
            eveningChoicePending = dayState.isDishesSlot,
            eveningChoice = day.pendingEvening.value,
            eveningSheetOpen = eveningSheetOpen,
            draggingId = draggingId,
            selectId = insertedBlockId,
            sheet = sheet,
            sheetId = sheetId,
            use24 = timeFormatter.use24.value,
            calendarStartMin = calendarStartMin,
            walkFix = walkFix,
        )
    }

    private fun debugClockLabel(minute: Int): String? =
        if (debugTime.offsetMinutes.value == 0) null else "Debug clock ${TimeFormatter.label(minute, timeFormatter.use24.value)}"

    /**
     * Today's segments as the [DayTimeline] sees them: minutes of the day, with the id [withEvening]
     * gives the evening's blocks. A closed SYNTHETIC_FREE ("done early" leftover) is skipped: it isn't
     * a block, just free time that has been absorbed.
     */
    private fun segmentSpans(segments: List<Segment>): List<SegmentSpan> = buildList {
        for (seg in segments) {
            if (seg.kind == SegmentKind.SYNTHETIC_FREE && seg.actualEnd != null) continue
            val startMin = seg.actualStart.minuteOfDay(zone)
            val endMin = seg.actualEnd?.minuteOfDay(zone)
            val id = when {
                seg.type == BlockType.Dishes -> EVENING_DISHES_ID
                seg.type is BlockType.EveningChoice -> if (seg.actualEnd == null) EVENING_CHOICE_ID else "evening.choice@$startMin"
                else -> seg.blockId
            }
            add(SegmentSpan(id, seg.kind, startMin, endMin))
        }
    }

    /**
     * After Kid asleep: kid time ends when it was tapped, then Dishes and the evening choice appear
     * as blocks on the calendar. Built from the evening segments plus the pending activity (2.5.0);
     * it only cuts the kid-time block, never replaces it. Closed activities get their own
     * `evening.choice@<startMin>` block so a switch shows as two blocks.
     */
    private fun withEvening(specs: List<BlockSpec>, segments: List<Segment>, pending: Choice?): List<BlockSpec> {
        val evening = segments.filter { it.kind == SegmentKind.PLANNED && (it.type == BlockType.Dishes || it.type is BlockType.EveningChoice) }
        if (evening.isEmpty()) return specs
        val asleepAt = evening.first().actualStart.toLocalDateTime(zone).time
        val day = specs.map { s -> if (s.type == BlockType.KidTime && s.start < asleepAt && s.end > asleepAt) s.copy(end = asleepAt) else s }
        val added = evening.map { seg ->
            val start = seg.actualStart.toLocalDateTime(zone).time
            val end = (seg.actualEnd ?: seg.plannedEnd ?: seg.actualStart).toLocalDateTime(zone).time
            val id = when {
                seg.type == BlockType.Dishes -> EVENING_DISHES_ID
                seg.actualEnd == null -> EVENING_CHOICE_ID
                else -> "evening.choice@${start.hour * 60 + start.minute}"
            }
            BlockSpec(id = id, type = seg.type, start = start, end = end)
        }
        // A pick made during Dishes shows as the planned activity from Dishes' end to 22:30, until it
        // actually starts (then the open EveningChoice segment carries it).
        val openActivity = evening.lastOrNull { it.type is BlockType.EveningChoice && it.actualEnd == null }
        val pendingBlock = if (pending != null && openActivity == null) {
            val dishes = evening.lastOrNull { it.type == BlockType.Dishes }
            dishes?.plannedEnd?.toLocalDateTime(zone)?.time?.let { start ->
                listOf(BlockSpec(EVENING_CHOICE_ID, BlockType.EveningChoice(pending), start, LocalTime(22, 30)))
            }.orEmpty()
        } else emptyList()
        return (day + added + pendingBlock).sortedBy { it.start }
    }

    private fun currentIndex(dayState: DayState, daySpecs: List<BlockSpec>?): Int = when (dayState) {
        is DayState.Running -> dayState.blockIndex
        is DayState.Paused -> dayState.running.blockIndex
        is DayState.Evening -> daySpecs?.indexOfFirst {
            it.id == if (dayState.isDishesSlot) EVENING_DISHES_ID else EVENING_CHOICE_ID
        } ?: -1
        else -> -1
    }

    /** The line under the day's title (2.6.2). */
    private fun statusLine(
        dayState: DayState,
        blocks: List<CalendarBlockUi>,
        index: Int,
        ranOverMin: Int?,
        emergency: Boolean,
        nowMin: Int,
        startsAtMin: Int? = null,
    ): String {
        if (emergency) return "Stopped early (emergency)"
        if (startsAtMin != null) return "Starts at ${timeFormatter.label(startsAtMin)}"
        if (dayState == DayState.Off) return "Off day"
        if (dayState == DayState.NotStarted || blocks.isEmpty()) return "Pick a template"
        val dayBlocks = blocks.filter { it.type.isWork || it.type == BlockType.Free }
        val running = dayState is DayState.Running || dayState is DayState.Paused
        val ahead = if (running) {
            dayBlocks.count { it.startMin > (blocks.getOrNull(index)?.startMin ?: nowMin) }
        } else {
            dayBlocks.count { it.startMin >= nowMin } // Fri/Sat: kid time first, day blocks after
        }
        if (running || ahead > 0) {
            val left = if (ahead == 0) "Last block" else "$ahead ${if (ahead == 1) "block" else "blocks"} left"
            return if (ranOverMin != null && ranOverMin > 0) "$left · ran over $ranOverMin min" else left
        }
        val rows = DayRecapMapper.map(
            if (endedRecord != null) endedRecord!! else day.liveRecord(),
            timeFormatter.use24.value,
        ).rows
            .filter { r -> r.type?.let { it.isWork || it == BlockType.Free } == true }
        val missed = rows.count { it.missed }
        val done = rows.size - missed
        return if (rows.isEmpty()) "Day done" else "$done done · $missed missed"
    }

    private fun nowCard(
        dayState: DayState,
        current: CalendarBlockUi?,
        index: Int,
        total: Int,
        leaking: Boolean,
        effectiveEndMin: Int?,
        minute: Int,
        next: CalendarBlockUi?,
        doneEarly: Boolean,
        startsAtMin: Int? = null,
    ): NowCardUi? {
        // 2.7.0: the day is picked but waits for its first block: a calm card, Start now or Change day.
        if (startsAtMin != null) {
            return NowCardUi(
                title = "Day starts at ${timeFormatter.label(startsAtMin)}",
                type = null,
                chip = null,
                chipTone = ChipTone.NEUTRAL,
                subtitle = "in ${waitingLeft(startsAtMin - minute)}",
                isLeaking = false,
                showProgress = false,
                buttons = persistentListOf(
                    NowButton(NowButtonId.START_NOW, "Start now", NowButtonStyle.PRIMARY),
                    NowButton(NowButtonId.CHANGE_DAY, "Change day", NowButtonStyle.SECONDARY),
                ),
            )
        }
        val nextTitle = next?.title
        return when (dayState) {
            DayState.NotStarted -> NowCardUi(
                title = "Ready when you are",
                type = null,
                chip = null,
                chipTone = ChipTone.NEUTRAL,
                subtitle = "Pick a template to start the day",
                isLeaking = false,
                showProgress = false,
                buttons = persistentListOf(NowButton(NowButtonId.NEXT, "Start", NowButtonStyle.PRIMARY)),
            )
            is DayState.Running -> {
                if (doneEarly) {
                    val subtitle = buildString {
                        effectiveEndMin?.let { append("until ${timeFormatter.label(it)}") }
                        if (nextTitle != null) {
                            if (isNotEmpty()) append(" · ")
                            append("then $nextTitle")
                        }
                    }
                    NowCardUi(
                        title = "Free time",
                        type = BlockType.Free,
                        chip = "Done early",
                        chipTone = ChipTone.NEUTRAL,
                        subtitle = subtitle.ifEmpty { null },
                        isLeaking = false,
                        showProgress = false,
                        buttons = buildList {
                            add(NowButton(NowButtonId.NEXT, if (nextTitle != null) "Next: $nextTitle" else "Next", NowButtonStyle.PRIMARY))
                            add(NowButton(NowButtonId.WALK, "Walk", NowButtonStyle.SECONDARY))
                            add(NowButton(NowButtonId.EMERGENCY, "Emergency", NowButtonStyle.SECONDARY))
                        }.toPersistentList(),
                        endsAt = effectiveEndMin?.let { timeFormatter.label(it) },
                    )
                } else {
                val title = current?.title ?: "Running"
                val pomodoro = current?.subtitle != null
                if (leaking) {
                    NowCardUi(
                        title = title,
                        type = current?.type,
                        chip = "Ran over",
                        chipTone = ChipTone.LEAK,
                        subtitle = effectiveEndMin?.let { "$title ended at ${timeFormatter.label(it)}" },
                        isLeaking = true,
                        showProgress = false,
                        buttons = buildList {
                            nextTitle?.let { add(NowButton(NowButtonId.NEXT, "Next: $it", NowButtonStyle.PRIMARY)) }
                            add(NowButton(NowButtonId.WALK, "Walk", NowButtonStyle.SECONDARY))
                            add(NowButton(NowButtonId.EMERGENCY, "Emergency", NowButtonStyle.SECONDARY))
                        }.toPersistentList(),
                    )
                } else {
                    val workNotOver = current?.type?.isWork == true && effectiveEndMin != null && minute <= effectiveEndMin
                    val nextLabel = when {
                        current?.type != null && !current.type.isWork -> "Next: ${nextTitle ?: "Free"}"
                        workNotOver -> "Done early"
                        else -> "Next: ${nextTitle ?: "Free"}"
                    }
                    NowCardUi(
                        title = title,
                        type = current?.type,
                        chip = if (pomodoro) "Focus" else null,
                        chipTone = ChipTone.NEUTRAL,
                        subtitle = run {
                            if (effectiveEndMin == null) null
                            else {
                                val until = "until ${timeFormatter.label(effectiveEndMin)}"
                                if (nextTitle != null && current?.type != null) "$until · then $nextTitle" else until
                            }
                        },
                        isLeaking = false,
                        showProgress = pomodoro,
                        buttons = if (current?.type != null && !current.type.isWork) {
                            persistentListOf(
                                NowButton(NowButtonId.NEXT, nextLabel, NowButtonStyle.PRIMARY),
                                NowButton(NowButtonId.WALK, "Walk", NowButtonStyle.SECONDARY),
                                NowButton(NowButtonId.EMERGENCY, "Emergency", NowButtonStyle.SECONDARY),
                            )
                        } else {
                            persistentListOf(
                                NowButton(NowButtonId.PAUSE, "Pause", NowButtonStyle.SECONDARY),
                                NowButton(NowButtonId.NEXT, nextLabel, NowButtonStyle.PRIMARY),
                                NowButton(NowButtonId.WALK, "Walk", NowButtonStyle.SECONDARY),
                                NowButton(NowButtonId.EMERGENCY, "Emergency", NowButtonStyle.SECONDARY),
                            )
                        },
                        endsAt = effectiveEndMin?.let { timeFormatter.label(it) },
                    )
                }
                }
            }
            is DayState.Paused -> NowCardUi(
                title = "Paused",
                type = current?.type,
                chip = null,
                chipTone = ChipTone.NEUTRAL,
                subtitle = effectiveEndMin?.let { "until ${timeFormatter.label(it)}" },
                isLeaking = false,
                showProgress = false,
                buttons = persistentListOf(
                    NowButton(NowButtonId.RESUME, "Resume", NowButtonStyle.PRIMARY),
                    NowButton(NowButtonId.EMERGENCY, "Emergency", NowButtonStyle.SECONDARY),
                ),
                endsAt = effectiveEndMin?.let { timeFormatter.label(it) },
            )
            DayState.KidTime -> NowCardUi(
                title = "Kid time",
                type = BlockType.KidTime,
                chip = null,
                chipTone = ChipTone.NEUTRAL,
                subtitle = "Evening starts when you tap Kid asleep",
                isLeaking = false,
                showProgress = false,
                buttons = persistentListOf(
                    NowButton(NowButtonId.KID_ASLEEP, "Kid asleep", NowButtonStyle.PRIMARY),
                    NowButton(NowButtonId.END_DAY, "End day", NowButtonStyle.SECONDARY),
                ),
            )
            is DayState.Evening -> if (dayState.isDishesSlot) {
                val pick = day.pendingEvening.value
                NowCardUi(
                    title = "Dishes",
                    type = BlockType.Dishes,
                    chip = null,
                    chipTone = ChipTone.NEUTRAL,
                    subtitle = if (pick != null) "Tonight: ${choiceLabel(pick)}" else "Pick what tonight is about",
                    isLeaking = false,
                    showProgress = false,
                    buttons = persistentListOf(
                        NowButton(NowButtonId.CHOOSE_EVENING, if (pick != null) "Change evening" else "Choose evening", NowButtonStyle.PRIMARY),
                        NowButton(NowButtonId.SKIP_DISHES, "Done dishes", NowButtonStyle.SECONDARY),
                        NowButton(NowButtonId.END_DAY, "End day", NowButtonStyle.SECONDARY),
                    ),
                )
            } else {
                NowCardUi(
                    title = current?.title ?: "Evening",
                    type = current?.type,
                    chip = null,
                    chipTone = ChipTone.NEUTRAL,
                    subtitle = "until ${timeFormatter.label(22 * 60 + 30)}",
                    isLeaking = false,
                    showProgress = false,
                    buttons = persistentListOf(
                        NowButton(NowButtonId.CHOOSE_EVENING, "Switch activity", NowButtonStyle.PRIMARY),
                        NowButton(NowButtonId.END_DAY, "End day", NowButtonStyle.SECONDARY),
                    ),
                )
            }
            DayState.Ended -> NowCardUi(
                title = "Day done",
                type = null,
                chip = null,
                chipTone = ChipTone.NEUTRAL,
                subtitle = "ran over ${(effectiveEndMin?.let { minute - it } ?: 0).coerceAtLeast(0)} min · $total blocks",
                isLeaking = false,
                showProgress = false,
                buttons = persistentListOf(),
            )
            DayState.Off -> NowCardUi(
                title = "Off day. Enjoy.",
                type = null,
                chip = null,
                chipTone = ChipTone.NEUTRAL,
                subtitle = null,
                isLeaking = false,
                showProgress = false,
                buttons = persistentListOf(),
            )
        }
    }

    private fun countdown(s: HomeState, minute: Int, second: Int): String {
        val block = s.blocks.firstOrNull { it.id == s.currentBlockId } ?: return ""
        if (minute < block.endMin) return "${block.endMin - minute} min left"
        val over = (minute - block.endMin) * 60 + second
        val h = over / 3600
        val m = (over % 3600) / 60
        val sec = over % 60
        return if (h > 0) "-$h:${"%02d".format(m)}:${"%02d".format(sec)}" else "-${"%02d".format(m)}:${"%02d".format(sec)}"
    }

    private fun progressOf(s: HomeState, minute: Int): Float {
        val block = s.blocks.firstOrNull { it.id == s.currentBlockId } ?: return 0f
        val span = (block.endMin - block.startMin).coerceAtLeast(1)
        return ((minute - block.startMin).toFloat() / span).coerceIn(0f, 1f)
    }
}

private const val LOCKED_KID_TIME = "Kid time and the evening can't be moved."
private const val LOCKED_PICK_TEMPLATE = "Pick a template first; then you can change the day."
private const val LOCKED_DAY_BLOCK_RUNNING = "Blocks can be changed while a day block is running."
private const val LOCKED_WILL_MISS = "This block can't start before it ends, so it will count as missed."

private fun snapUp(min: Int): Int = ((min + PlanEditor.SNAP - 1) / PlanEditor.SNAP) * PlanEditor.SNAP

/** The evening activity's name for the card (2.5.0). */
private fun choiceLabel(choice: Choice): String = when (choice) {
    Choice.GAME -> "Game"
    Choice.PROJECT -> "Project"
    Choice.READ -> "Read"
    Choice.CHILL -> "Chill"
}

/** "1h 20m" / "45 min": how long until the day's first block (2.7.0). */
private fun waitingLeft(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "$m min"
}

/** " · N min late" for a block that started late; empty otherwise. */
private fun lateSuffix(lateMin: Int): String = if (lateMin > 0) " · $lateMin min late" else ""

@Composable
fun HomeRoute(
    onMenu: (NavKey) -> Unit,
    onSwap: () -> Unit,
    onTab: (NavKey) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val minute by viewModel.minuteOfDay.collectAsStateWithLifecycle()
    val countdown by viewModel.countdownText.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val walkView by viewModel.walkView.collectAsStateWithLifecycle()
    val suppressed by viewModel.suppressedConfirmations.collectAsStateWithLifecycle()
    val stepsLabel by viewModel.stepsLabel.collectAsStateWithLifecycle()
    val minuteOfDay = { minute }
    val countdownText = { countdown }
    val progressValue = { progress }

    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message = it }
    }

    val correctingRow by viewModel.correctingRow.collectAsStateWithLifecycle()

    ScreenScaffold(bottomBar = { AppBottomBar(AppTab.HOME) { onTab(tabKey(it)) } }) { _ ->
        HomeScreen(
            state = state,
            minuteOfDay = minuteOfDay,
            countdownText = countdownText,
            progress = progressValue,
            suppressedConfirmations = suppressed,
            onSuppressConfirmation = viewModel::onSuppressConfirmation,
            onAskAgain = viewModel::onAskAgain,
            onCorrect = viewModel::correctRecord,
            onCorrectPast = viewModel::correctPast,
            onCheckIn = viewModel::checkIn,
            walkView = { walkView },
            stepsLabel = stepsLabel,
            onAction = { action ->
                when (action) {
                    HomeAction.SwapTemplate -> onSwap()
                    HomeAction.History -> onMenu(HistoryKey)
                    HomeAction.Profiles -> onMenu(ProfilesKey)
                    HomeAction.Settings -> onMenu(SettingsKey)
                    else -> viewModel.onAction(action)
                }
            },
        )
    }

    correctingRow?.let { row ->
        CorrectBlockSheet(
            row = row,
            use24 = state.use24,
            onEdit = { viewModel.correctPast(it) },
            onDismiss = { viewModel.dismissCorrectPast() },
        )
    }

    message?.let { m ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = null,
            text = { Text(m, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { message = null }) { Text("OK") }
            },
        )
    }
}

@Composable
fun HomeScreen(
    state: HomeState,
    minuteOfDay: () -> Int,
    countdownText: () -> String,
    progress: () -> Float,
    onAction: (HomeAction) -> Unit,
    suppressedConfirmations: Set<NowButtonId> = emptySet(),
    onSuppressConfirmation: (NowButtonId) -> Unit = {},
    onAskAgain: (NowButtonId) -> Unit = {},
    onCorrect: (RecordEdit) -> Unit = {},
    onCorrectPast: (RecordEdit) -> Unit = {},
    onCheckIn: (CheckIn) -> Unit = {},
    walkView: () -> WalkView? = { null },
    stepsLabel: String? = null,
) {
    SideEffect { RecompositionCounter.hit("HomeScreen") }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    // Measured once per font scale in the exact styles CalendarBlock draws (2.6.1): the layout fits
    // against real text heights, so a bigger font makes shorter blocks use one line or a pill.
    val labelLarge = MaterialTheme.typography.labelLarge
    val titleSmall = MaterialTheme.typography.titleSmall
    val timeStyle = MaterialTheme.typography.labelSmall.merge(AppType.numbers)
    val textMetrics = remember(measurer, density, labelLarge, titleSmall, timeStyle) {
        with(density) {
            TextMetrics(
                oneLineDp = measurer.measure("Ag · 00:00", labelLarge).size.height.toDp().value,
                twoLinesDp = (measurer.measure("Ag", titleSmall).size.height + measurer.measure("00:00", timeStyle).size.height)
                    .toDp().value,
            )
        }
    }
    LaunchedEffect(textMetrics) {
        onAction(HomeAction.TextMeasured(textMetrics))
    }
    var correcting by remember { mutableStateOf<RecapRow?>(null) }

    LaunchedEffect(state.currentBlockId) {
        val start = state.blocks.firstOrNull { it.id == state.currentBlockId }?.startMin
            ?: return@LaunchedEffect
        val target = with(density) {
            TopInset.toPx() + (start - state.calendarStartMin) * HourHeight.toPx() / 60f - Metrics.scrollLeadIn.toPx()
        }
        scroll.animateScrollTo(target.toInt().coerceAtLeast(0))
    }

    BackHandler(enabled = state.sheet != null) { onAction(HomeAction.Sheet(BlockSheetAction.Dismiss)) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HomeTopBar(
                weekdayShort = state.weekdayShort,
                dayOfMonth = state.dayOfMonth,
                title = state.title,
                status = state.status,
                debugClock = state.debugClock,
                walkEnabled = state.walkEnabled,
                stepsLabel = stepsLabel,
                showDivider = { scroll.value > 0 },
                onAction = onAction,
            )
            val recap = state.recap
            if (recap != null) {
                // A standalone walk (2.4.0) shows its card above an ended day's recap list.
                Column(Modifier.weight(1f)) {
                    state.walkCard?.let { ui ->
                        WalkCard(
                            ui = ui,
                            view = walkView,
                            onAction = onAction,
                            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
                        )
                    }
                    RecapList(
                        s = recap,
                        bottomPadding = Spacing.l,
                        modifier = Modifier.weight(1f).testTag("home_calendar"),
                        onRowTap = { correcting = it },
                        onCheckIn = onCheckIn,
                    )
                }
            } else {
                DayCalendar(
                    blocks = state.blocks,
                    calendarFit = state.calendarFit,
                    pillLabels = state.pillLabels,
                    walkMarkers = state.walkMarkers,
                    dayStartMin = state.calendarStartMin,
                    currentBlockId = state.currentBlockId,
                    hourLabels = state.hourLabels,
                    minuteOfDay = minuteOfDay,
                    scrollState = scroll,
                    bottomPadding = NowCardReservedHeight,
                    onBlockClick = { clicked ->
                        val block = state.blocks.firstOrNull { it.id == clicked }
                        if (block != null && block.edit == BlockEdit.NONE) {
                            onAction(HomeAction.LockedBlockTapped(clicked))
                        } else {
                            onAction(HomeAction.OpenBlock(clicked))
                        }
                    },
                    selectedBlockId = state.sheetId ?: state.selectId,
                    draggingId = state.draggingId,
                    onAction = onAction,
                    modifier = Modifier.weight(1f).testTag("home_calendar"),
                )
                state.walkFix?.let { fix ->
                    WalkFixCard(
                        ui = fix,
                        onFix = { onAction(HomeAction.FixWalk) },
                        onDismiss = { onAction(HomeAction.DismissWalkFix) },
                        modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
                    )
                }
            }
        }
        if (state.recap == null) {
            state.walkCard?.let { ui ->
                WalkCard(
                    ui = ui,
                    view = walkView,
                    onAction = onAction,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = Spacing.l, vertical = Spacing.s),
                )
            } ?: state.nowCard?.let { ui ->
                NowCard(
                    ui = ui,
                    countdownText = countdownText,
                    progress = progress,
                    onAction = onAction,
                    suppressedConfirmations = suppressedConfirmations,
                    onSuppressConfirmation = onSuppressConfirmation,
                    onAskAgain = onAskAgain,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = Spacing.l, vertical = Spacing.s),
                )
            }
        }
    }

    if (state.eveningSheetOpen) {
        EveningChoiceSheet(
            onPick = { onAction(HomeAction.PickEvening(it)) },
            onDismiss = { onAction(HomeAction.DismissEveningSheet) },
        )
    }
    state.sheet?.let { ui ->
        BlockSheet(
            ui = ui,
            use24 = state.use24,
            onAction = { onAction(HomeAction.Sheet(it)) },
        )
    }
    state.walkSheet?.let { sheet ->
        WalkSheet(
            ui = sheet,
            onStep = { onAction(HomeAction.WalkStep(it)) },
            onStart = { onAction(HomeAction.StartWalk) },
            onDismiss = { onAction(HomeAction.DismissWalkSheet) },
        )
    }
    correcting?.let { row ->
        CorrectBlockSheet(
            row = row,
            use24 = state.use24,
            onEdit = onCorrect,
            onDismiss = { correcting = null },
        )
    }
}

@Composable
fun HomeTopBar(
    weekdayShort: String,
    dayOfMonth: String,
    title: String,
    status: String,
    debugClock: String?,
    /** A walk fits right now (2.4.0): not already walking and not paused. */
    walkEnabled: Boolean,
    showDivider: () -> Boolean,
    onAction: (HomeAction) -> Unit,
    /** "5,240 steps" (2.5.0); null hides the line. */
    stepsLabel: String? = null,
) {
    SideEffect { RecompositionCounter.hit("HomeTopBar") }
    val c = AppTheme.colors
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .drawBehind {
                if (showDivider()) {
                    drawLine(c.outline, Offset(0f, size.height), Offset(size.width, size.height), Metrics.hairline.toPx())
                }
            }
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(weekdayShort, style = MaterialTheme.typography.labelSmall, color = c.primary)
            Box(
                Modifier.size(Metrics.dateBadge).background(c.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(dayOfMonth, style = MaterialTheme.typography.titleMedium.merge(AppType.numbers), color = c.onPrimary)
            }
        }
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(status, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                debugClock?.let { AppChip(it, c.secondary, Modifier.padding(start = Spacing.xxs)) }
            }
            stepsLabel?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
        }
        IconButton(onClick = { onAction(HomeAction.SwapTemplate) }) {
            Icon(Icons.Outlined.SwapHoriz, "Swap template")
        }
        if (walkEnabled) {
            IconButton(onClick = { onAction(HomeAction.Walk) }) {
                Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, "Take a walk")
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.Menu, "Menu") }
            AppDropdownMenu(
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                onAction = { action ->
                    menuOpen = false
                    onAction(action)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (HomeAction) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = AppShapes.card,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        MenuRow(Icons.Outlined.History, "History") { onAction(HomeAction.History) }
        MenuRow(Icons.Outlined.People, "Profiles") { onAction(HomeAction.Profiles) }
        MenuRow(Icons.Outlined.Settings, "Settings") { onAction(HomeAction.Settings) }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) },
        leadingIcon = { Icon(icon, null) },
        onClick = onClick,
    )
}

/**
 * The step-counting card (2.5.0). Allow asks Android for the physical-activity permission; Not now
 * makes Home stop asking, while Settings can still ask again later.
 */
@Composable
fun NowCard(
    ui: NowCardUi,
    countdownText: () -> String,
    progress: () -> Float,
    onAction: (HomeAction) -> Unit,
    infoDialogOpen: Boolean = false,
    onDismissInfoDialog: () -> Unit = {},
    suppressedConfirmations: Set<NowButtonId> = emptySet(),
    onSuppressConfirmation: (NowButtonId) -> Unit = {},
    onAskAgain: (NowButtonId) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    SideEffect { RecompositionCounter.hit("NowCard") }
    val c = AppTheme.colors
    val accent = ui.type?.let { c.block(it).accent } ?: c.primary
    var dialog by remember { mutableStateOf<NowCardDialog?>(if (infoDialogOpen) NowCardDialog.Info else null) }

    AppCard(modifier.fillMaxWidth().testTag("now_card"), borderColor = if (ui.isLeaking) c.leak else null) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(Metrics.nowDot * 2).background(accent, CircleShape))
                Spacer(Modifier.width(Spacing.s))
                Text(
                    ui.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ui.chip?.let { AppChip(it, ui.chipTone.color(c)) }
                IconButton(
                    onClick = { dialog = NowCardDialog.Info },
                    modifier = Modifier.size(Metrics.touch),
                ) { Icon(Icons.Outlined.Info, "What the buttons do") }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                CountdownText(countdownText, if (ui.isLeaking) c.leak else c.textPrimary, Modifier.weight(1f))
                ui.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                }
            }
            if (ui.showProgress) ProgressLine(progress, accent)
            val pause = ui.buttons.firstOrNull { it.id == NowButtonId.PAUSE }
            val primary = ui.buttons.firstOrNull { it.style == NowButtonStyle.PRIMARY }
            val emergency = ui.buttons.filter { it.id == NowButtonId.EMERGENCY }
            val others = ui.buttons.filter { it != pause && it != primary && it.id != NowButtonId.EMERGENCY }
            val open = { button: NowButton ->
                if (button.id.needsConfirmation && button.id !in suppressedConfirmations) {
                    dialog = NowCardDialog.Confirm(button.id)
                } else {
                    onAction(button.id.toAction())
                }
            }
            if (pause != null && primary != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    NowSecondaryButton(button = pause, modifier = Modifier.weight(1f), onClick = { open(pause) })
                    NowPrimaryButton(button = primary, modifier = Modifier.weight(1f), onClick = { onAction(primary.id.toAction()) })
                }
            } else {
                primary?.let { button -> NowPrimaryButton(button, Modifier.fillMaxWidth()) { onAction(button.id.toAction()) } }
            }
            if (others.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    others.forEach { button ->
                        NowSecondaryButton(
                            button = button,
                            modifier = Modifier.weight(1f),
                            onClick = { open(button) },
                        )
                    }
                }
            }
            emergency.forEach { button ->
                TextButton(
                    onClick = { dialog = NowCardDialog.Confirm(button.id) },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .heightIn(min = 40.dp)
                        .testTag("now_emergency"),
                    colors = ButtonDefaults.textButtonColors(contentColor = c.leak),
                ) {
                    Text(button.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    when (val open = dialog) {
        null -> Unit
        NowCardDialog.Info -> NowCardInfoDialog(
            blockTitle = ui.title,
            onDismiss = {
                dialog = null
                onDismissInfoDialog()
            },
        )
        is NowCardDialog.Confirm -> ConfirmationDialog(
            spec = checkNotNull(open.id.confirmation(ui.title, ui.endsAt)),
            onConfirm = { askedAgain ->
                dialog = null
                if (askedAgain) onSuppressConfirmation(open.id) else onAskAgain(open.id)
                onAction(open.id.toAction())
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
fun WalkFixCard(
    ui: WalkFixUi,
    onFix: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SideEffect { RecompositionCounter.hit("WalkFixCard") }
    val c = AppTheme.colors
    AppCard(modifier.fillMaxWidth().testTag("walk_fix_card")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(
                text = ui.text,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textPrimary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onFix, modifier = Modifier.testTag("walk_fix_button")) {
                Text("Fix the time")
            }
            IconButton(onClick = onDismiss, modifier = Modifier.testTag("walk_fix_dismiss")) {
                Text("\u2715", style = MaterialTheme.typography.titleMedium, color = c.textSecondary)
            }
        }
    }
}

@Composable
fun WalkCard(
    ui: WalkCardUi,
    view: () -> WalkView?,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SideEffect { RecompositionCounter.hit("WalkCard") }
    val c = AppTheme.colors
    val accent = c.block(BlockType.Walk).accent
    val v = view()
    val home = v?.phase == WalkPhase.HOME || v?.phase == WalkPhase.DONE

    AppCard(modifier.fillMaxWidth().testTag("walk_card")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(WalkRingSize), contentAlignment = Alignment.Center) {
                    WalkRing(progress = { v?.progress ?: 0f }, modifier = Modifier.fillMaxSize())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CountdownText({ v?.text ?: "" }, c.textPrimary)
                        Text(
                            if (home) "to home" else "to turn",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Walk", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        AppChip(if (home) "Heading home" else "Heading out", accent)
                    }
                    Text(
                        "Turn back at ${ui.turnBackAt}",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textSecondary,
                    )
                    Text(
                        buildString {
                            append("Home by ${ui.homeAt}")
                            ui.nextLabel?.let { append(" · then $it") }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textSecondary,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (v?.phase == WalkPhase.OUT) {
                    NowSecondaryButton(
                        button = NowButton(NowButtonId.WALK, "Turn back now", NowButtonStyle.SECONDARY),
                        onClick = { onAction(HomeAction.TurnBack) },
                        modifier = Modifier.weight(1f),
                    )
                }
                NowPrimaryButton(
                    button = NowButton(NowButtonId.NEXT, "End walk", NowButtonStyle.PRIMARY),
                    modifier = Modifier.weight(1f),
                    onClick = { onAction(if (ui.standalone) HomeAction.EndWalk else HomeAction.Next) },
                )
            }
        }
    }
}

private val WalkRingSize = 120.dp

/** The way out is cool, the way home warm; elapsed solid, the rest dimmed. */
@Composable
private fun WalkRing(progress: () -> Float, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val out = c.block(BlockType.Walk).accent
    val home = c.secondary
    val track = c.outline
    Canvas(modifier) {
        val stroke = 8.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        val style = Stroke(width = stroke, cap = StrokeCap.Butt)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension / 2f
        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = style)

        val p = progress().coerceIn(0f, 1f)
        fun leg(color: Color, from: Float, to: Float) {
            val solidEnd = p.coerceIn(from, to)
            if (solidEnd > from) {
                drawArc(color, -90f + from * 360f, (solidEnd - from) * 360f, false, topLeft, arcSize, style = style)
            }
            if (to > solidEnd) {
                drawArc(color.copy(alpha = 0.35f), -90f + solidEnd * 360f, (to - solidEnd) * 360f, false, topLeft, arcSize, style = style)
            }
        }
        leg(out, 0f, 0.5f)
        leg(home, 0.5f, 1f)

        // Notch at the turn-back point (the bottom of the ring).
        drawLine(c.background, Offset(cx, cy + radius - stroke), Offset(cx, cy + radius), stroke * 0.5f)

        // A dot on the ring for now.
        val rad = Math.toRadians((-90.0 + p * 360.0))
        val dot = Offset(cx + (cos(rad) * (radius - stroke / 2)).toFloat(), cy + (sin(rad) * (radius - stroke / 2)).toFloat())
        drawCircle(c.textPrimary, radius = stroke * 0.55f, center = dot)
    }
}

@Composable
private fun CountdownText(text: () -> String, color: Color, modifier: Modifier = Modifier) {
    SideEffect { RecompositionCounter.hit("CountdownText") }
    val value = text()
    if (value.isNotEmpty()) {
        Text(value, style = MaterialTheme.typography.headlineSmall.merge(AppType.numbers), color = color, modifier = modifier)
    } else {
        Spacer(modifier)
    }
}

@Composable
private fun ProgressLine(progress: () -> Float, color: Color) {
    val track = AppTheme.colors.outline
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(Metrics.progress)
            .drawBehind {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = r)
                drawRoundRect(color, size = Size(size.width * progress().coerceIn(0f, 1f), size.height), cornerRadius = r)
            },
    )
}

@Composable
private fun NowPrimaryButton(button: NowButton, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = Metrics.touch).testTag("now_primary"),
        shape = AppShapes.chip,
        colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
    ) {
        Text(button.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun NowSecondaryButton(button: NowButton, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = Metrics.touch)
            .then(if (button.id == NowButtonId.PAUSE) Modifier.testTag("now_pause") else Modifier),
        shape = AppShapes.chip,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = c.surfaceRaised, contentColor = c.textPrimary),
    ) {
        Icon(nowButtonIcon(button.id), contentDescription = null, modifier = Modifier.size(Metrics.iconSm))
        Spacer(Modifier.width(Spacing.xs))
        Text(button.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

private fun nowButtonIcon(id: NowButtonId): ImageVector = when (id) {
    NowButtonId.PAUSE -> Icons.Outlined.Pause
    NowButtonId.EMERGENCY -> Icons.Outlined.WarningAmber
    NowButtonId.END_DAY -> Icons.Outlined.Stop
    NowButtonId.NEXT -> Icons.Outlined.PlayArrow
    NowButtonId.WALK -> Icons.AutoMirrored.Outlined.DirectionsWalk
    NowButtonId.RESUME -> Icons.Outlined.PlayArrow
    NowButtonId.KID_ASLEEP -> Icons.Outlined.Nightlight
    NowButtonId.CHOOSE_EVENING -> Icons.Outlined.Nightlight
    NowButtonId.SKIP_DISHES -> Icons.Outlined.PlayArrow
    NowButtonId.START_NOW -> Icons.Outlined.PlayArrow
    NowButtonId.CHANGE_DAY -> Icons.Outlined.SwapHoriz
}

private fun NowButtonId.toAction(): HomeAction = when (this) {
    NowButtonId.PAUSE -> HomeAction.Pause
    NowButtonId.EMERGENCY -> HomeAction.Emergency
    NowButtonId.NEXT -> HomeAction.Next
    NowButtonId.WALK -> HomeAction.Walk
    NowButtonId.RESUME -> HomeAction.Resume
    NowButtonId.KID_ASLEEP -> HomeAction.KidAsleep
    NowButtonId.CHOOSE_EVENING -> HomeAction.ChooseEvening
    NowButtonId.SKIP_DISHES -> HomeAction.SkipDishes
    NowButtonId.END_DAY -> HomeAction.EndDay
    NowButtonId.START_NOW -> HomeAction.StartNow
    NowButtonId.CHANGE_DAY -> HomeAction.SwapTemplate
}

private fun ChipTone.color(c: com.roni.blocks.designsystem.AppColors): Color = when (this) {
    ChipTone.NEUTRAL -> c.textSecondary
    ChipTone.BREAK -> c.secondary
    ChipTone.LEAK -> c.leak
}
