package com.roni.blocks.feature.insights

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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
import com.roni.blocks.core.model.minutesPastPlan
import com.roni.blocks.core.model.plannedMinutes
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.debtMinutes
import com.roni.blocks.core.system.AppRescue
import com.roni.blocks.core.system.Failures
import com.roni.blocks.core.system.ProblemAction
import com.roni.blocks.designsystem.AppBottomBar
import com.roni.blocks.designsystem.AppShapes
import com.roni.blocks.designsystem.AppTab
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.AppTopBar
import com.roni.blocks.designsystem.CardDivider
import com.roni.blocks.designsystem.EmptyState
import com.roni.blocks.designsystem.ErrorCard
import com.roni.blocks.designsystem.IconCircle
import com.roni.blocks.designsystem.Metrics
import com.roni.blocks.designsystem.ScreenColumn
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.SectionCard
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.designsystem.WhatTheseMeanDialog
import com.roni.blocks.designsystem.blockColors
import com.roni.blocks.designsystem.formatStepsApprox
import com.roni.blocks.designsystem.icons.AppIcons
import com.roni.blocks.feature.weekly.formatHm
import com.roni.blocks.feature.weekly.tabKey
import com.roni.blocks.navigation.HistoryKey
import com.roni.blocks.navigation.ProfilesKey
import com.roni.blocks.navigation.SettingsKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

enum class TrendArrow { DOWN, UP, FLAT }
enum class TrendTone { GOOD, LEAK, NEUTRAL }

data class InsightTrend(val arrow: TrendArrow, val delta: String, val tone: TrendTone)
data class InsightRow(val label: String, val value: String, val hint: String, val trend: InsightTrend? = null)
data class OverrunStat(
    val label: String,
    val overBlocks: Int,
    val plannedBlocks: Int,
    val avgMinutes: Int,
    val accent: Color,
    val fraction: Float = 1f,
)

enum class HeadlineKind { OVER_RUN, METRIC_MOVED, STEADY }

data class InsightsUiState(
    val hasData: Boolean,
    val daysLogged: Int = 0,
    val headlineTitle: String = "",
    val headlineBody: String = "",
    val headlineKind: HeadlineKind = HeadlineKind.STEADY,
    val headlineAccent: Color = Color.Unspecified,
    val rows: List<InsightRow> = emptyList(),
    val worst: List<OverrunStat> = emptyList(),
    val failed: Boolean = false,
)

sealed interface InsightsAction {
    data object DismissProgressNudge : InsightsAction
    data class Problem(val action: ProblemAction) : InsightsAction
}

private const val DAYS_IN_WEEK = 7

@HiltViewModel
/**
 * Compares the last complete week with the one before it. Rules: only whole weeks are compared, so
 * the current week is excluded — it is still moving and would make every figure jump; no history
 * publishes `hasData = false` and the screen explains itself instead of showing zeroes.
 *
 * Talks to: [HistoryManager], [Clock].
 */
class InsightsViewModel @Inject constructor(
    private val history: HistoryManager,
    private val clock: Clock,
    private val zone: TimeZone,
    private val failures: Failures,
    private val rescue: AppRescue,
) : ViewModel() {
    private val lastCompleteWeek = history.weekStart(clock.now().toLocalDateTime(zone).date).minus(7, DateTimeUnit.DAY)

    private val _state = MutableStateFlow(InsightsUiState(hasData = false))
    val state: StateFlow<InsightsUiState> = _state.asStateFlow()

    private fun fail() = _state.update { it.copy(failed = true) }

    init {
        failures.launch(viewModelScope, "Insights: load", onFailure = ::fail) { reload() }
    }

    fun onAction(action: InsightsAction) {
        when (action) {
            is InsightsAction.Problem -> rescue.handle(action.action)
            else -> Unit
        }
    }

    private suspend fun reload() {
        val days = history.days()
        val previous = lastCompleteWeek.minus(7, DateTimeUnit.DAY)
        val subjectDays = days.filter { history.weekStart(it.date) == lastCompleteWeek }
        val previousDays = days.filter { history.weekStart(it.date) == previous }
        if (previousDays.size < DAYS_IN_WEEK) {
            _state.value = InsightsUiState(hasData = false, daysLogged = previousDays.size, failed = _state.value.failed)
            return
        }
        val nowMetrics = metricsOf(
            subjectDays,
            history.walksBetween(lastCompleteWeek, lastCompleteWeek.plus(6, DateTimeUnit.DAY)),
            subjectDays.mapNotNull { history.steps(it.date) },
        )
        val prevMetrics = metricsOf(
            previousDays,
            history.walksBetween(previous, previous.plus(6, DateTimeUnit.DAY)),
            previousDays.mapNotNull { history.steps(it.date) },
        )
        val worst = worstOverruns(nowMetrics)
        val headline = headline(nowMetrics, prevMetrics, worst)

        _state.value = InsightsUiState(
            hasData = true,
            daysLogged = previousDays.size,
            headlineTitle = headline.title,
            headlineBody = headline.body,
            headlineKind = headline.kind,
            headlineAccent = worst.firstOrNull()?.accent ?: blockColors(BlockType.Coding).accent,
            rows = scorecardRows(nowMetrics, prevMetrics),
            worst = worst.map { it.copy(fraction = fractionOf(it, worst)) },
            failed = _state.value.failed,
        )
    }
}

