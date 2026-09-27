package com.roni.blocks.feature.timedebt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.debtMinutes
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.BackTopBar
import com.roni.blocks.designsystem.EmptyState
import com.roni.blocks.designsystem.Good
import com.roni.blocks.designsystem.Leak
import com.roni.blocks.designsystem.Metrics
import com.roni.blocks.designsystem.ScreenColumn
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.SectionCard
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.designsystem.blockColors
import com.roni.blocks.feature.home.blockTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class DebtSlice(val label: String, val minutes: Int, val accent: Color)

data class DebtDay(
    val label: String,
    val minutes: Int,
    val isToday: Boolean = false,
    val byType: List<DebtSlice> = emptyList(),
)

data class TimeDebtUiState(
    val hasDebt: Boolean,
    val totalMinutes: Int = 0,
    val todayMinutes: Int = 0,
    val avgPerDay: Int = 0,
    val days: List<DebtDay> = emptyList(),
)

@HiltViewModel
class TimeDebtViewModel @Inject constructor(
    private val history: HistoryManager,
    private val clock: Clock,
    private val zone: TimeZone,
) : ViewModel() {
    private val _state = MutableStateFlow(TimeDebtUiState(hasDebt = false))
    val state: StateFlow<TimeDebtUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val today = clock.now().toLocalDateTime(zone).date
        val windowDates = (13 downTo 0).map { today.minus(it, DateTimeUnit.DAY) }
        val records = history.days().filter { it.date in windowDates.toSet() }
        val days = windowDates.sorted().map { date ->
            val record = records.firstOrNull { it.date == date }
            DebtDay(
                label = date.dayOfWeek.name.take(1),
                minutes = record?.let { debtMinutes(it.segments) } ?: 0,
                isToday = date == today,
                byType = record?.let { byType(it.segments) } ?: emptyList(),
            )
        }
        val weekStart = history.weekStart(today)
        _state.value = TimeDebtUiState(
            hasDebt = days.sumOf { it.minutes } > 0,
            totalMinutes = windowDates.zip(days) { date, day -> if (date >= weekStart) day.minutes else 0 }.sum(),
            todayMinutes = days.last().minutes,
            avgPerDay = days.sumOf { it.minutes } / days.size,
            days = days,
        )
    }
}

/** Debt per block type: a block's leak is what it ran past its planned end. */
private fun byType(segments: List<com.roni.blocks.core.model.Segment>): List<DebtSlice> =
    segments
        .filter { it.kind == SegmentKind.PLANNED && it.actualEnd != null && it.plannedEnd != null }
        .mapNotNull { segment ->
            val over = ((segment.actualEnd!! - segment.plannedEnd!!).inWholeMinutes).toInt()
            if (over <= 0) return@mapNotNull null
            DebtSlice(
                label = blockTitle(segment.type),
                minutes = over,
                accent = blockColors(segment.type).accent,
            )
        }
        .groupBy { it.label }
        .map { (label, slices) ->
            DebtSlice(label, slices.sumOf { it.minutes }, slices.first().accent)
        }
        .sortedByDescending { it.minutes }

internal fun formatDebt(minutes: Int): String {
    val positive = minutes.coerceAtLeast(0)
    val hours = positive / 60
    val rest = positive % 60
    return when {
        hours == 0 -> "$rest min"
        rest == 0 -> "$hours h"
        else -> "${hours}h ${rest}m"
    }
}

/** Content only; the top bar lives in [TimeDebtRoute]. */
@Composable
fun TimeDebtScreen(state: TimeDebtUiState, onAction: (DebtDay) -> Unit) {
    if (!state.hasDebt) {
        EmptyState(
            icon = Icons.Outlined.HourglassEmpty,
            title = "No time debt yet",
            body = "When a block runs over, it shows up here.",
        )
        return
    }

    ScreenColumn {
        AppCard(Modifier.testTag("debt_hero").fillMaxWidth()) {
            Text(
                text = "This week",
                style = MaterialTheme.typography.labelLarge,
                color = AppTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(Spacing.s))
            Text(
                text = formatDebt(state.totalMinutes),
                style = MaterialTheme.typography.displaySmall,
                color = if (state.totalMinutes == 0) Good else Leak,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Today ${formatDebt(state.todayMinutes)} · avg ${state.avgPerDay} min/day",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
        }
        AppCard(Modifier.testTag("debt_chart").fillMaxWidth()) {
            Text(
                text = "Last 14 days",
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(Spacing.s))
            DebtChart(state.days)
        }
        SectionCard(Modifier.fillMaxWidth()) {
            state.days.filter { it.minutes > 0 }.forEachIndexed { index, day ->
                if (index > 0) {
                    Box(
                        Modifier
                            .padding(horizontal = Spacing.l)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(AppTheme.colors.outline),
                    )
                }
                DebtDayRow(day)
            }
        }
    }
}

private const val CHART_HEIGHT = 160

@Composable
private fun DebtChart(days: List<DebtDay>) {
    if (days.isEmpty()) return
    val maxMinutes = days.maxOf { it.minutes }.coerceAtLeast(1)
    val c = AppTheme.colors
    Canvas(Modifier.fillMaxWidth().height(CHART_HEIGHT.dp)) {
        val slot = size.width / days.size
        val barW = slot * 0.5f
        val avg = days.sumOf { it.minutes }.toFloat() / days.size
        val avgY = size.height * (1f - avg / maxMinutes)
        days.forEachIndexed { i, day ->
            val x = slot * i + slot / 2
            val h = size.height * day.minutes / maxMinutes
            val color = if (day.isToday) Leak else Leak.copy(alpha = 0.6f)
            drawRoundRect(
                color = color,
                topLeft = Offset(x - barW / 2, size.height - h),
                size = androidx.compose.ui.geometry.Size(barW, h.coerceAtLeast(if (day.minutes > 0) 2f else 0f)),
                cornerRadius = CornerRadius(4f),
            )
        }
        drawLine(
            color = c.textMuted,
            start = Offset(0f, avgY),
            end = Offset(size.width, avgY),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { day ->
            Text(
                text = day.label.take(1),
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.width(20.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DebtDayRow(day: DebtDay) {
    Column(
        Modifier
            .testTag("debt_day_row")
            .fillMaxWidth()
            .heightIn(min = Metrics.row)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = day.label,
                style = MaterialTheme.typography.titleSmall,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatDebt(day.minutes),
                style = MaterialTheme.typography.titleSmall,
                color = Leak,
            )
        }
        if (day.byType.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xxs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                day.byType.forEach { slice ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.height(6.dp).width(6.dp).background(slice.accent, androidx.compose.foundation.shape.CircleShape))
                        Text(
                            text = "${slice.label} ${slice.minutes}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimeDebtRoute(onBack: () -> Unit, viewModel: TimeDebtViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScreenScaffold(
        topBar = {
            BackTopBar(title = "Time Debt", onBack = onBack)
        },
    ) { padding ->
        Column(Modifier.fillMaxWidth().padding(padding)) {
            TimeDebtScreen(state = state, onAction = {})
        }
    }
}
