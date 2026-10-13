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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.roni.blocks.core.model.StoredTemplate
import com.roni.blocks.core.model.TemplateId
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the templates section can do on the list itself. */
sealed interface TemplateListAction {
    data class Open(val id: TemplateId) : TemplateListAction
    data object NewTemplate : TemplateListAction
    data class NewName(val text: String) : TemplateListAction
    data object Create : TemplateListAction
    data object DismissNew : TemplateListAction
}

/** What the list asks the screen around it to do. */
sealed interface TemplateListEffect {
    data class OpenTemplate(val id: TemplateId) : TemplateListEffect
}

/** The templates section: the stored templates (deleted ones are archived) and the New dialog. */
@Immutable
data class TemplateSectionState(
    val templates: ImmutableList<StoredTemplate> = persistentListOf(),
    val newDialog: NewTemplateUi? = null,
)

/** The New dialog's fields; null [TemplateSectionState.newDialog] means the dialog is closed. */
@Immutable
data class NewTemplateUi(val name: String = "", val canCreate: Boolean = false)

/**
 * The template list for the Templates tab (3.1.1): follows [TemplateStore.templates], so a rename,
 * re-day or delete in the editor shows here at once, and creates own templates through
 * [TemplateActions] (the only way to write).
 */
@HiltViewModel
class TemplateListViewModel @Inject constructor(
    private val store: TemplateStore,
    private val actions: TemplateActions,
) : ViewModel() {
    private val _state = MutableStateFlow(TemplateSectionState())
    val state: StateFlow<TemplateSectionState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TemplateListEffect>()
    val effects: SharedFlow<TemplateListEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            store.templates.collect { list ->
                _state.update { it.copy(templates = list.toPersistentList()) }
            }
        }
    }

    fun onAction(action: TemplateListAction) {
        when (action) {
            is TemplateListAction.Open -> viewModelScope.launch {
                _effects.emit(TemplateListEffect.OpenTemplate(action.id))
            }
            TemplateListAction.NewTemplate -> _state.update { it.copy(newDialog = NewTemplateUi()) }
            is TemplateListAction.NewName -> _state.update { state ->
                state.copy(newDialog = NewTemplateUi(action.text, action.text.trim().length in 1..24))
            }
            TemplateListAction.Create -> create()
            TemplateListAction.DismissNew -> _state.update { it.copy(newDialog = null) }
        }
    }

    private fun create() {
        val name = _state.value.newDialog?.name?.trim() ?: return
        if (name.isEmpty() || name.length > 24) return
        viewModelScope.launch {
            val created = actions.createIn(store.activeProfileId(), name)
            _state.update { it.copy(newDialog = null) }
            _effects.emit(TemplateListEffect.OpenTemplate(created.id))
        }
    }
}

/** One card per stored template: icon in its accent, title, weekdays and the picker's strip. */
@Composable
fun TemplateSection(
    onOpenTemplate: (TemplateId) -> Unit,
    viewModel: TemplateListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TemplateListEffect.OpenTemplate -> onOpenTemplate(effect.id)
            }
        }
    }
    TemplateList(state = state, onAction = viewModel::onAction)
}

/** One card per stored template; [TemplateSection] owns the ViewModel and hands this stateless view state. */
@Composable
private fun TemplateList(state: TemplateSectionState, onAction: (TemplateListAction) -> Unit) {
    SectionHeader("Templates")
    Button(
        onClick = { onAction(TemplateListAction.NewTemplate) },
        modifier = Modifier.fillMaxWidth().testTag("tpl_new"),
    ) {
        Text("+ New template")
    }
    Spacer(Modifier.height(Spacing.s))
    state.newDialog?.let { NewTemplateDialog(ui = it, onAction = onAction) }
    state.templates.forEach { template ->
        val display = templateDisplay(template.id)
        AppCard(
            onClick = { onAction(TemplateListAction.Open(template.id)) },
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

/** The New dialog's fields live in [NewTemplateUi]; Create runs through [TemplateListAction.Create]. */
@Composable
private fun NewTemplateDialog(ui: NewTemplateUi, onAction: (TemplateListAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(TemplateListAction.DismissNew) },
        title = { Text("New template") },
        text = {
            OutlinedTextField(
                value = ui.name,
                onValueChange = { onAction(TemplateListAction.NewName(it.take(24))) },
                singleLine = true,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = ui.canCreate,
                onClick = { onAction(TemplateListAction.Create) },
            ) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = { onAction(TemplateListAction.DismissNew) }) { Text("Cancel") } },
    )
}