internal data class WeekMetrics(
    val leakMinutes: Int = 0,
    val codingWorked: Int = 0,
    val codingPlanned: Int = 0,
    val gymDone: Int = 0,
    val gymPlanned: Int = 0,
    val cvWorkedDays: Int = 0,
    val cvPlannedDays: Int = 0,
    val eveningsSkipped: Int = 0,
    val chillPercent: Int = 0,
    val choiceEvenings: Int = 0,
    val pauseMinutes: Int = 0,
    val offDays: Int = 0,
    /** Dishes evenings (3.3.0): how many, how many went over 20 minutes, and the total overage. */
    val dishesEvenings: Int = 0,
    val dishesOver20: Int = 0,
    val dishesOverSum: Int = 0,
    /** Walks and steps (3.4.0): how many walks, their minutes, average daily steps, evening walks. */
    val walks: Int = 0,
    val walkMinutes: Int = 0,
    val avgSteps: Int = 0,
    val eveningWalks: Int = 0,
    val overruns: List<OverrunStat> = emptyList(),
)

internal fun metricsOf(
    days: List<com.roni.blocks.core.model.DayRecord>,
    walks: Map<LocalDate, List<WalkLog>> = emptyMap(),
    stepTotals: List<Long> = emptyList(),
): WeekMetrics {
    var leakMinutes = 0
    var codingWorked = 0
    var codingPlanned = 0
    var gymDone = 0
    val gymPlannedDays = mutableMapOf("Gym" to 0)
    var cvWorkedDays = 0
    var cvPlannedDays = 0
    var eveningsSkipped = 0
    var choiceTotal = 0
    var chillCount = 0
    var pauseMinutes = 0
    var offDays = 0
    var dishesEvenings = 0
    var dishesOver20 = 0
    var dishesOverSum = 0
    val over = mutableMapOf<String, Triple<Int, Int, Int>>() // label -> (overBlocks, totalOver, plannedBlocks)

    days.forEach { day ->
        leakMinutes += debtMinutes(day.segments)
        if (DayFlag.OFF_DAY in day.flags) offDays++
        if (DayFlag.EVENING_SKIPPED in day.flags) eveningsSkipped++
        val pause = day.segments.filter { it.kind == SegmentKind.PAUSE && it.actualEnd != null }
            .sumOf { it.actualMinutes ?: 0 }.toInt()
        pauseMinutes += pause

        day.segments.forEach { segment ->
            when (segment.kind) {
                SegmentKind.PLANNED -> {
                    if (segment.type == BlockType.Dishes && segment.actualEnd != null) {
                        val minutes = (segment.actualMinutes ?: 0).toInt()
                        dishesEvenings++
                        if (minutes > 20) {
                            dishesOver20++
                            dishesOverSum += minutes - 20
                        }
                    }
                    if (segment.type.isWork && segment.plannedEnd != null) {
                        val typeKey = segmentTypeLabel(segment.type)
                        val entry = over.getOrPut(typeKey) { Triple(0, 0, 0) }
                        over[typeKey] = Triple(entry.first, entry.second, entry.third + 1)
                    }
                    if (segment.plannedStart != null && segment.plannedEnd != null) {
                        when (segment.type) {
                            BlockType.Coding -> {
                                codingPlanned += (segment.plannedMinutes ?: 0).toInt()
                                segment.actualEnd?.let { actualEnd ->
                                    codingWorked += ((actualEnd - segment.actualStart).inWholeMinutes).toInt()
                                }
                            }
                            is BlockType.Gym -> gymPlannedDays["Gym"] = gymPlannedDays.getValue("Gym") + 1
                            BlockType.Cv -> {
                                cvPlannedDays++
                                segment.actualEnd?.let { actualEnd ->
                                    if ((actualEnd - segment.actualStart).inWholeMinutes > 0) cvWorkedDays++
                                }
                            }
                            is BlockType.EveningChoice -> {
                                choiceTotal++
                                if (segment.type.choice == com.roni.blocks.core.model.Choice.CHILL) chillCount++
                            }
                            else -> Unit
                        }
                    }
                    if (segment.type == BlockType.Gym && segment.actualEnd != null) {
                        val worked = ((segment.actualMinutes ?: 0) - pause).toInt()
                        if (worked >= 30) gymDone++
                    }
                    segment.actualEnd?.let { actualEnd ->
                        segment.plannedEnd?.takeIf { actualEnd > it }?.let { _ ->
                            val typeKey = segmentTypeLabel(segment.type)
                            val entry = over.getOrPut(typeKey) { Triple(0, 0, 0) }
                            val overMin = (segment.minutesPastPlan ?: 0).toInt()
                            over[typeKey] = Triple(entry.first + 1, entry.second + overMin, entry.third)
                        }
                    }
                }
                else -> Unit
            }
        }
    }
    val gymPlanned = gymPlannedDays.getValue("Gym")
    val allWalks = walks.values.flatten()
    val avgSteps = if (stepTotals.isEmpty()) 0 else (stepTotals.sum().toDouble() / stepTotals.size).roundToInt()
    val overruns = over.map { (label, t) ->
        OverrunStat(
            label = label,
            overBlocks = t.first,
            plannedBlocks = t.third,
            avgMinutes = if (t.first == 0) 0 else (t.second.toDouble() / t.first).roundToInt(),
            accent = accentFor(label),
        )
    }.filter { it.plannedBlocks > 0 }
    return WeekMetrics(
        leakMinutes = leakMinutes,
        codingWorked = codingWorked,
        codingPlanned = codingPlanned,
        gymDone = gymDone,
        gymPlanned = gymPlanned,
        cvWorkedDays = cvWorkedDays,
        cvPlannedDays = cvPlannedDays,
        eveningsSkipped = eveningsSkipped,
        chillPercent = if (choiceTotal == 0) 0 else (chillCount * 100.0 / choiceTotal).roundToInt(),
        choiceEvenings = choiceTotal,
        pauseMinutes = pauseMinutes,
        offDays = offDays,
        dishesEvenings = dishesEvenings,
        dishesOver20 = dishesOver20,
        dishesOverSum = dishesOverSum,
        walks = allWalks.size,
        walkMinutes = allWalks.sumOf { (it.end - it.start).inWholeMinutes.toInt() },
        avgSteps = avgSteps,
        eveningWalks = eveningWalks(days, walks),
        overruns = overruns,
    )
}

