package com.roni.blocks.feature.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.roni.blocks.core.storage.ExplainerKey
import com.roni.blocks.core.storage.ExplainerStore
import com.roni.blocks.designsystem.ExplainerDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class FirstVisitState(val show: Boolean = false)

sealed interface FirstVisitAction {
    data object GotIt : FirstVisitAction
}

/** Shows one explainer card the first time a place is opened (3.3.0). */
@HiltViewModel
class FirstVisitViewModel @Inject constructor(private val store: ExplainerStore) : ViewModel() {
    private val _state = MutableStateFlow(FirstVisitState())
    val state: StateFlow<FirstVisitState> = _state.asStateFlow()

    private var key: ExplainerKey? = null

    fun load(key: ExplainerKey) {
        this.key = key
        viewModelScope.launch { _state.update { it.copy(show = !store.seen(key)) } }
    }

    fun onAction(action: FirstVisitAction) {
        when (action) {
            FirstVisitAction.GotIt -> {
                val key = key ?: return
                viewModelScope.launch {
                    store.markSeen(key)
                    _state.update { it.copy(show = false) }
                }
            }
        }
    }
}

/** Draws [content], then its one explainer card while it hasn't been seen. */
@Composable
fun FirstVisit(key: ExplainerKey, content: @Composable () -> Unit) {
    val viewModel: FirstVisitViewModel = hiltViewModel()
    LaunchedEffect(key) { viewModel.load(key) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    content()
    if (state.show) {
        ExplainerDialog(key, onGotIt = { viewModel.onAction(FirstVisitAction.GotIt) })
    }
}
