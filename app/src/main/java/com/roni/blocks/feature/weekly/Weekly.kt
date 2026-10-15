package com.roni.blocks.feature.weekly

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.WalkLog
import com.roni.blocks.core.model.actualMinutes
import com.roni.blocks.core.model.isWork
import com.roni.blocks.core.model.plannedMinutes
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.core.storage.debtMinutes
import com.roni.blocks.core.system.AppRescue
import com.roni.blocks.core.system.Failures
import com.roni.blocks.core.system.ProblemAction
import com.roni.blocks.core.time.at
import com.roni.blocks.designsystem.AppBottomBar
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppTab
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.AppTopBar
import com.roni.blocks.designsystem.AppType
import com.roni.blocks.designsystem.EmptyState
import com.roni.blocks.designsystem.ErrorCard
import com.roni.blocks.designsystem.ScreenColumn
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.designsystem.WhatTheseMeanDialog
import com.roni.blocks.designsystem.blockColors
import com.roni.blocks.designsystem.formatStepsApprox
import com.roni.blocks.designsystem.icons.AppIcons
import com.roni.blocks.navigation.HistoryKey
import com.roni.blocks.navigation.HomeKey
import com.roni.blocks.navigation.InsightsKey
import com.roni.blocks.navigation.ProfilesKey
import com.roni.blocks.navigation.SettingsKey
import com.roni.blocks.navigation.WeeklyKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

@Immutable
data class PlanProgressUi(
    val type: BlockType,
    val name: String,            // "Coding"
    val actual: String,          // "11h 05m"
    val planned: String,         // "13h"
    val fraction: Float,         // actual / planned
    val overText: String?,       // "1h 30m more than planned", only when actual > planned
)

data class WeeklyStat(val label: String, val value: String, val hint: String)

data class WeeklyUiState(
    val hasData: Boolean,
    val weekLabel: String = "",
    val canGoNext: Boolean = false,
    val canGoPrevious: Boolean = false,
    val plannedFraction: Float = 0f,
    val plannedSummary: String = "",
    val missedSummary: String? = null,
    val rows: ImmutableList<PlanProgressUi> = persistentListOf(),
    val stats: List<WeeklyStat> = emptyList(),
    val failed: Boolean = false,
)

sealed interface WeeklyAction {
    data object PreviousWeek : WeeklyAction
    data object NextWeek : WeeklyAction
    data class Problem(val action: ProblemAction) : WeeklyAction
}

@HiltViewModel
/**
 * Shows one week of scheduled versus done time, and moves between weeks. Rules: the selection
 * starts on the current week and steps a whole week at a time, so no partial week is ever compared;
 * every figure comes from [HistoryManager.aggregateWeek], the same builder the day-end flush uses,
 * so Weekly and Insights cannot disagree; an empty week is shown as empty, not as zero effort.
 *
 * Talks to: [HistoryManager], [Clock].
 */
