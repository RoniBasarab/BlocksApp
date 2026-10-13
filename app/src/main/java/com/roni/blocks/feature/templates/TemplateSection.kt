package com.roni.blocks.feature.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roni.blocks.core.model.StoredTemplate
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.model.builtInTemplateSeeds
import com.roni.blocks.core.storage.TemplateActions
import com.roni.blocks.core.storage.TemplateStore
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.SectionHeader
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.designsystem.templateDisplay
import com.roni.blocks.feature.templatepicker.stripFor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the templates section can do on the list itself. */
sealed interface TemplateListAction {
    data object NewTemplate : TemplateListAction
    data object DismissNew : TemplateListAction
}

/** The templates section: the stored templates (deleted ones are archived) and the New dialog. */
@Immutable
data class TemplateSectionState(
    val templates: ImmutableList<StoredTemplate> = persistentListOf(),
    val showNew: Boolean = false,
)

/**
 * The template list for the 3.1.0 Templates tab. Read-only in Base slice 2 (one card per stored
 * template; tapping reports the id). Track B turns [TemplateSection] into the editor entry point.
 */
class TemplateListViewModel : ViewModel() {
    private val _state = MutableStateFlow(TemplateSectionState(templates = templates()))
    val state: StateFlow<TemplateSectionState> = _state.asStateFlow()

    fun onAction(action: TemplateListAction) {
        _state.update { state ->
            when (action) {
                TemplateListAction.NewTemplate -> state.copy(showNew = true)
                TemplateListAction.DismissNew -> state.copy(showNew = false)
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(templates = templates()) }
    }

    private fun templates(): ImmutableList<StoredTemplate> {
        TemplateStore.instance?.let { return it.all().toPersistentList() }
        return TemplateId.builtIns.map { id ->
            val seed = builtInTemplateSeeds.getValue(id)
            StoredTemplate(id, seed.title, seed.subtitle, seed.short, seed.iconKey, seed.accentArgb, seed.weekdays, emptyList())
        }.toPersistentList()
    }
}

/** One card per stored template: icon in its accent, title, weekdays and the picker's strip. */
@Composable
fun TemplateSection(
    onOpenTemplate: (TemplateId) -> Unit,
    onNewTemplate: () -> Unit,
    viewModel: TemplateListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SectionHeader("Templates")
    Button(
        onClick = { viewModel.onAction(TemplateListAction.NewTemplate) },
        modifier = Modifier.fillMaxWidth().testTag("tpl_new"),
    ) {
        Text("+ New template")
    }
    Spacer(Modifier.height(Spacing.s))
    if (state.showNew) {
        NewTemplateDialog(
            onDismiss = { viewModel.onAction(TemplateListAction.DismissNew) },
            onCreated = { id ->
                viewModel.onAction(TemplateListAction.DismissNew)
                viewModel.refresh()
                onOpenTemplate(id)
            },
        )
    }
    state.templates.forEach { template ->
        val display = templateDisplay(template.id)
        AppCard(
            onClick = { onOpenTemplate(template.id) },
            modifier = Modifier.fillMaxWidth().testTag("template_${template.id.key}"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(display.icon, null, tint = display.accent)
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(display.title, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
                        if (template.id in TemplateId.builtIns) {
                            Spacer(Modifier.width(Spacing.s))
                            TagLabel("Built-in")
                        }
                    }
                    Text(display.short, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                }
                BuildingBlockStrip(template.blocks)
            }
        }
        Spacer(Modifier.height(Spacing.s))
    }
}

/** The picker's strip: one coloured segment per distinct block kind, in the template's accent. */
@Composable
private fun BuildingBlockStrip(blocks: List<com.roni.blocks.core.model.BlockSpec>) {
    val strip = stripFor(blocks)
    if (strip.isEmpty()) return
    Row(Modifier.width(56.dp)) {
        strip.forEach { (color, _) ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(color, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun TagLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier
            .border(1.dp, AppTheme.colors.outline, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Creates a new blank own template through [TemplateActions], the only way to write (3.1.0). */
@HiltViewModel
class NewTemplateViewModel @Inject constructor(
    private val actions: TemplateActions,
) : ViewModel() {
    fun create(name: String, onCreated: (TemplateId) -> Unit) {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > 24) return
        viewModelScope.launch { onCreated(actions.create(clean).id) }
    }
}

@Composable
private fun NewTemplateDialog(
    onDismiss: () -> Unit,
    onCreated: (TemplateId) -> Unit,
    viewModel: NewTemplateViewModel = hiltViewModel(),
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New template") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                singleLine = true,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { viewModel.create(name, onCreated) },
            ) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}