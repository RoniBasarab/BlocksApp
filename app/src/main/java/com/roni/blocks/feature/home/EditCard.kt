package com.roni.blocks.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.schedule.BlockEdit
import com.roni.blocks.core.schedule.Edge
import com.roni.blocks.core.schedule.PlanEditor
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppShapes
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.AppType
import com.roni.blocks.designsystem.Metrics
import com.roni.blocks.designsystem.Spacing

private val EditTypes = listOf(BlockType.Cv, BlockType.Coding, BlockType.Gym, BlockType.Free)

private val WorkTypes = listOf(BlockType.Cv, BlockType.Coding, BlockType.Gym)

private fun typeKey(type: BlockType): String = when (type) {
    BlockType.Cv -> "Cv"
    BlockType.Coding -> "Coding"
    BlockType.Gym -> "Gym"
    BlockType.Free -> "Free"
    else -> "Other"
}

@Composable
fun EditCard(
    block: CalendarBlockUi,
    onDone: () -> Unit,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val accent = c.block(block.type).accent
    AppCard(modifier.fillMaxWidth().testTag("edit_card"), borderColor = accent) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(Metrics.nowDot * 2).background(accent, CircleShape))
                Spacer(Modifier.width(Spacing.s))
                Text(
                    block.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onDone, modifier = Modifier.testTag("edit_done")) {
                    Text("Done", style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(
                "${hhmm(block.startMin)} – ${hhmm(block.endMin)} · ${duration(block.endMin - block.startMin)}",
                style = MaterialTheme.typography.titleSmall.merge(AppType.numbers),
                color = c.textPrimary,
            )
            when (block.edit) {
                BlockEdit.FULL -> {
                    TypeChips(block, onAction)
                    Steppers(block, showStart = true, showEnd = true, onAction = onAction)
                }
                BlockEdit.TYPE_ONLY -> {
                    TypeChips(block, onAction)
                    AddBlockRow(block, onAction)
                    Caption("Pick a type to turn this free time into a block, or add a 30-minute block at its start.")
                }
                BlockEdit.END_ONLY -> {
                    Steppers(block, showStart = false, showEnd = true, onAction = onAction)
                    Caption("This block is running. You can move its end; use Next to switch to something else.")
                }
                BlockEdit.NONE -> Unit
            }
            Text("Changes apply to today only.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
    }
}

@Composable
private fun TypeChips(block: CalendarBlockUi, onAction: (HomeAction) -> Unit) {
    val c = AppTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        EditTypes.forEach { type ->
            val selected = block.type == type
            Text(
                blockTitle(type),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) c.onPrimary else c.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(AppShapes.chip)
                    .background(if (selected) c.block(type).accent else c.surfaceRaised)
                    .clickable { onAction(HomeAction.Retype(block.id, type)) }
                    .padding(vertical = Spacing.s)
                    .testTag("edit_type_${typeKey(type)}"),
            )
        }
    }
}

@Composable
private fun AddBlockRow(block: CalendarBlockUi, onAction: (HomeAction) -> Unit) {
    val c = AppTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Add a block:", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        WorkTypes.forEach { type ->
            Text(
                blockTitle(type),
                style = MaterialTheme.typography.labelLarge,
                color = c.textPrimary,
                modifier = Modifier
                    .clip(AppShapes.chip)
                    .background(c.surfaceRaised)
                    .clickable { onAction(HomeAction.Insert(block.id, type)) }
                    .padding(horizontal = Spacing.m, vertical = Spacing.s)
                    .testTag("edit_add_${typeKey(type)}"),
            )
        }
    }
}

@Composable
private fun Steppers(
    block: CalendarBlockUi,
    showStart: Boolean,
    showEnd: Boolean,
    onAction: (HomeAction) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        if (showStart) {
            Stepper(
                label = "Start",
                value = hhmm(block.startMin),
                minusTag = "edit_start_minus",
                plusTag = "edit_start_plus",
                onMinus = { onAction(HomeAction.StepEdge(block.id, Edge.TOP, -PlanEditor.SNAP)) },
                onPlus = { onAction(HomeAction.StepEdge(block.id, Edge.TOP, PlanEditor.SNAP)) },
                modifier = Modifier.weight(1f),
            )
        }
        if (showEnd) {
            Stepper(
                label = "End",
                value = hhmm(block.endMin),
                minusTag = "edit_end_minus",
                plusTag = "edit_end_plus",
                onMinus = { onAction(HomeAction.StepEdge(block.id, Edge.BOTTOM, -PlanEditor.SNAP)) },
                onPlus = { onAction(HomeAction.StepEdge(block.id, Edge.BOTTOM, PlanEditor.SNAP)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    minusTag: String,
    plusTag: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        StepButton("–", minusTag, onMinus)
        Text(value, style = MaterialTheme.typography.titleSmall.merge(AppType.numbers), color = c.textPrimary)
        StepButton("+", plusTag, onPlus)
    }
}

@Composable
private fun StepButton(symbol: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(AppShapes.chip)
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
}