/**
 * An evening walk (3.4.0) starts at or after the day's evening begins (the earliest Dishes or
 * EveningChoice segment) or after the day closed (its last segment ended). A day with no segments
 * has no evening, so no evening walks.
 */
private fun eveningWalks(days: List<com.roni.blocks.core.model.DayRecord>, walks: Map<LocalDate, List<WalkLog>>): Int {
    val byDate = days.associateBy { it.date }
    var count = 0
    walks.forEach { (date, logs) ->
        val day = byDate[date] ?: return@forEach
        val eveningStart = day.segments
            .filter { it.type == BlockType.Dishes || it.type is BlockType.EveningChoice }
            .mapNotNull { it.actualStart }
            .minOrNull()
        val dayClose = day.segments.mapNotNull { it.actualEnd }.maxOrNull()
        logs.forEach { walk ->
            val afterEvening = eveningStart != null && walk.start >= eveningStart
            val afterClose = dayClose != null && walk.start >= dayClose
            if (afterEvening || afterClose) count++
        }
    }
    return count
}

private fun segmentTypeLabel(type: com.roni.blocks.core.model.BlockType): String = when (type) {
    com.roni.blocks.core.model.BlockType.Cv -> "CV"
    com.roni.blocks.core.model.BlockType.Coding -> "Coding"
    com.roni.blocks.core.model.BlockType.Gym -> "Gym"
    com.roni.blocks.core.model.BlockType.Free -> "Free"
    else -> type.toString()
}

