package com.roni.blocks.feature.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.roni.blocks.core.model.CategoryPalette
import com.roni.blocks.designsystem.AppSheet
import com.roni.blocks.designsystem.AppShapes
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.Leak
import com.roni.blocks.designsystem.Spacing

/**
 * The category editor's sheet (3.1.0): name, a 12-colour palette, Pomodoro and (yours only) "counts
 * as work". [CategorySheet] takes plain values and callbacks so it holds no state of its own.
 */
@Composable
fun CategorySheet(
    ui: CategorySheetUi,
    onAction: (CategoryAction) -> Unit,
) {
    val colors = AppTheme.colors
    AppSheet(onDismiss = { onAction(CategoryAction.Dismiss) }) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                if (ui.id == null) "New category" else ui.name,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            OutlinedTextField(
                value = ui.name,
                onValueChange = { if (it.length <= 16) onAction(CategoryAction.Name(it)) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("cat_name"),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                CategoryPalette.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        row.forEach { argb ->
                            ColorSwatch(
                                argb = argb,
                                selected = argb == ui.colorArgb,
                                onClick = { onAction(CategoryAction.Color(argb)) },
                                modifier = Modifier.testTag("cat_color_${CategoryPalette.indexOf(argb)}"),
                            )
                        }
                    }
                }
            }
            SwitchRow("Pomodoro", "50 min focus, 10 min break", ui.pomodoro) { onAction(CategoryAction.Pomodoro(it)) }
            if (!ui.builtIn) {
                SwitchRow("Counts as work", "Shows in your work hours", ui.work) { onAction(CategoryAction.Work(it)) }
            }
            ui.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.leak) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!ui.builtIn) {
                    TextButton(onClick = { onAction(CategoryAction.Delete) }) {
                        Text("Delete", style = MaterialTheme.typography.labelLarge, color = colors.leak)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onAction(CategoryAction.Save) }, shape = AppShapes.chip) {
                    Text("Save", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    if (ui.confirmDelete) {
        AlertDialog(
            onDismissRequest = { onAction(CategoryAction.CancelDelete) },
            title = { Text("Delete ${ui.name}?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Its blocks in your templates become free time. Days you already had keep it.") },
            confirmButton = {
                TextButton(
                    onClick = { onAction(CategoryAction.ConfirmDelete) },
                    colors = ButtonDefaults.buttonColors(containerColor = Leak, contentColor = Color(0xFF1C1B19)),
                ) {
                    Text("Delete", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(CategoryAction.CancelDelete) }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }
}

@Composable
private fun ColorSwatch(argb: Long, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(36.dp)
            .background(Color(argb), CircleShape)
            .then(if (selected) Modifier.border(2.dp, AppTheme.colors.textPrimary, CircleShape) else Modifier)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = AppTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
