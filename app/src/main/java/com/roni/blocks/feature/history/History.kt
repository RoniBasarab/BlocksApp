package com.roni.blocks.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.roni.blocks.core.model.CheckIn
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.WalkLog
import com.roni.blocks.core.model.actualMinutes
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.schedule.RecordEdit
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.debtMinutes
import com.roni.blocks.core.system.AppRescue
import com.roni.blocks.core.system.Failures
import com.roni.blocks.core.system.ProblemAction
import com.roni.blocks.core.system.reportFailures
import com.roni.blocks.core.time.TimeFormatter
import com.roni.blocks.core.time.minuteOfDay
import com.roni.blocks.core.walk.WalkCorrection
import com.roni.blocks.core.walk.WalkFix
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppChip
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.AppType
import com.roni.blocks.designsystem.BackTopBar
import com.roni.blocks.designsystem.BlockStrip
import com.roni.blocks.designsystem.EmptyState
import com.roni.blocks.designsystem.ErrorCard
import com.roni.blocks.designsystem.Good
import com.roni.blocks.designsystem.Leak
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.Secondary
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.designsystem.TextSecondary
import com.roni.blocks.designsystem.blockColors
import com.roni.blocks.designsystem.formatStepsApprox
import com.roni.blocks.designsystem.icons.AppIcons
import com.roni.blocks.designsystem.templateDisplay
import com.roni.blocks.feature.home.CorrectBlockSheet
import com.roni.blocks.feature.home.DayRecapMapper
import com.roni.blocks.feature.home.DayRecapUi
import com.roni.blocks.feature.home.RecapList
import com.roni.blocks.feature.home.RecapRow
import com.roni.blocks.feature.templates.BlockSheet
import com.roni.blocks.feature.templates.BlockSheetAction
import com.roni.blocks.feature.templates.BlockSheetUi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class HistoryChip(val text: String, val color: Color)

data class HistoryDay(
    val date: String,
    val weekdayShort: String,
    val dayNumber: String,
    val templateTitle: String,
    val profileName: String?,
    val strip: List<Pair<Color, Float>>,
    val summary: String,
    val chip: HistoryChip,
    val outlined: Boolean,
)

data class HistoryWeek(val header: String, val days: List<HistoryDay>)

data class HistoryUiState(
    val hasData: Boolean,
    val weeks: List<HistoryWeek> = emptyList(),
    val failed: Boolean = false,
)

/** What the History list can do (3.5.0): handle a failure. Tapping a day is navigation, kept in the Route. */
sealed interface HistoryAction {
    data class Problem(val action: ProblemAction) : HistoryAction
}

/** One logged walk as History lists it (3.4.1): its start (to open the sheet) and its line. */
@Immutable
data class WalkRowUi(val startEpochSec: Long, val text: String)

/** What the History detail screen can do (3.4.1). */
sealed interface HistoryDetailAction {
    /** Tapping a walk row opens the correction sheet for it. */
    data class OpenWalk(val startEpochSec: Long) : HistoryDetailAction
    /** One action inside the walk sheet, shared with Home. */
    data class Sheet(val action: BlockSheetAction) : HistoryDetailAction
    /** The error card's Restart / Send log (3.5.0). */
    data class Problem(val action: ProblemAction) : HistoryDetailAction
}

data class HistoryDetailUi(
    val title: String,
    val recap: DayRecapUi,
    val use24: Boolean = true,
    /** One per standalone walk (2.4.0); empty hides the section. */
    val walks: ImmutableList<WalkRowUi> = persistentListOf(),
    /** "6,240 steps" for the day (2.5.0); null hides the header line. */
    val steps: String? = null,
    /** The walk correction sheet (3.4.1); null when it's closed. */
    val sheet: BlockSheetUi? = null,
    /** Something failed while loading or editing (3.5.0); the card replaces the content. */
    val failed: Boolean = false,
)

