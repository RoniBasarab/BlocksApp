package com.roni.blocks.feature.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.time.TimeFormatter
import com.roni.blocks.designsystem.AppDropdown
import com.roni.blocks.designsystem.AppSheet
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.AppTimePicker
import com.roni.blocks.designsystem.Spacing
import kotlinx.collections.immutable.ImmutableList

/**
 * One block as the shared sheet shows it (3.3.0): Home's real-day block or a template editor row.
 * Only the rows that are set are drawn; [categories] empty means no Category dropdown. Takes plain
 * values and forwards [BlockSheetAction]s, so both Home and the template editor use it.
 */
@Immutable
data class BlockSheetUi(
    /** The block's name, or "New block". */
    val title: String,
    val type: BlockType,
    /** What the dropdown offers; empty = no dropdown. */
    val categories: ImmutableList<BlockType>,
    /** null = no Start row. */
    val startMin: Int?,
    /** null = no Length row. */
    val lengthMin: Int?,
    /** Shown instead of the Length row, e.g. "Until 22:30". */
    val lengthText: String? = null,
    /** The live result line, or null when there's nothing to say. */
    val line: String? = null,
    val lineIsError: Boolean = false,
    val canSave: Boolean = true,
    val canDelete: Boolean = false,
    val canAddAfter: Boolean = false,
)

/** Everything the user can do in the block sheet. Each caller turns it into its own edit. */
sealed interface BlockSheetAction {
    data class Type(val type: BlockType) : BlockSheetAction
    data class StartBy(val minutes: Int) : BlockSheetAction
    data class StartAt(val minute: Int) : BlockSheetAction
    data class LengthBy(val minutes: Int) : BlockSheetAction
    /** Add a block right after this one (3.3.0); Home only. */
    data object AddAfter : BlockSheetAction
    data object Delete : BlockSheetAction
    data object Save : BlockSheetAction
    data object Dismiss : BlockSheetAction
}

/**
 * The shared block sheet (3.3.0): category, start and length with the caller's live result line, plus
 * Add after / Delete / Save. Home and the template editor both map their state into [BlockSheetUi].
 */
@Composable
fun BlockSheet(
    ui: BlockSheetUi,
    use24: Boolean,
    onAction: (BlockSheetAction) -> Unit,
) {
    val colors = AppTheme.colors
    AppSheet(onDismiss = { onAction(BlockSheetAction.Dismiss) }) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                ui.title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            if (ui.categories.isNotEmpty()) {
                CategoryMenu(ui.type, ui.categories, onAction)
            }
            ui.startMin?.let { StartRow(it, use24, onAction) }
            when {
                ui.lengthMin != null -> LengthRow(ui.lengthMin, onAction)
                ui.lengthText != null -> Text(
                    ui.lengthText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            ui.line?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ui.lineIsError) colors.leak else colors.textSecondary,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                if (ui.canDelete) {
                    TextButton(
                        onClick = { onAction(BlockSheetAction.Delete) },
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.leak),
                    ) {
                        Text("Delete")
                    }
                }
                if (ui.canAddAfter) {
                    TextButton(onClick = { onAction(BlockSheetAction.AddAfter) }) {
                        Text("Add after")
                    }
                }
                val fullWidthSave = !ui.canDelete && !ui.canAddAfter
                if (!fullWidthSave) Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onAction(BlockSheetAction.Save) },
                    enabled = ui.canSave,
                    modifier = if (fullWidthSave) {
                        Modifier.testTag("sheet_save").fillMaxWidth()
                    } else {
                        Modifier.testTag("sheet_save")
                    },
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun CategoryMenu(selected: BlockType, categories: ImmutableList<BlockType>, onAction: (BlockSheetAction) -> Unit) {
    AppDropdown(
        value = blockName(selected),
        label = "Category",
        options = categories,
        optionLabel = { blockName(it) },
        onSelect = { onAction(BlockSheetAction.Type(it)) },
    )
    Spacer(Modifier.height(Spacing.xs))
}

@Composable
private fun StartRow(startMin: Int, use24: Boolean, onAction: (BlockSheetAction) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        StepButton("\u221215", "sheet_start_minus15", Modifier.weight(1f)) { onAction(BlockSheetAction.StartBy(-15)) }
        StepButton("\u22125", "sheet_start_minus5", Modifier.weight(1f)) { onAction(BlockSheetAction.StartBy(-5)) }
        Text(
            TimeFormatter.label(startMin, use24),
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
        AppTimePicker(
            initialMin = startMin,
            use24 = use24,
            onDismiss = { picker = false },
            onConfirm = { minute -> onAction(BlockSheetAction.StartAt(minute)); picker = false },
        )
    }
}

@Composable
private fun LengthRow(lengthMin: Int, onAction: (BlockSheetAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        StepButton("\u221215", "sheet_len_minus15", Modifier.weight(1f)) { onAction(BlockSheetAction.LengthBy(-15)) }
        StepButton("\u22125", "sheet_len_minus5", Modifier.weight(1f)) { onAction(BlockSheetAction.LengthBy(-5)) }
        Text(
            "${lengthMin / 60}h ${lengthMin % 60}m",
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
