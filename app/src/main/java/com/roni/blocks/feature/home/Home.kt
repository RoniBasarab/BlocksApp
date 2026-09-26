package com.roni.blocks.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.roni.blocks.core.daystate.DayEvent
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.core.time.Ticker
import androidx.navigation3.runtime.NavKey
import com.roni.blocks.navigation.HistoryKey
import com.roni.blocks.navigation.HomeKey
import com.roni.blocks.navigation.InsightsKey
import com.roni.blocks.navigation.SettingsKey
import com.roni.blocks.navigation.TimeDebtKey
import com.roni.blocks.navigation.WeeklyKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
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
    val paused: Boolean = false,
    val canNext: Boolean = false,
    val title: String = "",
)

sealed interface HomeAction {
    data object Next : HomeAction
    data object Pause : HomeAction
    data object Interrupt : HomeAction
    data object EndEarly : HomeAction
    data object EndDay : HomeAction
    data object Swap : HomeAction
    data class Menu(val key: NavKey) : HomeAction
    data class Tab(val key: NavKey) : HomeAction
}

sealed interface HomeEffect

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val day: DayOrchestrator,
    private val templates: TemplateManager,
    ticker: Ticker,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state = _state
    val minuteOfDay = ticker.minutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    private val _effects = MutableSharedFlow<HomeEffect>()
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            day.state.collect { publish(it) }
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Next -> day.send(DayEvent.Next)
            HomeAction.Pause -> day.send(DayEvent.Pause)
            HomeAction.Interrupt -> day.send(DayEvent.Interrupt)
            HomeAction.EndEarly -> day.send(DayEvent.EndBlockEarly)
            HomeAction.EndDay -> day.send(DayEvent.EndDay)
            else -> Unit
        }
    }

    private fun publish(dayState: DayState) {
        val id = dayState.templateId()
        val template = id?.let { runCatching { templates.get(it) }.getOrNull() }
        val blocks = template?.blocks?.map {
            HomeBlock(it.type.toString(), "${it.start}–${it.end}", it.start.hour * 60 + it.start.minute, it.end.hour * 60 + it.end.minute)
        }.orEmpty().toPersistentList()
        val index = (dayState as? DayState.Running)?.blockIndex ?: -1
        _state.update {
            it.copy(
                templateName = template?.name ?: id?.name.orEmpty(),
                blocks = blocks,
                currentIndex = index,
                paused = dayState is DayState.Paused,
                canNext = dayState is DayState.Running,
                title = blocks.getOrNull(index)?.title ?: dayState.toString(),
            )
        }
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
    HomeScreen(
        state = state,
        minuteOfDay = { minute },
        onAction = { action ->
            when (action) {
                HomeAction.Swap -> onSwap()
                is HomeAction.Menu -> onMenu(action.key)
                is HomeAction.Tab -> onTab(action.key)
                else -> viewModel.onAction(action)
            }
        },
    )
}

@Composable
fun HomeScreen(
    state: HomeState,
    minuteOfDay: () -> Int,
    onAction: (HomeAction) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.templateName.ifBlank { "Blocks" })
                TextButton(onClick = { onAction(HomeAction.Swap) }) { Text("Swap") }
                TextButton(onClick = { menu = true }) { Text("Menu") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("History") }, onClick = { menu = false; onAction(HomeAction.Menu(HistoryKey)) })
                    DropdownMenuItem(text = { Text("Time Debt") }, onClick = { menu = false; onAction(HomeAction.Menu(TimeDebtKey)) })
                    DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onAction(HomeAction.Menu(SettingsKey)) })
                }
            }
        },
        bottomBar = {
            Column {
                ControlBar(state, minuteOfDay, onAction)
                NavigationBar {
                    NavigationBarItem(selected = true, onClick = { onAction(HomeAction.Tab(HomeKey)) }, icon = { Text("Home") }, label = { Text("Home") })
                    NavigationBarItem(selected = false, onClick = { onAction(HomeAction.Tab(WeeklyKey)) }, icon = { Text("W") }, label = { Text("Weekly") })
                    NavigationBarItem(selected = false, onClick = { onAction(HomeAction.Tab(InsightsKey)) }, icon = { Text("I") }, label = { Text("Insights") })
                }
            }
        },
    ) { padding ->
        DayCalendar(
            blocks = state.blocks,
            currentIndex = state.currentIndex,
            minuteOfDay = minuteOfDay,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

@Composable
private fun ControlBar(state: HomeState, minuteOfDay: () -> Int, onAction: (HomeAction) -> Unit) {
    Column(Modifier.padding(12.dp)) {
        Text(state.title)
        Text(countdown(state, minuteOfDay))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onAction(HomeAction.Pause) }) { Text("Pause") }
            OutlinedButton(onClick = { onAction(HomeAction.Interrupt) }) { Text("Interrupt") }
            OutlinedButton(onClick = { onAction(HomeAction.EndEarly) }) { Text("End early") }
            Button(onClick = { onAction(HomeAction.Next) }, enabled = state.canNext) { Text("Next") }
        }
    }
}

private fun countdown(state: HomeState, minuteOfDay: () -> Int): String {
    val block = state.blocks.getOrNull(state.currentIndex) ?: return ""
    val left = block.endMinute - minuteOfDay()
    return if (left >= 0) "$left min left" else "leak"
}
