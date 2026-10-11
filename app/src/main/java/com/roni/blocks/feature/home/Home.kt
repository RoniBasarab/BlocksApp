package com.roni.blocks.feature.home

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.roni.blocks.core.schedule.Edge
import com.roni.blocks.core.schedule.PlanBlock
import com.roni.blocks.core.schedule.PlanEdit
import com.roni.blocks.core.schedule.PlanEditor
import com.roni.blocks.core.schedule.RecordEdit
import com.roni.blocks.core.schedule.isEditable
import com.roni.blocks.core.schedule.toBlockSpecs
import com.roni.blocks.core.schedule.toPlanBlocks
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.daystate.EndReason
import com.roni.blocks.core.daystate.isDishesSlot
import com.roni.blocks.core.daystate.templateId
import com.roni.blocks.core.debug.RecompositionCounter
import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.CheckIn
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
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.core.time.DebugTimeController
import com.roni.blocks.core.time.Ticker
import com.roni.blocks.core.time.TimeFormatter
import com.roni.blocks.core.time.minuteOfDay
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
import com.roni.blocks.designsystem.templateDisplay
import com.roni.blocks.feature.weekly.tabKey
import com.roni.blocks.navigation.HistoryKey
import com.roni.blocks.navigation.SettingsKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
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
import kotlinx.datetime.toLocalDateTime

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

    private val _suppressedConfirmations = MutableStateFlow<Set<NowButtonId>>(emptySet())

    /** Buttons the user chose "Don't ask again" for; survives process death. */
    val suppressedConfirmations: StateFlow<Set<NowButtonId>> = _suppressedConfirmations.asStateFlow()

    private var preview: List<PlanBlock>? = null
    private var draggingId: String? = null
    private var eveningSheetOpen = false

    /** The block a just-finished insert should select, so its handles show. */
    private var insertedBlockId: String? = null

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
            history.observeDay(clock.now().toLocalDateTime(zone).date).collect { record ->
                endedRecord = record?.takeIf { it.date == clock.now().toLocalDateTime(zone).date }
                recap = endedRecord?.let { DayRecapMapper.map(it, timeFormatter.use24.value) }
                publish(day.state.value)
            }
        }
        // Leaking is a boundary, not a per-minute value: only re-publish when it flips.
        viewModelScope.launch { minuteOfDay.collect { publishLeakIfChanged() } }
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
            HomeAction.KidAsleep -> day.send(DayEvent.KidAsleep)
            HomeAction.ChooseEvening -> eveningSheetOpen = true
            HomeAction.EndDay -> day.send(DayEvent.EndDay(EndReason.NORMAL))
            is HomeAction.PickEvening -> {
                eveningSheetOpen = false
                day.send(DayEvent.EveningChoice(action.choice))
            }
            is HomeAction.DragEdge -> {
                draggingId = action.id
                preview = previewResize(action.id, action.edge, action.toMin)
            }
            is HomeAction.DragDone -> commitEdit(PlanEdit.Resize(action.id, action.edge, action.toMin))
            HomeAction.DragCancelled -> {
                preview = null
                draggingId = null
            }
            is HomeAction.StepEdge -> stepEdge(action.id, action.edge, action.byMin)
            is HomeAction.Retype -> commitEdit(PlanEdit.Retype(action.id, action.type))
            is HomeAction.Insert -> insertBlock(action.id, action.type)
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
            HomeAction.Settings,
            -> Unit
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

    private fun insertBlock(id: String, type: BlockType) {
        val free = day.todayPlan.value?.blocks?.firstOrNull { it.id == id } ?: return
        val nowMin = clock.now().minuteOfDay(zone)
        val start = maxOf(free.start.hour * 60 + free.start.minute, snapUp(nowMin + PlanEditor.SNAP))
        viewModelScope.launch {
            if (!day.editPlan(PlanEdit.Insert(id, type))) return@launch
            insertedBlockId = day.todayPlan.value?.blocks?.firstOrNull {
                it.type == type && it.start.hour * 60 + it.start.minute == start
            }?.id
            preview = null
            draggingId = null
            publish(day.state.value)
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
            day.correctPast(edit)
            _correctingRow.value = null
            publish(day.state.value)
        }
    }

    fun dismissCorrectPast() {
        _correctingRow.value = null
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
        val daySpecs = specs?.let { withEvening(it, segments) }
        val index = currentIndex(dayState, daySpecs)
        val mapped = daySpecs?.let {
            CalendarMapper.toBlocks(
                it,
                timeFormatter.use24.value,
                currentIndex = index,
                actualStartMin = if (doneEarly) null else open?.actualStart?.minuteOfDay(zone),
            )
        } ?: persistentListOf()
        val blocks = if (todayPlan != null) {
            val planBlocks = preview ?: todayPlan.blocks.toPlanBlocks()
            val currentForEdit = day.currentForEditing()
            val currentBlockIndex = index.takeIf { it >= 0 }
            val liveRows = DayRecapMapper.map(day.liveRecord(), timeFormatter.use24.value).rows
            mapped.mapIndexed { i, b ->
                val isCurrentAndDoneEarly = doneEarly && currentBlockIndex != null && i == currentBlockIndex
                val phaseSubtitle = if (isCurrentAndDoneEarly) {
                    "Done early · free until ${timeFormatter.label(openAny!!.plannedEnd!!.minuteOfDay(zone))}"
                } else b.subtitle
                val phasesForUi = if (isCurrentAndDoneEarly) persistentListOf() else b.phases
                // Past blocks (i < currentIndex) show what happened: "Done HH:MM–HH:MM" or "Missed".
                val pastSubtitle = if (currentBlockIndex != null && i < currentBlockIndex) {
                    val row = liveRows.firstOrNull { it.id == "${b.id}@${b.startMin}" }
                    row?.let { pastSubtitleFor(it, timeFormatter.use24.value) }
                } else null
                b.copy(
                    edit = PlanEditor.editMode(planBlocks, b.id, minute, currentForEdit),
                    subtitle = pastSubtitle ?: phaseSubtitle,
                    phases = phasesForUi,
                )
            }.toPersistentList()
        } else {
            mapped
        }
        val current = blocks.getOrNull(index)
        val effectiveEndMin = openAny?.plannedEnd?.let { it.minuteOfDay(zone) }
        val leaking = current != null && effectiveEndMin != null && minute > effectiveEndMin
        val nextLabel = blocks.getOrNull(index + 1)
        val emergencyEnded = dayState is DayState.Ended && endedRecord?.flags?.contains(DayFlag.EMERGENCY_STOP) == true

        if (dayState is DayState.Ended || dayState is DayState.Off || dayState is DayState.NotStarted) {
            preview = null
            draggingId = null
        }
        if (!dayState.isDishesSlot) eveningSheetOpen = false

        _state.value = HomeState(
            weekdayShort = now.toLocalDateTime(zone).dayOfWeek.name.take(3),
            dayOfMonth = now.toLocalDateTime(zone).day.toString(),
            title = day.todayPlan.value?.templateId?.let { templateDisplay(it).title }
                ?: endedRecord?.template?.let { templateDisplay(it).title }
                ?: id?.let { templateDisplay(it).title }.orEmpty(),
            status = if (emergencyEnded) "Stopped early (emergency)" else status(blocks, index, leaking, effectiveEndMin, minute),
            debugClock = debugClockLabel(minute),
            hourLabels = CalendarMapper.hourLabels(DAY_START_MIN, DAY_END_MIN, timeFormatter.use24.value).toPersistentList(),
            blocks = blocks,
            currentBlockId = current?.id,
            leakFromMin = if (leaking) effectiveEndMin else null,
            recap = recap,
            nowCard = nowCard(dayState, current, index, blocks.size, leaking, effectiveEndMin, minute, nextLabel, doneEarly),
            eveningChoicePending = dayState.isDishesSlot,
            eveningSheetOpen = eveningSheetOpen,
            draggingId = draggingId,
            selectId = insertedBlockId,
            use24 = timeFormatter.use24.value,
        )
    }

    private fun debugClockLabel(minute: Int): String? =
        if (debugTime.offsetMinutes.value == 0) null else "Debug clock ${TimeFormatter.label(minute, timeFormatter.use24.value)}"

    private fun publishLeakIfChanged() {
        val before = _state.value.leakFromMin
        publish(day.state.value)
        if (_state.value.leakFromMin != before) return
        // Undo cosmetic churn when only the flag is unchanged.
        if (before == null) _state.value = _state.value.copy(leakFromMin = null)
    }

    /**
     * After Kid asleep: kid time ends when it was tapped, then Dishes and the evening choice appear
     * as blocks on the calendar.
     */
    private fun withEvening(specs: List<BlockSpec>, segments: List<Segment>): List<BlockSpec> {
        val evening = segments.filter { it.kind == SegmentKind.PLANNED && (it.type == BlockType.Dishes || it.type is BlockType.EveningChoice) }
        if (evening.isEmpty()) return specs
        val asleepAt = evening.first().actualStart.toLocalDateTime(zone).time
        val day = specs.map { s -> if (s.type == BlockType.KidTime && s.start < asleepAt && s.end > asleepAt) s.copy(end = asleepAt) else s }
        val added = evening.map { seg ->
            BlockSpec(
                id = if (seg.type == BlockType.Dishes) EVENING_DISHES_ID else EVENING_CHOICE_ID,
                type = seg.type,
                start = seg.actualStart.toLocalDateTime(zone).time,
                end = (seg.actualEnd ?: seg.plannedEnd ?: seg.actualStart).toLocalDateTime(zone).time,
            )
        }
        return (day + added).sortedBy { it.start }
    }

    private fun currentIndex(dayState: DayState, daySpecs: List<BlockSpec>?): Int = when (dayState) {
        is DayState.Running -> dayState.blockIndex
        is DayState.Paused -> dayState.running.blockIndex
        is DayState.Evening -> daySpecs?.indexOfFirst {
            it.id == if (dayState.isDishesSlot) EVENING_DISHES_ID else EVENING_CHOICE_ID
        } ?: -1
        else -> -1
    }

    private fun status(
        blocks: List<CalendarBlockUi>,
        index: Int,
        leaking: Boolean,
        effectiveEndMin: Int?,
        minute: Int,
    ): String {
        if (blocks.isEmpty()) return if (index < 0) "Pick a template" else ""
        val left = (blocks.size - index - 1).coerceAtLeast(0)
        val leak = if (leaking && effectiveEndMin != null) " · leak ${minute - effectiveEndMin} min" else ""
        return "$left blocks left$leak"
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
    ): NowCardUi? {
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
                            if (isNotEmpty()) append(" · then ")
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
                                NowButton(NowButtonId.EMERGENCY, "Emergency", NowButtonStyle.SECONDARY),
                            )
                        } else {
                            persistentListOf(
                                NowButton(NowButtonId.PAUSE, "Pause", NowButtonStyle.SECONDARY),
                                NowButton(NowButtonId.NEXT, nextLabel, NowButtonStyle.PRIMARY),
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
                NowCardUi(
                    title = "Dishes",
                    type = BlockType.Dishes,
                    chip = null,
                    chipTone = ChipTone.NEUTRAL,
                    subtitle = "Pick what tonight is about",
                    isLeaking = false,
                    showProgress = false,
                    buttons = persistentListOf(
                        NowButton(NowButtonId.CHOOSE_EVENING, "Choose evening", NowButtonStyle.PRIMARY),
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
                    buttons = persistentListOf(NowButton(NowButtonId.END_DAY, "End day", NowButtonStyle.SECONDARY)),
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

/** "Done HH:MM–HH:MM" or "Missed" for the calendar subtitle of a past block. */
private fun pastSubtitleFor(row: RecapRow, use24: Boolean): String? {
    val range = row.range
    return when {
        row.missed -> "Missed"
        range.isNotEmpty() -> "Done $range"
        else -> null
    }
}

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
    val suppressed by viewModel.suppressedConfirmations.collectAsStateWithLifecycle()
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
            onAction = { action ->
                when (action) {
                    HomeAction.SwapTemplate -> onSwap()
                    HomeAction.History -> onMenu(HistoryKey)
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
) {
    SideEffect { RecompositionCounter.hit("HomeScreen") }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = state.blocks.firstOrNull { it.id == selectedId }?.takeIf { it.edit != BlockEdit.NONE }
    var correcting by remember { mutableStateOf<RecapRow?>(null) }

    LaunchedEffect(state.selectId) {
        state.selectId?.let { selectedId = it }
    }

    LaunchedEffect(state.currentBlockId) {
        val start = state.blocks.firstOrNull { it.id == state.currentBlockId }?.startMin
            ?: return@LaunchedEffect
        val target = with(density) {
            TopInset.toPx() + (start - DAY_START_MIN) * HourHeight.toPx() / 60f - Metrics.scrollLeadIn.toPx()
        }
        scroll.animateScrollTo(target.toInt().coerceAtLeast(0))
    }

    BackHandler(enabled = selected != null) { selectedId = null }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HomeTopBar(
                weekdayShort = state.weekdayShort,
                dayOfMonth = state.dayOfMonth,
                title = state.title,
                status = state.status,
                debugClock = state.debugClock,
                showDivider = { scroll.value > 0 },
                onAction = onAction,
            )
            val recap = state.recap
            if (recap != null) {
                RecapList(
                    s = recap,
                    bottomPadding = Spacing.l,
                    modifier = Modifier.weight(1f).testTag("home_calendar"),
                    onRowTap = { correcting = it },
                    onCheckIn = onCheckIn,
                )
            } else {
                DayCalendar(
                    blocks = state.blocks,
                    currentBlockId = state.currentBlockId,
                    leakFromMin = state.leakFromMin,
                    hourLabels = state.hourLabels,
                    minuteOfDay = minuteOfDay,
                    scrollState = scroll,
                    bottomPadding = NowCardReservedHeight,
                    onBlockClick = { clicked ->
                        val block = state.blocks.firstOrNull { it.id == clicked }
                        if (block != null && block.edit == BlockEdit.NONE) {
                            onAction(HomeAction.LockedBlockTapped(clicked))
                        } else {
                            selectedId = if (selectedId == clicked) null else clicked
                        }
                    },
                    selectedBlockId = selected?.id,
                    draggingId = state.draggingId,
                    onAction = onAction,
                    modifier = Modifier.weight(1f).testTag("home_calendar"),
                )
            }
        }
        if (state.recap == null) {
            if (selected != null) {
                EditCard(
                    block = selected,
                    onDone = { selectedId = null },
                    onAction = onAction,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = Spacing.l, vertical = Spacing.s),
                )
            } else state.nowCard?.let { ui ->
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

    if (state.eveningChoicePending || state.eveningSheetOpen) {
        EveningChoiceSheet(onPick = { onAction(HomeAction.PickEvening(it)) })
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
    showDivider: () -> Boolean,
    onAction: (HomeAction) -> Unit,
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
        }
        IconButton(onClick = { onAction(HomeAction.SwapTemplate) }) {
            Icon(Icons.Outlined.SwapHoriz, "Swap template")
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
    NowButtonId.RESUME -> Icons.Outlined.PlayArrow
    NowButtonId.KID_ASLEEP -> Icons.Outlined.Nightlight
    NowButtonId.CHOOSE_EVENING -> Icons.Outlined.Nightlight
}

private fun NowButtonId.toAction(): HomeAction = when (this) {
    NowButtonId.PAUSE -> HomeAction.Pause
    NowButtonId.EMERGENCY -> HomeAction.Emergency
    NowButtonId.NEXT -> HomeAction.Next
    NowButtonId.RESUME -> HomeAction.Resume
    NowButtonId.KID_ASLEEP -> HomeAction.KidAsleep
    NowButtonId.CHOOSE_EVENING -> HomeAction.ChooseEvening
    NowButtonId.END_DAY -> HomeAction.EndDay
}

private fun ChipTone.color(c: com.roni.blocks.designsystem.AppColors): Color = when (this) {
    ChipTone.NEUTRAL -> c.textSecondary
    ChipTone.BREAK -> c.secondary
    ChipTone.LEAK -> c.leak
}
