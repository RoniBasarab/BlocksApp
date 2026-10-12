package com.roni.blocks.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import com.roni.blocks.designsystem.Leak
import com.roni.blocks.designsystem.Spacing
import kotlinx.serialization.Serializable

internal sealed interface NowCardDialog {
    data object Info : NowCardDialog
    data class Confirm(val id: NowButtonId) : NowCardDialog
}

@Serializable
internal data class SuppressedConfirmations(val ids: Set<String> = emptySet())

internal const val SUPPRESSED_CONFIRMATIONS = "suppressed_confirmations"

internal fun NowButtonId.toStorageKey(): String = name

internal fun suppressedFromKeys(keys: Set<String>): Set<NowButtonId> =
    NowButtonId.entries.filterTo(mutableSetOf()) { it.toStorageKey() in keys }

internal fun keysFromSuppressed(ids: Set<NowButtonId>): Set<String> =
    ids.mapTo(mutableSetOf()) { it.toStorageKey() }

internal data class Confirmation(
    val title: String,
    val body: String,
    val confirmLabel: String,
    val askAgain: Boolean,
    val red: Boolean = false,
)

internal fun pauseBody(blockTitle: String, endsAt: String?): String =
    "For short breaks like the bathroom or a call: the timer stops counting your $blockTitle " +
        "time, and $blockTitle still ends at ${endsAt ?: "its planned end"}."

internal fun emergencyBody(): String =
    "The day has been disrupted by an emergency. Stop all schedules for today?"

internal data class InfoRow(val label: String, val body: String)

internal val infoRows = listOf(
    InfoRow("Pause", "Stops counting your time for a short break; the block still ends on time."),
    InfoRow(
        "Next",
        "Moves on now: finish a work block early and the rest becomes free time, end free time early " +
            "and the next block starts right away, and any time you ran over counts as debt.",
    ),
    InfoRow("Emergency", "Stops all schedules for today. Nothing after it counts as debt."),
)

internal val NowButtonId.needsConfirmation: Boolean
    get() = when (this) {
        NowButtonId.PAUSE, NowButtonId.EMERGENCY -> true
        NowButtonId.NEXT, NowButtonId.WALK, NowButtonId.RESUME, NowButtonId.KID_ASLEEP,
        NowButtonId.CHOOSE_EVENING, NowButtonId.SKIP_DISHES, NowButtonId.END_DAY,
        -> false
    }

internal fun NowButtonId.confirmation(blockTitle: String, endsAt: String? = null): Confirmation? = when (this) {
    NowButtonId.PAUSE -> Confirmation(
        title = "Pause $blockTitle?",
        body = pauseBody(blockTitle, endsAt),
        confirmLabel = "Pause",
        askAgain = true,
    )
    NowButtonId.EMERGENCY -> Confirmation(
        title = "Emergency?",
        body = emergencyBody(),
        confirmLabel = "Stop today",
        askAgain = false,
        red = true,
    )
    NowButtonId.NEXT, NowButtonId.WALK, NowButtonId.RESUME, NowButtonId.KID_ASLEEP,
    NowButtonId.CHOOSE_EVENING, NowButtonId.SKIP_DISHES, NowButtonId.END_DAY,
    -> null
}

@Composable
internal fun NowCardInfoDialog(blockTitle: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What the buttons do", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                infoRows.forEach { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(row.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            row.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
internal fun ConfirmationDialog(spec: Confirmation, onConfirm: (askAgain: Boolean) -> Unit, onDismiss: () -> Unit) {
    var ask by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(spec.title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(
                    spec.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (spec.askAgain) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { ask = !ask },
                    ) {
                        Checkbox(checked = ask, onCheckedChange = { ask = it })
                        Text("Don't ask again", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(ask) },
                colors = if (spec.red) {
                    ButtonDefaults.textButtonColors(contentColor = Leak)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) { Text(spec.confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
