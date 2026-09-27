package com.roni.blocks.feature.weekly

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
import com.roni.blocks.navigation.HistoryKey
import com.roni.blocks.navigation.HomeKey
import com.roni.blocks.navigation.InsightsKey
import com.roni.blocks.navigation.SettingsKey
import com.roni.blocks.navigation.TimeDebtKey

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
        Column(Modifier.fillMaxSize().padding(padding).padding(top = Spacing.l)) {
            AppCard { Text("No data for this week yet", style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

internal fun tabKey(tab: AppTab): NavKey = when (tab) {
    AppTab.HOME -> HomeKey
    AppTab.WEEKLY -> WeeklyKey()
    AppTab.INSIGHTS -> InsightsKey
}

private fun WeeklyKey() = com.roni.blocks.navigation.WeeklyKey
