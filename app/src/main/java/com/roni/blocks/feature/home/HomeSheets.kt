package com.roni.blocks.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.schedule.RecordEdit
import com.roni.blocks.core.schedule.RowFix
import com.roni.blocks.designsystem.AppSheet
import com.roni.blocks.designsystem.AppShapes
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.Metrics
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.designsystem.blockColors

private fun Modifier.blockSheetDrags(): Modifier = pointerInput(Unit) {
    detectVerticalDragGestures(
        onDragStart = {},
        onDragEnd = {},
        onDragCancel = {},
        onVerticalDrag = { change, _ -> change.consume() },
    )
}

@Composable
fun EveningChoiceSheet(onPick: (Choice) -> Unit, onDismiss: () -> Unit = {}) {
    val colors = AppTheme.colors
    val choices = listOf(
        Choice.GAME to "Game",
        Choice.PROJECT to "Project",
        Choice.READ to "Read",
        Choice.CHILL to "Chill",
    )
    AppSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .blockSheetDrags()
                .padding(Spacing.l),
        ) {
            Text(
                "What's the evening for?",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Text(
                "Until 22:30",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            choices.chunked(2).forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.m),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    row.forEach { (choice, label) ->
                        EveningTile(choice, label, Modifier.weight(1f), onPick)
                    }
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End).padding(top = Spacing.s),
            ) {
                Text("Not now")
            }
        }
    }
}

@Composable
private fun RowScope.EveningTile(
    choice: Choice,
    label: String,
    modifier: Modifier,
    onPick: (Choice) -> Unit,
) {
    val colors = AppTheme.colors
    val palette = blockColors(BlockType.EveningChoice(choice))
    Box(
        modifier
            .height(Metrics.tile)
            .background(palette.container, AppShapes.card)
            .clickable { onPick(choice) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                eveningIcon(choice),
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(Metrics.iconEvening),
            )
            Text(label, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        }
    }
}

private fun eveningIcon(choice: Choice): ImageVector = when (choice) {
    Choice.GAME -> Icons.Outlined.SportsEsports
    Choice.PROJECT -> Icons.Outlined.Code
    Choice.READ -> Icons.AutoMirrored.Outlined.MenuBook
    Choice.CHILL -> Icons.Outlined.Weekend
}

private const val CORRECT_SNAP = 5

private val correctableTypes = listOf(BlockType.Cv, BlockType.Coding, BlockType.Gym, BlockType.Free)

/**
 * Corrects one block of a past day (2.1.0). [RowFix.END_AND_TYPE] fixes when a done block ended and what it
 * became; [RowFix.MARK_DONE] marks a missed block as happened. Each tap commits at once through [onEdit].
 */
@Composable
fun CorrectBlockSheet(
    row: RecapRow,
    use24: Boolean,
    onEdit: (RecordEdit) -> Unit,
    onDismiss: () -> Unit,
) {
    val index = row.segmentIndex ?: return
    AppSheet(onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            when (row.fix) {
                RowFix.END_AND_TYPE -> CorrectEndBody(row, index, use24, onEdit, onDismiss)
                RowFix.MARK_DONE -> CorrectMissingBody(row, index, onEdit, onDismiss)
                RowFix.NONE -> Unit
            }
        }
    }
}

@Composable
private fun CorrectEndBody(
    row: RecapRow,
    index: Int,
    use24: Boolean,
    onEdit: (RecordEdit) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    var endMin by remember(row.id) { mutableStateOf(row.endMin ?: 0) }
    Text("Correct ${row.title} ${row.range}", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
    Text("Ended at", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
    Text(label(endMin, use24), style = MaterialTheme.typography.titleLarge)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        listOf(-15, -5, 5, 15).forEach { delta ->
            TextButton(
                onClick = {
                    endMin = (endMin + delta).coerceAtLeast(0)
                    onEdit(RecordEdit.SetEnd(index, endMin))
                },
                modifier = Modifier.weight(1f),
            ) { Text(if (delta > 0) "+$delta" else "\u2212${-delta}") }
        }
    }
    Text("What did you do?", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        correctableTypes.forEach { type ->
            TextButton(onClick = { onEdit(RecordEdit.Retype(index, type)) }) { Text(blockTitle(type)) }
        }
    }
    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
}

@Composable
private fun CorrectMissingBody(
    row: RecapRow,
    index: Int,
    onEdit: (RecordEdit) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    Text(
        "${row.title} ${row.range} was missed",
        style = MaterialTheme.typography.titleLarge,
        color = colors.textPrimary,
    )
    Text(
        "Did it happen after all? The block that ran into it is shortened to make room.",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        TextButton(onClick = {
            onEdit(RecordEdit.MarkDone(index))
            onDismiss()
        }) { Text("It happened") }
        TextButton(onClick = onDismiss) { Text("Cancel") }
    }
}

/**
 * Starts a walk (2.3.0): pick how long, see when to turn back and be home, then go. The -5/+5
 * limits and every label come from [WalkSheetUi], built from the same preview the walk commits with.
 */
@Composable
fun WalkSheet(
    ui: WalkSheetUi,
    onStep: (Int) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    AppSheet(onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text("Take a walk", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.s),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WalkStepButton("\u22125", ui.canShrink, Modifier.weight(1f)) { onStep(-5) }
                Text(
                    "${ui.minutes} min",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(2f),
                )
                WalkStepButton("+5", ui.canGrow, Modifier.weight(1f)) { onStep(5) }
            }
            WalkSheetRow(Icons.Outlined.WbTwilight, "Turn back at ${ui.turnBackAt}")
            WalkSheetRow(
                Icons.Outlined.Home,
                if (ui.cappedByKidTime) "Home by ${ui.homeAt}, before kid time" else "Home by ${ui.homeAt}",
            )
            ui.afterTitle?.let { after ->
                Text(
                    buildString {
                        append("Then $after")
                        ui.afterEnd?.let { append(" until $it") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = Spacing.l),
                )
            }
            Text(
                "Rings like a timer at half time and at the end, even on silent.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = Spacing.s),
            )
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
                shape = AppShapes.chip,
            ) { Text("Start walk") }
        }
    }
}

@Composable
private fun WalkStepButton(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier, shape = AppShapes.chip) {
        Text(label)
    }
}

@Composable
private fun WalkSheetRow(icon: ImageVector, text: String) {
    val colors = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(Metrics.iconSm))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(start = Spacing.s),
        )
    }
}

