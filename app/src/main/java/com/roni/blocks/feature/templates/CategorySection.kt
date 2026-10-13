package com.roni.blocks.feature.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roni.blocks.core.model.Categories
import com.roni.blocks.core.model.CategoryInfo
import com.roni.blocks.core.model.CategoryPalette
import com.roni.blocks.core.model.CategorySet
import com.roni.blocks.core.storage.CategoryActions
import com.roni.blocks.core.storage.CategoryResult
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.SectionHeader
import com.roni.blocks.designsystem.Spacing
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/** The real actions in the running app. In previews and Robolectric tests without Hilt, writes are dropped. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface CategoryActionsEntryPoint {
    fun categoryActions(): CategoryActions
}

/**
 * The category editor for the 3.1.0 Templates tab: the rows (built-ins, then yours) plus the sheet
 * that edits one. Every write goes through [CategoryActions]; the rows re-read [Categories] after.
 */
class CategoryListViewModel(private val actions: CategoryActions?) : ViewModel() {
    private val _categories = MutableStateFlow(Categories.current)
    val categories: StateFlow<CategorySet> = _categories.asStateFlow()

    private val _sheet = MutableStateFlow<CategorySheetUi?>(null)
    val sheet: StateFlow<CategorySheetUi?> = _sheet.asStateFlow()

    fun Open(id: String) {
        val info = all().firstOrNull { it.id == id } ?: return
        _sheet.value = CategorySheetUi(info.id, info.name, info.colorArgb, info.work, info.pomodoro, info.builtIn)
    }

    fun New() {
        val used = all().map { it.colorArgb }.toSet()
        val color = CategoryPalette.firstOrNull { it !in used } ?: CategoryPalette.first()
        _sheet.value = CategorySheetUi(null, "", color, work = true, pomodoro = false, builtIn = false)
    }

    fun Name(text: String) = edit { it.copy(name = text, error = null) }
    fun Color(argb: Long) = edit { it.copy(colorArgb = argb) }
    fun Work(on: Boolean) = edit { it.copy(work = on) }
    fun Pomodoro(on: Boolean) = edit { it.copy(pomodoro = on) }
    fun Delete() = edit { it.copy(confirmDelete = true) }
    fun Dismiss() { _sheet.value = null }

    fun Save() {
        val ui = _sheet.value ?: return
        viewModelScope.launch {
            val result = if (ui.id == null) {
                actions?.add(ui.name, ui.colorArgb, ui.work, ui.pomodoro)
            } else {
                actions?.update(CategoryInfo(ui.id, ui.name, ui.colorArgb, ui.work, ui.pomodoro, ui.builtIn))
            }
            when (result) {
                CategoryResult.EmptyName -> _sheet.value = ui.copy(error = "Give it a name.")
                CategoryResult.NameTaken -> _sheet.value = ui.copy(error = "That name is taken.")
                CategoryResult.TooLong -> _sheet.value = ui.copy(error = "16 characters at most.")
                else -> {
                    _sheet.value = null
                    refresh()
                }
            }
        }
    }

    fun ConfirmDelete() {
        val id = _sheet.value?.id ?: return
        viewModelScope.launch {
            actions?.delete(id)
            _sheet.value = null
            refresh()
        }
    }

    private fun all(): List<CategoryInfo> = Categories.current.let { it.builtIns + it.customs }

    private fun edit(block: (CategorySheetUi) -> CategorySheetUi) {
        _sheet.value = _sheet.value?.let(block)
    }

    private fun refresh() {
        _categories.value = Categories.current
    }
}

/** One row per category, tap to edit: a 12 dp dot in its colour, name and muted flags. */
@Composable
fun CategorySection(viewModel: CategoryListViewModel = rememberCategoryListViewModel()) {
    val set by viewModel.categories.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    SectionHeader("Categories")
    (set.builtIns + set.customs).forEach { info ->
        CategoryRow(info, onClick = { viewModel.Open(info.id) })
    }
    TextButton(onClick = { viewModel.New() }, modifier = Modifier.testTag("cat_new")) {
        Text("+ New category")
    }
    sheet?.let { ui ->
        CategorySheet(
            ui = ui,
            onName = viewModel::Name,
            onColor = viewModel::Color,
            onWork = viewModel::Work,
            onPomodoro = viewModel::Pomodoro,
            onSave = viewModel::Save,
            onDelete = viewModel::Delete,
            onConfirmDelete = viewModel::ConfirmDelete,
            onDismiss = viewModel::Dismiss,
        )
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

/** The real [CategoryActions] when Hilt is up; null in previews and Hilt-free Robolectric tests. */
@Composable
private fun rememberCategoryListViewModel(): CategoryListViewModel {
    val appContext = LocalContext.current.applicationContext
    return viewModel {
        val actions = runCatching {
            EntryPointAccessors.fromApplication(appContext, CategoryActionsEntryPoint::class.java).categoryActions()
        }.getOrNull()
        CategoryListViewModel(actions)
    }
}
