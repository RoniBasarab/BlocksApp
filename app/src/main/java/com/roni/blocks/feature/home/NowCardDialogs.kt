package com.roni.blocks.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
)

internal fun pauseBody(blockTitle: String): String =
    "For short breaks like the bathroom or a call: $blockTitle keeps its remaining time, " +
        "and the paused minutes come out of the next block."

internal fun emergencyBody(): String =
    "For something urgent: the day ends, nothing counts as debt, and the day is written up as " +
        "an emergency stop."

internal fun startNextBody(): String =
    "Ends the current block and starts the next one; if you're late, the time you ran over counts as debt."

internal val NowButtonId.needsConfirmation: Boolean
    get() = when (this) {
        NowButtonId.PAUSE, NowButtonId.EMERGENCY -> true
        NowButtonId.NEXT, NowButtonId.RESUME, NowButtonId.KID_ASLEEP,
        NowButtonId.CHOOSE_EVENING, NowButtonId.END_DAY,
        -> false
    }

internal fun NowButtonId.confirmation(blockTitle: String): Confirmation? = when (this) {
    NowButtonId.PAUSE -> Confirmation(
        title = "Pause $blockTitle?",
        body = pauseBody(blockTitle),
        confirmLabel = "Pause",
        askAgain = true,
    )
    NowButtonId.EMERGENCY -> Confirmation(
        title = "End the day?",
        body = emergencyBody(),
        confirmLabel = "End day",
        askAgain = false,
    )
    NowButtonId.NEXT, NowButtonId.RESUME, NowButtonId.KID_ASLEEP,
    NowButtonId.CHOOSE_EVENING, NowButtonId.END_DAY,
    -> null
}

@Composable
internal fun NowCardInfoDialog(blockTitle: String, onDismiss: () -> Unit) {
    val rows = listOf(
        "Pause" to pauseBody(blockTitle),
        "Emergency" to emergencyBody(),
        "Start next" to startNextBody(),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What the buttons do", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                rows.forEach { (label, body) ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            body,
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
        confirmButton = { TextButton(onClick = { onConfirm(ask) }) { Text(spec.confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
