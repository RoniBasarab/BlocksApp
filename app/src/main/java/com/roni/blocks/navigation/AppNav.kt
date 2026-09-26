package com.roni.blocks.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.roni.blocks.feature.history.HistoryRoute
import com.roni.blocks.feature.home.HomeRoute
import com.roni.blocks.feature.insights.InsightsRoute
import com.roni.blocks.feature.settings.SettingsRoute
import com.roni.blocks.feature.templatepicker.TemplatePickerRoute
import com.roni.blocks.feature.timedebt.TimeDebtRoute
import com.roni.blocks.feature.weekly.WeeklyRoute
import kotlinx.serialization.Serializable

@Serializable data object TemplatePickerKey : NavKey
@Serializable data object HomeKey : NavKey
@Serializable data object WeeklyKey : NavKey
@Serializable data object InsightsKey : NavKey
@Serializable data object HistoryKey : NavKey
@Serializable data object TimeDebtKey : NavKey
@Serializable data object SettingsKey : NavKey
@Serializable data class HistoryDayKey(val date: String) : NavKey

@Composable
fun AppNav(startOnPicker: Boolean) {
    val backStack = rememberNavBackStack(if (startOnPicker) TemplatePickerKey else HomeKey)
    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        entryProvider = entryProvider {
            entry<TemplatePickerKey> {
                TemplatePickerRoute(onStarted = { backStack.clear(); backStack.add(HomeKey) })
            }
            entry<HomeKey> {
                HomeRoute(
                    onMenu = { key -> backStack.add(key) },
                    onSwap = { backStack.add(TemplatePickerKey) },
                    onTab = { key -> backStack.clear(); backStack.add(key) },
                )
            }
            entry<WeeklyKey> { WeeklyRoute(onTab = { key -> backStack.clear(); backStack.add(key) }) }
            entry<InsightsKey> { InsightsRoute(onTab = { key -> backStack.clear(); backStack.add(key) }) }
            entry<HistoryKey> { HistoryRoute(onDay = { backStack.add(HistoryDayKey(it)) }) }
            entry<HistoryDayKey> { key -> HistoryRoute(date = key.date, onDay = {}) }
            entry<TimeDebtKey> { TimeDebtRoute() }
            entry<SettingsKey> { SettingsRoute() }
        },
    )
}
