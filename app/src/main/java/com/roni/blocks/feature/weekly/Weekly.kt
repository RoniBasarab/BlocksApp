package com.roni.blocks.feature.weekly

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
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
import com.roni.blocks.core.model.isWork
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.debtMinutes
import com.roni.blocks.designsystem.AppBottomBar
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppTab
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.AppTopBar
import com.roni.blocks.designsystem.EmptyState
import com.roni.blocks.designsystem.ScreenColumn
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.designsystem.blockColors
import com.roni.blocks.navigation.HistoryKey
import com.roni.blocks.navigation.HomeKey
import com.roni.blocks.navigation.InsightsKey
import com.roni.blocks.navigation.SettingsKey
import com.roni.blocks.navigation.TimeDebtKey
import com.roni.blocks.navigation.WeeklyKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class PairedBar(val label: String, val plannedMin: Int, val actualMin: Int, val accent: Color)

data class WeeklyStat(val label: String, val value: String)

data class WeeklyUiState(
    val hasData: Boolean,
    val weekLabel: String = "",
    val canGoNext: Boolean = false,
    val plannedFraction: Float = 0f,
    val plannedSummary: String = "",
    val bars: ImmutableList<PairedBar> = persistentListOf(),
    val stats: List<WeeklyStat> = emptyList(),
)

sealed interface WeeklyAction {
    data object PreviousWeek : WeeklyAction
    data object NextWeek : WeeklyAction
}

