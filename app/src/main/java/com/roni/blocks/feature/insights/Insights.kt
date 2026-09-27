package com.roni.blocks.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.roni.blocks.designsystem.AppBottomBar
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppTab
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.AppTopBar
import com.roni.blocks.designsystem.CardDivider
import com.roni.blocks.designsystem.EmptyState
import com.roni.blocks.designsystem.Metrics
import com.roni.blocks.designsystem.ScreenColumn
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.SectionCard
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.feature.weekly.tabKey
import com.roni.blocks.navigation.HistoryKey
import com.roni.blocks.navigation.SettingsKey
import com.roni.blocks.navigation.TimeDebtKey

data class InsightRow(val label: String, val value: String)

data class InsightsUiState(
    val hasData: Boolean,
    val daysLogged: Int = 0,
    val headline: String = "",
    val rows: List<InsightRow> = emptyList(),
)

sealed interface InsightsAction {
    data object DismissProgressNudge : InsightsAction
}

private const val DAYS_IN_WEEK = 7

/** Content only; the top and bottom bars live in [InsightsRoute]. */
@Composable
fun InsightsScreen(state: InsightsUiState, onAction: (InsightsAction) -> Unit) {
    if (!state.hasData) {
        EmptyState(
            icon = Icons.Outlined.Insights,
            title = "Insights need a full week",
            body = "${state.daysLogged} of $DAYS_IN_WEEK days logged.",
            extra = { LoggedProgress(state.daysLogged) },
        )
        return
    }

    ScreenColumn {
        AppCard(Modifier.testTag("insights_headline").fillMaxWidth()) {
            Text(
                text = state.headline,
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
            )
        }
        SectionCard(Modifier.testTag("insights_scorecard").fillMaxWidth()) {
            state.rows.forEachIndexed { index, row ->
                if (index > 0) CardDivider()
                ScorecardRow(row)
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
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodyLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.m))
        Text(
            text = row.value,
            style = MaterialTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
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
fun InsightsRoute(onTab: (NavKey) -> Unit, onMenu: (NavKey) -> Unit) {
    ScreenScaffold(
        topBar = {
            AppTopBar(
                title = { Text("Insights", style = MaterialTheme.typography.titleLarge) },
                onHistory = { onMenu(HistoryKey) },
                onDebt = { onMenu(TimeDebtKey) },
                onSettings = { onMenu(SettingsKey) },
            )
        },
        bottomBar = { AppBottomBar(AppTab.INSIGHTS) { onTab(tabKey(it)) } },
    ) { padding ->
        Column(Modifier.fillMaxWidth().padding(padding)) {
            InsightsScreen(state = InsightsUiState(hasData = false), onAction = {})
        }
    }
}