private fun accentFor(label: String): Color = when (label) {
    "CV" -> blockColors(com.roni.blocks.core.model.BlockType.Cv).accent
    "Coding" -> blockColors(com.roni.blocks.core.model.BlockType.Coding).accent
    "Gym" -> blockColors(com.roni.blocks.core.model.BlockType.Gym).accent
    else -> blockColors(com.roni.blocks.core.model.BlockType.Free).accent
}

private fun worstOverruns(metrics: WeekMetrics): List<OverrunStat> =
    metrics.overruns.filter { it.overBlocks > 0 && it.avgMinutes > 0 }.sortedByDescending { it.avgMinutes }

private fun fractionOf(stat: OverrunStat, worst: List<OverrunStat>): Float {
    val top = worst.maxOfOrNull { it.avgMinutes } ?: return 0f
    return (stat.avgMinutes.toFloat() / top).coerceIn(0.1f, 1f)
}

private enum class Direction { LOWER, HIGHER }

private data class Rival(val metric: String, val new: Int, val old: Int, val goodWhen: Direction) {
    val relative: Float
        get() = Math.abs((new - old).toFloat()) / Math.max(old, 1).toFloat()

    fun improved(): String =
        if ((new < old && goodWhen == Direction.LOWER) || (new > old && goodWhen == Direction.HIGHER)) {
            "improved"
        } else {
            "got worse"
        }

    fun text(value: Int): String = when (metric) {
        "Ran over", "Pause time" -> formatHm(value)
        "Chill evenings" -> "$value%"
        else -> "$value"
    }
}

internal data class Headline(val title: String, val body: String, val kind: HeadlineKind)

internal fun headline(now: WeekMetrics, prev: WeekMetrics, worst: List<OverrunStat>): Headline {
    val chronic = worst.filter { it.overBlocks >= 3 }.maxByOrNull { it.avgMinutes }
    if (chronic != null) {
        return Headline(
            title = "${chronic.label} tends to run long",
            body = "${chronic.overBlocks} of ${chronic.plannedBlocks} ${chronic.label} blocks this week went past their planned end, by ${chronic.avgMinutes} min on average.",
            kind = HeadlineKind.OVER_RUN,
        )
    }
    val rivals = listOf(
        Rival("Ran over", now.leakMinutes, prev.leakMinutes, Direction.LOWER),
        Rival("Coding time", now.codingWorked, prev.codingWorked, Direction.HIGHER),
        Rival("Gym sessions", now.gymDone, prev.gymDone, Direction.HIGHER),
        Rival("Job-search days", now.cvWorkedDays, prev.cvWorkedDays, Direction.HIGHER),
        Rival("Evenings skipped", now.eveningsSkipped, prev.eveningsSkipped, Direction.LOWER),
        Rival("Chill evenings", now.chillPercent, prev.chillPercent, Direction.LOWER),
        Rival("Pause time", now.pauseMinutes, prev.pauseMinutes, Direction.LOWER),
    )
    val moved = rivals.filter { it.old > 0 }.maxByOrNull { it.relative }
    if (moved != null && moved.new != moved.old) {
        return Headline(
            title = "${moved.metric} ${moved.improved()}",
            body = "Last week: ${moved.text(moved.old)}. This week: ${moved.text(moved.new)}.",
            kind = HeadlineKind.METRIC_MOVED,
        )
    }
    return Headline(
        title = "A steady week",
        body = "Nothing changed much compared with last week.",
        kind = HeadlineKind.STEADY,
    )
}

