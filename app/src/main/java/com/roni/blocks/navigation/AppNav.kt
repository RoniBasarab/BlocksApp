package com.roni.blocks.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.feature.history.HistoryDetailRoute
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

enum class StartDestination { TEMPLATE_PICKER, HOME }

/** The picker is only ever the start when today has not been started; everything else opens Home. */
fun resolveStartDestination(state: DayState): StartDestination = when (state) {
    DayState.NotStarted -> StartDestination.TEMPLATE_PICKER
    is DayState.Running,
    is DayState.Paused,
    DayState.KidTime,
    is DayState.Evening,
    DayState.Ended,
    DayState.Off,
    -> StartDestination.HOME
}

private val StartDestination.key: NavKey
    get() = when (this) {
        StartDestination.TEMPLATE_PICKER -> TemplatePickerKey
        StartDestination.HOME -> HomeKey
    }

@Composable
fun AppRoot(start: StartDestination, modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(start.key)
    fun openTab(key: NavKey) {
        backStack.clear()
        backStack.add(key)
    }
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        entryProvider = entryProvider {
            entry<TemplatePickerKey> {
                TemplatePickerRoute(onStarted = { openTab(HomeKey) })
            }
            entry<HomeKey> {
                HomeRoute(
                    onMenu = { backStack.add(it) },
                    onSwap = { backStack.add(TemplatePickerKey) },
                    onTab = ::openTab,
                )
            }
            entry<WeeklyKey> { WeeklyRoute(onTab = ::openTab, onMenu = { backStack.add(it) }) }
            entry<InsightsKey> { InsightsRoute(onTab = ::openTab, onMenu = { backStack.add(it) }) }
            entry<HistoryKey> { HistoryRoute(onBack = { backStack.removeAt(backStack.lastIndex) }, onDay = { backStack.add(HistoryDayKey(it)) }) }
            entry<HistoryDayKey> { key -> HistoryDetailRoute(date = key.date, onBack = { backStack.removeAt(backStack.lastIndex) }) }
            entry<TimeDebtKey> { TimeDebtRoute(onBack = { backStack.removeAt(backStack.lastIndex) }) }
            entry<SettingsKey> {
                SettingsRoute(
                    onBack = { backStack.removeAt(backStack.lastIndex) },
                    onResetToday = { openTab(TemplatePickerKey) },
                )
            }
        },
    )
}