/** Content only; the top bar lives in [HistoryRoute]. */
@Composable
fun HistoryScreen(state: HistoryUiState, onAction: (HistoryAction) -> Unit, onDay: (HistoryDay) -> Unit) {
    if (state.failed) {
        ErrorCard(
            onAction = { onAction(HistoryAction.Problem(it)) },
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
        )
        return
    }
    if (!state.hasData) {
        EmptyState(
            icon = AppIcons.History,
            title = "No history yet",
            body = "Finished days are listed here.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.l,
            top = Spacing.s,
            end = Spacing.l,
            bottom = Spacing.l + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        state.weeks.forEachIndexed { index, week ->
            item(key = "header-${week.header}") {
                HistoryHeader(week.header, isFirst = index == 0)
            }
            items(items = week.days, key = { it.date }) { day ->
                HistoryDayRow(day = day, onClick = { onDay(day) })
            }
        }
    }
}

@Composable
private fun HistoryHeader(header: String, isFirst: Boolean) {
    Text(
        text = header,
        style = MaterialTheme.typography.labelLarge,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = if (isFirst) 0.dp else 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun HistoryDayRow(day: HistoryDay, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier
            .testTag("history_day_row")
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        borderColor = if (day.outlined) AppTheme.colors.outline else null,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Column(Modifier.width(56.dp)) {
                Text(
                    text = day.weekdayShort,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textSecondary,
                )
                Text(
                    text = day.dayNumber,
                    style = MaterialTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary,
                )
            }
            Column(Modifier.weight(1f)) {
                if (day.templateTitle.isNotEmpty()) {
                    Text(
                        text = day.templateTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.colors.textPrimary,
                    )
                }
                day.profileName?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textMuted,
                    )
                }
                if (day.strip.isNotEmpty()) {
                    BlockStrip(day.strip, Modifier.padding(vertical = Spacing.xs), height = 8.dp)
                }
                if (day.summary.isNotEmpty()) {
                    Text(
                        text = day.summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary,
                    )
                }
            }
            AppChip(day.chip.text, day.chip.color)
        }
    }
}

@HiltViewModel
/**
 * Groups every recorded day into weeks for the History list. Rules: "no days yet" is a normal state
 * that publishes `hasData = false` rather than an error; week boundaries come from
 * [HistoryManager.weekStart], so this list cannot disagree with Weekly; each day's headline is
 * derived here from its own flags and segments, never stored twice.
 *
 * Talks to: [HistoryManager], [Clock].
 */
