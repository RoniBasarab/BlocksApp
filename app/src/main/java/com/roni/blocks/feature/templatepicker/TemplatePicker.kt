package com.roni.blocks.feature.templatepicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.roni.blocks.core.daystate.DayEvent
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.model.TemplateId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class PickerState(val names: ImmutableList<String> = persistentListOf("Mon/Thu", "Sun/Tue/Wed", "Fri/Sat", "Off"))

sealed interface PickerAction {
    data class Pick(val id: TemplateId) : PickerAction
}

@HiltViewModel
class TemplatePickerViewModel @Inject constructor(
    private val day: DayOrchestrator,
) : ViewModel() {
    val state = PickerState()
    fun onAction(action: PickerAction) {
        when (action) {
            is PickerAction.Pick -> if (action.id == TemplateId.OFF) day.send(DayEvent.TakeOffDay) else day.send(DayEvent.StartDay(action.id))
        }
    }
}

@Composable
fun TemplatePickerRoute(onStarted: () -> Unit, viewModel: TemplatePickerViewModel = hiltViewModel()) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pick a template")
        Button(onClick = { viewModel.onAction(PickerAction.Pick(TemplateId.MON_THU)); onStarted() }) { Text("Mon/Thu") }
        Button(onClick = { viewModel.onAction(PickerAction.Pick(TemplateId.SUN_TUE_WED)); onStarted() }) { Text("Sun/Tue/Wed") }
        Button(onClick = { viewModel.onAction(PickerAction.Pick(TemplateId.FRI_SAT)); onStarted() }) { Text("Fri/Sat") }
        Button(onClick = { viewModel.onAction(PickerAction.Pick(TemplateId.OFF)); onStarted() }) { Text("Off") }
    }
}
