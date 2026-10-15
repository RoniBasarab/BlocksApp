package com.roni.blocks.feature.templates

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.roni.blocks.core.model.Categories
import com.roni.blocks.core.model.CategoryInfo
import com.roni.blocks.core.model.CategoryPalette
import com.roni.blocks.core.model.CategorySet
import com.roni.blocks.core.storage.CategoryActions
import com.roni.blocks.core.storage.CategoryResult
import com.roni.blocks.core.storage.ExplainerKey
import com.roni.blocks.core.storage.ExplainerStore
import com.roni.blocks.core.system.AppRescue
import com.roni.blocks.core.system.Failures
import com.roni.blocks.core.system.ProblemAction
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.ErrorCard
import com.roni.blocks.designsystem.HintCard
import com.roni.blocks.designsystem.SectionHeader
import com.roni.blocks.designsystem.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The category editor's sheet state (3.1.0). [id] is null while making a new category. */
data class CategorySheetUi(
    val id: String?,
    val name: String,
    val colorArgb: Long,
    val work: Boolean,
    val pomodoro: Boolean,
    val builtIn: Boolean,
    val error: String? = null,
    val confirmDelete: Boolean = false,
)

/** The whole category editor's screen state (3.1.0): the rows and the sheet, when one is open. */
@Immutable
data class CategoryListState(
    val categories: CategorySet,
    val hint: Boolean = false,
    val sheet: CategorySheetUi? = null,
    val failed: Boolean = false,
)

/** Everything the category editor can do, sent to [CategoryListViewModel.onAction]. */
sealed interface CategoryAction {
    data class Open(val id: String) : CategoryAction
    data object New : CategoryAction
    data class Name(val text: String) : CategoryAction
    data class Color(val argb: Long) : CategoryAction
    data class Work(val on: Boolean) : CategoryAction
    data class Pomodoro(val on: Boolean) : CategoryAction
    data object Save : CategoryAction
    data object Delete : CategoryAction
    data object ConfirmDelete : CategoryAction
    data object CancelDelete : CategoryAction
    data object Dismiss : CategoryAction
    data object DismissHint : CategoryAction
    data class Problem(val action: ProblemAction) : CategoryAction
}

/**
 * The category editor for the 3.1.0 Templates tab: the rows (built-ins, then yours) plus the sheet
 * that edits one. Every write goes through [CategoryActions]; the rows re-read [Categories] after.
 */
