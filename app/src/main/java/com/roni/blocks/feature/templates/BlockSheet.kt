package com.roni.blocks.feature.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Categories
import com.roni.blocks.core.schedule.TemplateEdit
import com.roni.blocks.core.schedule.TemplateRules
import com.roni.blocks.core.time.TimeFormatter
import com.roni.blocks.designsystem.AppShapes
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.Spacing

/** Everything the user can do in the block sheet. The editor turns each into a [TemplateEdit]. */
sealed interface BlockSheetAction {
    data class Type(val type: BlockType) : BlockSheetAction
    data class StartBy(val minutes: Int) : BlockSheetAction
    data class StartAt(val minute: Int) : BlockSheetAction
    data class LengthBy(val minutes: Int) : BlockSheetAction
    data object Delete : BlockSheetAction
    data object Save : BlockSheetAction
    data object Dismiss : BlockSheetAction
}

/**
 * Edits one block of a template (3.1.0): category, start and length, with the editor's live result
 * line and Delete / Save. Takes plain values and forwards [BlockSheetAction]s; Home wraps it in its
 * own action in 3.2.0.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockSheet(
    ui: BlockSheetUi,
    use24: Boolean,
    onAction: (BlockSheetAction) -> Unit,
) {
    val colors = AppTheme.colors
    ModalBottomSheet(
        onDismissRequest = { onAction(BlockSheetAction.Dismiss) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = AppShapes.sheet,
        containerColor = colors.surface,
        dragHandle = null,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                if (ui.id == null) "New block" else blockName(ui.type),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            if (!ui.isDayEnd) {
                CategoryMenu(ui.type, onAction)
            }
            StartRow(ui, use24, onAction)
            if (ui.isDayEnd) {
                Text(
                    "Until ${TimeFormatter.label(TemplateRules.DAY_END, use24)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            } else {
                LengthRow(ui, onAction)
            }
            if (ui.line.isNotEmpty()) {
                Text(
                    ui.line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ui.result is TemplateEdit.Ok) colors.textSecondary else colors.leak,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                if (ui.id != null && !ui.isDayEnd) {
                    TextButton(
                        onClick = { onAction(BlockSheetAction.Delete) },
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.leak),
                    ) {
                        Text("Delete")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { onAction(BlockSheetAction.Save) },
                        enabled = ui.result is TemplateEdit.Ok,
                        modifier = Modifier.testTag("sheet_save"),
                    ) {
                        Text("Save")
                    }
                } else {
                    Button(
                        onClick = { onAction(BlockSheetAction.Save) },
                        enabled = ui.result is TemplateEdit.Ok,
                        modifier = Modifier.testTag("sheet_save").fillMaxWidth(),
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryMenu(selected: BlockType, onAction: (BlockSheetAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = buildList {
        Categories.current.builtIns.forEach { add(it to typeForCategory(it)) }
        Categories.current.customs.forEach { add(it to typeForCategory(it)) }
        add(null to BlockType.KidTime)
    }
    val colors = AppTheme.colors
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = blockName(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (info, type) ->
                DropdownMenuItem(
                    text = { Text(info?.name ?: "Kid time") },
                    onClick = {
                        onAction(BlockSheetAction.Type(type))
                        expanded = false
                    },
                )
            }
        }
    }
    Spacer(Modifier.height(Spacing.xs))
}

@Composable
private fun StartRow(ui: BlockSheetUi, use24: Boolean, onAction: (BlockSheetAction) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        StepButton("\u221215", "sheet_start_minus15", Modifier.weight(1f)) { onAction(BlockSheetAction.StartBy(-15)) }
        StepButton("\u22125", "sheet_start_minus5", Modifier.weight(1f)) { onAction(BlockSheetAction.StartBy(-5)) }
        Text(
            TimeFormatter.label(ui.startMin, use24),
            style = MaterialTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1.6f)
                .clickable { picker = true }
                .testTag("sheet_start_time"),
        )
        StepButton("+5", "sheet_start_plus5", Modifier.weight(1f)) { onAction(BlockSheetAction.StartBy(5)) }
        StepButton("+15", "sheet_start_plus15", Modifier.weight(1f)) { onAction(BlockSheetAction.StartBy(15)) }
    }
    if (picker) {
        TimePickerDialog(
            initialMin = ui.startMin,
            use24 = use24,
            onDismiss = { picker = false },
            onConfirm = { minute -> onAction(BlockSheetAction.StartAt(minute)); picker = false },
        )
    }
}

@Composable
private fun LengthRow(ui: BlockSheetUi, onAction: (BlockSheetAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        StepButton("\u221215", "sheet_len_minus15", Modifier.weight(1f)) { onAction(BlockSheetAction.LengthBy(-15)) }
        StepButton("\u22125", "sheet_len_minus5", Modifier.weight(1f)) { onAction(BlockSheetAction.LengthBy(-5)) }
        Text(
            "${ui.lengthMin / 60}h ${ui.lengthMin % 60}m",
            style = MaterialTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.6f),
        )
        StepButton("+5", "sheet_len_plus5", Modifier.weight(1f)) { onAction(BlockSheetAction.LengthBy(5)) }
        StepButton("+15", "sheet_len_plus15", Modifier.weight(1f)) { onAction(BlockSheetAction.LengthBy(15)) }
    }
}

@Composable
private fun StepButton(label: String, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier.testTag(tag)) { Text(label) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initialMin: Int, use24: Boolean, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initialMin / 60,
        initialMinute = initialMin % 60,
        is24Hour = use24,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start time") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}