package com.roni.blocks.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.WalkLog
import com.roni.blocks.core.schedule.RecordEdit
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.debtMinutes
import com.roni.blocks.core.time.TimeFormatter
import com.roni.blocks.core.time.minuteOfDay
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppChip
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.AppType
import com.roni.blocks.designsystem.BackTopBar
import com.roni.blocks.designsystem.BlockStrip
import com.roni.blocks.designsystem.EmptyState
import com.roni.blocks.designsystem.Good
import com.roni.blocks.designsystem.Leak
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.Secondary
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.designsystem.TextSecondary
import com.roni.blocks.designsystem.blockColors
import com.roni.blocks.designsystem.formatSteps
import com.roni.blocks.designsystem.templateDisplay
import com.roni.blocks.feature.home.CorrectBlockSheet
import com.roni.blocks.feature.home.DayRecapMapper
import com.roni.blocks.feature.home.DayRecapUi
import com.roni.blocks.feature.home.RecapList
import com.roni.blocks.feature.home.RecapRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val strip: List<Pair<Color, Float>>,
    val summary: String,
    val chip: HistoryChip,
    val outlined: Boolean,
)

data class HistoryWeek(val header: String, val days: List<HistoryDay>)

data class HistoryUiState(
    val hasData: Boolean,
    val weeks: List<HistoryWeek> = emptyList(),
)

data class HistoryDetailUi(
    val title: String,
    val recap: DayRecapUi,
    val use24: Boolean = true,
    /** "Walk · HH:MM–HH:MM · N min", one per standalone walk (2.4.0); empty hides the section. */
    val walks: List<String> = emptyList(),
    /** "6,240 steps" for the day (2.5.0); null hides the header line. */
    val steps: String? = null,
)

/** Content only; the top bar lives in [HistoryRoute]. */
@Composable
fun HistoryScreen(state: HistoryUiState, onAction: (HistoryDay) -> Unit) {
    if (!state.hasData) {
        EmptyState(
            icon = Icons.Outlined.History,
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
                HistoryDayRow(day = day, onClick = { onAction(day) })
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
) : ViewModel() {
    private val currentWeek = history.weekStart(clock.now().toLocalDateTime(zone).date)

    private val _state = MutableStateFlow(HistoryUiState(hasData = false))
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val days = history.days()
        if (days.isEmpty()) {
            _state.value = HistoryUiState(hasData = false)
            return
        }
        val weeks = days.groupBy { history.weekStart(it.date) }
            .map { (weekStart, records) -> HistoryWeek(header(weekStart), records.map { dayUi(it) }) }
        _state.value = HistoryUiState(hasData = true, weeks = weeks)
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
            .map { segment -> blockColors(segment.type).accent to ((segment.actualEnd!! - segment.actualStart).inWholeMinutes).toFloat() }
        val summary = if (planned == 0) "" else {
            "${done.size}/$planned blocks" + if (pauseCount > 0) " · $pauseCount pause" + (if (pauseCount > 1) "s" else "") else ""
        }
        return HistoryDay(
            date = date.toString(),
            weekdayShort = date.dayOfWeek.name.take(3),
            dayNumber = date.dayOfMonth.toString(),
            templateTitle = (record.template?.let { id -> runCatching { templateDisplay(id).title }.getOrNull() }.orEmpty())
                + (checkInWord(record.checkIn)?.let { " · $it" } ?: ""),
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
    private val timeFormatter: TimeFormatter,
    private val clock: Clock,
    private val zone: TimeZone,
) : ViewModel() {
    private val _state = MutableStateFlow<HistoryDetailUi?>(null)
    val state: StateFlow<HistoryDetailUi?> = _state.asStateFlow()
    private var loadedDate: LocalDate? = null
    private var job: Job? = null

    fun load(dateKey: String) {
        job?.cancel()
        job = viewModelScope.launch {
            val date = runCatching { LocalDate.parse(dateKey) }.getOrNull() ?: return@launch
            loadedDate = date
            combine(
                history.observeDay(date),
                history.observeWalks(date),
                history.observeSteps(date),
            ) { record, logs, steps -> Triple(record, logs, steps) }
                .collect { (record, logs, steps) ->
                    _state.value = record?.let {
                        HistoryDetailUi(
                            title = "${weekdayFull(it.date.dayOfWeek)} ${it.date.dayOfMonth} ${monthName(it.date.monthNumber)}",
                            recap = DayRecapMapper.map(it, timeFormatter.use24.value, checkInTitle = "How was this day?"),
                            use24 = timeFormatter.use24.value,
                            walks = logs.map(::walkLine),
                            steps = steps?.let { value -> "${formatSteps(value)} steps" },
                        )
                    }
                }
        }
    }

    private fun walkLine(log: WalkLog): String {
        val t = timeFormatter
        val line = "Walk · ${t.label(log.start.minuteOfDay(zone))}–${t.label(log.end.minuteOfDay(zone))} · ${(log.end - log.start).inWholeMinutes} min"
        return log.steps?.let { "$line · ${formatSteps(it)} steps" } ?: line
    }

    fun checkIn(pick: CheckIn) {
        val date = loadedDate ?: return
        viewModelScope.launch { history.setCheckIn(date, pick) }
    }

    fun correct(edit: RecordEdit) {
        val date = loadedDate ?: return
        viewModelScope.launch { history.correctDay(date, edit, zone) }
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
                onAction = { onDay(it.date) },
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
        detail?.let { d ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                d.steps?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.titleMedium.merge(AppType.numbers),
                        color = AppTheme.colors.textPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = Spacing.l, end = Spacing.l, top = Spacing.s),
                    )
                }
                RecapList(
                    s = d.recap,
                    bottomPadding = Spacing.l,
                    modifier = Modifier.weight(1f),
                    onRowTap = { correcting = it },
                    onCheckIn = viewModel::checkIn,
                )
                WalksSection(d.walks)
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
}

/** The "Walks" section of a day (2.4.0): one line per standalone walk; hidden when there are none. */
@Composable
private fun WalksSection(walks: List<String>) {
    if (walks.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text("Walks", style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.textPrimary)
        walks.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
    }
}

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