internal fun scorecardRows(now: WeekMetrics, prev: WeekMetrics): List<InsightRow> {
    val offDelta = now.offDays - prev.offDays
    return buildList {
        add(row("Ran over", formatHm(now.leakMinutes), "Time past block ends", now.leakMinutes, prev.leakMinutes, Direction.LOWER))
        add(row("Coding time", "${formatHm(now.codingWorked)} / ${formatHm(now.codingPlanned)}", "Done of planned", now.codingWorked, prev.codingWorked, Direction.HIGHER))
        add(row("Gym sessions", "${now.gymDone} / ${now.gymPlanned}", "Done of planned", now.gymDone, prev.gymDone, Direction.HIGHER))
        add(row("Job-search days", "${now.cvWorkedDays} / ${now.cvPlannedDays}", "Days the CV block happened", now.cvWorkedDays, prev.cvWorkedDays, Direction.HIGHER))
        add(row("Evenings skipped", "${now.eveningsSkipped}", "Kid asleep never tapped", now.eveningsSkipped, prev.eveningsSkipped, Direction.LOWER))
        add(row("Chill evenings", "${now.chillPercent}%", "Share of evenings spent on Chill", now.chillPercent, prev.chillPercent, Direction.LOWER))
        add(row("Pause time", formatHm(now.pauseMinutes), "Breaks taken during blocks", now.pauseMinutes, prev.pauseMinutes, Direction.LOWER))
        add(row("Walks", "${now.walks}", "Walks this week", now.walks, prev.walks, Direction.HIGHER))
        add(row("Walk time", formatHm(now.walkMinutes), "Time spent walking", now.walkMinutes, prev.walkMinutes, Direction.HIGHER))
        add(row("Steps", "≈ ${formatStepsApprox(now.avgSteps.toLong())} a day", "Average over days with steps", now.avgSteps, prev.avgSteps, Direction.HIGHER))
        add(row("Evening walks", "${now.eveningWalks}", "Walks after kid time", now.eveningWalks, prev.eveningWalks, Direction.HIGHER))
        // Dishes (3.3.0): only for a week that had evenings with Dishes.
        if (now.dishesEvenings > 0) {
            val over = if (now.dishesOver20 == 0) 0 else (now.dishesOverSum.toDouble() / now.dishesOver20).roundToInt()
            add(InsightRow("Dishes", "${now.dishesOver20} of ${now.dishesEvenings} evenings over 20 min", "+$over min on average"))
        }
        add(
            InsightRow(
                label = "Off days",
                value = "${now.offDays}",
                hint = "Days with no schedule",
                trend = InsightTrend(
                    arrow = when {
                        offDelta < 0 -> TrendArrow.DOWN
                        offDelta > 0 -> TrendArrow.UP
                        else -> TrendArrow.FLAT
                    },
                    delta = "${Math.abs(offDelta)}",
                    tone = TrendTone.NEUTRAL,
                ),
            ),
        )
    }
}

private fun row(
    label: String,
    value: String,
    hint: String,
    new: Int,
    old: Int,
    goodWhen: Direction,
): InsightRow {
    val delta = new - old
    val arrow = when {
        delta < 0 -> TrendArrow.DOWN
        delta > 0 -> TrendArrow.UP
        else -> TrendArrow.FLAT
    }
    val tone = when {
        delta == 0 -> TrendTone.NEUTRAL
        (delta < 0 && goodWhen == Direction.LOWER) || (delta > 0 && goodWhen == Direction.HIGHER) -> TrendTone.GOOD
        else -> TrendTone.LEAK
    }
    val text = when (label) {
        "Ran over", "Pause time", "Walk time" -> "${Math.abs(delta)}m"
        "Chill evenings" -> "${Math.abs(delta)}%"
        else -> "${Math.abs(delta)}"
    }
    return InsightRow(label, value, hint, InsightTrend(arrow, text, tone))
}