class HistoryViewModel @Inject constructor(
    private val history: HistoryManager,
    private val clock: Clock,
    private val zone: TimeZone,
    private val failures: Failures,
    private val rescue: AppRescue,
) : ViewModel() {
    private val currentWeek = history.weekStart(clock.now().toLocalDateTime(zone).date)

    private val _state = MutableStateFlow(HistoryUiState(hasData = false))
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private fun fail() = _state.update { it.copy(failed = true) }

    init {
        failures.launch(viewModelScope, "History: load", onFailure = ::fail) { reload() }
    }

    fun onAction(action: HistoryAction) {
        when (action) {
            is HistoryAction.Problem -> rescue.handle(action.action)
        }
    }

    private suspend fun reload() {
        val days = history.days()
        if (days.isEmpty()) {
            _state.value = HistoryUiState(hasData = false, failed = _state.value.failed)
            return
        }
        val weeks = days.groupBy { history.weekStart(it.date) }
            .map { (weekStart, records) -> HistoryWeek(header(weekStart), records.map { dayUi(it) }) }
        _state.value = HistoryUiState(hasData = true, weeks = weeks, failed = _state.value.failed)
    }

    private fun header(weekStart: LocalDate): String = when (weekStart) {
        currentWeek -> "This week"
        currentWeek.minus(7, DateTimeUnit.DAY) -> "Last week"
        else -> weekRangeLabel(weekStart)
    }

    private fun weekRangeLabel(start: LocalDate): String {
        val end = start.plus(6, DateTimeUnit.DAY)
        return if (start.monthNumber == end.monthNumber) {
            "${start.dayOfMonth} – ${end.dayOfMonth} ${monthName(end.monthNumber)}"
        } else {
            "${start.dayOfMonth} ${monthName(start.monthNumber)} – ${end.dayOfMonth} ${monthName(end.monthNumber)}"
        }
    }

    private fun dayUi(record: com.roni.blocks.core.model.DayRecord): HistoryDay {
        val date = record.date
        val off = DayFlag.OFF_DAY in record.flags
        val leak = debtMinutes(record.segments)
        val pauseCount = record.segments.count { it.kind == SegmentKind.PAUSE }
        val done = record.segments.filter { it.kind == SegmentKind.PLANNED && it.actualEnd != null }
        val planned = record.segments.count { it.kind == SegmentKind.PLANNED }
        val chunk = HistoryChip(
            text = when {
                off -> "Off day"
                DayFlag.EMERGENCY_STOP in record.flags -> "Emergency"
                leak > 0 -> "Ran over ${leak}m"
                else -> "On time"
            },
            color = when {
                off -> TextSecondary
                DayFlag.EMERGENCY_STOP in record.flags -> Secondary
                leak > 0 -> Leak
                else -> Good
            },
        )
        val strip = record.segments
            .filter { it.actualEnd != null && (it.kind == SegmentKind.PLANNED || it.kind == SegmentKind.SYNTHETIC_FREE) }
            .map { segment -> blockColors(segment.type).accent to (segment.actualMinutes ?: 0).toFloat() }
        val summary = if (planned == 0) "" else {
            "${done.size}/$planned blocks" + if (pauseCount > 0) " · $pauseCount pause" + (if (pauseCount > 1) "s" else "") else ""
        }
        return HistoryDay(
            date = date.toString(),
            weekdayShort = date.dayOfWeek.name.take(3),
            dayNumber = date.dayOfMonth.toString(),
            templateTitle = (record.template?.let { id -> runCatching { templateDisplay(id).title }.getOrNull() }.orEmpty())
                + (checkInWord(record.checkIn)?.let { " · $it" } ?: ""),
            profileName = record.profileName,
            strip = strip,
            summary = summary,
            chip = chunk,
            outlined = off,
        )
    }
}

@HiltViewModel
/**
 * Loads one stored day for the detail screen. Rules: a date that no longer parses, or a record that
 * is gone, publishes null instead of throwing, so the screen can just close; the blocks come from
 * [DayRecapMapper], the same mapping Home uses, so a day looks the same in both places.
 *
 * Talks to: [HistoryManager], [DayRecapMapper], [TimeFormatter].
 */