class WeeklyViewModel @Inject constructor(
    private val history: HistoryManager,
    private val templates: TemplateManager,
    private val clock: Clock,
    private val zone: TimeZone,
    private val failures: Failures,
    private val rescue: AppRescue,
) : ViewModel() {
    private val currentWeek = history.weekStart(clock.now().toLocalDateTime(zone).date)
    private val _selectedWeek = MutableStateFlow(currentWeek)
    private val _state = MutableStateFlow(WeeklyUiState(hasData = false))
    val state: StateFlow<WeeklyUiState> = _state.asStateFlow()

    private fun fail() = _state.update { it.copy(failed = true) }

    init {
        failures.launch(viewModelScope, "Weekly: load", onFailure = ::fail) { reload() }
    }

    fun onAction(action: WeeklyAction) {
        when (action) {
            is WeeklyAction.Problem -> rescue.handle(action.action)
            else -> Unit
        }
    }

    fun onPreviousWeek() {
        if (!_state.value.canGoPrevious) return
        _selectedWeek.value = _selectedWeek.value.plus(-7, kotlinx.datetime.DateTimeUnit.DAY)
        failures.launch(viewModelScope, "Weekly: previous week", onFailure = ::fail) { reload() }
    }

    fun onNextWeek() {
        if (!_state.value.canGoNext) return
        _selectedWeek.value = _selectedWeek.value.plus(7, kotlinx.datetime.DateTimeUnit.DAY)
        failures.launch(viewModelScope, "Weekly: next week", onFailure = ::fail) { reload() }
    }

    private suspend fun reload() {
        val selected = _selectedWeek.value
        val allDays = history.days()
        val earliestWeek = allDays.minOfOrNull { history.weekStart(it.date) }
        val canGoPrevious = earliestWeek != null && selected > earliestWeek
        val days = allDays.filter { history.weekStart(it.date) == selected }
        if (days.isEmpty()) {
            _state.value = WeeklyUiState(
                hasData = false,
                weekLabel = weekLabel(selected),
                canGoNext = selected < currentWeek,
                canGoPrevious = canGoPrevious,
                failed = _state.value.failed,
            )
            return
        }
        _state.value = WeeklyUiState(
            hasData = true,
            weekLabel = weekLabel(selected),
            failed = _state.value.failed,
            canGoNext = selected < currentWeek,
            canGoPrevious = canGoPrevious,
            plannedFraction = plannedFraction(days),
            plannedSummary = plannedSummary(days),
            missedSummary = missedSummary(days),
            rows = planRows(days),
            stats = stats(
                days,
                days.mapNotNull { history.steps(it.date) },
                history.walksBetween(selected, selected.plus(6, DateTimeUnit.DAY)),
            ),
        )
    }

    private fun weekLabel(start: kotlinx.datetime.LocalDate): String {
        val end = start.plus(6, kotlinx.datetime.DateTimeUnit.DAY)
        val startLabel = "${start.dayOfMonth}"
        val endLabel = "${end.dayOfMonth} ${monthName(end.monthNumber)}"
        val sameMonth = start.monthNumber == end.monthNumber
        return if (sameMonth) "$startLabel – $endLabel" else "$startLabel ${monthName(start.monthNumber)} – $endLabel"
    }

    private fun plannedFraction(days: List<com.roni.blocks.core.model.DayRecord>): Float {
        var worked = 0
        var planned = 0
        days.forEach { day ->
            val emergency = DayFlag.EMERGENCY_STOP in day.flags
            worked += workedWorkMinutes(day.segments)
            if (!emergency) {
                planned += plannedWorkMinutes(day.segments)
            }
        }
        if (planned <= 0) return 0f
        return (worked.toFloat() / planned).coerceIn(0f, 1f)
    }

    private fun plannedSummary(days: List<com.roni.blocks.core.model.DayRecord>): String {
        var worked = 0
        var planned = 0
        days.forEach { day ->
            worked += workedWorkMinutes(day.segments)
            if (DayFlag.EMERGENCY_STOP !in day.flags) planned += plannedWorkMinutes(day.segments)
        }
        return "${formatHm(worked)} of the ${formatHm(planned)} planned for CV, Coding and Gym"
    }

    /** Work-block minutes actually worked: planned segments minus the pause that ran inside them. */
    private fun workedWorkMinutes(segments: List<com.roni.blocks.core.model.Segment>): Int {
        val pause = segments.filter { it.kind == SegmentKind.PAUSE && it.actualEnd != null }
            .sumOf { it.actualMinutes ?: 0 }.toInt()
        val actual = segments.filter { it.kind == SegmentKind.PLANNED && it.actualEnd != null && it.type.isWork }
            .sumOf { it.actualMinutes ?: 0 }.toInt()
        return (actual - pause).coerceAtLeast(0)
    }

    private fun plannedWorkMinutes(segments: List<com.roni.blocks.core.model.Segment>): Int =
        segments.filter { it.kind == SegmentKind.PLANNED && it.plannedStart != null && it.plannedEnd != null && it.type.isWork }
            .sumOf { it.plannedMinutes ?: 0 }.toInt()

    /**
     * Planned minutes come from each day's final plan (`plannedBlocks`, falling back to the
     * template), not from what happened to get recorded: a missed block was still planned time.
     * Off days lend nothing; on an emergency-stop day only the plan up to the stop counts.
     * Actual minutes are the time spent in the type's segments; pauses are their own segments,
     * so excluding them needs no subtraction, and synthetic free counts as Free.
     */
    private fun planRows(days: List<com.roni.blocks.core.model.DayRecord>): ImmutableList<PlanProgressUi> {
        val order = listOf(
            "CV" to BlockType.Cv,
            "Coding" to BlockType.Coding,
            "Gym" to BlockType.Gym,
            "Free" to BlockType.Free,
        )
        val planned = mutableMapOf<BlockType, Int>()
        val actual = mutableMapOf<BlockType, Int>()
        days.forEach { day ->
            if (DayFlag.OFF_DAY in day.flags) return@forEach
            val emergencyStop = if (DayFlag.EMERGENCY_STOP in day.flags) {
                day.segments.mapNotNull { it.actualEnd }.maxOrNull()
            } else {
                null
            }
            val blocks = day.plannedBlocks ?: day.template?.let { templates.find(it)?.blocks } ?: emptyList()
            blocks.forEach { block ->
                val start = block.start.at(day.date, zone)
                var end = block.end.at(day.date, zone)
                if (emergencyStop != null && end > emergencyStop) end = emergencyStop
                planned.merge(block.type, (end - start).inWholeMinutes.toInt().coerceAtLeast(0), Int::plus)
            }
            day.segments.forEach { segment ->
                if (segment.kind != SegmentKind.PLANNED && segment.kind != SegmentKind.SYNTHETIC_FREE) return@forEach
                val end = segment.actualEnd ?: return@forEach
                actual.merge(segment.type, (end - segment.actualStart).inWholeMinutes.toInt().coerceAtLeast(0), Int::plus)
            }
        }
        return order.mapNotNull { (label, type) ->
            val plannedMin = planned[type] ?: 0
            if (plannedMin <= 0) return@mapNotNull null
            val actualMin = actual[type] ?: 0
            PlanProgressUi(
                type = type,
                name = label,
                actual = formatHm(actualMin),
                planned = formatHm(plannedMin),
                fraction = actualMin.toFloat() / plannedMin,
                overText = if (actualMin > plannedMin) "${formatHm(actualMin - plannedMin)} more than planned" else null,
            )
        }.toPersistentList()
    }

    /** Planned time in blocks that never happened (a late start, or an earlier block ran over). */
    private fun missedSummary(days: List<com.roni.blocks.core.model.DayRecord>): String? {
        val missed = days.sumOf { day ->
            day.segments
                .filter { it.missReason != null && it.plannedStart != null && it.plannedEnd != null }
                .sumOf { it.plannedMinutes ?: 0 }
        }.toInt()
        if (missed <= 0) return null
        return "Missed: ${formatHm(missed)} of planned blocks that never happened"
    }

    private fun stats(
        days: List<com.roni.blocks.core.model.DayRecord>,
        stepTotals: List<Long>,
        walks: Map<LocalDate, List<WalkLog>>,
    ): List<WeeklyStat> {
        val leak = days.sumOf { debtMinutes(it.segments) }
        val gymPlanned = days.sumOf { day -> day.segments.count { it.type == BlockType.Gym && it.kind == SegmentKind.PLANNED && it.plannedStart != null } }
        val gymDone = days.sumOf { day ->
            val pause = day.segments.filter { it.kind == SegmentKind.PAUSE && it.actualEnd != null }
                .sumOf { it.actualMinutes ?: 0 }.toInt()
            val gymWorked = day.segments.filter { it.type == BlockType.Gym && it.kind == SegmentKind.PLANNED && it.actualEnd != null }
                .sumOf { it.actualMinutes ?: 0 }
            if (gymWorked - pause >= 30) 1 else 0
        }
        val total = stepTotals.sum()
        val steps = if (stepTotals.isEmpty()) null else WeeklyStat(
            "Steps",
            "≈ ${formatStepsApprox(total)}",
            "About ${formatStepsApprox(total / stepTotals.size)} a day",
        )
        val allWalks = walks.values.flatten()
        val walkMinutes = allWalks.sumOf { (it.end - it.start).inWholeMinutes.toInt() }
        val walksTile = if (allWalks.isEmpty()) {
            WeeklyStat("Walks", "0", "No walks this week")
        } else {
            val noun = if (allWalks.size == 1) "walk" else "walks"
            WeeklyStat("Walks", "${allWalks.size} $noun · ${formatHm(walkMinutes)}", "Day and evening walks together")
        }
        val walkSteps = allWalks.mapNotNull { it.steps }.sum()
        val fromWalks = if (stepTotals.isNotEmpty() && allWalks.any { it.steps != null }) {
            WeeklyStat(
                "Steps from walks",
                "≈ ${formatStepsApprox(walkSteps)}",
                "About ${formatStepsApprox(walkSteps)} of your steps came from walks",
            )
        } else {
            null
        }
        return listOfNotNull(
            steps,
            walksTile,
            fromWalks,
            WeeklyStat("Ran over", formatHm(leak), "Time past block ends"),
            WeeklyStat("Gym sessions", "${gymDone.coerceAtLeast(0)} / $gymPlanned", "Done of planned"),
            WeeklyStat("Off days", days.count { DayFlag.OFF_DAY in it.flags }.toString(), "Days with no schedule"),
            WeeklyStat(
                "Evenings skipped",
                days.count { DayFlag.EVENING_SKIPPED in it.flags }.toString(),
                "Kid asleep never tapped",
            ),
        )
    }
}