@HiltViewModel
class CategoryListViewModel @Inject constructor(
    private val actions: CategoryActions,
    private val explainers: ExplainerStore,
    private val failures: Failures,
    private val rescue: AppRescue,
) : ViewModel() {
    private val _state = MutableStateFlow(CategoryListState(Categories.current))
    val state: StateFlow<CategoryListState> = _state.asStateFlow()

    private fun fail() = _state.update { it.copy(failed = true) }

    init {
        failures.launch(viewModelScope, "Categories: hint", onFailure = ::fail) {
            _state.update { it.copy(hint = !explainers.seen(ExplainerKey.CATEGORIES)) }
        }
    }

    fun onAction(action: CategoryAction) {
        when (action) {
            is CategoryAction.Open -> open(action.id)
            CategoryAction.New -> new()
            is CategoryAction.Name -> editSheet { it.copy(name = action.text, error = null) }
            is CategoryAction.Color -> editSheet { it.copy(colorArgb = action.argb) }
            is CategoryAction.Work -> editSheet { it.copy(work = action.on) }
            is CategoryAction.Pomodoro -> editSheet { it.copy(pomodoro = action.on) }
            CategoryAction.Save -> save()
            CategoryAction.Delete -> editSheet { it.copy(confirmDelete = true) }
            CategoryAction.ConfirmDelete -> confirmDelete()
            CategoryAction.CancelDelete -> editSheet { it.copy(confirmDelete = false) }
            CategoryAction.Dismiss -> _state.update { it.copy(sheet = null) }
            CategoryAction.DismissHint -> {
                _state.update { it.copy(hint = false) }
                failures.launch(viewModelScope, "Categories: mark hint seen", onFailure = ::fail) { explainers.markSeen(ExplainerKey.CATEGORIES) }
            }
            is CategoryAction.Problem -> rescue.handle(action.action)
        }
    }

    private fun open(id: String) {
        val info = all().firstOrNull { it.id == id } ?: return
        _state.update {
            it.copy(sheet = CategorySheetUi(info.id, info.name, info.colorArgb, info.work, info.pomodoro, info.builtIn))
        }
    }

    private fun new() {
        val used = all().map { it.colorArgb }.toSet()
        val color = CategoryPalette.firstOrNull { it !in used } ?: CategoryPalette.first()
        _state.update {
            it.copy(sheet = CategorySheetUi(null, "", color, work = true, pomodoro = false, builtIn = false))
        }
    }

    private fun save() {
        val ui = _state.value.sheet ?: return
        failures.launch(viewModelScope, "Categories: save", onFailure = ::fail) {
            val result = if (ui.id == null) {
                actions.add(ui.name, ui.colorArgb, ui.work, ui.pomodoro)
            } else {
                actions.update(CategoryInfo(ui.id, ui.name, ui.colorArgb, ui.work, ui.pomodoro, ui.builtIn))
            }
            when (result) {
                CategoryResult.EmptyName -> _state.update { it.copy(sheet = ui.copy(error = "Give it a name.")) }
                CategoryResult.NameTaken -> _state.update { it.copy(sheet = ui.copy(error = "That name is taken.")) }
                CategoryResult.TooLong -> _state.update { it.copy(sheet = ui.copy(error = "16 characters at most.")) }
                else -> _state.update { it.copy(categories = Categories.current, sheet = null) }
            }
        }
    }

    private fun confirmDelete() {
        val id = _state.value.sheet?.id ?: return
        failures.launch(viewModelScope, "Categories: delete", onFailure = ::fail) {
            actions.delete(id)
            _state.update { it.copy(categories = Categories.current, sheet = null) }
        }
    }

    private fun all(): List<CategoryInfo> = Categories.current.let { it.builtIns + it.customs }

    private fun editSheet(block: (CategorySheetUi) -> CategorySheetUi) {
        _state.update { it.copy(sheet = it.sheet?.let(block)) }
    }
}

/** One row per category, tap to edit: a 12 dp dot in its colour, name and muted flags. */
@Composable
fun CategorySection(viewModel: CategoryListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.failed) {
        ErrorCard(onAction = { viewModel.onAction(CategoryAction.Problem(it)) })
        return
    }
    SectionHeader("Categories")
    AnimatedVisibility(
        visible = state.hint,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column {
            HintCard(ExplainerKey.CATEGORIES, onGotIt = { viewModel.onAction(CategoryAction.DismissHint) })
            Spacer(Modifier.height(Spacing.s))
        }
    }
    (state.categories.builtIns + state.categories.customs).forEach { info ->
        CategoryRow(info, onClick = { viewModel.onAction(CategoryAction.Open(info.id)) })
    }
    TextButton(
        onClick = { viewModel.onAction(CategoryAction.New) },
        modifier = Modifier.testTag("cat_new"),
    ) {
        Text("+ New category")
    }
    state.sheet?.let { ui ->
        CategorySheet(ui = ui, onAction = viewModel::onAction)
    }
}

@Composable
private fun CategoryRow(info: CategoryInfo, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("cat_row_${info.id}")
            .padding(vertical = Spacing.xs),
    ) {
        Box(Modifier.size(12.dp).background(Color(info.colorArgb), CircleShape))
        Spacer(Modifier.width(Spacing.m))
        Text(info.name, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.textPrimary)
        Spacer(Modifier.weight(1f))
        if (info.work) MutedLabel("Work")
        if (info.pomodoro) MutedLabel("Pomodoro")
    }
}

@Composable
private fun MutedLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
    Spacer(Modifier.width(Spacing.s))
}
