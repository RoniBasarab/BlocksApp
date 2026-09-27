package com.roni.blocks.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.BackTopBar
import com.roni.blocks.designsystem.CardDivider
import com.roni.blocks.designsystem.EmptyState
import com.roni.blocks.designsystem.Metrics
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.Spacing

data class HistoryDay(val date: String, val summary: String, val status: String)
data class HistoryWeek(val header: String, val days: List<HistoryDay>)

data class HistoryUiState(
    val hasData: Boolean,
    val weeks: List<HistoryWeek> = emptyList(),
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
        contentPadding = PaddingValues(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        state.weeks.forEach { week ->
            item(key = "header-${week.header}") {
                Text(
                    text = week.header,
                    style = MaterialTheme.typography.labelLarge,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.padding(top = Spacing.l),
                )
            }
            items(items = week.days, key = { "${week.header}-${it.date}" }) { day ->
                HistoryDayRow(day = day, onClick = { onAction(day) })
            }
        }
    }
}

@Composable
private fun HistoryDayRow(day: HistoryDay, onClick: () -> Unit) {
    Row(
        Modifier
            .testTag("history_day_row")
            .fillMaxWidth()
            .heightIn(min = Metrics.row)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(horizontal = Spacing.m, vertical = Spacing.m)) {
            Text(
                text = day.date,
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.colors.textPrimary,
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = day.summary,
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
        }
        Text(
            text = day.status,
            style = MaterialTheme.typography.labelLarge,
            color = AppTheme.colors.textMuted,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(end = Spacing.m),
        )
    }
}

@Composable
fun HistoryRoute(date: String? = null, onBack: () -> Unit, onDay: (String) -> Unit) {
    ScreenScaffold(
        topBar = {
            BackTopBar(title = date ?: "History", onBack = onBack)
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            HistoryScreen(
                state = HistoryUiState(hasData = false),
                onAction = { onDay(it.date) },
            )
        }
    }
}