internal fun formatHm(minutes: Int): String {
    val positive = minutes.coerceAtLeast(0)
    val h = positive / 60
    val m = positive % 60
    return if (h == 0) "${m}m" else "${h}h ${"%02d".format(m)}m"
}

private fun monthName(number: Int): String = months[number - 1]

private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** Content only; the top and bottom bars live in [WeeklyRoute]. */
@Composable
fun WeeklyScreen(state: WeeklyUiState, onAction: (WeeklyAction) -> Unit) {
    if (state.failed) {
        ErrorCard(onAction = { onAction(WeeklyAction.Problem(it)) }, modifier = Modifier.padding(Spacing.m))
        return
    }
    if (!state.hasData) {
        Column(Modifier.fillMaxSize()) {
            WeekSelector(state, onAction)
            EmptyState(
                icon = AppIcons.BarChart,
                title = "No data for this week",
                body = "Use the arrows to see other weeks.",
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    ScreenColumn {
        WeekSelector(state, onAction)
        AppCard(Modifier.testTag("weekly_hero").fillMaxWidth()) {
            Text(
                text = "Plan followed",
                style = MaterialTheme.typography.labelLarge,
                color = AppTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "${(state.plannedFraction * 100).toInt()}%",
                style = MaterialTheme.typography.displaySmall,
                color = AppTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = state.plannedSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
            state.missedSummary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
            }
            Spacer(Modifier.height(Spacing.s))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(AppTheme.colors.outline, RoundedCornerShape(6.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(AppTheme.colors.primary, RoundedCornerShape(6.dp))
                        .fillMaxWidth(state.plannedFraction),
                )
            }
        }
        AppCard(Modifier.testTag("weekly_chart").fillMaxWidth()) {
            Text(
                text = "Planned vs actual time",
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(Spacing.s))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                state.rows.forEach { PlanProgressRow(it) }
            }
        }
        StatGrid(state.stats)
        Spacer(Modifier.height(Spacing.m))
    }
}

@Composable
fun PlanProgressRow(p: PlanProgressUi) {
    val c = AppTheme.colors
    val accent = blockColors(p.type).accent
    Column(Modifier.fillMaxWidth().testTag("plan_progress_row"), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(accent, CircleShape))
            Spacer(Modifier.width(Spacing.s))
            Text(p.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(p.actual, style = MaterialTheme.typography.titleSmall.merge(AppType.numbers))
            Text(" of ${p.planned}", style = MaterialTheme.typography.bodyMedium.merge(AppType.numbers), color = c.textSecondary)
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(accent.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxWidth(p.fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(accent))
        }
        p.overText?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = c.textSecondary) }
    }
}

