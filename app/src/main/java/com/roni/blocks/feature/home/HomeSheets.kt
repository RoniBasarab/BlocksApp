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
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockDetailSheet(ui: BlockSheetUi, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = AppShapes.sheet,
        containerColor = colors.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .blockSheetDrags()
                .padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(ui.title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            SheetRow("Planned", ui.planned, colors.textPrimary)
            ui.actual?.let { SheetRow("Actual", it, colors.textPrimary) }
            ui.delta?.let { SheetRow("Drift", it, colors.textSecondary) }
            ui.pauses?.let { SheetRow("Pauses", it, colors.textSecondary) }
        }
    }
}

@Composable
private fun SheetRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    val colors = AppTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EveningChoiceSheet(onPick: (Choice) -> Unit) {
    val colors = AppTheme.colors
    val choices = listOf(
        Choice.GAME to "Game",
        Choice.PROJECT to "Project",
        Choice.READ to "Read",
        Choice.CHILL to "Chill",
    )
    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = AppShapes.sheet,
        containerColor = colors.surface,
        dragHandle = null,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = false,
            shouldDismissOnClickOutside = false,
        ),
    ) {
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

@Composable
fun ResumeDialog(
    onResumeWhereIWas: () -> Unit,
    onJumpToNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text("Interrupted", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        },
        text = {
            Text(
                "Pick up where you left off, or jump ahead to now.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onResumeWhereIWas) { Text("Resume where I was") }
        },
        dismissButton = {
            TextButton(onClick = onJumpToNow) { Text("Jump to now") }
        },
    )
}