@HiltViewModel
class WeeklyViewModel @Inject constructor(
    private val history: HistoryManager,
    private val clock: Clock,
    private val zone: TimeZone,
) : ViewModel() {
    private val currentWeek = history.weekStart(clock.now().toLocalDateTime(zone).date)
    private val _selectedWeek = MutableStateFlow(currentWeek)
    private val _state = MutableStateFlow(WeeklyUiState(hasData = false))
    val state: StateFlow<WeeklyUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    fun onPreviousWeek() {
        _selectedWeek.value = _selectedWeek.value.plus(-7, kotlinx.datetime.DateTimeUnit.DAY)
        viewModelScope.launch { reload() }
    }

    fun onNextWeek() {
        if (!_state.value.canGoNext) return
        _selectedWeek.value = _selectedWeek.value.plus(7, kotlinx.datetime.DateTimeUnit.DAY)
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val selected = _selectedWeek.value
        val days = history.days().filter { history.weekStart(it.date) == selected }
        if (days.isEmpty()) {
            _state.value = WeeklyUiState(
                hasData = false,
                weekLabel = weekLabel(selected),
                canGoNext = selected < currentWeek,
            )
            return
        }
        _state.value = WeeklyUiState(
            hasData = true,
            weekLabel = weekLabel(selected),
            canGoNext = selected < currentWeek,
            plannedFraction = plannedFraction(days),
            plannedSummary = plannedSummary(days),
            bars = bars(days),
            stats = stats(days),
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
        return "${formatHm(worked)} of ${formatHm(planned)}"
    }

    /** Work-block minutes actually worked: planned segments minus the pause that ran inside them. */
    private fun workedWorkMinutes(segments: List<com.roni.blocks.core.model.Segment>): Int {
        val pause = segments.filter { it.kind == SegmentKind.PAUSE && it.actualEnd != null }
            .sumOf { (it.actualEnd!! - it.actualStart).inWholeMinutes }.toInt()
        val actual = segments.filter { it.kind == SegmentKind.PLANNED && it.actualEnd != null && it.type.isWork }
            .sumOf { (it.actualEnd!! - it.actualStart).inWholeMinutes }.toInt()
        return (actual - pause).coerceAtLeast(0)
    }

    private fun plannedWorkMinutes(segments: List<com.roni.blocks.core.model.Segment>): Int =
        segments.filter { it.kind == SegmentKind.PLANNED && it.plannedStart != null && it.plannedEnd != null && it.type.isWork }
            .sumOf { (it.plannedEnd!! - it.plannedStart!!).inWholeMinutes }.toInt()

    private fun bars(days: List<com.roni.blocks.core.model.DayRecord>): ImmutableList<PairedBar> {
        val order = listOf(
            "CV" to BlockType.Cv,
            "Coding" to BlockType.Coding,
            "Gym" to BlockType.Gym,
            "Free" to BlockType.Free,
        )
        return order.mapNotNull { (label, type) ->
            var planned = 0
            var actual = 0
            days.forEach { day ->
                val emergency = DayFlag.EMERGENCY_STOP in day.flags
                day.segments.forEach { segment ->
                    if (segment.type != type) return@forEach
                    when (segment.kind) {
                        SegmentKind.PLANNED -> {
                            segment.actualEnd?.let { actualEnd ->
                                actual += ((actualEnd - segment.actualStart).inWholeMinutes).toInt().coerceAtLeast(0)
                            }
                            if (!emergency) {
                                val wide = segment.plannedStart?.let { ps -> segment.plannedEnd?.let { pe -> ((pe - ps).inWholeMinutes).toInt() } } ?: 0
                                planned += wide
                            }
                        }
                        else -> Unit
                    }
                }
            }
            if (planned <= 0 && actual <= 0) return@mapNotNull null
            PairedBar(
                label = label,
                plannedMin = planned,
                actualMin = actual,
                accent = blockColors(type).accent,
            )
        }.toPersistentList()
    }

    private fun stats(days: List<com.roni.blocks.core.model.DayRecord>): List<WeeklyStat> {
        val leak = days.sumOf { debtMinutes(it.segments) }
        val gymPlanned = days.sumOf { day -> day.segments.count { it.type == BlockType.Gym && it.kind == SegmentKind.PLANNED && it.plannedStart != null } }
        val gymDone = days.sumOf { day ->
            val pause = day.segments.filter { it.kind == SegmentKind.PAUSE && it.actualEnd != null }
                .sumOf { (it.actualEnd!! - it.actualStart).inWholeMinutes }.toInt()
            val gymWorked = day.segments.filter { it.type == BlockType.Gym && it.kind == SegmentKind.PLANNED && it.actualEnd != null }
                .sumOf { (it.actualEnd!! - it.actualStart).inWholeMinutes }
            if (gymWorked - pause >= 30) 1 else 0
        }
        return listOf(
            WeeklyStat("Leak total", formatHm(leak)),
            WeeklyStat("Gym sessions", "${gymDone.coerceAtLeast(0)} / $gymPlanned"),
            WeeklyStat("Off days", days.count { DayFlag.OFF_DAY in it.flags }.toString()),
            WeeklyStat("Evenings skipped", days.count { DayFlag.EVENING_SKIPPED in it.flags }.toString()),
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
    if (!state.hasData) {
        EmptyState(
            icon = Icons.Outlined.BarChart,
            title = "No data for this week yet",
            body = "Finish a day and it shows up here.",
        )
        return
    }

    ScreenColumn {
        WeekSelector(state, onAction)
        AppCard(Modifier.testTag("weekly_hero").fillMaxWidth()) {
            Text(
                text = "Planned work done",
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
                text = "Planned vs actual",
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(Spacing.s))
            PairedBarChart(state.bars)
            Spacer(Modifier.height(Spacing.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                Legend(color = AppTheme.colors.outline, label = "Planned")
                Legend(color = AppTheme.colors.primary, label = "Actual")
            }
        }
        StatGrid(state.stats)
        Spacer(Modifier.height(Spacing.m))
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Box(Modifier.height(10.dp).width(10.dp).background(color, RoundedCornerShape(3.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
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
                IconButton(onClick = { onAction(WeeklyAction.PreviousWeek) }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
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
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "Next week",
                        tint = AppTheme.colors.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
fun PairedBarChart(bars: ImmutableList<PairedBar>, modifier: Modifier = Modifier) {
    if (bars.isEmpty()) return
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val valueStyle = MaterialTheme.typography.labelSmall.copy(color = c.textPrimary)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = c.textSecondary)
    Spacer(
        modifier.fillMaxWidth().height(200.dp).drawWithCache {
            val barW = 18.dp.toPx()
            val gap = 4.dp.toPx()
            val r = CornerRadius(6.dp.toPx())
            val valueH = 18.dp.toPx()
            val labelH = 22.dp.toPx()
            val chartH = size.height - valueH - labelH
            val maxMin = bars.maxOf { maxOf(it.plannedMin, it.actualMin) }.coerceAtLeast(1)
            val groupW = size.width / bars.size
            val labels = bars.map { measurer.measure(it.label, labelStyle) }
            val values = bars.map { measurer.measure(formatHm(it.actualMin), valueStyle) }
            onDrawBehind {
                val baseY = valueH + chartH
                bars.forEachIndexed { i, b ->
                    val cx = groupW * i + groupW / 2
                    val pH = chartH * b.plannedMin / maxMin
                    val aH = chartH * b.actualMin / maxMin
                    drawRoundRect(b.accent.copy(alpha = 0.35f), Offset(cx - gap / 2 - barW, baseY - pH), Size(barW, pH), r)
                    drawRoundRect(b.accent, Offset(cx + gap / 2, baseY - aH), Size(barW, aH), r)
                    val v = values[i]
                    drawText(v, topLeft = Offset(cx + gap / 2 + barW / 2 - v.size.width / 2, baseY - aH - v.size.height - 2.dp.toPx()))
                    val l = labels[i]
                    drawText(l, topLeft = Offset(cx - l.size.width / 2, baseY + 4.dp.toPx()))
                }
            }
        },
    )
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
    }
}

@Composable
fun WeeklyRoute(onTab: (NavKey) -> Unit, onMenu: (NavKey) -> Unit, viewModel: WeeklyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScreenScaffold(
        topBar = {
            AppTopBar(
                title = { Text("Weekly", style = MaterialTheme.typography.titleLarge) },
                onHistory = { onMenu(HistoryKey) },
                onDebt = { onMenu(TimeDebtKey) },
                onSettings = { onMenu(SettingsKey) },
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
                    }
                },
            )
        }
    }
}

internal fun tabKey(tab: AppTab): NavKey = when (tab) {
    AppTab.HOME -> HomeKey
    AppTab.WEEKLY -> WeeklyKey
    AppTab.INSIGHTS -> InsightsKey
}
