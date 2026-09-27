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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.roni.blocks.designsystem.AppBottomBar
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppTab
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.AppTopBar
import com.roni.blocks.designsystem.EmptyState
import com.roni.blocks.designsystem.Metrics
import com.roni.blocks.designsystem.ScreenColumn
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.navigation.HistoryKey
import com.roni.blocks.navigation.HomeKey
import com.roni.blocks.navigation.InsightsKey
import com.roni.blocks.navigation.SettingsKey
import com.roni.blocks.navigation.TimeDebtKey
import com.roni.blocks.navigation.WeeklyKey

data class WeeklyStat(val label: String, val value: String)
data class WeeklyBar(val label: String, val fraction: Float)

data class WeeklyUiState(
    val hasData: Boolean,
    val weekLabel: String = "13 – 19 Sep",
    val previousWeekLabel: String = "6 – 12 Sep",
    val nextWeekLabel: String = "20 – 26 Sep",
    val heroLine: String = "",
    val stats: List<WeeklyStat> = emptyList(),
    val bars: List<WeeklyBar> = emptyList(),
)

sealed interface WeeklyAction {
    data object PreviousWeek : WeeklyAction
    data object NextWeek : WeeklyAction
}

private val ChartHeight = 120.dp

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
                text = state.heroLine,
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(Spacing.s))
            Text(
                text = "${state.previousWeekLabel}  ·  ${state.nextWeekLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
        }
        AppCard(Modifier.testTag("weekly_chart").fillMaxWidth()) {
            WeeklyChart(state.bars)
        }
        StatGrid(state.stats)
    }
}

@Composable
private fun WeekSelector(state: WeeklyUiState, onAction: (WeeklyAction) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = AppTheme.colors.surface,
            modifier = Modifier.testTag("week_selector"),
        ) {
            Row(
                modifier = Modifier.heightIn(min = Metrics.touch),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onAction(WeeklyAction.PreviousWeek) }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = state.previousWeekLabel,
                        tint = AppTheme.colors.textSecondary,
                    )
                }
                Text(
                    text = state.weekLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                )
                IconButton(onClick = { onAction(WeeklyAction.NextWeek) }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = state.nextWeekLabel,
                        tint = AppTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyChart(bars: List<WeeklyBar>) {
    if (bars.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().height(ChartHeight),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { bar ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(barHeight(bar.fraction))
                        .clip(CircleShape)
                        .background(AppTheme.colors.primary),
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A zero-length bar still shows a baseline stub so the day remains visible. */
private fun barHeight(fraction: Float): Dp {
    val clamped = fraction.coerceIn(0f, 1f)
    return if (clamped == 0f) 2.dp else (ChartHeight * clamped).coerceAtLeast(2.dp)
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
fun WeeklyRoute(onTab: (NavKey) -> Unit, onMenu: (NavKey) -> Unit) {
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
            WeeklyScreen(state = WeeklyUiState(hasData = false), onAction = {})
        }
    }
}

internal fun tabKey(tab: AppTab): NavKey = when (tab) {
    AppTab.HOME -> HomeKey
    AppTab.WEEKLY -> WeeklyKey
    AppTab.INSIGHTS -> InsightsKey
}
