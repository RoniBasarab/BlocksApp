package com.roni.blocks.feature.insights

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import com.roni.blocks.designsystem.AppBottomBar
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppTab
import com.roni.blocks.designsystem.AppTopBar
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.feature.weekly.tabKey
import com.roni.blocks.navigation.HistoryKey
import com.roni.blocks.navigation.SettingsKey
import com.roni.blocks.navigation.TimeDebtKey

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
        Column(Modifier.fillMaxSize().padding(padding).padding(top = Spacing.l)) {
            AppCard { Text("Insights start after your first full week", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
