package com.roni.blocks.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.roni.blocks.core.daystate.DayEvent
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.core.time.Ticker
import com.roni.blocks.designsystem.AppBottomBar
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppTab
import com.roni.blocks.designsystem.AppTopBar
import com.roni.blocks.designsystem.AppType
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.feature.weekly.tabKey
import com.roni.blocks.navigation.HistoryKey
import com.roni.blocks.navigation.SettingsKey
import com.roni.blocks.navigation.TimeDebtKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class HomeBlock(val title: String, val range: String, val startMinute: Int, val endMinute: Int)

@Immutable
data class HomeState(
    val templateName: String = "",
    val blocks: ImmutableList<HomeBlock> = persistentListOf(),
    val currentIndex: Int = -1,
    val title: String = "",
    val canNext: Boolean = false,
)

sealed interface HomeAction {
    data object Next : HomeAction
    data object Pause : HomeAction
    data object Interrupt : HomeAction
    data object EndEarly : HomeAction
    data object EndDay : HomeAction
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val day: DayOrchestrator,
    private val templates: TemplateManager,
    ticker: Ticker,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state = _state
    val minuteOfDay = ticker.minutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch { day.state.collect { publish(it) } }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Next -> day.send(DayEvent.Next)
            HomeAction.Pause -> day.send(DayEvent.Pause)
            HomeAction.Interrupt -> day.send(DayEvent.Interrupt)
            HomeAction.EndEarly -> day.send(DayEvent.EndBlockEarly)
            HomeAction.EndDay -> day.send(DayEvent.EndDay)
        }
    }

    private fun publish(dayState: DayState) {
        val id = dayState.templateId()
        val template = id?.let { runCatching { templates.get(it) }.getOrNull() }
        val blocks = template?.blocks?.map {
            HomeBlock(it.type.toString(), "${it.start}–${it.end}", it.start.hour * 60 + it.start.minute, it.end.hour * 60 + it.end.minute)
        }.orEmpty().toPersistentList()
        val index = (dayState as? DayState.Running)?.blockIndex ?: -1
        _state.update { it.copy(templateName = template?.name ?: "", blocks = blocks, currentIndex = index, title = blocks.getOrNull(index)?.title ?: dayState.toString(), canNext = dayState is DayState.Running) }
    }
}

private fun DayState.templateId(): TemplateId? = when (this) {
    is DayState.Running -> template
    is DayState.Paused -> running.template
    is DayState.Interrupted -> running.template
    else -> null
}

@Composable
fun HomeRoute(
    onMenu: (NavKey) -> Unit,
    onSwap: () -> Unit,
    onTab: (NavKey) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val minute by viewModel.minuteOfDay.collectAsStateWithLifecycle()
    ScreenScaffold(
        topBar = {
            AppTopBar(
                title = { Text(state.templateName.ifBlank { "Blocks" }, style = MaterialTheme.typography.titleMedium) },
                onHistory = { onMenu(HistoryKey) },
                onDebt = { onMenu(TimeDebtKey) },
                onSettings = { onMenu(SettingsKey) },
                actions = { IconButton(onClick = onSwap) { Icon(Icons.Outlined.SwapHoriz, "Swap template") } },
            )
        },
        bottomBar = { AppBottomBar(AppTab.HOME) { onTab(tabKey(it)) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).testTag("home_calendar")) {
            DayCalendar(state.blocks, state.currentIndex, { minute }, Modifier.weight(1f))
            AppCard(Modifier.padding(bottom = Spacing.s)) {
                Text(state.title, style = MaterialTheme.typography.titleMedium)
                Text(countdown(state, minute), style = MaterialTheme.typography.headlineSmall.merge(AppType.numbers))
            }
        }
    }
}

private fun countdown(state: HomeState, minute: Int): String {
    val block = state.blocks.getOrNull(state.currentIndex) ?: return ""
    val left = block.endMinute - minute
    return if (left >= 0) "$left min left" else "leak"
}