@Composable
private fun WeekSelector(state: WeeklyUiState, onAction: (WeeklyAction) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = AppTheme.colors.surface,
            modifier = Modifier
                .testTag("week_selector")
                .heightIn(min = 40.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onAction(WeeklyAction.PreviousWeek) }, enabled = state.canGoPrevious) {
                    Icon(
                        AppIcons.KeyboardArrowLeft,
                        contentDescription = "Previous week",
                        tint = AppTheme.colors.textSecondary,
                    )
                }
                Text(
                    text = state.weekLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                )
                IconButton(onClick = { onAction(WeeklyAction.NextWeek) }, enabled = state.canGoNext) {
                    Icon(
                        AppIcons.KeyboardArrowRight,
                        contentDescription = "Next week",
                        tint = AppTheme.colors.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatGrid(stats: List<WeeklyStat>) {
    if (stats.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        stats.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                row.forEach { stat ->
                    StatTile(stat, Modifier.weight(1f).fillMaxHeight())
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTile(stat: WeeklyStat, modifier: Modifier = Modifier) {
    AppCard(modifier.testTag("stat_tile")) {
        Text(
            text = stat.label,
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stat.value,
            style = MaterialTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stat.hint,
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.textMuted,
        )
    }
}

@Composable
fun WeeklyRoute(onTab: (NavKey) -> Unit, onMenu: (NavKey) -> Unit, viewModel: WeeklyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var explain by remember { mutableStateOf(false) }
    ScreenScaffold(
        topBar = {
            AppTopBar(
                title = { Text("Weekly", style = MaterialTheme.typography.titleLarge) },
                onHistory = { onMenu(HistoryKey) },
                onProfiles = { onMenu(ProfilesKey) },
                onSettings = { onMenu(SettingsKey) },
                onExplain = { explain = true },
            )
        },
        bottomBar = { AppBottomBar(AppTab.WEEKLY) { onTab(tabKey(it)) } },
    ) { padding ->
        Column(Modifier.fillMaxWidth().padding(padding)) {
            WeeklyScreen(
                state = state,
                onAction = { action ->
                    when (action) {
                        WeeklyAction.PreviousWeek -> viewModel.onPreviousWeek()
                        WeeklyAction.NextWeek -> viewModel.onNextWeek()
                        is WeeklyAction.Problem -> viewModel.onAction(action)
                    }
                },
            )
        }
    }
    if (explain) WhatTheseMeanDialog(onDismiss = { explain = false })
}

internal fun tabKey(tab: AppTab): NavKey = when (tab) {
    AppTab.HOME -> HomeKey
    AppTab.WEEKLY -> WeeklyKey
    AppTab.INSIGHTS -> InsightsKey
}
