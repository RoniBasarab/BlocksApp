package com.roni.blocks.feature.timedebt

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.AppType
import com.roni.blocks.designsystem.BackTopBar
import com.roni.blocks.designsystem.EmptyState
import com.roni.blocks.designsystem.Good
import com.roni.blocks.designsystem.Leak
import com.roni.blocks.designsystem.Metrics
import com.roni.blocks.designsystem.ScreenColumn
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.Spacing

data class DebtDay(val label: String, val minutes: Int)

data class TimeDebtUiState(
    val hasDebt: Boolean,
    val totalMinutes: Int = 0,
    val days: List<DebtDay> = emptyList(),
)

private val ChartHeight = 96.dp

/** Content only; the top bar lives in [TimeDebtRoute]. */
@Composable
fun TimeDebtScreen(state: TimeDebtUiState, onAction: (DebtDay) -> Unit) {
    if (!state.hasDebt) {
        EmptyState(
            icon = Icons.Outlined.HourglassBottom,
            title = "No time debt yet",
            body = "When a block runs over, it shows up here.",
        )
        return
    }

    ScreenColumn {
        AppCard(Modifier.testTag("debt_hero").fillMaxWidth()) {
            Text(
                text = "This week",
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(Spacing.s))
            Text(
                text = formatDebt(state.totalMinutes),
                style = MaterialTheme.typography.displaySmall,
                color = if (state.totalMinutes == 0) Good else Leak,
            )
            Spacer(Modifier.height(Spacing.s))
            Text(
                text = if (state.totalMinutes == 0) "No debt. Clean week." else "Blocks that ran over.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.totalMinutes == 0) Good else AppTheme.colors.textSecondary,
            )
        }
        AppCard(Modifier.testTag("debt_chart").fillMaxWidth()) {
            DebtChart(state.days)
        }
        state.days.forEach { day ->
            DebtDayRow(day = day, onClick = { onAction(day) })
        }
    }
}

@Composable
private fun DebtChart(days: List<DebtDay>) {
    if (days.isEmpty()) return
    val maxMinutes = days.maxOf { it.minutes }
    Row(
        modifier = Modifier.fillMaxWidth().height(ChartHeight),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(debtBarHeight(day.minutes, maxMinutes))
                        .clip(CircleShape)
                        .background(Leak),
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = day.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Never divides by zero: an all-zero week renders flat baseline stubs. */
private fun debtBarHeight(minutes: Int, maxMinutes: Int): Dp {
    if (maxMinutes <= 0 || minutes <= 0) return 2.dp
    return (ChartHeight * (minutes.toFloat() / maxMinutes)).coerceIn(2.dp, ChartHeight)
}

@Composable
private fun DebtDayRow(day: DebtDay, onClick: () -> Unit) {
    Row(
        Modifier
            .testTag("debt_day_row")
            .fillMaxWidth()
            .heightIn(min = Metrics.row)
            .clip(CircleShape)
            .padding(horizontal = Spacing.m, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day.label,
            style = MaterialTheme.typography.bodyLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.m))
        Text(
            text = if (day.minutes == 0) "Clean" else "+${formatDebt(day.minutes)}",
            style = MaterialTheme.typography.labelLarge,
            color = if (day.minutes == 0) Good else Leak,
            textAlign = TextAlign.End,
        )
    }
}

private fun formatDebt(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "$rest min"
        rest == 0 -> "$hours h"
        else -> "$hours h $rest min"
    }
}

@Composable
fun TimeDebtRoute(onBack: () -> Unit) {
    ScreenScaffold(
        topBar = {
            BackTopBar(title = "Time Debt", onBack = onBack)
        },
    ) { padding ->
        Column(Modifier.fillMaxWidth().padding(padding)) {
            TimeDebtScreen(state = TimeDebtUiState(hasDebt = false), onAction = {})
        }
    }
}