class HistoryDetailViewModel @Inject constructor(
    private val history: HistoryManager,
    private val day: DayOrchestrator,
    private val timeFormatter: TimeFormatter,
    private val clock: Clock,
    private val zone: TimeZone,
    private val failures: Failures,
    private val rescue: AppRescue,
) : ViewModel() {
    private val _state = MutableStateFlow<HistoryDetailUi?>(null)
    val state: StateFlow<HistoryDetailUi?> = _state.asStateFlow()
    private var loadedDate: LocalDate? = null
    private var job: Job? = null
    /** The walk the correction sheet edits (3.4.1); null when it's closed. */
    private var walkEdit: WalkEdit? = null

    private fun fail() = _state.update { it?.copy(failed = true) }

    fun load(dateKey: String) {
        job?.cancel()
        job = failures.launch(viewModelScope, "History detail: load", onFailure = ::fail) {
            val date = runCatching { LocalDate.parse(dateKey) }.getOrNull() ?: return@launch
            loadedDate = date
            combine(
                history.observeDay(date),
                history.observeWalks(date),
                history.observeSteps(date),
            ) { record, logs, steps -> Triple(record, logs, steps) }
                .reportFailures(failures, "History detail: day", onFailure = ::fail)
                .collect { (record, logs, steps) ->
                    _state.value = record?.let {
                        HistoryDetailUi(
                            title = "${weekdayFull(it.date.dayOfWeek)} ${it.date.dayOfMonth} ${monthName(it.date.monthNumber)}",
                            recap = DayRecapMapper.map(it, timeFormatter.use24.value, checkInTitle = "How was this day?"),
                            use24 = timeFormatter.use24.value,
                            walks = logs.map { log -> WalkRowUi(log.start.epochSeconds, walkLine(log)) }.toPersistentList(),
                            steps = steps?.let { value ->
                                val driveSteps = history.drives(date).mapNotNull { it.steps }.sum()
                                "About ${formatStepsApprox(value)} steps" +
                                    if (driveSteps > 0) " · about ${formatStepsApprox(driveSteps)} while driving" else ""
                            },
                            sheet = walkSheetUi(),
                        )
                    }
                }
        }
    }

    fun onAction(action: HistoryDetailAction) {
        when (action) {
            is HistoryDetailAction.OpenWalk -> openWalk(action.startEpochSec)
            is HistoryDetailAction.Sheet -> onSheet(action.action)
            is HistoryDetailAction.Problem -> rescue.handle(action.action)
        }
    }

    /** Opens the shared sheet over the walk starting at [startEpochSec], with the day's real bounds. */
    private fun openWalk(startEpochSec: Long) {
        val date = loadedDate ?: return
        failures.launch(viewModelScope, "History detail: open walk", onFailure = ::fail) {
            val log = history.walks(date).firstOrNull { it.start.epochSeconds == startEpochSec } ?: return@launch
            val (floor, ceil) = day.walkBounds(date, log.start)
            walkEdit = WalkEdit(log, log.start.minuteOfDay(zone), log.end.minuteOfDay(zone), floor, ceil)
            refreshSheet()
        }
    }

    private fun onSheet(action: BlockSheetAction) {
        val edit = walkEdit ?: return
        when (action) {
            is BlockSheetAction.StartBy -> setWalk(startMin = edit.startMin + action.minutes)
            is BlockSheetAction.StartAt -> setWalk(startMin = action.minute)
            is BlockSheetAction.LengthBy -> setWalk(lengthMin = (edit.endMin - edit.startMin) + action.minutes)
            BlockSheetAction.Save -> saveWalk()
            BlockSheetAction.Dismiss -> {
                walkEdit = null
                refreshSheet()
            }
            else -> Unit
        }
    }

    private fun setWalk(startMin: Int? = null, lengthMin: Int? = null) {
        val edit = walkEdit ?: return
        val start = startMin ?: edit.startMin
        val end = start + (lengthMin ?: (edit.endMin - edit.startMin))
        walkEdit = edit.copy(startMin = start, endMin = end)
        refreshSheet()
    }

    private fun saveWalk() {
        val edit = walkEdit ?: return
        val date = loadedDate ?: return
        walkEdit = null
        failures.launch(viewModelScope, "History detail: save walk", onFailure = ::fail) {
            day.correctWalk(date, edit.log.start, edit.startMin, edit.endMin)
            refreshSheet()
        }
    }

    /** The shared sheet's state for the walk being corrected: no categories, Start/Length and the live line. */
    private fun walkSheetUi(): BlockSheetUi? {
        val edit = walkEdit ?: return null
        val nowMin = if (loadedDate == clock.now().toLocalDateTime(zone).date) clock.now().minuteOfDay(zone) else null
        val fix = WalkCorrection.check(edit.startMin, edit.endMin, edit.floorMin, edit.ceilMin, nowMin)
        val use24 = timeFormatter.use24.value
        val line = when (fix) {
            is WalkFix.Ok -> "${TimeFormatter.label(fix.startMin, use24)}–${TimeFormatter.label(fix.endMin, use24)} · ${fix.endMin - fix.startMin} min"
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

    private fun refreshSheet() {
        _state.value = _state.value?.copy(sheet = walkSheetUi())
    }

    private fun walkLine(log: WalkLog): String {
        val t = timeFormatter
        val line = "Walk · ${t.label(log.start.minuteOfDay(zone))}–${t.label(log.end.minuteOfDay(zone))} · ${(log.end - log.start).inWholeMinutes} min"
        return log.steps?.let { "$line · about ${formatStepsApprox(it)} steps" } ?: line
    }

    fun checkIn(pick: CheckIn) {
        val date = loadedDate ?: return
        failures.launch(viewModelScope, "History detail: check in", onFailure = ::fail) { history.setCheckIn(date, pick) }
    }

    fun correct(edit: RecordEdit) {
        val date = loadedDate ?: return
        failures.launch(viewModelScope, "History detail: correct", onFailure = ::fail) { history.correctDay(date, edit, zone) }
    }
}

@Composable
fun HistoryRoute(onBack: () -> Unit, onDay: (String) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScreenScaffold(
        topBar = {
            BackTopBar(title = "History", onBack = onBack)
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            HistoryScreen(
                state = state,
                onAction = viewModel::onAction,
                onDay = { onDay(it.date) },
            )
        }
    }
}

@Composable
fun HistoryDetailRoute(date: String?, onBack: () -> Unit, viewModel: HistoryDetailViewModel = hiltViewModel()) {
    LaunchedEffect(date) { date?.let { viewModel.load(it) } }
    val detail by viewModel.state.collectAsStateWithLifecycle()
    var correcting by remember { mutableStateOf<RecapRow?>(null) }
    ScreenScaffold(
        topBar = {
            BackTopBar(title = detail?.title.orEmpty(), onBack = onBack)
        },
    ) { padding ->
        if (detail?.failed == true) {
            ErrorCard(
                onAction = { viewModel.onAction(HistoryDetailAction.Problem(it)) },
                modifier = Modifier.padding(padding).padding(Spacing.l),
            )
            return@ScreenScaffold
        }
        detail?.let { d ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                RecapList(
                    s = d.recap,
                    bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + Spacing.l,
                    modifier = Modifier.weight(1f),
                    onRowTap = { correcting = it },
                    onCheckIn = viewModel::checkIn,
                    header = if (d.steps != null || d.walks.isNotEmpty()) {
                        {
                            AppCard(Modifier.fillMaxWidth()) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                                    modifier = Modifier.padding(vertical = Spacing.s),
                                ) {
                                    d.steps?.let { line ->
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.titleMedium.merge(AppType.numbers),
                                            color = AppTheme.colors.textPrimary,
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
                                        )
                                    }
                                    d.walks.forEach { row ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.onAction(HistoryDetailAction.OpenWalk(row.startEpochSec)) }
                                                .padding(horizontal = Spacing.l),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(color = Color(0xFF8FC7C9), shape = CircleShape),
                                            )
                                            Text(
                                                text = row.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = AppTheme.colors.textSecondary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else null,
                )
            }
        }
    }
    correcting?.let { row ->
        CorrectBlockSheet(
            row = row,
            use24 = detail?.use24 ?: true,
            onEdit = viewModel::correct,
            onDismiss = { correcting = null },
        )
    }
    detail?.sheet?.let { ui ->
        BlockSheet(
            ui = ui,
            use24 = detail?.use24 ?: true,
            onAction = { viewModel.onAction(HistoryDetailAction.Sheet(it)) },
        )
    }
}

/** The walk the History correction sheet is editing (3.4.1): its log, the times being changed, and the day's bounds. */
private data class WalkEdit(val log: WalkLog, val startMin: Int, val endMin: Int, val floorMin: Int, val ceilMin: Int)

private fun checkInWord(checkIn: CheckIn?): String? = when (checkIn) {
    CheckIn.GOOD -> "Good"
    CheckIn.OK -> "OK"
    CheckIn.ROUGH -> "Rough"
    null, CheckIn.SKIPPED -> null
}

private fun weekdayFull(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Monday"
    DayOfWeek.TUESDAY -> "Tuesday"
    DayOfWeek.WEDNESDAY -> "Wednesday"
    DayOfWeek.THURSDAY -> "Thursday"
    DayOfWeek.FRIDAY -> "Friday"
    DayOfWeek.SATURDAY -> "Saturday"
    DayOfWeek.SUNDAY -> "Sunday"
}

private fun monthName(number: Int): String = months[number - 1]

private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
