package com.roni.blocks.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.storage.ExplainerKey
import com.roni.blocks.designsystem.Motion
import com.roni.blocks.feature.history.HistoryDetailRoute
import com.roni.blocks.feature.history.HistoryRoute
import com.roni.blocks.feature.home.HomeRoute
import com.roni.blocks.feature.insights.InsightsRoute
import com.roni.blocks.feature.profiles.CategoriesRoute
import com.roni.blocks.feature.profiles.FirstVisit
import com.roni.blocks.feature.profiles.ProfileRoute
import com.roni.blocks.feature.profiles.ProfilesRoute
import com.roni.blocks.feature.settings.SettingsRoute
import com.roni.blocks.feature.templatepicker.TemplatePickerRoute
import com.roni.blocks.feature.templates.TemplateEditorRoute
import com.roni.blocks.feature.weekly.WeeklyRoute
import kotlinx.serialization.Serializable

@Serializable data object TemplatePickerKey : NavKey
@Serializable data object HomeKey : NavKey
@Serializable data object WeeklyKey : NavKey
@Serializable data object InsightsKey : NavKey
@Serializable data object ProfilesKey : NavKey
@Serializable data object CategoriesKey : NavKey
@Serializable data object HistoryKey : NavKey
@Serializable data object SettingsKey : NavKey
@Serializable data class ProfileKey(val id: String) : NavKey
@Serializable data class HistoryDayKey(val date: String) : NavKey
@Serializable data class TemplateEditorKey(val key: String) : NavKey

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
        transitionSpec = { Motion.crossfade },
        popTransitionSpec = { Motion.crossfade },
        predictivePopTransitionSpec = { Motion.crossfade },
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
            entry<ProfilesKey> {
                FirstVisit(ExplainerKey.PROFILES) {
                    ProfilesRoute(
                        onBack = { backStack.removeAt(backStack.lastIndex) },
                        onOpenProfile = { backStack.add(ProfileKey(it)) },
                        onOpenCategories = { backStack.add(CategoriesKey) },
                    )
                }
            }
            entry<ProfileKey> { key ->
                FirstVisit(ExplainerKey.TEMPLATES) {
                    ProfileRoute(
                        id = key.id,
                        onBack = { backStack.removeAt(backStack.lastIndex) },
                        onOpenTemplate = { backStack.add(TemplateEditorKey(it.key)) },
                    )
                }
            }
            entry<CategoriesKey> {
                FirstVisit(ExplainerKey.CATEGORIES) {
                    CategoriesRoute(onBack = { backStack.removeAt(backStack.lastIndex) })
                }
            }
            entry<TemplateEditorKey> { key ->
                FirstVisit(ExplainerKey.BLOCKS) {
                    TemplateEditorRoute(id = TemplateId(key.key), onBack = { backStack.removeAt(backStack.lastIndex) })
                }
            }
            entry<HistoryKey> { HistoryRoute(onBack = { backStack.removeAt(backStack.lastIndex) }, onDay = { backStack.add(HistoryDayKey(it)) }) }
            entry<HistoryDayKey> { key -> HistoryDetailRoute(date = key.date, onBack = { backStack.removeAt(backStack.lastIndex) }) }
            entry<SettingsKey> {
                SettingsRoute(
                    onBack = { backStack.removeAt(backStack.lastIndex) },
                    onResetToday = { openTab(TemplatePickerKey) },
                    onOpenHome = { openTab(HomeKey) },
                )
            }
        },
    )
}
