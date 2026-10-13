package com.roni.blocks.feature.templates

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roni.blocks.core.model.Categories
import com.roni.blocks.core.model.CategoryInfo
import com.roni.blocks.core.model.CategorySet
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.SectionHeader
import com.roni.blocks.designsystem.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The category list for the 3.1.0 Templates tab. Read-only in Base slice 2 (one row per category,
 * built-ins first). Track A turns [CategorySection] into the category editor's entry point.
 */
class CategoryListViewModel : ViewModel() {
    private val _categories = MutableStateFlow(Categories.current)
    val categories: StateFlow<CategorySet> = _categories.asStateFlow()
}

/** One row per category (built-ins, then yours): a 12 dp dot in its colour, name and muted flags. */
@Composable
fun CategorySection(viewModel: CategoryListViewModel = viewModel()) {
    val set by viewModel.categories.collectAsStateWithLifecycle()
    SectionHeader("Categories")
    (set.builtIns + set.customs).forEach { info ->
        CategoryRow(info)
    }
}

@Composable
private fun CategoryRow(info: CategoryInfo) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
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