/** Content only; the top and bottom bars live in [InsightsRoute]. */
@Composable
fun InsightsScreen(state: InsightsUiState, onAction: (InsightsAction) -> Unit) {
    if (state.failed) {
        ErrorCard(onAction = { onAction(InsightsAction.Problem(it)) }, modifier = Modifier.padding(Spacing.m))
        return
    }
    if (!state.hasData) {
        EmptyState(
            icon = AppIcons.Insights,
            title = "Insights need a full week",
            body = "${state.daysLogged} of $DAYS_IN_WEEK days logged.",
            extra = { LoggedProgress(state.daysLogged) },
        )
        return
    }

    ScreenColumn {
        Headline(state)
        SectionCard(Modifier.testTag("insights_scorecard").fillMaxWidth()) {
            Text(
                text = "Compared with last week",
                style = MaterialTheme.typography.labelLarge,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            )
            state.rows.forEachIndexed { index, row ->
                if (index > 0) CardDivider()
                ScorecardRow(row)
            }
        }
        if (state.worst.isNotEmpty()) {
            SectionCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "Blocks that run long",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
                )
                state.worst.forEach { stat ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Metrics.row)
                            .padding(horizontal = Spacing.l, vertical = Spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .height(8.dp)
                                .width(8.dp)
                                .background(stat.accent, androidx.compose.foundation.shape.CircleShape),
                        )
                        Spacer(Modifier.width(Spacing.s))
                        Text(
                            text = stat.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppTheme.colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(Spacing.m))
                        Text(
                            text = "${stat.avgMinutes} min over on average",
                            style = MaterialTheme.typography.labelLarge,
                            color = AppTheme.colors.textSecondary,
                        )
                        Spacer(Modifier.width(Spacing.s))
                        Box(
                            Modifier
                                .width((120.dp * stat.fraction))
                                .height(6.dp)
                                .background(stat.accent, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Headline(state: InsightsUiState) {
    val c = AppTheme.colors
    Box(
        Modifier
            .testTag("insights_headline")
            .fillMaxWidth()
            .clip(AppShapes.hero)
            .background(
                Brush.verticalGradient(listOf(state.headlineAccent.copy(alpha = 0.14f), c.surface)),
            )
            .padding(Spacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            val icon = when (state.headlineKind) {
                HeadlineKind.METRIC_MOVED -> AppIcons.Insights
                HeadlineKind.OVER_RUN -> AppIcons.Insights
                HeadlineKind.STEADY -> AppIcons.Remove
            }
            IconCircle(
                icon = icon,
                modifier = Modifier,
                tint = state.headlineAccent,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.headlineTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = state.headlineBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ScorecardRow(row: InsightRow) {
    Row(
        Modifier
            .testTag("scorecard_row")
            .fillMaxWidth()
            .heightIn(min = Metrics.row)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.colors.textPrimary,
            )
            Text(
                text = row.hint,
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
        }
        Spacer(Modifier.width(Spacing.m))
        Text(
            text = row.value,
            style = MaterialTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
        )
        row.trend?.let { trend ->
            Spacer(Modifier.width(Spacing.s))
            TrendPill(trend)
        }
    }
}

@Composable
private fun TrendPill(trend: InsightTrend) {
    val c = AppTheme.colors
    val tint = when (trend.tone) {
        TrendTone.GOOD -> c.good
        TrendTone.LEAK -> c.leak
        TrendTone.NEUTRAL -> c.neutralTrend
    }
    if (trend.delta.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Icon(
            when (trend.arrow) {
                TrendArrow.DOWN -> AppIcons.ArrowDownward
                TrendArrow.UP -> AppIcons.ArrowUpward
                TrendArrow.FLAT -> AppIcons.Remove
            },
            contentDescription = when (trend.tone) {
                TrendTone.GOOD -> "improving"
                TrendTone.LEAK -> "worsening"
                TrendTone.NEUTRAL -> "steady"
            },
            tint = tint,
        )
        Text(
            text = trend.delta,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
        )
    }
}

@Composable
private fun LoggedProgress(daysLogged: Int) {
    val fraction = (daysLogged.toFloat() / DAYS_IN_WEEK).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = AppTheme.colors.primary,
            trackColor = AppTheme.colors.outline,
        )
        Spacer(Modifier.height(Spacing.s))
        Text(
            text = "${(fraction * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.textSecondary,
        )
    }
}

@Composable
fun InsightsRoute(onTab: (NavKey) -> Unit, onMenu: (NavKey) -> Unit, viewModel: InsightsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var explain by remember { mutableStateOf(false) }
    ScreenScaffold(
        topBar = {
            AppTopBar(
                title = { Text("Insights", style = MaterialTheme.typography.titleLarge) },
                onHistory = { onMenu(HistoryKey) },
                onProfiles = { onMenu(ProfilesKey) },
                onSettings = { onMenu(SettingsKey) },
                onExplain = { explain = true },
            )
        },
        bottomBar = { AppBottomBar(AppTab.INSIGHTS) { onTab(tabKey(it)) } },
    ) { padding ->
        Column(Modifier.fillMaxWidth().padding(padding)) {
            InsightsScreen(state = state, onAction = viewModel::onAction)
        }
    }
    if (explain) WhatTheseMeanDialog(onDismiss = { explain = false })
}
